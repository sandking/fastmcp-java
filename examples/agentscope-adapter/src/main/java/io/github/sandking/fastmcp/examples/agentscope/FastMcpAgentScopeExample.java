package io.github.sandking.fastmcp.examples.agentscope;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.github.sandking.fastmcp.FastMcp;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.JsonSchemas;
import io.github.sandking.fastmcp.ToolResult;
import java.util.Map;

public final class FastMcpAgentScopeExample {
    private FastMcpAgentScopeExample() {
    }

    public static void main(String[] args) {
        FastMcpServer server = FastMcp.server("AgentScope Example");
        var schema = JsonSchemas.object();
        JsonSchemas.addProperty(schema, "name", JsonSchemas.string());
        JsonSchemas.require(schema, "name");
        server.tool("greet", "Create a greeting", schema,
                arguments -> ToolResult.text("Hello " + arguments.getString("name") + "!"));

        Toolkit toolkit = new Toolkit();
        FastMcpAgentScopeTools.register(toolkit, server);

        ToolResultBlock result = toolkit.callTool(ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder()
                        .id("call-1")
                        .name("greet")
                        .input(Map.of("name", "Ada"))
                        .content("{\"name\":\"Ada\"}")
                        .build())
                .input(Map.of("name", "Ada"))
                .build()).block();

        System.out.println(((TextBlock) result.getOutput().get(0)).getText());
    }
}
