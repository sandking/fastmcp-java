package io.github.sandking.fastmcp.springai.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration;
import io.github.sandking.fastmcp.springai.SpringAiToolArgumentResolver;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class FastMcpSafeAutoConfigurationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final AtomicReference<McpSyncClient> FAILING_MANAGED_CLIENT = new AtomicReference<>();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastMcpSafeAutoConfiguration.class))
            .withPropertyValues(safeOrderProperties());

    @Test
    void bindsFastMcpSafePropertiesIntoSafeConfiguration() {
        contextRunner.withUserConfiguration(NoManagedClientConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(SafeMcpConfiguration.class);

            SafeMcpConfiguration configuration = context.getBean(SafeMcpConfiguration.class);
            SafeMcpToolConfiguration tool = configuration.server("orders").tool("getOrdersByUserId");

            assertThat(configuration.server("orders").transport()).isEqualTo("stdio");
            assertThat(configuration.server("orders").command()).isEqualTo("node");
            assertThat(configuration.server("orders").arguments()).containsExactly("orders-mcp.js");
            assertThat(configuration.server("orders").environment()).containsEntry("ORDERS_ENV", "test");
            assertThat(configuration.server("orders").httpHeaders())
                    .containsEntry("Authorization", "Bearer test-token");
            assertThat(configuration.server("orders").httpQueryParams()).containsEntry("region", "cn");
            assertThat(configuration.server("orders").httpCookiesEnabled()).isFalse();
            assertThat(tool.name()).isEqualTo("get_my_orders");
            assertThat(tool.description()).isEqualTo("Get orders for the authenticated user.");
            assertThat(tool.inputSchema().toString()).doesNotContain("userId");
            assertThat(tool.argumentMappings()).containsEntry("status", "orderStatus");
            assertThat(tool.injectedArguments()).containsEntry("userId", "currentUserId");
            assertThat(tool.readOnly()).isTrue();
        });
    }

    @Test
    void createsPrimarySafeToolCallbackProviderFromConfiguredMappings() {
        contextRunner.withUserConfiguration(RawToolConfiguration.class).run(context -> {
            assertThat(context).hasBean("fastMcpSafeToolCallbackProvider");
            assertThat(context.getBean(ToolCallbackProvider.class))
                    .isSameAs(context.getBean("fastMcpSafeToolCallbackProvider"));

            ToolCallbackProvider safeProvider = context.getBean("fastMcpSafeToolCallbackProvider",
                    ToolCallbackProvider.class);
            ToolCallback safeTool = safeProvider.getToolCallbacks()[0];

            assertThat(safeTool.getToolDefinition().name()).isEqualTo("get_my_orders");
            assertThat(safeTool.getToolDefinition().description())
                    .isEqualTo("Get orders for the authenticated user.");
            assertThat(safeTool.getToolDefinition().inputSchema()).doesNotContain("userId");

            String result = safeTool.call("{\"status\":\"PAID\"}", new ToolContext(Map.of("userId", "user-123")));

            CapturingToolCallback rawTool = context.getBean(CapturingToolCallback.class);
            assertThat(result).isEqualTo("orders for user-123 with status PAID");
            assertThat(rawTool.lastInput()).containsEntry("orderStatus", "PAID")
                    .containsEntry("userId", "user-123");
        });
    }

    @Test
    void createsSafeProviderFromManagedMcpClientsWithoutExposingRawProviderBean() {
        contextRunner.withUserConfiguration(ManagedOnlyConfiguration.class)
                .withPropertyValues(
                        "fastmcp.safe.servers.orders.transport=streamable-http",
                        "fastmcp.safe.servers.orders.endpoint=https://mcp.example.test/mcp")
                .run(context -> {
                    assertThat(context).hasBean("fastMcpSafeToolCallbackProvider");
                    assertThat(context).hasSingleBean(ToolCallbackProvider.class);

                    ToolCallbackProvider safeProvider = context.getBean("fastMcpSafeToolCallbackProvider",
                            ToolCallbackProvider.class);
                    ToolCallback safeTool = safeProvider.getToolCallbacks()[0];

                    assertThat(safeTool.getToolDefinition().name()).isEqualTo("get_my_orders");

                    String result = safeTool.call("{\"status\":\"PAID\"}",
                            new ToolContext(Map.of("userId", "user-123")));

                    CapturingToolCallback rawTool = context.getBean(CapturingToolCallback.class);
                    assertThat(result).isEqualTo("orders for user-123 with status PAID");
                    assertThat(rawTool.lastInput()).containsEntry("orderStatus", "PAID")
                            .containsEntry("userId", "user-123");
                });
    }

    @Test
    void closesManagedClientsWhenSafeProviderCreationFails() {
        McpSyncClient managedClient = mock(McpSyncClient.class);
        FAILING_MANAGED_CLIENT.set(managedClient);

        try {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(FastMcpSafeAutoConfiguration.class))
                    .withUserConfiguration(FailingManagedClientConfiguration.class)
                    .withPropertyValues(safeOrderProperties())
                    .withPropertyValues(
                            "fastmcp.safe.servers.orders.transport=streamable-http",
                            "fastmcp.safe.servers.orders.endpoint=https://mcp.example.test/mcp")
                    .run(context -> assertThat(context).hasFailed());
        } finally {
            FAILING_MANAGED_CLIENT.set(null);
        }

        verify(managedClient).closeGracefully();
    }

    @Test
    void backsOffWhenFastMcpSafeIsDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FastMcpSafeAutoConfiguration.class))
                .withUserConfiguration(RawToolConfiguration.class)
                .withPropertyValues("fastmcp.safe.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SafeMcpConfiguration.class);
                    assertThat(context).doesNotHaveBean("fastMcpSafeToolCallbackProvider");
                });
    }

    private static String[] safeOrderProperties() {
        return new String[] {
                "fastmcp.safe.servers.orders.transport=stdio",
                "fastmcp.safe.servers.orders.command=node",
                "fastmcp.safe.servers.orders.arguments[0]=orders-mcp.js",
                "fastmcp.safe.servers.orders.environment.ORDERS_ENV=test",
                "fastmcp.safe.servers.orders.http.headers.Authorization=Bearer test-token",
                "fastmcp.safe.servers.orders.http.query-params.region=cn",
                "fastmcp.safe.servers.orders.http.cookies.enabled=false",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.name=get_my_orders",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.description=Get orders for the authenticated user.",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.type=object",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.properties.status.type=string",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.required[0]=status",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.argument-mappings.status=orderStatus",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.injected-arguments.userId=currentUserId",
                "fastmcp.safe.servers.orders.tools.getOrdersByUserId.read-only=true"
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class RawToolConfiguration {
        @Bean
        CapturingToolCallback rawOrderTool() {
            return new CapturingToolCallback();
        }

        @Bean
        FastMcpSpringAiManagedClientFactory fastMcpSpringAiManagedClientFactory() {
            return new TestManagedClientFactory(ToolCallbackProvider.from(List.of()));
        }

        @Bean
        ToolCallbackProvider rawOrderToolProvider(CapturingToolCallback rawOrderTool) {
            return ToolCallbackProvider.from(rawOrderTool);
        }

        @Bean("currentUserId")
        SpringAiToolArgumentResolver currentUserId() {
            return context -> context.getContext().get("userId");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NoManagedClientConfiguration {
        @Bean
        FastMcpSpringAiManagedClientFactory fastMcpSpringAiManagedClientFactory() {
            return new TestManagedClientFactory(ToolCallbackProvider.from(List.of()));
        }

        @Bean("fastMcpSafeToolCallbackProvider")
        ToolCallbackProvider fastMcpSafeToolCallbackProvider() {
            return ToolCallbackProvider.from(List.of());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ManagedOnlyConfiguration {
        @Bean
        CapturingToolCallback managedRawOrderTool() {
            return new CapturingToolCallback();
        }

        @Bean
        FastMcpSpringAiManagedClientFactory fastMcpSpringAiManagedClientFactory(
                CapturingToolCallback managedRawOrderTool) {
            return new TestManagedClientFactory(ToolCallbackProvider.from(managedRawOrderTool));
        }

        @Bean("currentUserId")
        SpringAiToolArgumentResolver currentUserId() {
            return context -> context.getContext().get("userId");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingManagedClientConfiguration {
        @Bean
        FastMcpSpringAiManagedClientFactory fastMcpSpringAiManagedClientFactory() {
            return new FailingManagedClientFactory(FAILING_MANAGED_CLIENT.get());
        }
    }

    static final class TestManagedClientFactory extends FastMcpSpringAiManagedClientFactory {
        private final ToolCallbackProvider rawProvider;

        private TestManagedClientFactory(ToolCallbackProvider rawProvider) {
            this.rawProvider = rawProvider;
        }

        @Override
        List<McpSyncClient> createClients(SafeMcpConfiguration configuration) {
            return List.of();
        }

        @Override
        ToolCallbackProvider createRawProvider(List<McpSyncClient> clients) {
            assertThat(clients).isEmpty();
            return rawProvider;
        }
    }

    static final class FailingManagedClientFactory extends FastMcpSpringAiManagedClientFactory {
        private final McpSyncClient managedClient;

        private FailingManagedClientFactory(McpSyncClient managedClient) {
            this.managedClient = managedClient;
        }

        @Override
        List<McpSyncClient> createClients(SafeMcpConfiguration configuration) {
            return List.of(managedClient);
        }

        @Override
        ToolCallbackProvider createRawProvider(List<McpSyncClient> clients) {
            return ToolCallbackProvider.from(List.of());
        }
    }

    static final class CapturingToolCallback implements ToolCallback {
        private final AtomicReference<Map<String, Object>> lastInput = new AtomicReference<>();
        private final ToolDefinition toolDefinition = new DefaultToolDefinition("getOrdersByUserId",
                "Raw order lookup",
                "{\"type\":\"object\",\"properties\":{\"orderStatus\":{\"type\":\"string\"},"
                        + "\"userId\":{\"type\":\"string\"}},\"required\":[\"orderStatus\",\"userId\"]}");

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, new ToolContext(Map.of()));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                Map<String, Object> input = OBJECT_MAPPER.readValue(toolInput, MAP_TYPE);
                lastInput.set(input);
                return "orders for " + input.get("userId") + " with status " + input.get("orderStatus");
            } catch (Exception exception) {
                throw new IllegalArgumentException(exception);
            }
        }

        Map<String, Object> lastInput() {
            return lastInput.get();
        }
    }
}
