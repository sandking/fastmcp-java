package io.github.sandking.fastmcp.examples.agentscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FastMcpAgentScopeToolsTest {
    @Test
    void exposesOnlyVirtualToolAndInjectsRuntimeArguments() {
        FastMcpAgentScopeExample.OrderScenario scenario = FastMcpAgentScopeExample.createOrderScenario();

        assertTrue(scenario.toolkit().getToolNames().contains("get_my_orders"));
        assertFalse(scenario.toolkit().getToolNames().contains("getOrdersByUserId"));

        Map<?, ?> properties = (Map<?, ?>) scenario.toolkit().getTool("get_my_orders")
                .getParameters()
                .get("properties");
        assertTrue(properties.containsKey("status"));
        assertTrue(properties.containsKey("limit"));
        assertFalse(properties.containsKey("userId"));
        assertFalse(properties.containsKey("tenantId"));
        assertFalse(properties.containsKey("orderStatus"));

        RuntimeContext runtimeContext = FastMcpAgentScopeExample.currentUser("tenant-1", "user-123");
        ToolResultBlock result = scenario.callModelTool(Map.of("status", "PAID", "limit", 2), runtimeContext);

        assertEquals("get_my_orders", result.getName());
        assertEquals("orders for tenant-1/user-123 with status PAID limit 2",
                ((TextBlock) result.getOutput().get(0)).getText());
        assertEquals(Map.of(
                "orderStatus", "PAID",
                "limit", 2,
                "tenantId", "tenant-1",
                "userId", "user-123"), scenario.rawOrderTool().lastInput());
    }

    @Test
    void rejectsModelSuppliedProtectedArguments() {
        FastMcpAgentScopeExample.OrderScenario scenario = FastMcpAgentScopeExample.createOrderScenario();
        RuntimeContext runtimeContext = FastMcpAgentScopeExample.currentUser("tenant-1", "user-123");

        ToolResultBlock result = scenario.callModelTool(Map.of("status", "PAID", "tenantId", "tenant-attacker"),
                runtimeContext);

        assertTrue(((TextBlock) result.getOutput().get(0)).getText().contains("Model supplied protected argument"));
        assertFalse(scenario.rawOrderTool().wasCalled());
    }
}
