package io.github.sandking.fastmcp.agentscope.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.sandking.fastmcp.agentscope.ToolArgumentResolver;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class FastMcpAgentScopeHttpConfigurationIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void appliesHttpHeadersQueryParamsAndCookiesToAgentScopeManagedClient() throws Exception {
        AtomicInteger listToolsRequests = new AtomicInteger();
        AtomicReference<String> listToolsAuthorization = new AtomicReference<>();
        AtomicReference<String> listToolsQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog/mcp",
                exchange -> handleMcp(exchange, listToolsRequests, listToolsAuthorization, listToolsQuery));
        server.start();
        McpClientWrapper client = null;
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/catalog/mcp";
            SafeMcpConfiguration configuration = SafeMcpConfiguration.builder()
                    .server(SafeMcpServerConfiguration.builder("catalog")
                            .transport("streamable-http")
                            .endpoint(endpoint)
                            .httpHeader("Authorization", "Bearer test-token")
                            .httpQueryParam("region", "cn")
                            .tool(SafeMcpToolConfiguration.builder("listCatalogItems")
                                    .name("list_catalog_items")
                                    .description("List catalog items.")
                                    .inputSchema(emptySchema())
                                    .build())
                            .build())
                    .build();

            client = new FastMcpAgentScopeManagedClientFactory().createClients(configuration).get(0);
            client.initialize().block();
            List<McpSchema.Tool> tools = client.listTools().block();

            assertThat(tools).extracting(McpSchema.Tool::name).containsExactly("listCatalogItems");
            assertThat(listToolsRequests).hasValueGreaterThanOrEqualTo(1);
            assertThat(listToolsAuthorization).hasValue("Bearer test-token");
            assertThat(listToolsQuery).hasValue("region=cn");
        } finally {
            if (client != null) {
                client.close();
            }
            server.stop(0);
        }
    }

    @Test
    void registersOnlyVirtualAgentScopeToolOverStreamableHttpManagedClient() throws Exception {
        AtomicInteger callToolRequests = new AtomicInteger();
        AtomicReference<String> callToolName = new AtomicReference<>();
        AtomicReference<String> callToolStatus = new AtomicReference<>();
        AtomicReference<String> callToolUserId = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/orders/mcp",
                exchange -> handleSafeMcp(exchange, callToolRequests, callToolName, callToolStatus,
                        callToolUserId));
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/orders/mcp";
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(FastMcpAgentScopeSafeAutoConfiguration.class))
                    .withUserConfiguration(AgentScopeResolverConfiguration.class)
                    .withPropertyValues(safeOrderProperties(endpoint))
                    .run(context -> {
                        assertThat(context).hasBean("fastMcpAgentScopeSafeRegistrar");
                        assertThat(context).hasSingleBean(Toolkit.class);
                        assertThat(context).doesNotHaveBean(McpClientWrapper.class);

                        Toolkit toolkit = context.getBean(Toolkit.class);
                        assertThat(toolkit.getToolNames()).contains("get_my_orders");
                        assertThat(toolkit.getToolNames()).doesNotContain("getOrdersByUserId");
                        assertThat(toolkit.getToolNames()).doesNotContain("mcp__orders__getOrdersByUserId");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> virtualProperties =
                                (Map<String, Object>) toolkit.getTool("get_my_orders")
                                        .getParameters()
                                        .get("properties");
                        assertThat(virtualProperties)
                                .containsKey("status")
                                .doesNotContainKey("userId");

                        ToolResultBlock result = toolkit.callTool(ToolCallParam.builder()
                                .toolUseBlock(ToolUseBlock.builder()
                                        .id("call-1")
                                        .name("get_my_orders")
                                        .input(Map.of("status", "PAID"))
                                        .content("{\"status\":\"PAID\"}")
                                        .build())
                                .input(Map.of("status", "PAID"))
                                .runtimeContext(RuntimeContext.builder()
                                        .put(UserContext.class, new UserContext("user-123"))
                                        .build())
                                .build()).block();

                        assertThat(result.getName()).isEqualTo("get_my_orders");
                        assertThat(((TextBlock) result.getOutput().get(0)).getText())
                                .isEqualTo("orders for user-123 with status PAID");
                        assertThat(result.getMetadata()).isEmpty();
                        assertThat(callToolRequests).hasValue(1);
                        assertThat(callToolName).hasValue("getOrdersByUserId");
                        assertThat(callToolStatus).hasValue("PAID");
                        assertThat(callToolUserId).hasValue("user-123");
                    });
        } finally {
            server.stop(0);
        }
    }

    private static void handleMcp(HttpExchange exchange, AtomicInteger listToolsRequests,
            AtomicReference<String> listToolsAuthorization, AtomicReference<String> listToolsQuery)
            throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            write(exchange, 405, "application/json", "{\"error\":\"GET stream not supported\"}");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            write(exchange, 405, "application/json", "{\"error\":\"method not allowed\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.contains("\"method\":\"initialize\"")) {
            exchange.getResponseHeaders().add("Mcp-Session-Id", "session-1");
            exchange.getResponseHeaders().add("Set-Cookie", "route=sticky; Path=/");
            write(exchange, 200, "application/json", "{\"jsonrpc\":\"2.0\",\"id\":" + idLiteral(body)
                    + ",\"result\":{\"protocolVersion\":\"2025-06-18\","
                    + "\"capabilities\":{\"tools\":{\"listChanged\":true}},"
                    + "\"serverInfo\":{\"name\":\"fake-catalog-mcp\",\"version\":\"1.0.0\"}}}");
            return;
        }

        if (!"session-1".equals(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"))
                || !String.valueOf(exchange.getRequestHeaders().getFirst("Cookie")).contains("route=sticky")) {
            write(exchange, 404, "application/json", "{\"error\":\"session not found\"}");
            return;
        }

        if (body.contains("\"method\":\"notifications/initialized\"")) {
            write(exchange, 202, null, "");
            return;
        }
        if (body.contains("\"method\":\"tools/list\"")) {
            listToolsRequests.incrementAndGet();
            listToolsAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            listToolsQuery.set(exchange.getRequestURI().getRawQuery());
            write(exchange, 200, "text/event-stream;charset=UTF-8", "event:message\n"
                    + "data:{\"jsonrpc\":\"2.0\",\"id\":" + idLiteral(body)
                    + ",\"result\":{\"tools\":[{\"name\":\"listCatalogItems\","
                    + "\"description\":\"Raw project catalog\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":{},\"required\":[]}}]}}\n\n");
            return;
        }
        write(exchange, 400, "application/json", "{\"error\":\"unsupported request\"}");
    }

    private static ObjectNode emptySchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.set("properties", JsonNodeFactory.instance.objectNode());
        schema.putArray("required");
        return schema;
    }

    private static String[] safeOrderProperties(String endpoint) {
        return new String[] {
                "fastmcp.safe.servers.orders.transport=streamable-http",
                "fastmcp.safe.servers.orders.endpoint=" + endpoint,
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.name=get_my_orders",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.description=Get orders for the authenticated user.",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.type=object",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.properties.status.type=string",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.required[0]=status",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.argument-mappings.status=status",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.injected-arguments.userId=currentUserId"
        };
    }

    private static void handleSafeMcp(HttpExchange exchange, AtomicInteger callToolRequests,
            AtomicReference<String> callToolName, AtomicReference<String> callToolStatus,
            AtomicReference<String> callToolUserId) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            write(exchange, 405, "application/json", "{\"error\":\"GET stream not supported\"}");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            write(exchange, 405, "application/json", "{\"error\":\"method not allowed\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.contains("\"method\":\"initialize\"")) {
            exchange.getResponseHeaders().add("Mcp-Session-Id", "session-1");
            exchange.getResponseHeaders().add("Set-Cookie", "route=sticky; Path=/");
            write(exchange, 200, "application/json", "{\"jsonrpc\":\"2.0\",\"id\":" + idLiteral(body)
                    + ",\"result\":{\"protocolVersion\":\"2025-06-18\","
                    + "\"capabilities\":{\"tools\":{\"listChanged\":true}},"
                    + "\"serverInfo\":{\"name\":\"fake-orders-mcp\",\"version\":\"1.0.0\"}}}");
            return;
        }

        if (!"session-1".equals(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"))
                || !String.valueOf(exchange.getRequestHeaders().getFirst("Cookie")).contains("route=sticky")) {
            write(exchange, 404, "application/json", "{\"error\":\"session not found\"}");
            return;
        }

        if (body.contains("\"method\":\"notifications/initialized\"")) {
            write(exchange, 202, null, "");
            return;
        }
        if (body.contains("\"method\":\"tools/list\"")) {
            write(exchange, 200, "text/event-stream;charset=UTF-8", "event:message\n"
                    + "data:{\"jsonrpc\":\"2.0\",\"id\":" + idLiteral(body)
                    + ",\"result\":{\"tools\":[{\"name\":\"getOrdersByUserId\","
                    + "\"description\":\"Raw order lookup by user id\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"userId\":{\"type\":\"string\"},"
                    + "\"status\":{\"type\":\"string\"}},\"required\":[\"userId\",\"status\"]}}]}}\n\n");
            return;
        }
        if (body.contains("\"method\":\"tools/call\"")) {
            JsonNode params = OBJECT_MAPPER.readTree(body).path("params");
            JsonNode arguments = params.path("arguments");
            String name = params.path("name").asText();
            String status = arguments.path("status").asText();
            String userId = arguments.path("userId").asText();
            callToolRequests.incrementAndGet();
            callToolName.set(name);
            callToolStatus.set(status);
            callToolUserId.set(userId);
            write(exchange, 200, "text/event-stream;charset=UTF-8", "event:message\n"
                    + "data:{\"jsonrpc\":\"2.0\",\"id\":" + idLiteral(body)
                    + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"orders for " + userId
                    + " with status " + status + "\"}],\"isError\":false}}\n\n");
            return;
        }
        write(exchange, 400, "application/json", "{\"error\":\"unsupported request\"}");
    }

    private static String idLiteral(String body) throws IOException {
        JsonNode id = OBJECT_MAPPER.readTree(body).get("id");
        return id == null ? "null" : id.toString();
    }

    private static void write(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        if (status == 202 && bytes.length == 0) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AgentScopeResolverConfiguration {
        @Bean
        Toolkit toolkit() {
            return new Toolkit();
        }

        @Bean("currentUserId")
        ToolArgumentResolver currentUserId() {
            return param -> param.getRuntimeContext().get(UserContext.class).userId();
        }
    }

    private static final class UserContext {
        private final String userId;

        private UserContext(String userId) {
            this.userId = userId;
        }

        private String userId() {
            return userId;
        }
    }
}
