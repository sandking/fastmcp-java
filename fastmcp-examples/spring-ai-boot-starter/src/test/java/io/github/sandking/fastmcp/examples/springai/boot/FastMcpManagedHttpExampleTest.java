package io.github.sandking.fastmcp.examples.springai.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.sandking.fastmcp.springai.SpringAiToolArgumentResolver;
import io.github.sandking.fastmcp.springai.boot.FastMcpSafeAutoConfiguration;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class FastMcpManagedHttpExampleTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    void createsManagedStreamableHttpClientAndExposesOnlySafeVirtualTool() throws Exception {
        AtomicReference<String> receivedToolName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> receivedArguments = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> handleMcp(exchange, receivedToolName, receivedArguments));
        server.start();
        try {
            int port = server.getAddress().getPort();
            ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(FastMcpSafeAutoConfiguration.class))
                    .withUserConfiguration(TenantResolverConfiguration.class)
                    .withPropertyValues(managedCatalogProperties(port));

            contextRunner.run(context -> {
                assertThat(context).hasBean("fastMcpSafeToolCallbackProvider");
                assertThat(context).hasSingleBean(ToolCallbackProvider.class);

                ToolCallbackProvider provider = context.getBean(ToolCallbackProvider.class);
                assertThat(provider.getToolCallbacks()).hasSize(1);
                assertThat(provider.getToolCallbacks()[0].getToolDefinition().name()).isEqualTo("search_catalog");
                assertThat(provider.getToolCallbacks()[0].getToolDefinition().inputSchema())
                        .contains("keyword")
                        .doesNotContain("tenantId");

                String result = provider.getToolCallbacks()[0].call("{\"keyword\":\"phone\"}",
                        new ToolContext(Map.of("tenantId", "tenant-1")));

                assertThat(result).contains("tenant-1").contains("phone");
                assertThat(receivedToolName).hasValue("searchCatalogByTenant");
                assertThat(receivedArguments.get())
                        .containsEntry("tenantId", "tenant-1")
                        .containsEntry("keyword", "phone");
            });
        } finally {
            server.stop(0);
        }
    }

    private static String[] managedCatalogProperties(int port) {
        return new String[] {
                "fastmcp.safe.servers.catalog.transport=streamable-http",
                "fastmcp.safe.servers.catalog.endpoint=http://127.0.0.1:" + port + "/mcp",
                "fastmcp.safe.servers.catalog.tools.searchCatalogByTenant.name=search_catalog",
                "fastmcp.safe.servers.catalog.tools.searchCatalogByTenant.description=Search the fake catalog.",
                "fastmcp.safe.servers.catalog.tools.searchCatalogByTenant.input-schema.type=object",
                "fastmcp.safe.servers.catalog.tools.searchCatalogByTenant.input-schema.properties.keyword.type=string",
                "fastmcp.safe.servers.catalog.tools.searchCatalogByTenant.input-schema.required[0]=keyword",
                "fastmcp.safe.servers.catalog.tools.searchCatalogByTenant.injected-arguments.tenantId=currentTenantId",
                "fastmcp.safe.servers.catalog.tools.searchCatalogByTenant.read-only=true"
        };
    }

    private static void handleMcp(HttpExchange exchange, AtomicReference<String> receivedToolName,
            AtomicReference<Map<String, Object>> receivedArguments) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            write(exchange, 405, "application/json", "{\"error\":\"GET stream not supported\"}");
            return;
        }
        if ("DELETE".equals(exchange.getRequestMethod())) {
            write(exchange, 202, null, "");
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
            write(exchange, 200, "text/event-stream;charset=UTF-8", "event:message\n"
                    + "data:{\"jsonrpc\":\"2.0\",\"id\":" + idLiteral(body)
                    + ",\"result\":{\"tools\":[{\"name\":\"searchCatalogByTenant\","
                    + "\"description\":\"Raw fake catalog search\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":{"
                    + "\"keyword\":{\"type\":\"string\"},\"tenantId\":{\"type\":\"string\"}},"
                    + "\"required\":[\"keyword\",\"tenantId\"]}}]}}\n\n");
            return;
        }
        if (body.contains("\"method\":\"tools/call\"")) {
            JsonNode params = OBJECT_MAPPER.readTree(body).path("params");
            Map<String, Object> arguments = OBJECT_MAPPER.convertValue(params.path("arguments"), MAP_TYPE);
            receivedToolName.set(params.path("name").asText());
            receivedArguments.set(arguments);
            String text = "catalog search for " + arguments.get("tenantId") + " keyword " + arguments.get("keyword");
            write(exchange, 200, "text/event-stream;charset=UTF-8", "event:message\n"
                    + "data:{\"jsonrpc\":\"2.0\",\"id\":" + idLiteral(body)
                    + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":"
                    + OBJECT_MAPPER.writeValueAsString(text) + "}],\"isError\":false}}\n\n");
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
    static class TenantResolverConfiguration {
        @Bean("currentTenantId")
        SpringAiToolArgumentResolver currentTenantId() {
            return context -> context.getContext().get("tenantId");
        }
    }
}
