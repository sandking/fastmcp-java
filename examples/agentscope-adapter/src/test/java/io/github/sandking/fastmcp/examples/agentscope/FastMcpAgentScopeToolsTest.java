package io.github.sandking.fastmcp.examples.agentscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class FastMcpAgentScopeToolsTest {
    @Test
    void registersSafeVirtualAgentScopeToolIntoAgentScopeToolkit() {
        Toolkit toolkit = new Toolkit();
        FastMcpAgentScopeTools.register(toolkit, new RawOrderTool(), FastMcpToolMapping.builder("getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(virtualOrderSchema())
                .injectArgument("userId", param -> param.getRuntimeContext().get(UserContext.class).userId())
                .build());

        assertTrue(toolkit.getToolNames().contains("get_my_orders"));
        assertEquals("object", toolkit.getTool("get_my_orders").getParameters().get("type"));
        assertTrue(((Map<?, ?>) toolkit.getTool("get_my_orders")
                .getParameters()
                .get("properties")).containsKey("status"));

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

        assertEquals("orders for user-123 with status PAID",
                ((TextBlock) result.getOutput().get(0)).getText());
    }

    private ObjectNode virtualOrderSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode status = JsonNodeFactory.instance.objectNode();
        status.put("type", "string");
        properties.set("status", status);
        schema.putArray("required").add("status");
        return schema;
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

    private static final class RawOrderTool extends ToolBase {
        private RawOrderTool() {
            super(ToolBase.builder()
                    .name("getOrdersByUserId")
                    .description("Internal raw order lookup by user id")
                    .inputSchema(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "userId", Map.of("type", "string"),
                                    "status", Map.of("type", "string")),
                            "required", List.of("userId", "status")))
                    .readOnly(true)
                    .concurrencySafe(true));
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.just(ToolResultBlock.of(param.getToolUseBlock().getId(), getName(),
                    TextBlock.builder()
                            .text("orders for " + param.getInput().get("userId")
                                    + " with status " + param.getInput().get("status"))
                            .build()));
        }
    }
}
