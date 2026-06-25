package io.github.sandking.fastmcp.examples.springai.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.sandking.fastmcp.safe.SafeMcpException;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.springai.boot.FastMcpSafeAutoConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FastMcpSpringAiBootExampleTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastMcpSafeAutoConfiguration.class))
            .withUserConfiguration(FastMcpSpringAiBootExample.RawToolConfiguration.class)
            .withPropertyValues(FastMcpSpringAiBootExample.safeOrderProperties());

    @Test
    void bindsFastMcpSafePropertiesAndKeepsSensitiveValuesInResolvers() {
        contextRunner.run(context -> {
            SafeMcpConfiguration configuration = context.getBean(SafeMcpConfiguration.class);

            assertThat(configuration.server("orders").transport()).isEqualTo("stdio");
            assertThat(configuration.server("orders").command()).isEqualTo("node");
            assertThat(configuration.server("orders").arguments()).containsExactly("orders-mcp.js");
            assertThat(configuration.server("orders").tool("getOrdersByUserId").name()).isEqualTo("get_my_orders");
            assertThat(configuration.server("orders").tool("getOrdersByUserId").argumentMappings())
                    .containsEntry("status", "orderStatus");
            assertThat(configuration.server("orders").tool("getOrdersByUserId").injectedArguments())
                    .containsEntry("userId", "currentUserId")
                    .containsEntry("tenantId", "currentTenantId");
            assertThat(configuration.server("orders").tool("getOrdersByUserId").inputSchema().toString())
                    .contains("status")
                    .contains("limit")
                    .doesNotContain("userId")
                    .doesNotContain("tenantId");
        });
    }

    @Test
    void publishesPrimarySafeProviderThatWrapsExistingRawProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("rawOrderToolProvider");
            assertThat(context).hasBean("fastMcpSafeToolCallbackProvider");
            assertThat(context.getBean(ToolCallbackProvider.class))
                    .isSameAs(context.getBean("fastMcpSafeToolCallbackProvider"));

            ToolCallback safeTool = context.getBean(ToolCallbackProvider.class).getToolCallbacks()[0];
            assertThat(safeTool.getToolDefinition().name()).isEqualTo("get_my_orders");
            assertThat(safeTool.getToolDefinition().inputSchema())
                    .contains("status")
                    .contains("limit")
                    .doesNotContain("userId")
                    .doesNotContain("tenantId")
                    .doesNotContain("orderStatus");

            String result = safeTool.call("{\"status\":\"PAID\",\"limit\":2}",
                    FastMcpSpringAiBootExample.currentUser("tenant-1", "user-123"));

            FastMcpSpringAiBootExample.RawOrderTool rawTool =
                    context.getBean(FastMcpSpringAiBootExample.RawOrderTool.class);
            assertThat(result).isEqualTo("orders for tenant-1/user-123 with status PAID limit 2");
            assertThat(rawTool.lastInput()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "orderStatus", "PAID",
                    "limit", 2,
                    "tenantId", "tenant-1",
                    "userId", "user-123"));
        });
    }

    @Test
    void rejectsModelSuppliedProtectedArgumentsBeforeCallingRawProvider() {
        contextRunner.run(context -> {
            ToolCallback safeTool = context.getBean(ToolCallbackProvider.class).getToolCallbacks()[0];

            assertThatThrownBy(() -> safeTool.call("{\"status\":\"PAID\",\"userId\":\"attacker\"}",
                    FastMcpSpringAiBootExample.currentUser("tenant-1", "user-123")))
                    .isInstanceOf(SafeMcpException.class)
                    .extracting("code")
                    .isEqualTo("PROTECTED_ARGUMENT_SUPPLIED");

            FastMcpSpringAiBootExample.RawOrderTool rawTool =
                    context.getBean(FastMcpSpringAiBootExample.RawOrderTool.class);
            assertThat(rawTool.wasCalled()).isFalse();
        });
    }
}
