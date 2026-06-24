package io.github.sandking.fastmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FastMcpServerTest {
    @Test
    void registersListsAndCallsTools() {
        ObjectNode inputSchema = JsonSchemas.object();
        JsonSchemas.addProperty(inputSchema, "text", JsonSchemas.string());
        JsonSchemas.require(inputSchema, "text");

        FastMcpServer server = FastMcp.server("Echo Server")
                .tool("echo", "Echo the input text", inputSchema,
                        arguments -> ToolResult.text(arguments.getString("text")));

        List<McpTool> tools = server.listTools();

        assertEquals("Echo Server", server.name());
        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).name());
        assertEquals("Echo the input text", tools.get(0).description());
        assertEquals("object", tools.get(0).inputSchema().get("type").asText());

        ToolResult result = server.callTool("echo", Map.of("text", "hello"));

        assertEquals("hello", result.content());
        assertFalse(result.structuredContent().isPresent());
    }

    @Test
    void rejectsDuplicateToolNames() {
        FastMcpServer server = FastMcp.server("Demo")
                .tool("echo", "Echo text", JsonSchemas.object(), arguments -> ToolResult.text("ok"));

        assertThrows(IllegalArgumentException.class,
                () -> server.tool("echo", "Duplicate", JsonSchemas.object(), arguments -> ToolResult.text("ok")));
    }

    @Test
    void throwsForUnknownTools() {
        FastMcpServer server = FastMcp.server("Demo");

        assertThrows(ToolNotFoundException.class, () -> server.callTool("missing", Map.of()));
    }

    @Test
    void exposesStructuredContentAndMetadata() {
        ToolResult result = ToolResult.builder()
                .content("Echoed: hello")
                .structuredContent(Map.of("text", "hello", "length", 5))
                .meta("durationMs", 3)
                .build();

        assertEquals("Echoed: hello", result.content());
        assertEquals("hello", ((Map<?, ?>) result.structuredContent().orElseThrow()).get("text"));
        assertEquals(3, result.meta().get("durationMs"));
    }
}
