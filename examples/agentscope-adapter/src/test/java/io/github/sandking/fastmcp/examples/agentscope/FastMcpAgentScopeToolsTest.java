package io.github.sandking.fastmcp.examples.agentscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolCallParam;
import io.github.sandking.fastmcp.agentscope.FastMcpAgentScopeTools;
import io.github.sandking.fastmcp.agentscope.FastMcpToolMapping;
import io.github.sandking.fastmcp.FastMcp;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.JsonSchemas;
import io.github.sandking.fastmcp.ToolResult;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FastMcpAgentScopeToolsTest {
    @Test
    void registersSafeVirtualFastMcpToolIntoAgentScopeToolkit() {
        FastMcpServer server = FastMcp.server("Example")
                .tool("getOrdersByUserId", "Internal raw order lookup by user id", rawOrderSchema(),
                        arguments -> ToolResult.text("orders for " + arguments.getString("userId")
                                + " with status " + arguments.getString("status")));

        Toolkit toolkit = new Toolkit();
        FastMcpAgentScopeTools.register(toolkit, server, FastMcpToolMapping.builder("getOrdersByUserId")
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

    private ObjectNode rawOrderSchema() {
        ObjectNode schema = JsonSchemas.object();
        JsonSchemas.addProperty(schema, "userId", JsonSchemas.string());
        JsonSchemas.addProperty(schema, "status", JsonSchemas.string());
        JsonSchemas.require(schema, "userId");
        JsonSchemas.require(schema, "status");
        return schema;
    }

    private ObjectNode virtualOrderSchema() {
        ObjectNode schema = JsonSchemas.object();
        JsonSchemas.addProperty(schema, "status", JsonSchemas.string());
        JsonSchemas.require(schema, "status");
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
}
