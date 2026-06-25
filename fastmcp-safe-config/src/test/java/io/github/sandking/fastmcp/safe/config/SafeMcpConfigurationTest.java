package io.github.sandking.fastmcp.safe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeMcpConfigurationTest {
    @Test
    void buildsImmutableServerAndToolConfiguration() {
        ObjectNode schema = virtualOrderSchema();
        SafeMcpToolConfiguration tool = SafeMcpToolConfiguration.builder("getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(schema)
                .mapArgument("status", "orderStatus")
                .injectArgument("userId", "currentUserId")
                .readOnly(true)
                .build();
        schema.withObject("/properties").set("userId",
                JsonNodeFactory.instance.objectNode().put("type", "string"));

        SafeMcpServerConfiguration server = SafeMcpServerConfiguration.builder("orders")
                .transport("stdio")
                .command("node")
                .argument("orders-mcp.js")
                .environment("ORDERS_ENV", "test")
                .httpHeader("X-App-Id", "orders-agent")
                .httpQueryParam("region", "cn")
                .tool(tool)
                .build();
        SafeMcpConfiguration configuration = SafeMcpConfiguration.builder()
                .server(server)
                .build();

        assertEquals("orders", configuration.server("orders").name());
        assertEquals("stdio", configuration.server("orders").transport());
        assertEquals("node", configuration.server("orders").command());
        assertEquals("orders-mcp.js", configuration.server("orders").arguments().get(0));
        assertEquals("test", configuration.server("orders").environment().get("ORDERS_ENV"));
        assertEquals("orders-agent", configuration.server("orders").httpHeaders().get("X-App-Id"));
        assertEquals("cn", configuration.server("orders").httpQueryParams().get("region"));
        assertEquals("get_my_orders", configuration.server("orders").tool("getOrdersByUserId").name());
        assertEquals(Map.of("status", "orderStatus"), tool.argumentMappings());
        assertEquals(Map.of("userId", "currentUserId"), tool.injectedArguments());
        assertTrue(tool.readOnly());
        assertFalse(tool.inputSchema().toString().contains("userId"));

        assertThrows(UnsupportedOperationException.class,
                () -> configuration.servers().put("other", server));
        assertThrows(UnsupportedOperationException.class,
                () -> server.tools().put("other", tool));
        assertThrows(UnsupportedOperationException.class,
                () -> server.httpHeaders().put("Authorization", "Bearer token"));
        assertThrows(UnsupportedOperationException.class,
                () -> server.httpQueryParams().put("tenant", "internal"));
        assertThrows(UnsupportedOperationException.class,
                () -> tool.injectedArguments().put("tenantId", "currentTenantId"));
    }

    @Test
    void rejectsMissingVirtualMetadata() {
        assertConfigError("INVALID_TOOL_NAME",
                () -> SafeMcpToolConfiguration.builder("getOrdersByUserId")
                        .description("Get orders for the authenticated user.")
                        .inputSchema(virtualOrderSchema())
                        .build());
        assertConfigError("INVALID_TOOL_DESCRIPTION",
                () -> SafeMcpToolConfiguration.builder("getOrdersByUserId")
                        .name("get_my_orders")
                        .inputSchema(virtualOrderSchema())
                        .build());
        assertConfigError("INVALID_TOOL_SCHEMA",
                () -> SafeMcpToolConfiguration.builder("getOrdersByUserId")
                        .name("get_my_orders")
                        .description("Get orders for the authenticated user.")
                        .build());
    }

    @Test
    void rejectsDuplicateServersAndTools() {
        SafeMcpServerConfiguration server = SafeMcpServerConfiguration.builder("orders")
                .tool(currentUserOrdersTool())
                .build();
        assertConfigError("DUPLICATE_SERVER",
                () -> SafeMcpConfiguration.builder()
                        .server(server)
                        .server(server)
                        .build());
        assertConfigError("DUPLICATE_TOOL",
                () -> SafeMcpServerConfiguration.builder("orders")
                        .tool(currentUserOrdersTool())
                        .tool(currentUserOrdersTool())
                        .build());
    }

    @Test
    void rejectsBlankArgumentMappingAndInjectionSource() {
        assertConfigError("INVALID_ARGUMENT_MAPPING",
                () -> SafeMcpToolConfiguration.builder("getOrdersByUserId")
                        .name("get_my_orders")
                        .description("Get orders for the authenticated user.")
                        .inputSchema(virtualOrderSchema())
                        .mapArgument(" ", "orderStatus")
                        .build());
        assertConfigError("INVALID_INJECTED_ARGUMENT",
                () -> SafeMcpToolConfiguration.builder("getOrdersByUserId")
                        .name("get_my_orders")
                        .description("Get orders for the authenticated user.")
                        .inputSchema(virtualOrderSchema())
                        .injectArgument("userId", " ")
                        .build());
    }

    @Test
    void storesManagedClientConnectionSettings() {
        SafeMcpServerConfiguration server = SafeMcpServerConfiguration.builder("orders")
                .enabled(false)
                .transport("streamable-http")
                .endpoint("https://mcp.example.test/mcp")
                .sseEndpoint("/events")
                .requestTimeout(Duration.ofSeconds(3))
                .initializationTimeout(Duration.ofSeconds(5))
                .clientName("fastmcp-orders")
                .clientVersion("0.1-test")
                .httpCookiesEnabled(false)
                .httpHeader("Authorization", "Bearer test-token")
                .httpQueryParam("region", "cn")
                .build();

        assertFalse(server.enabled());
        assertEquals("streamable-http", server.transport());
        assertEquals("https://mcp.example.test/mcp", server.endpoint());
        assertEquals("/events", server.sseEndpoint());
        assertTrue(server.requestTimeout().isPresent());
        assertEquals(Duration.ofSeconds(3), server.requestTimeout().get());
        assertTrue(server.initializationTimeout().isPresent());
        assertEquals(Duration.ofSeconds(5), server.initializationTimeout().get());
        assertEquals("fastmcp-orders", server.clientName());
        assertEquals("0.1-test", server.clientVersion());
        assertFalse(server.httpCookiesEnabled());
        assertEquals("Bearer test-token", server.httpHeaders().get("Authorization"));
        assertEquals("cn", server.httpQueryParams().get("region"));
    }

    private static SafeMcpToolConfiguration currentUserOrdersTool() {
        return SafeMcpToolConfiguration.builder("getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(virtualOrderSchema())
                .injectArgument("userId", "currentUserId")
                .build();
    }

    private static ObjectNode virtualOrderSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = JsonNodeFactory.instance.objectNode();
        properties.set("status", JsonNodeFactory.instance.objectNode().put("type", "string"));
        schema.set("properties", properties);
        schema.putArray("required").add("status");
        return schema;
    }

    private static void assertConfigError(String code, Executable executable) {
        SafeMcpConfigException exception = assertThrows(SafeMcpConfigException.class, executable::execute);
        assertEquals(code, exception.code());
    }

    @FunctionalInterface
    private interface Executable {
        void execute();
    }
}
