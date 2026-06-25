package io.github.sandking.fastmcp.agentscope.boot;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
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
            builder.streamableHttpTransport(requireAbsoluteUrl(server.endpoint(), "streamable-http endpoint"))
                    .customizeStreamableHttpClient(httpClientBuilder -> configureHttpClient(httpClientBuilder, server));
            return;
        }
        if ("sse".equalsIgnoreCase(transport)) {
            builder.sseTransport(sseUrl(server))
                    .customizeSseClient(httpClientBuilder -> configureHttpClient(httpClientBuilder, server));
            return;
        }
        if ("stdio".equalsIgnoreCase(transport)) {
            builder.stdioTransport(requireText(server.command(), "stdio command"), server.arguments(),
                    server.environment());
            return;
        }
        throw new IllegalArgumentException("Unsupported MCP transport for AgentScope server '" + server.name()
                + "': " + transport);
    }

    static String sseUrl(SafeMcpServerConfiguration server) {
        URI endpoint = URI.create(requireAbsoluteUrl(server.endpoint(), "sse endpoint"));
        String sseEndpoint = requireText(server.sseEndpoint(), "sseEndpoint");
        URI resolved = URI.create(sseEndpoint).isAbsolute()
                ? URI.create(sseEndpoint)
                : endpoint.resolve(sseEndpoint);
        return resolved.toString();
    }

    private static void configureHttpClient(HttpClient.Builder builder, SafeMcpServerConfiguration server) {
        builder.version(HttpClient.Version.HTTP_1_1);
        if (server.httpCookiesEnabled()) {
            builder.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
    }

    private static String requireAbsoluteUrl(String value, String fieldName) {
        String text = requireText(value, fieldName);
        URI uri = URI.create(text);
        if (!uri.isAbsolute() || uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be an absolute URL: " + value);
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException(fieldName + " must not contain a fragment: " + value);
        }
        return text;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
