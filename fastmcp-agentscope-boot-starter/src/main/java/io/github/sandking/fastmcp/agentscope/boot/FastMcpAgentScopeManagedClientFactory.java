package io.github.sandking.fastmcp.agentscope.boot;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import java.util.ArrayList;
import java.util.List;

class FastMcpAgentScopeManagedClientFactory {
    List<McpClientWrapper> createClients(SafeMcpConfiguration configuration) {
        List<McpClientWrapper> clients = new ArrayList<>();
        for (SafeMcpServerConfiguration server : configuration.servers().values()) {
            if (!server.enabled() || server.tools().isEmpty()) {
                continue;
            }
            clients.add(buildClient(server));
        }
        return clients;
    }

    McpClientWrapper buildClient(SafeMcpServerConfiguration server) {
        McpClientBuilder builder = McpClientBuilder.create(server.name());
        configureTransport(builder, server);
        builder.headers(server.httpHeaders());
        builder.queryParams(server.httpQueryParams());
        server.requestTimeout().ifPresent(builder::timeout);
        server.initializationTimeout().ifPresent(builder::initializationTimeout);
        return builder.buildSync();
    }

    private void configureTransport(McpClientBuilder builder, SafeMcpServerConfiguration server) {
        String transport = server.transport();
        if ("streamable-http".equalsIgnoreCase(transport)) {
            builder.streamableHttpTransport(FastMcpAgentScopeHttpClientSupport.requireAbsoluteUrl(server.endpoint(),
                    "streamable-http endpoint"))
                    .customizeStreamableHttpClient(httpClientBuilder -> FastMcpAgentScopeHttpClientSupport
                            .configureHttpClient(httpClientBuilder, server));
            return;
        }
        if ("sse".equalsIgnoreCase(transport)) {
            builder.sseTransport(FastMcpAgentScopeHttpClientSupport.sseUrl(server))
                    .customizeSseClient(httpClientBuilder -> FastMcpAgentScopeHttpClientSupport
                            .configureHttpClient(httpClientBuilder, server));
            return;
        }
        if ("stdio".equalsIgnoreCase(transport)) {
            builder.stdioTransport(FastMcpAgentScopeHttpClientSupport.requireText(server.command(), "stdio command"),
                    server.arguments(), server.environment());
            return;
        }
        throw new IllegalArgumentException("Unsupported MCP transport for AgentScope server '" + server.name()
                + "': " + transport);
    }
}
