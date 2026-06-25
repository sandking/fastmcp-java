package io.github.sandking.fastmcp.springai.boot;

import io.modelcontextprotocol.client.McpSyncClient;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

final class FastMcpManagedSpringAiToolCallbackProvider implements ToolCallbackProvider, AutoCloseable {
    private final List<McpSyncClient> clients;
    private final ToolCallbackProvider delegate;

    FastMcpManagedSpringAiToolCallbackProvider(List<McpSyncClient> clients, ToolCallbackProvider delegate) {
        this.clients = List.copyOf(clients);
        this.delegate = delegate;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return delegate.getToolCallbacks();
    }

    @Override
    public void close() {
        clients.forEach(McpSyncClient::closeGracefully);
    }
}
