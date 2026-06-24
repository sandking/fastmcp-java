package io.github.sandking.fastmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnnotatedToolRegistrarTest {
    @Test
    void registersAnnotatedMethodsFromServiceObject() {
        FastMcpServer server = FastMcp.server("Demo");

        AnnotatedToolRegistrar.register(server, new GreetingService());

        List<ToolDefinition> tools = server.listTools();
        JsonNode schema = tools.get(0).inputSchema();

        assertEquals(1, tools.size());
        assertEquals("greet", tools.get(0).name());
        assertEquals("Create a greeting", tools.get(0).description());
        assertEquals("string", schema.path("properties").path("name").path("type").asText());
        assertEquals("integer", schema.path("properties").path("times").path("type").asText());
        assertEquals("Name to greet", schema.path("properties").path("name").path("description").asText());
        assertEquals("name", schema.path("required").get(0).asText());

        ToolResult result = server.callTool("greet", Map.of("name", "Ada", "times", 2));

        assertEquals("Hello Ada! Hello Ada!", result.content());
    }

    @Test
    void usesMethodNameWhenAnnotationNameIsBlank() {
        FastMcpServer server = FastMcp.server("Demo");

        AnnotatedToolRegistrar.register(server, new MathService());

        ToolResult result = server.callTool("add", Map.of("a", 2, "b", 3));

        assertEquals("5", result.content());
    }

    @Test
    void reportsUnsupportedParameterTypes() {
        FastMcpServer server = FastMcp.server("Demo");

        assertThrows(IllegalArgumentException.class,
                () -> AnnotatedToolRegistrar.register(server, new UnsupportedService()));
    }

    static final class GreetingService {
        @McpTool(name = "greet", description = "Create a greeting")
        String greet(
                @ToolParam(description = "Name to greet") String name,
                @ToolParam(required = false, description = "Repeat count") Integer times) {
            int repeat = times == null ? 1 : times;
            return String.join(" ", java.util.Collections.nCopies(repeat, "Hello " + name + "!"));
        }
    }

    static final class MathService {
        @McpTool(description = "Add two numbers")
        int add(@ToolParam int a, @ToolParam int b) {
            return a + b;
        }
    }

    static final class UnsupportedService {
        @McpTool
        String unsupported(@ToolParam Object value) {
            return String.valueOf(value);
        }
    }
}
