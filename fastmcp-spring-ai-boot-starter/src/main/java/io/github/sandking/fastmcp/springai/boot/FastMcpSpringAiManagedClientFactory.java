package io.github.sandking.fastmcp.springai.boot;

import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;

class FastMcpSpringAiManagedClientFactory {
    record ManagedMcpClient(String serverName, McpSyncClient client) {
    }

    record ManagedClientSpec(String clientName, String clientVersion, Optional<Duration> requestTimeout,
            Optional<Duration> initializationTimeout) {
    }

    record ManagedTransportSpec(String transport, String endpoint, String sseEndpoint) {
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

    List<ManagedMcpClient> createClients(SafeMcpConfiguration configuration) {
        List<ManagedMcpClient> clients = new ArrayList<>();
        for (SafeMcpServerConfiguration server : configuration.servers().values()) {
            if (!requiresManagedClient(server)) {
                continue;
            }
            McpSyncClient client = buildClient(server);
            initialize(client);
            clients.add(new ManagedMcpClient(server.name(), client));
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

    static boolean requiresManagedClient(SafeMcpServerConfiguration server) {
        return server.enabled() && !server.tools().isEmpty() && !server.transport().isBlank();
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
            FastMcpSpringAiHttpTransportSupport.StreamableHttpEndpoint endpoint =
                    FastMcpSpringAiHttpTransportSupport.streamableHttpEndpoint(transportSpec.endpoint());
            return HttpClientStreamableHttpTransport.builder(endpoint.baseUri())
                    .endpoint(endpoint.endpoint())
                    .clientBuilder(FastMcpSpringAiHttpTransportSupport.httpClientBuilder(server))
                    .httpRequestCustomizer(FastMcpSpringAiHttpTransportSupport.httpRequestCustomizer(server))
                    .build();
        }
        if ("sse".equalsIgnoreCase(transport)) {
            return HttpClientSseClientTransport.builder(transportSpec.endpoint())
                    .sseEndpoint(transportSpec.sseEndpoint())
                    .clientBuilder(FastMcpSpringAiHttpTransportSupport.httpClientBuilder(server))
                    .httpRequestCustomizer(FastMcpSpringAiHttpTransportSupport.httpRequestCustomizer(server))
                    .build();
        }
        throw new IllegalArgumentException("Unsupported MCP transport for server '" + server.name()
                + "': " + transport);
    }
}
