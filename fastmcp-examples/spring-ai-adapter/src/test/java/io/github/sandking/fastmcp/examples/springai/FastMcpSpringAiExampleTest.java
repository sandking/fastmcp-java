package io.github.sandking.fastmcp.examples.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sandking.fastmcp.safe.SafeMcpException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

class FastMcpSpringAiExampleTest {
    @Test
    void wrapsRawProviderAsSafeVirtualToolCallback() {
        FastMcpSpringAiExample.OrderScenario scenario = FastMcpSpringAiExample.createOrderScenario();

        ToolCallback[] callbacks = scenario.safeProvider().getToolCallbacks();
        assertEquals(1, callbacks.length);
        assertEquals("get_my_orders", callbacks[0].getToolDefinition().name());
        assertEquals("Get orders for the authenticated user.", callbacks[0].getToolDefinition().description());
        assertTrue(callbacks[0].getToolDefinition().inputSchema().contains("\"status\""));
        assertTrue(callbacks[0].getToolDefinition().inputSchema().contains("\"limit\""));
        assertFalse(callbacks[0].getToolDefinition().inputSchema().contains("userId"));
        assertFalse(callbacks[0].getToolDefinition().inputSchema().contains("tenantId"));
        assertFalse(callbacks[0].getToolDefinition().inputSchema().contains("orderStatus"));

        String result = scenario.callModelTool("{\"status\":\"PAID\",\"limit\":2}",
                FastMcpSpringAiExample.currentUser("tenant-1", "user-123"));

        assertEquals("orders for tenant-1/user-123 with status PAID limit 2", result);
        assertEquals(Map.of(
                "orderStatus", "PAID",
                "limit", 2,
                "tenantId", "tenant-1",
                "userId", "user-123"), scenario.rawOrderTool().lastInput());
        assertEquals("tenant-1", scenario.rawOrderTool().lastContext().getContext().get("tenantId"));
    }

    @Test
    void rejectsModelSuppliedProtectedArgumentsBeforeCallingRawCallback() {
        FastMcpSpringAiExample.OrderScenario scenario = FastMcpSpringAiExample.createOrderScenario();
        ToolContext toolContext = FastMcpSpringAiExample.currentUser("tenant-1", "user-123");

        SafeMcpException exception = assertThrows(SafeMcpException.class,
                () -> scenario.callModelTool("{\"status\":\"PAID\",\"tenantId\":\"tenant-attacker\"}",
                        toolContext));

        assertEquals("PROTECTED_ARGUMENT_SUPPLIED", exception.code());
        assertFalse(scenario.rawOrderTool().wasCalled());
    }
}
