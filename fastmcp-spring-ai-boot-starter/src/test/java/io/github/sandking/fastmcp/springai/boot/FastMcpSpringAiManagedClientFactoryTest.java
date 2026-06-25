package io.github.sandking.fastmcp.springai.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.TypeRef;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class FastMcpSpringAiManagedClientFactoryTest {
    @Test
    void skipsDisabledServers() {
        SafeMcpConfiguration configuration = SafeMcpConfiguration.builder()
                .server(SafeMcpServerConfiguration.builder("orders")
                        .enabled(false)
                        .transport("streamable-http")
                        .endpoint("https://mcp.example.test/mcp")
                        .build())
                .build();

        FastMcpSpringAiManagedClientFactory factory = new FastMcpSpringAiManagedClientFactory(
                server -> {
                    throw new AssertionError("disabled server must not create transport");
                });

        List<FastMcpSpringAiManagedClientFactory.ManagedMcpClient> clients = factory.createClients(configuration);

        assertThat(clients).isEmpty();
    }

    @Test
    void skipsServersWithoutConfiguredTools() {
        SafeMcpConfiguration configuration = SafeMcpConfiguration.builder()
                .server(SafeMcpServerConfiguration.builder("orders")
                        .transport("streamable-http")
                        .endpoint("https://mcp.example.test/mcp")
                        .build())
                .build();

        FastMcpSpringAiManagedClientFactory factory = new FastMcpSpringAiManagedClientFactory(
                server -> {
                    throw new AssertionError("server without tools must not create transport");
                });

        List<FastMcpSpringAiManagedClientFactory.ManagedMcpClient> clients = factory.createClients(configuration);

        assertThat(clients).isEmpty();
    }

    @Test
    void skipsServersWithoutConfiguredTransport() {
        SafeMcpConfiguration configuration = SafeMcpConfiguration.builder()
                .server(SafeMcpServerConfiguration.builder("orders")
                        .tool(currentUserOrdersTool())
                        .build())
                .build();

        FastMcpSpringAiManagedClientFactory factory = new FastMcpSpringAiManagedClientFactory(
                server -> {
                    throw new AssertionError("server without transport must not create transport");
                });

        List<FastMcpSpringAiManagedClientFactory.ManagedMcpClient> clients = factory.createClients(configuration);

        assertThat(clients).isEmpty();
    }

    @Test
    void rejectsUnsupportedTransport() {
        SafeMcpConfiguration configuration = SafeMcpConfiguration.builder()
                .server(SafeMcpServerConfiguration.builder("orders")
                        .transport("websocket")
                        .tool(currentUserOrdersTool())
                        .build())
                .build();

        FastMcpSpringAiManagedClientFactory factory = new FastMcpSpringAiManagedClientFactory();

        assertThatThrownBy(() -> factory.createClients(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported MCP transport")
                .hasMessageContaining("orders")
                .hasMessageContaining("websocket");
    }

    @Test
    void createsManagedClientSpecFromServerConfiguration() {
        SafeMcpServerConfiguration server = SafeMcpServerConfiguration.builder("orders")
                .transport("streamable-http")
                .endpoint("https://mcp.example.test/mcp")
                .requestTimeout(Duration.ofSeconds(3))
                .initializationTimeout(Duration.ofSeconds(5))
                .clientName("fastmcp-orders")
                .clientVersion("0.1-test")
                .build();

        FastMcpSpringAiManagedClientFactory.ManagedClientSpec spec =
                FastMcpSpringAiManagedClientFactory.managedClientSpec(server);

        assertThat(spec.clientName()).isEqualTo("fastmcp-orders");
        assertThat(spec.clientVersion()).isEqualTo("0.1-test");
        assertThat(spec.requestTimeout()).contains(Duration.ofSeconds(3));
        assertThat(spec.initializationTimeout()).contains(Duration.ofSeconds(5));
    }

    @Test
    void createsSpringMcpStreamableHttpTransport() {
        SafeMcpServerConfiguration server = SafeMcpServerConfiguration.builder("orders")
                .transport("streamable-http")
                .endpoint("https://mcp.example.test/mcp")
                .build();

        McpClientTransport transport = FastMcpSpringAiManagedClientFactory.createTransport(server);

        assertThat(transport).isInstanceOf(HttpClientStreamableHttpTransport.class);
    }

    @Test
    void splitsStreamableHttpEndpointIntoBaseUriAndEndpointPath() {
        FastMcpSpringAiHttpTransportSupport.StreamableHttpEndpoint endpoint =
                FastMcpSpringAiHttpTransportSupport.streamableHttpEndpoint(
                        "https://mcp.example.test/catalog/mcp?region=cn");

        assertThat(endpoint.baseUri()).isEqualTo("https://mcp.example.test");
        assertThat(endpoint.endpoint()).isEqualTo("/catalog/mcp?region=cn");
    }

    @Test
    void appendsConfiguredQueryParamsToRequestUri() {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("app id", "catalog agent");
        queryParams.put("token", "a+b");

        URI uri = FastMcpSpringAiHttpTransportSupport.appendQueryParams(
                URI.create("https://mcp.example.test/catalog/mcp?region=cn"),
                queryParams);

        assertThat(uri.toString())
                .isEqualTo("https://mcp.example.test/catalog/mcp?region=cn&app%20id=catalog%20agent&token=a%2Bb");
    }

    @Test
    void appliesConfiguredHttpHeadersAndQueryParamsToRequestBuilder() {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("https://mcp.example.test/catalog/mcp"));

        FastMcpSpringAiHttpTransportSupport.applyHttpRequestConfiguration(requestBuilder,
                URI.create("https://mcp.example.test/catalog/mcp"),
                Map.of("Authorization", "Bearer test-token"),
                Map.of("region", "cn"));

        HttpRequest request = requestBuilder.build();
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer test-token");
        assertThat(request.uri().toString()).isEqualTo("https://mcp.example.test/catalog/mcp?region=cn");
    }

    @Test
    void createsSseTransport() {
        SafeMcpServerConfiguration server = SafeMcpServerConfiguration.builder("orders")
                .transport("sse")
                .endpoint("https://mcp.example.test")
                .sseEndpoint("/events")
                .build();

        FastMcpSpringAiManagedClientFactory.ManagedTransportSpec spec =
                FastMcpSpringAiManagedClientFactory.managedTransportSpec(server);
        McpClientTransport transport = FastMcpSpringAiManagedClientFactory.createTransport(server);

        assertThat(spec.transport()).isEqualTo("sse");
        assertThat(spec.endpoint()).isEqualTo("https://mcp.example.test");
        assertThat(spec.sseEndpoint()).isEqualTo("/events");
        assertThat(transport).isInstanceOf(HttpClientSseClientTransport.class);
    }

    @Test
    void buildsClientsWithConfiguredMetadata() {
        SafeMcpConfiguration configuration = SafeMcpConfiguration.builder()
                .server(SafeMcpServerConfiguration.builder("orders")
                        .transport("streamable-http")
                        .endpoint("https://mcp.example.test/mcp")
                        .clientName("fastmcp-orders")
                        .clientVersion("0.1-test")
                        .tool(currentUserOrdersTool())
                        .build())
                .build();
        FastMcpSpringAiManagedClientFactory factory =
                new NonInitializingFactory(server -> new NoopMcpClientTransport());

        List<FastMcpSpringAiManagedClientFactory.ManagedMcpClient> clients = factory.createClients(configuration);

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).serverName()).isEqualTo("orders");
        assertThat(clients.get(0).client().getClientInfo().name()).isEqualTo("fastmcp-orders");
        assertThat(clients.get(0).client().getClientInfo().version()).isEqualTo("0.1-test");
    }

    private static SafeMcpToolConfiguration currentUserOrdersTool() {
        return SafeMcpToolConfiguration.builder("getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(virtualOrderSchema())
                .injectArgument("userId", "currentUserId")
                .build();
    }

    private static ObjectNode virtualOrderSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = JsonNodeFactory.instance.objectNode();
        properties.set("status", JsonNodeFactory.instance.objectNode().put("type", "string"));
        schema.set("properties", properties);
        schema.putArray("required").add("status");
        return schema;
    }

    private static final class NonInitializingFactory extends FastMcpSpringAiManagedClientFactory {
        private NonInitializingFactory(TransportFactory transportFactory) {
            super(transportFactory);
        }

        @Override
        void initialize(McpSyncClient client) {
        }
    }

    private static final class NoopMcpClientTransport implements McpClientTransport {
        @Override
        public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.empty();
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            throw new UnsupportedOperationException("Noop transport does not unmarshal data");
        }
    }
}
