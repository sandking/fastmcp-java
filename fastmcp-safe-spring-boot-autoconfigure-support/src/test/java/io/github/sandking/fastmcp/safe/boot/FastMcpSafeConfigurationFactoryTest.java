package io.github.sandking.fastmcp.safe.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FastMcpSafeConfigurationFactoryTest {
    @Test
    void createsFrameworkNeutralConfigurationFromBoundProperties() {
        FastMcpSafeProperties properties = new FastMcpSafeProperties();
        FastMcpSafeProperties.Server server = new FastMcpSafeProperties.Server();
        server.setEnabled(false);
        server.setTransport("streamable-http");
        server.setEndpoint("https://mcp.example.test/mcp");
        server.setSseEndpoint("/events");
        server.setRequestTimeout(Duration.ofSeconds(3));
        server.setInitializationTimeout(Duration.ofSeconds(5));
        server.setClientName("fastmcp-orders");
        server.setClientVersion("0.1-test");
        server.setArguments(List.of("orders-mcp.js"));
        server.setEnvironment(linkedMap("ORDERS_ENV", "test"));
        FastMcpSafeProperties.Http http = new FastMcpSafeProperties.Http();
        http.setHeaders(linkedMap("Authorization", "Bearer test-token"));
        http.setQueryParams(linkedMap("region", "cn"));
        FastMcpSafeProperties.Cookies cookies = new FastMcpSafeProperties.Cookies();
        cookies.setEnabled(false);
        http.setCookies(cookies);
        server.setHttp(http);

        FastMcpSafeProperties.Tool tool = new FastMcpSafeProperties.Tool();
        tool.setName("get_my_orders");
        tool.setDescription("Get orders for the authenticated user.");
        tool.setInputSchema(orderInputSchema());
        tool.setArgumentMappings(linkedMap("status", "orderStatus"));
        tool.setInjectedArguments(linkedMap("userId", "currentUserId", "tenantId", "currentTenantId"));
        tool.setReadOnly(true);
        tool.setConcurrencySafe(true);
        server.setTools(linkedMap("getOrdersByUserId", tool));
        properties.setServers(linkedMap("orders", server));

        SafeMcpConfiguration configuration = new FastMcpSafeConfigurationFactory().create(properties);
        SafeMcpToolConfiguration safeTool = configuration.server("orders").tool("getOrdersByUserId");

        assertThat(configuration.server("orders").enabled()).isFalse();
        assertThat(configuration.server("orders").transport()).isEqualTo("streamable-http");
        assertThat(configuration.server("orders").endpoint()).isEqualTo("https://mcp.example.test/mcp");
        assertThat(configuration.server("orders").sseEndpoint()).isEqualTo("/events");
        assertThat(configuration.server("orders").requestTimeout()).contains(Duration.ofSeconds(3));
        assertThat(configuration.server("orders").initializationTimeout()).contains(Duration.ofSeconds(5));
        assertThat(configuration.server("orders").clientName()).isEqualTo("fastmcp-orders");
        assertThat(configuration.server("orders").clientVersion()).isEqualTo("0.1-test");
        assertThat(configuration.server("orders").arguments()).containsExactly("orders-mcp.js");
        assertThat(configuration.server("orders").environment()).containsEntry("ORDERS_ENV", "test");
        assertThat(configuration.server("orders").httpHeaders()).containsEntry("Authorization", "Bearer test-token");
        assertThat(configuration.server("orders").httpQueryParams()).containsEntry("region", "cn");
        assertThat(configuration.server("orders").httpCookiesEnabled()).isFalse();
        assertThat(safeTool.name()).isEqualTo("get_my_orders");
        assertThat(safeTool.description()).isEqualTo("Get orders for the authenticated user.");
        assertThat(safeTool.inputSchema().toString()).doesNotContain("userId");
        assertThat(safeTool.argumentMappings()).containsEntry("status", "orderStatus");
        assertThat(safeTool.injectedArguments()).containsEntry("userId", "currentUserId")
                .containsEntry("tenantId", "currentTenantId");
        assertThat(safeTool.readOnly()).isTrue();
        assertThat(safeTool.concurrencySafe()).isTrue();
    }

    @Test
    void normalizesNullCollectionsForBootBinding() {
        FastMcpSafeProperties properties = new FastMcpSafeProperties();
        properties.setServers(null);

        FastMcpSafeProperties.Server server = new FastMcpSafeProperties.Server();
        server.setArguments(null);
        server.setEnvironment(null);
        server.setHttp(null);
        server.setTools(null);
        FastMcpSafeProperties.Http http = new FastMcpSafeProperties.Http();
        http.setHeaders(null);
        http.setQueryParams(null);
        http.setCookies(null);

        FastMcpSafeProperties.Tool tool = new FastMcpSafeProperties.Tool();
        tool.setInputSchema(null);
        tool.setArgumentMappings(null);
        tool.setInjectedArguments(null);

        assertThat(properties.getServers()).isEmpty();
        assertThat(server.isEnabled()).isTrue();
        assertThat(server.getSseEndpoint()).isEqualTo("/sse");
        assertThat(server.getRequestTimeout()).isNull();
        assertThat(server.getInitializationTimeout()).isNull();
        assertThat(server.getArguments()).isEmpty();
        assertThat(server.getEnvironment()).isEmpty();
        assertThat(server.getHttp().getHeaders()).isEmpty();
        assertThat(server.getHttp().getQueryParams()).isEmpty();
        assertThat(server.getHttp().getCookies().isEnabled()).isTrue();
        assertThat(http.getHeaders()).isEmpty();
        assertThat(http.getQueryParams()).isEmpty();
        assertThat(http.getCookies().isEnabled()).isTrue();
        assertThat(server.getTools()).isEmpty();
        assertThat(tool.getInputSchema()).isEmpty();
        assertThat(tool.getArgumentMappings()).isEmpty();
        assertThat(tool.getInjectedArguments()).isEmpty();
    }

    private static Map<String, Object> orderInputSchema() {
        Map<String, Object> statusProperty = new LinkedHashMap<>();
        statusProperty.put("type", "string");

        Map<String, Object> schemaProperties = new LinkedHashMap<>();
        schemaProperties.put("status", statusProperty);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", schemaProperties);
        schema.put("required", new ArrayList<>(List.of("status")));
        return schema;
    }

    private static <T> Map<String, T> linkedMap(String key, T value) {
        Map<String, T> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static <T> Map<String, T> linkedMap(String firstKey, T firstValue, String secondKey, T secondValue) {
        Map<String, T> map = linkedMap(firstKey, firstValue);
        map.put(secondKey, secondValue);
        return map;
    }
}
