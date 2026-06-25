package io.github.sandking.fastmcp.examples.agentscope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.github.sandking.fastmcp.agentscope.FastMcpAgentScopeTools;
import io.github.sandking.fastmcp.agentscope.FastMcpToolMapping;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

public final class FastMcpAgentScopeExample {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FastMcpAgentScopeExample() {
    }

    public static void main(String[] args) {
        OrderScenario scenario = createOrderScenario();
        ToolResultBlock result = scenario.callModelTool(Map.of("status", "PAID", "limit", 2),
                currentUser("tenant-1", "user-123"));

        System.out.println(((TextBlock) result.getOutput().get(0)).getText());
    }

    public static OrderScenario createOrderScenario() {
        Toolkit toolkit = new Toolkit();
        RawOrderTool rawOrderTool = new RawOrderTool();
        FastMcpAgentScopeTools.register(toolkit, rawOrderTool, FastMcpToolMapping.builder("getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(virtualOrderSchema())
                .mapArgument("status", "orderStatus")
                .injectArgument("userId", param -> param.getRuntimeContext().get(UserContext.class).userId())
                .injectArgument("tenantId", param -> param.getRuntimeContext().get(UserContext.class).tenantId())
                .readOnly(true)
                .concurrencySafe(true)
                .build());
        return new OrderScenario(toolkit, rawOrderTool);
    }

    public static RuntimeContext currentUser(String tenantId, String userId) {
        return RuntimeContext.builder()
                .userId(userId)
                .put(UserContext.class, new UserContext(tenantId, userId))
                .build();
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
        private final Toolkit toolkit;
        private final RawOrderTool rawOrderTool;

        private OrderScenario(Toolkit toolkit, RawOrderTool rawOrderTool) {
            this.toolkit = toolkit;
            this.rawOrderTool = rawOrderTool;
        }

        public Toolkit toolkit() {
            return toolkit;
        }

        public RawOrderTool rawOrderTool() {
            return rawOrderTool;
        }

        public ToolResultBlock callModelTool(Map<String, Object> modelInput, RuntimeContext runtimeContext) {
            return toolkit.callTool(ToolCallParam.builder()
                    .toolUseBlock(ToolUseBlock.builder()
                            .id("call-1")
                            .name("get_my_orders")
                            .input(modelInput)
                            .content(toJson(modelInput))
                            .build())
                    .input(modelInput)
                    .runtimeContext(runtimeContext)
                    .build()).block();
        }
    }

    public static final class RawOrderTool extends ToolBase {
        private final AtomicReference<Map<String, Object>> lastInput = new AtomicReference<>();

        private RawOrderTool() {
            super(ToolBase.builder()
                    .name("getOrdersByUserId")
                    .description("Internal raw order lookup by user and tenant")
                    .inputSchema(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "userId", Map.of("type", "string"),
                                    "tenantId", Map.of("type", "string"),
                                    "orderStatus", Map.of("type", "string"),
                                    "limit", Map.of("type", "integer")),
                            "required", List.of("userId", "tenantId", "orderStatus")))
                    .readOnly(true)
                    .concurrencySafe(true));
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            lastInput.set(param.getInput());
            return Mono.just(ToolResultBlock.of(param.getToolUseBlock().getId(), getName(),
                    TextBlock.builder()
                            .text("orders for " + param.getInput().get("tenantId")
                                    + "/" + param.getInput().get("userId")
                                    + " with status " + param.getInput().get("orderStatus")
                                    + " limit " + param.getInput().get("limit"))
                            .build()));
        }

        public Map<String, Object> lastInput() {
            return lastInput.get();
        }

        public boolean wasCalled() {
            return lastInput.get() != null;
        }
    }

    public static final class UserContext {
        private final String tenantId;
        private final String userId;

        private UserContext(String tenantId, String userId) {
            this.tenantId = tenantId;
            this.userId = userId;
        }

        private String tenantId() {
            return tenantId;
        }

        private String userId() {
            return userId;
        }
    }

    private static String toJson(Map<String, Object> input) {
        try {
            return OBJECT_MAPPER.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize model input", exception);
        }
    }
}
