package io.github.sandking.fastmcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FastMcpServer {
    private final String name;
    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    FastMcpServer(String name) {
        this.name = requireText(name, "name");
    }

    public String name() {
        return name;
    }

    public FastMcpServer tool(String name, String description, JsonNode inputSchema, ToolHandler handler) {
        McpTool tool = new McpTool(name, description, inputSchema, handler);
        if (tools.containsKey(tool.name())) {
            throw new IllegalArgumentException("Tool already registered: " + tool.name());
        }
        tools.put(tool.name(), tool);
        return this;
    }

    public List<McpTool> listTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    public ToolResult callTool(String name, Map<String, ?> arguments) {
        McpTool tool = tools.get(requireText(name, "name"));
        if (tool == null) {
            throw new ToolNotFoundException("Tool not found: " + name);
        }
        try {
            return tool.invoke(new ToolArguments(arguments));
        } catch (ToolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ToolException("Tool failed: " + name, exception);
        }
    }

    static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
