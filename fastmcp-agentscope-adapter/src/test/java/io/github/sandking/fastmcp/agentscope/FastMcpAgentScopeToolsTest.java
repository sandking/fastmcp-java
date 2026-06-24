package io.github.sandking.fastmcp.agentscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.sandking.fastmcp.FastMcp;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.JsonSchemas;
import io.github.sandking.fastmcp.ToolException;
import io.github.sandking.fastmcp.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class FastMcpAgentScopeToolsTest {
    @Test
    void exposesVirtualToolAndInjectsProtectedUserIdBeforeDelegatingToRawTool() {
        FastMcpServer server = orderServer();
        Toolkit toolkit = new Toolkit();
        FastMcpAgentScopeTools.register(toolkit, server, currentUserOrdersMapping());

        assertTrue(toolkit.getToolNames().contains("get_my_orders"));
        assertFalse(toolkit.getToolNames().contains("getOrdersByUserId"));
        assertFalse(((Map<?, ?>) toolkit.getTool("get_my_orders")
                .getParameters()
                .get("properties")).containsKey("userId"));

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

    @Test
    void rejectsModelSuppliedProtectedArguments() {
        FastMcpServer server = orderServer();
        Toolkit toolkit = new Toolkit();
        FastMcpAgentScopeTools.register(toolkit, server, currentUserOrdersMapping());

        ToolCallParam param = ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder()
                        .id("call-2")
                        .name("get_my_orders")
                        .input(Map.of("status", "PAID", "userId", "attacker"))
                        .content("{\"status\":\"PAID\",\"userId\":\"attacker\"}")
                        .build())
                .input(Map.of("status", "PAID", "userId", "attacker"))
                .runtimeContext(RuntimeContext.builder()
                        .put(UserContext.class, new UserContext("user-123"))
                        .build())
                .build();

        assertThrows(ToolException.class, () -> toolkit.getTool("get_my_orders").callAsync(param).block());
    }

    @Test
    void exposesVirtualToolForRawAgentScopeMcpTool() {
        Toolkit toolkit = new Toolkit();
        FastMcpAgentScopeTools.register(toolkit, new RawMcpOrderTool(), FastMcpToolMapping
                .builder("mcp__orders__getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(virtualOrderSchema())
                .injectArgument("userId", param -> param.getRuntimeContext().get(UserContext.class).userId())
                .build());

        assertTrue(toolkit.getToolNames().contains("get_my_orders"));
        assertFalse(toolkit.getToolNames().contains("mcp__orders__getOrdersByUserId"));

        ToolResultBlock result = toolkit.callTool(ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder()
                        .id("call-3")
                        .name("get_my_orders")
                        .input(Map.of("status", "PAID"))
                        .content("{\"status\":\"PAID\"}")
                        .build())
                .input(Map.of("status", "PAID"))
                .runtimeContext(RuntimeContext.builder()
                        .put(UserContext.class, new UserContext("user-123"))
                        .build())
                .build()).block();

        assertEquals("raw MCP orders for user-123 with status PAID",
                ((TextBlock) result.getOutput().get(0)).getText());
        assertEquals("get_my_orders", result.getName());
    }

    @Test
    void registersVirtualToolsFromMcpClientWithoutExposingRawMcpTools() {
        FakeMcpClientWrapper wrapper = new FakeMcpClientWrapper("orders");
        Toolkit toolkit = new Toolkit();

        FastMcpAgentScopeTools.registerMcpClient(toolkit, wrapper, List.of(FastMcpToolMapping
                .builder("mcp__orders__getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(virtualOrderSchema())
                .injectArgument("userId", param -> param.getRuntimeContext().get(UserContext.class).userId())
                .readOnly(true)
                .build())).block();

        assertTrue(wrapper.initialized);
        assertEquals(1, wrapper.listToolsCalls);
        assertTrue(toolkit.getToolNames().contains("get_my_orders"));
        assertFalse(toolkit.getToolNames().contains("getOrdersByUserId"));
        assertFalse(toolkit.getToolNames().contains("mcp__orders__getOrdersByUserId"));

        ToolResultBlock result = toolkit.callTool(ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder()
                        .id("call-4")
                        .name("get_my_orders")
                        .input(Map.of("status", "PAID"))
                        .content("{\"status\":\"PAID\"}")
                        .build())
                .input(Map.of("status", "PAID"))
                .runtimeContext(RuntimeContext.builder()
                        .put(UserContext.class, new UserContext("user-123"))
                        .build())
                .build()).block();

        assertEquals("mcp orders for user-123 with status PAID",
                ((TextBlock) result.getOutput().get(0)).getText());
        assertEquals("getOrdersByUserId", wrapper.lastToolName);
        assertEquals(Map.of("status", "PAID", "userId", "user-123"), wrapper.lastArguments);
    }

    private FastMcpServer orderServer() {
        ObjectNode rawSchema = JsonSchemas.object();
        JsonSchemas.addProperty(rawSchema, "userId", JsonSchemas.string());
        JsonSchemas.addProperty(rawSchema, "status", JsonSchemas.string());
        JsonSchemas.require(rawSchema, "userId");
        JsonSchemas.require(rawSchema, "status");

        return FastMcp.server("Order MCP")
                .tool("getOrdersByUserId", "Internal raw order lookup by user id", rawSchema,
                        arguments -> ToolResult.text("orders for " + arguments.getString("userId")
                                + " with status " + arguments.getString("status")));
    }

    private FastMcpToolMapping currentUserOrdersMapping() {
        return FastMcpToolMapping.builder("getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(virtualOrderSchema())
                .injectArgument("userId", param -> param.getRuntimeContext().get(UserContext.class).userId())
                .build();
    }

    private ObjectNode virtualOrderSchema() {
        ObjectNode virtualSchema = JsonSchemas.object();
        JsonSchemas.addProperty(virtualSchema, "status", JsonSchemas.string());
        JsonSchemas.require(virtualSchema, "status");
        return virtualSchema;
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

    private static final class RawMcpOrderTool extends ToolBase {
        private RawMcpOrderTool() {
            super(ToolBase.builder()
                    .name("mcp__orders__getOrdersByUserId")
                    .description("Raw MCP order lookup by user id")
                    .inputSchema(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "userId", Map.of("type", "string"),
                                    "status", Map.of("type", "string")),
                            "required", java.util.List.of("userId", "status")))
                    .readOnly(true)
                    .concurrencySafe(true)
                    .mcp("orders"));
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.just(ToolResultBlock.of(param.getToolUseBlock().getId(), getName(),
                    TextBlock.builder()
                            .text("raw MCP orders for " + param.getInput().get("userId")
                                    + " with status " + param.getInput().get("status"))
                            .build()));
        }
    }

    private static final class FakeMcpClientWrapper extends McpClientWrapper {
        private boolean initialized;
        private int listToolsCalls;
        private String lastToolName;
        private Map<String, Object> lastArguments;

        private FakeMcpClientWrapper(String name) {
            super(name);
        }

        @Override
        public Mono<Void> initialize() {
            initialized = true;
            return Mono.empty();
        }

        @Override
        public Mono<List<McpSchema.Tool>> listTools() {
            listToolsCalls++;
            return Mono.just(List.of(McpSchema.Tool.builder()
                    .name("getOrdersByUserId")
                    .description("Raw MCP order lookup by user id")
                    .inputSchema(new McpSchema.JsonSchema(
                            "object",
                            Map.of(
                                    "userId", Map.of("type", "string"),
                                    "status", Map.of("type", "string")),
                            List.of("userId", "status"),
                            false,
                            null,
                            null))
                    .annotations(new McpSchema.ToolAnnotations(null, true, false, true, false, false))
                    .build()));
        }

        @Override
        public Mono<McpSchema.CallToolResult> callTool(String toolName, Map<String, Object> arguments) {
            return callTool(toolName, arguments, Map.of());
        }

        @Override
        public Mono<McpSchema.CallToolResult> callTool(
                String toolName, Map<String, Object> arguments, Map<String, Object> meta) {
            lastToolName = toolName;
            lastArguments = arguments;
            return Mono.just(McpSchema.CallToolResult.builder()
                    .addTextContent("mcp orders for " + arguments.get("userId")
                            + " with status " + arguments.get("status"))
                    .isError(false)
                    .build());
        }

        @Override
        public void close() {
        }
    }
}
