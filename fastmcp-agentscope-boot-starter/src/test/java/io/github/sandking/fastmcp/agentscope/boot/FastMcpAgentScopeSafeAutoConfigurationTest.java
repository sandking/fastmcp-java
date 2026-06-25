package io.github.sandking.fastmcp.agentscope.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.sandking.fastmcp.agentscope.ToolArgumentResolver;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

class FastMcpAgentScopeSafeAutoConfigurationTest {
    private static final AtomicReference<FakeMcpClientWrapper> FAILING_MANAGED_CLIENT = new AtomicReference<>();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastMcpAgentScopeSafeAutoConfiguration.class))
            .withPropertyValues(safeOrderProperties());

    @Test
    void bindsFastMcpSafePropertiesIntoSafeConfiguration() {
        contextRunner.withUserConfiguration(NoManagedClientConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(SafeMcpConfiguration.class);

            SafeMcpConfiguration configuration = context.getBean(SafeMcpConfiguration.class);
            SafeMcpToolConfiguration tool = configuration.server("orders").tool("getOrdersByUserId");

            assertThat(configuration.server("orders").transport()).isEqualTo("streamable-http");
            assertThat(configuration.server("orders").endpoint()).isEqualTo("https://mcp.example.test/mcp");
            assertThat(configuration.server("orders").requestTimeout()).hasValue(java.time.Duration.ofSeconds(5));
            assertThat(configuration.server("orders").httpHeaders())
                    .containsEntry("Authorization", "Bearer test-token");
            assertThat(configuration.server("orders").httpQueryParams()).containsEntry("region", "cn");
            assertThat(configuration.server("orders").httpCookiesEnabled()).isFalse();
            assertThat(tool.name()).isEqualTo("get_my_orders");
            assertThat(tool.description()).isEqualTo("Get orders for the authenticated user.");
            assertThat(tool.inputSchema().toString()).doesNotContain("userId");
            assertThat(tool.argumentMappings()).containsEntry("status", "status");
            assertThat(tool.injectedArguments()).containsEntry("userId", "currentUserId");
            assertThat(tool.readOnly()).isTrue();
        });
    }

    @Test
    void createsManagedAgentScopeClientAndRegistersOnlyVirtualToolsIntoToolkit() {
        contextRunner.withUserConfiguration(ManagedClientConfiguration.class).run(context -> {
            assertThat(context).hasBean("fastMcpAgentScopeSafeRegistrar");
            assertThat(context).hasSingleBean(Toolkit.class);
            assertThat(context).doesNotHaveBean(McpClientWrapper.class);

            Toolkit toolkit = context.getBean(Toolkit.class);
            assertThat(toolkit.getToolNames()).contains("get_my_orders");
            assertThat(toolkit.getToolNames()).doesNotContain("getOrdersByUserId");
            assertThat(toolkit.getToolNames()).doesNotContain("mcp__orders__getOrdersByUserId");
            @SuppressWarnings("unchecked")
            Map<String, Object> virtualProperties = (Map<String, Object>) toolkit.getTool("get_my_orders")
                    .getParameters()
                    .get("properties");
            assertThat(virtualProperties)
                    .doesNotContainKey("userId");

            ToolResultBlock result = toolkit.callTool(ToolCallParam.builder()
                    .toolUseBlock(ToolUseBlock.builder()
                            .id("call-1")
                            .name("get_my_orders")
                            .input(Map.of("status", "PAID"))
                            .content("{\"status\":\"PAID\"}")
                            .build())
                    .input(Map.of("status", "PAID"))
                    .runtimeContext(RuntimeContext.builder()
                            .put(UserContext.class, new UserContext("user-123"))
                            .build())
                    .build()).block();

            FakeMcpClientWrapper client = context.getBean(ManagedClientConfiguration.class).client();
            assertThat(client.initialized()).isTrue();
            assertThat(client.listToolsCalls()).isEqualTo(1);
            assertThat(client.lastToolName()).isEqualTo("getOrdersByUserId");
            assertThat(client.lastArguments()).containsEntry("status", "PAID")
                    .containsEntry("userId", "user-123");
            assertThat(((TextBlock) result.getOutput().get(0)).getText())
                    .isEqualTo("mcp orders for user-123 with status PAID");
        });
    }

    @Test
    void closesManagedClientsWhenRegistrationFails() {
        FakeMcpClientWrapper managedClient = new FakeMcpClientWrapper("orders", List.of());
        FAILING_MANAGED_CLIENT.set(managedClient);

        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(FastMcpAgentScopeSafeAutoConfiguration.class))
                    .withUserConfiguration(FailingManagedClientConfiguration.class)
                    .withPropertyValues(safeOrderProperties())
                    .run(context -> assertThat(context).hasFailed());
        } finally {
            FAILING_MANAGED_CLIENT.set(null);
        }

        assertThat(managedClient.closed()).isTrue();
    }

    @Test
    void backsOffWhenFastMcpSafeIsDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FastMcpAgentScopeSafeAutoConfiguration.class))
                .withUserConfiguration(ManagedClientConfiguration.class)
                .withPropertyValues("fastmcp.safe.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SafeMcpConfiguration.class);
                    assertThat(context).doesNotHaveBean("fastMcpAgentScopeSafeRegistrar");
                });
    }

    private static String[] safeOrderProperties() {
        return new String[] {
                "fastmcp.safe.servers.orders.transport=streamable-http",
                "fastmcp.safe.servers.orders.endpoint=https://mcp.example.test/mcp",
                "fastmcp.safe.servers.orders.request-timeout=5s",
                "fastmcp.safe.servers.orders.http.headers.Authorization=Bearer test-token",
                "fastmcp.safe.servers.orders.http.query-params.region=cn",
                "fastmcp.safe.servers.orders.http.cookies.enabled=false",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.name=get_my_orders",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.description=Get orders for the authenticated user.",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.type=object",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.properties.status.type=string",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.required[0]=status",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.argument-mappings.status=status",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.injected-arguments.userId=currentUserId",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.read-only=true"
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class ManagedClientConfiguration {
        private final FakeMcpClientWrapper client = new FakeMcpClientWrapper("orders", orderTools());

        @Bean
        Toolkit toolkit() {
            return new Toolkit();
        }

        @Bean
        FastMcpAgentScopeManagedClientFactory fastMcpAgentScopeManagedClientFactory() {
            return new TestManagedClientFactory(List.of(client));
        }

        @Bean("currentUserId")
        ToolArgumentResolver currentUserId() {
            return param -> param.getRuntimeContext().get(UserContext.class).userId();
        }

        FakeMcpClientWrapper client() {
            return client;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NoManagedClientConfiguration {
        @Bean("fastMcpAgentScopeSafeRegistrar")
        FastMcpAgentScopeSafeRegistrar fastMcpAgentScopeSafeRegistrar() {
            return new FastMcpAgentScopeSafeRegistrar(List.of());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingManagedClientConfiguration {
        @Bean
        Toolkit toolkit() {
            return new Toolkit();
        }

        @Bean
        FastMcpAgentScopeManagedClientFactory fastMcpAgentScopeManagedClientFactory() {
            return new TestManagedClientFactory(List.of(FAILING_MANAGED_CLIENT.get()));
        }

        @Bean("currentUserId")
        ToolArgumentResolver currentUserId() {
            return param -> param.getRuntimeContext().get(UserContext.class).userId();
        }
    }

    static final class TestManagedClientFactory extends FastMcpAgentScopeManagedClientFactory {
        private final List<McpClientWrapper> clients;

        private TestManagedClientFactory(List<McpClientWrapper> clients) {
            this.clients = clients;
        }

        @Override
        List<McpClientWrapper> createClients(SafeMcpConfiguration configuration) {
            return clients;
        }
    }

    private static List<McpSchema.Tool> orderTools() {
        return List.of(McpSchema.Tool.builder()
                .name("getOrdersByUserId")
                .description("Raw MCP order lookup by user id")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        Map.of(
                                "userId", Map.of("type", "string"),
                                "status", Map.of("type", "string")),
                        List.of("userId", "status"),
                        false,
                        null,
                        null))
                .annotations(new McpSchema.ToolAnnotations(null, true, false, true, false, false))
                .build());
    }

    private static final class UserContext {
        private final String userId;

        private UserContext(String userId) {
            this.userId = userId;
        }

        private String userId() {
            return userId;
        }
    }

    private static final class FakeMcpClientWrapper extends McpClientWrapper {
        private final List<McpSchema.Tool> tools;
        private boolean initialized;
        private int listToolsCalls;
        private String lastToolName;
        private Map<String, Object> lastArguments;
        private boolean closed;

        private FakeMcpClientWrapper(String name, List<McpSchema.Tool> tools) {
            super(name);
            this.tools = tools;
        }

        @Override
        public Mono<Void> initialize() {
            initialized = true;
            return Mono.empty();
        }

        @Override
        public Mono<List<McpSchema.Tool>> listTools() {
            listToolsCalls++;
            return Mono.just(tools);
        }

        @Override
        public Mono<McpSchema.CallToolResult> callTool(String toolName, Map<String, Object> arguments) {
            return callTool(toolName, arguments, Map.of());
        }

        @Override
        public Mono<McpSchema.CallToolResult> callTool(
                String toolName, Map<String, Object> arguments, Map<String, Object> meta) {
            lastToolName = toolName;
            lastArguments = arguments;
            return Mono.just(McpSchema.CallToolResult.builder()
                    .addTextContent("mcp orders for " + arguments.get("userId")
                            + " with status " + arguments.get("status"))
                    .isError(false)
                    .build());
        }

        @Override
        public void close() {
            closed = true;
        }

        boolean initialized() {
            return initialized;
        }

        int listToolsCalls() {
            return listToolsCalls;
        }

        String lastToolName() {
            return lastToolName;
        }

        Map<String, Object> lastArguments() {
            return lastArguments;
        }

        boolean closed() {
            return closed;
        }
    }
}
