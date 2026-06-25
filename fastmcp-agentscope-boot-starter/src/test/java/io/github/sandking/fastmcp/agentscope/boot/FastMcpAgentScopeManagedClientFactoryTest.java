package io.github.sandking.fastmcp.agentscope.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FastMcpAgentScopeManagedClientFactoryTest {
    @Test
    void createsOneAgentScopeClientPerEnabledServerWithSafeToolMappings() {
        CapturingFactory factory = new CapturingFactory();
        SafeMcpConfiguration configuration = SafeMcpConfiguration.builder()
                .server(SafeMcpServerConfiguration.builder("orders")
                        .transport("streamable-http")
                        .endpoint("https://mcp.example.test/mcp")
                        .requestTimeout(Duration.ofSeconds(5))
                        .tool(io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration
                                .builder("getOrdersByUserId")
                                .name("get_my_orders")
                                .description("Get orders.")
                                .inputSchema(virtualOrderSchema())
                                .build())
                        .build())
                .server(SafeMcpServerConfiguration.builder("disabled")
                        .enabled(false)
                        .transport("streamable-http")
                        .endpoint("https://mcp.example.test/disabled")
                        .tool(io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration
                                .builder("disabledTool")
                                .name("disabled_tool")
                                .description("Disabled.")
                                .inputSchema(virtualOrderSchema())
                                .build())
                        .build())
                .build();

        List<McpClientWrapper> clients = factory.createClients(configuration);

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).getName()).isEqualTo("orders");
        assertThat(factory.servers).extracting(SafeMcpServerConfiguration::name).containsExactly("orders");
    }

    @Test
    void skipsEnabledServersWithoutSafeToolMappings() {
        CapturingFactory factory = new CapturingFactory();
        SafeMcpConfiguration configuration = SafeMcpConfiguration.builder()
                .server(SafeMcpServerConfiguration.builder("orders")
                        .transport("streamable-http")
                        .endpoint("https://mcp.example.test/mcp")
                        .build())
                .build();

        assertThat(factory.createClients(configuration)).isEmpty();
        assertThat(factory.servers).isEmpty();
    }

    private static ObjectNode virtualOrderSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("status").put("type", "string");
        schema.putArray("required").add("status");
        return schema;
    }

    static final class CapturingFactory extends FastMcpAgentScopeManagedClientFactory {
        private final List<SafeMcpServerConfiguration> servers = new ArrayList<>();

        @Override
        McpClientWrapper buildClient(SafeMcpServerConfiguration server) {
            servers.add(server);
            return new NoopMcpClientWrapper(server.name());
        }
    }

    static final class NoopMcpClientWrapper extends McpClientWrapper {
        private NoopMcpClientWrapper(String name) {
            super(name);
        }

        @Override
        public reactor.core.publisher.Mono<Void> initialize() {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public reactor.core.publisher.Mono<List<io.modelcontextprotocol.spec.McpSchema.Tool>> listTools() {
            return reactor.core.publisher.Mono.just(List.of());
        }

        @Override
        public reactor.core.publisher.Mono<io.modelcontextprotocol.spec.McpSchema.CallToolResult> callTool(
                String toolName, java.util.Map<String, Object> arguments) {
            return callTool(toolName, arguments, java.util.Map.of());
        }

        @Override
        public reactor.core.publisher.Mono<io.modelcontextprotocol.spec.McpSchema.CallToolResult> callTool(
                String toolName, java.util.Map<String, Object> arguments, java.util.Map<String, Object> meta) {
            return reactor.core.publisher.Mono.just(io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder()
                    .isError(false)
                    .build());
        }

        @Override
        public void close() {
        }
    }
}
