package io.github.sandking.fastmcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public final class ToolDefinition {
    private final String name;
    private final String description;
    private final JsonNode inputSchema;
    private final ToolHandler handler;

    ToolDefinition(String name, String description, JsonNode inputSchema, ToolHandler handler) {
        this.name = FastMcpServer.requireText(name, "name");
        this.description = description == null ? "" : description.trim();
        this.inputSchema = Objects.requireNonNull(inputSchema, "inputSchema must not be null").deepCopy();
        this.handler = Objects.requireNonNull(handler, "handler must not be null");
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public JsonNode inputSchema() {
        return inputSchema.deepCopy();
    }

    ToolResult invoke(ToolArguments arguments) throws Exception {
        return Objects.requireNonNull(handler.handle(arguments), "tool handler returned null");
    }
}
