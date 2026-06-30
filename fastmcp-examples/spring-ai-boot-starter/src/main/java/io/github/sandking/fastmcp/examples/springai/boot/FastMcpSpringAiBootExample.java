package io.github.sandking.fastmcp.examples.springai.boot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sandking.fastmcp.springai.SpringAiToolArgumentResolver;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class FastMcpSpringAiBootExample {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private FastMcpSpringAiBootExample() {
    }

    public static ToolContext currentUser(String tenantId, String userId) {
        return new ToolContext(Map.of("tenantId", tenantId, "userId", userId));
    }

    public static String[] safeOrderProperties() {
        return new String[] {
                "fastmcp.safe.diagnostics.external-raw-provider=warn",
                "fastmcp.safe.servers.orders.enabled=false",
                "fastmcp.safe.servers.orders.transport=stdio",
                "fastmcp.safe.servers.orders.command=node",
                "fastmcp.safe.servers.orders.arguments[0]=orders-mcp.js",
                "fastmcp.safe.servers.orders.environment.ORDERS_ENV=test",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.name=get_my_orders",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.description="
                        + "Get orders for the authenticated user.",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.type=object",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.properties.status.type=string",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.properties.limit.type=integer",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.required[0]=status",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.argument-mappings.status=orderStatus",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.injected-arguments.userId=currentUserId",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.injected-arguments.tenantId=currentTenantId",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.read-only=true",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.concurrency-safe=true"
        };
    }

    @Configuration(proxyBeanMethods = false)
    public static class RawToolConfiguration {
        @Bean
        RawOrderTool rawOrderTool() {
            return new RawOrderTool();
        }

        @Bean
        ToolCallbackProvider rawOrderToolProvider(RawOrderTool rawOrderTool) {
            return ToolCallbackProvider.from(rawOrderTool);
        }

        @Bean("currentUserId")
        SpringAiToolArgumentResolver currentUserId() {
            return context -> context.getContext().get("userId");
        }

        @Bean("currentTenantId")
        SpringAiToolArgumentResolver currentTenantId() {
            return context -> context.getContext().get("tenantId");
        }
    }

    public static final class RawOrderTool implements ToolCallback {
        private final AtomicReference<Map<String, Object>> lastInput = new AtomicReference<>();
        private final ToolDefinition toolDefinition = new DefaultToolDefinition("getOrdersByUserId",
                "Internal raw order lookup by user and tenant",
                "{\"type\":\"object\",\"properties\":{\"orderStatus\":{\"type\":\"string\"},"
                        + "\"limit\":{\"type\":\"integer\"},\"tenantId\":{\"type\":\"string\"},"
                        + "\"userId\":{\"type\":\"string\"}},"
                        + "\"required\":[\"orderStatus\",\"tenantId\",\"userId\"]}");

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, new ToolContext(Map.of()));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                Map<String, Object> input = OBJECT_MAPPER.readValue(toolInput, MAP_TYPE);
                lastInput.set(input);
                return "orders for " + input.get("tenantId")
                        + "/" + input.get("userId")
                        + " with status " + input.get("orderStatus")
                        + " limit " + input.get("limit");
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Raw order tool input must be a JSON object", exception);
            }
        }

        public Map<String, Object> lastInput() {
            return lastInput.get();
        }

        public boolean wasCalled() {
            return lastInput.get() != null;
        }
    }
}
