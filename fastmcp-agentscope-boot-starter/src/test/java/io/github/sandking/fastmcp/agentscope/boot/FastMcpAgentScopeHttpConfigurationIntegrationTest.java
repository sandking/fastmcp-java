package io.github.sandking.fastmcp.agentscope.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

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
}
