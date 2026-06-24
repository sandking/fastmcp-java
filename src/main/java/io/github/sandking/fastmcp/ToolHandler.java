package io.github.sandking.fastmcp;

@FunctionalInterface
public interface ToolHandler {
    ToolResult handle(ToolArguments arguments) throws Exception;
}
