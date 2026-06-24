package io.github.sandking.fastmcp.examples.agentscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolCallParam;
import io.github.sandking.fastmcp.FastMcp;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.JsonSchemas;
import io.github.sandking.fastmcp.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FastMcpAgentScopeToolsTest {
    @Test
    void registersFastMcpToolsIntoAgentScopeToolkit() {
        FastMcpServer server = FastMcp.server("Example");
        var schema = JsonSchemas.object();
        JsonSchemas.addProperty(schema, "name", JsonSchemas.string());
        JsonSchemas.require(schema, "name");
        server.tool("greet", "Create a greeting", schema,
                arguments -> ToolResult.text("Hello " + arguments.getString("name") + "!"));

        Toolkit toolkit = new Toolkit();
        FastMcpAgentScopeTools.register(toolkit, server);

        assertTrue(toolkit.getToolNames().contains("greet"));
        assertEquals("object", toolkit.getTool("greet").getParameters().get("type"));

        ToolResultBlock result = toolkit.callTool(ToolCallParam.builder()
                .toolUseBlock(ToolUseBlock.builder()
                        .id("call-1")
                        .name("greet")
                        .input(Map.of("name", "Ada"))
                        .content("{\"name\":\"Ada\"}")
                        .build())
                .input(Map.of("name", "Ada"))
                .build()).block();

        assertEquals("Hello Ada!", ((TextBlock) result.getOutput().get(0)).getText());
    }
}
