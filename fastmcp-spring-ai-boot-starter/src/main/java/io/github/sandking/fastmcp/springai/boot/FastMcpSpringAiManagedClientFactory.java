package io.github.sandking.fastmcp.springai.boot;

import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;

class FastMcpSpringAiManagedClientFactory {
    record ManagedClientSpec(String clientName, String clientVersion, Optional<Duration> requestTimeout,
            Optional<Duration> initializationTimeout) {
    }

    record ManagedTransportSpec(String transport, String endpoint, String sseEndpoint) {
    }

    record StreamableHttpEndpoint(String baseUri, String endpoint) {
    }

    interface TransportFactory {
        McpClientTransport create(SafeMcpServerConfiguration server);
    }

    private final TransportFactory transportFactory;

    FastMcpSpringAiManagedClientFactory() {
        this(FastMcpSpringAiManagedClientFactory::createTransport);
    }

    FastMcpSpringAiManagedClientFactory(TransportFactory transportFactory) {
        this.transportFactory = transportFactory;
    }

    List<McpSyncClient> createClients(SafeMcpConfiguration configuration) {
        List<McpSyncClient> clients = new ArrayList<>();
        for (SafeMcpServerConfiguration server : configuration.servers().values()) {
            if (!server.enabled()) {
                continue;
            }
            McpSyncClient client = buildClient(server);
            initialize(client);
            clients.add(client);
        }
        return clients;
    }

    McpSyncClient buildClient(SafeMcpServerConfiguration server) {
        McpClientTransport transport = transportFactory.create(server);
        ManagedClientSpec managedClientSpec = managedClientSpec(server);
        McpClient.SyncSpec spec = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder(managedClientSpec.clientName(),
                        managedClientSpec.clientVersion()).build());
        managedClientSpec.requestTimeout().ifPresent(spec::requestTimeout);
        managedClientSpec.initializationTimeout().ifPresent(spec::initializationTimeout);
        return spec.build();
    }

    void initialize(McpSyncClient client) {
        client.initialize();
    }

    ToolCallbackProvider createRawProvider(List<McpSyncClient> clients) {
        return new SyncMcpToolCallbackProvider(clients);
    }

    static ManagedClientSpec managedClientSpec(SafeMcpServerConfiguration server) {
        return new ManagedClientSpec(server.clientName(), server.clientVersion(),
                server.requestTimeout(), server.initializationTimeout());
    }

    static ManagedTransportSpec managedTransportSpec(SafeMcpServerConfiguration server) {
        return new ManagedTransportSpec(server.transport(), server.endpoint(), server.sseEndpoint());
    }

    static McpClientTransport createTransport(SafeMcpServerConfiguration server) {
        ManagedTransportSpec transportSpec = managedTransportSpec(server);
        String transport = transportSpec.transport();
        if ("stdio".equalsIgnoreCase(transport)) {
            ServerParameters parameters = ServerParameters.builder(server.command())
                    .args(server.arguments())
                    .env(server.environment())
                    .build();
            return new StdioClientTransport(parameters, McpJsonDefaults.getMapper());
        }
        if ("streamable-http".equalsIgnoreCase(transport)) {
            StreamableHttpEndpoint endpoint = streamableHttpEndpoint(transportSpec.endpoint());
            return HttpClientStreamableHttpTransport.builder(endpoint.baseUri())
                    .endpoint(endpoint.endpoint())
                    .clientBuilder(httpClientBuilder(server))
                    .httpRequestCustomizer(httpRequestCustomizer(server))
                    .build();
        }
        if ("sse".equalsIgnoreCase(transport)) {
            return HttpClientSseClientTransport.builder(transportSpec.endpoint())
                    .sseEndpoint(transportSpec.sseEndpoint())
                    .clientBuilder(httpClientBuilder(server))
                    .httpRequestCustomizer(httpRequestCustomizer(server))
                    .build();
        }
        throw new IllegalArgumentException("Unsupported MCP transport for server '" + server.name()
                + "': " + transport);
    }

    static StreamableHttpEndpoint streamableHttpEndpoint(String endpoint) {
        URI uri = URI.create(endpoint);
        if (!uri.isAbsolute() || uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()) {
            throw new IllegalArgumentException("streamable-http endpoint must be an absolute URL: " + endpoint);
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("streamable-http endpoint must not contain a fragment: " + endpoint);
        }
        String baseUri = uri.getScheme() + "://" + uri.getRawAuthority();
        String path = uri.getRawPath();
        String endpointPath = path == null || path.isBlank() ? "/" : path;
        if (uri.getRawQuery() != null) {
            endpointPath = endpointPath + "?" + uri.getRawQuery();
        }
        return new StreamableHttpEndpoint(baseUri, endpointPath);
    }

    private static HttpClient.Builder httpClientBuilder(SafeMcpServerConfiguration server) {
        HttpClient.Builder builder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
        if (server.httpCookiesEnabled()) {
            builder.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
        return builder;
    }

    private static McpSyncHttpClientRequestCustomizer httpRequestCustomizer(SafeMcpServerConfiguration server) {
        return (requestBuilder, method, uri, body, context) -> applyHttpRequestConfiguration(requestBuilder, uri,
                server.httpHeaders(), server.httpQueryParams());
    }

    static void applyHttpRequestConfiguration(HttpRequest.Builder requestBuilder, URI uri,
            Map<String, String> headers, Map<String, String> queryParams) {
        headers.forEach(requestBuilder::header);
        if (!queryParams.isEmpty()) {
            requestBuilder.uri(appendQueryParams(uri, queryParams));
        }
    }

    static URI appendQueryParams(URI uri, Map<String, String> queryParams) {
        if (queryParams.isEmpty()) {
            return uri;
        }
        StringBuilder value = new StringBuilder();
        value.append(uri.getScheme()).append("://").append(uri.getRawAuthority());
        if (uri.getRawPath() != null) {
            value.append(uri.getRawPath());
        }
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null && !rawQuery.isBlank()) {
            value.append('?').append(rawQuery);
        }
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            value.append(value.indexOf("?") >= 0 ? '&' : '?')
                    .append(encodeQueryPart(entry.getKey()))
                    .append('=')
                    .append(encodeQueryPart(entry.getValue()));
        }
        if (uri.getRawFragment() != null) {
            value.append('#').append(uri.getRawFragment());
        }
        return URI.create(value.toString());
    }

    private static String encodeQueryPart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
