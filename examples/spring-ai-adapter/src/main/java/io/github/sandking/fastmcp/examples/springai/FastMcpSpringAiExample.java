package io.github.sandking.fastmcp.examples.springai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.sandking.fastmcp.springai.FastMcpSpringAiTools;
import io.github.sandking.fastmcp.springai.SpringAiMcpToolMapping;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class FastMcpSpringAiExample {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private FastMcpSpringAiExample() {
    }

    public static void main(String[] args) {
        OrderScenario scenario = createOrderScenario();
        String result = scenario.callModelTool("{\"status\":\"PAID\",\"limit\":2}",
                currentUser("tenant-1", "user-123"));

        System.out.println(result);
    }

    public static OrderScenario createOrderScenario() {
        RawOrderTool rawOrderTool = new RawOrderTool();
        ToolCallbackProvider rawProvider = ToolCallbackProvider.from(rawOrderTool);
        ToolCallbackProvider safeProvider = FastMcpSpringAiTools.wrap(rawProvider, SpringAiMcpToolMapping
                .builder("getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(virtualOrderSchema())
                .mapArgument("status", "orderStatus")
                .injectArgument("userId", context -> context.getContext().get("userId"))
                .injectArgument("tenantId", context -> context.getContext().get("tenantId"))
                .readOnly(true)
                .concurrencySafe(true)
                .build());
        return new OrderScenario(rawProvider, safeProvider, rawOrderTool);
    }

    public static ToolContext currentUser(String tenantId, String userId) {
        return new ToolContext(Map.of("tenantId", tenantId, "userId", userId));
    }

    private static ObjectNode virtualOrderSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.set("status", JsonNodeFactory.instance.objectNode()
                .put("type", "string")
                .put("description", "Order status visible to the model, such as PAID or CREATED."));
        properties.set("limit", JsonNodeFactory.instance.objectNode()
                .put("type", "integer")
                .put("minimum", 1)
                .put("maximum", 20)
                .put("description", "Maximum number of orders to return."));
        schema.putArray("required").add("status");
        return schema;
    }

    public static final class OrderScenario {
        private final ToolCallbackProvider rawProvider;
        private final ToolCallbackProvider safeProvider;
        private final RawOrderTool rawOrderTool;

        private OrderScenario(ToolCallbackProvider rawProvider, ToolCallbackProvider safeProvider,
                RawOrderTool rawOrderTool) {
            this.rawProvider = rawProvider;
            this.safeProvider = safeProvider;
            this.rawOrderTool = rawOrderTool;
        }

        public ToolCallbackProvider rawProvider() {
            return rawProvider;
        }

        public ToolCallbackProvider safeProvider() {
            return safeProvider;
        }

        public RawOrderTool rawOrderTool() {
            return rawOrderTool;
        }

        public String callModelTool(String modelInput, ToolContext toolContext) {
            return safeProvider.getToolCallbacks()[0].call(modelInput, toolContext);
        }
    }

    public static final class RawOrderTool implements ToolCallback {
        private final AtomicReference<Map<String, Object>> lastInput = new AtomicReference<>();
        private final AtomicReference<ToolContext> lastContext = new AtomicReference<>();
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
                lastContext.set(toolContext);
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

        public ToolContext lastContext() {
            return lastContext.get();
        }

        public boolean wasCalled() {
            return lastInput.get() != null;
        }
    }
}
