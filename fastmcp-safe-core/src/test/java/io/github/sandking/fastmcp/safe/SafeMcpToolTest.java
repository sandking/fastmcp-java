package io.github.sandking.fastmcp.safe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SafeMcpToolTest {
    @Test
    void mapsVirtualArgumentsAndInjectsProtectedArguments() {
        AtomicReference<Map<String, Object>> capturedRawArguments = new AtomicReference<>();
        List<SafeAuditEvent> auditEvents = new ArrayList<>();
        SafeMcpTool tool = new SafeMcpTool("test", orderToolSpec(),
                (serverName, rawToolName, rawArguments, context) -> {
                    capturedRawArguments.set(rawArguments);
                    return CompletableFuture.completedFuture(RawToolResult.text("ok"));
                },
                SafeMcpPolicies.allow(),
                auditEvents::add);

        SafeToolResult result = tool.callAsync(
                Map.of("status", "paid"),
                SafeToolCallContext.builder()
                        .userId("user-1")
                        .tenantId("tenant-1")
                        .build())
                .toCompletableFuture()
                .join();

        assertEquals("get_my_orders", result.toolName());
        assertEquals("ok", result.content());
        assertEquals(Map.of("orderStatus", "paid", "userId", "user-1", "tenantId", "tenant-1"),
                capturedRawArguments.get());
        assertEquals(1, auditEvents.size());
        assertTrue(auditEvents.get(0).success());
        assertEquals("get_my_orders", auditEvents.get(0).virtualToolName());
        assertEquals("getOrdersByUserId", auditEvents.get(0).rawToolName());
        assertEquals("user-1", auditEvents.get(0).callerId());
        assertEquals("tenant-1", auditEvents.get(0).tenantId());
        assertEquals(SetSupport.of("userId", "tenantId"), auditEvents.get(0).injectedArgumentNames());
    }

    @Test
    void rejectsModelSuppliedProtectedRawArgument() {
        SafeMcpTool tool = new SafeMcpTool("test", orderToolSpec(),
                (serverName, rawToolName, rawArguments, context) -> CompletableFuture.completedFuture(
                        RawToolResult.text("ok")));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> tool.callAsync(
                        Map.of("userId", "attacker"),
                        SafeToolCallContext.builder().userId("user-1").tenantId("tenant-1").build())
                        .toCompletableFuture()
                        .join());

        SafeMcpException cause = assertInstanceOf(SafeMcpException.class, exception.getCause());
        assertEquals("PROTECTED_ARGUMENT_SUPPLIED", cause.code());
    }

    @Test
    void rejectsModelSuppliedProtectedVirtualArgument() {
        SafeMcpToolSpec spec = SafeMcpToolSpec.builder("orders", "getOrdersByUserId")
                .name("get_my_orders")
                .mapArgument("currentUser", "userId")
                .injectArgument("userId", context -> context.userId())
                .build();
        SafeMcpTool tool = new SafeMcpTool("test", spec,
                (serverName, rawToolName, rawArguments, context) -> CompletableFuture.completedFuture(
                        RawToolResult.text("ok")));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> tool.callAsync(
                        Map.of("currentUser", "attacker"),
                        SafeToolCallContext.builder().userId("user-1").build())
                        .toCompletableFuture()
                        .join());

        SafeMcpException cause = assertInstanceOf(SafeMcpException.class, exception.getCause());
        assertEquals("PROTECTED_ARGUMENT_SUPPLIED", cause.code());
    }

    @Test
    void rejectsInputSchemaThatExposesInjectedRawArgument() {
        SafeMcpException exception = assertThrows(SafeMcpException.class,
                () -> SafeMcpToolSpec.builder("orders", "getOrdersByUserId")
                        .name("get_my_orders")
                        .inputSchema(schemaWithProperty("userId"))
                        .injectArgument("userId", SafeToolCallContext::userId)
                        .build());

        assertEquals("PROTECTED_ARGUMENT_IN_SCHEMA", exception.code());
    }

    @Test
    void rejectsInputSchemaThatExposesInjectedVirtualArgument() {
        SafeMcpException exception = assertThrows(SafeMcpException.class,
                () -> SafeMcpToolSpec.builder("orders", "getOrdersByUserId")
                        .name("get_my_orders")
                        .inputSchema(schemaWithProperty("currentUser"))
                        .mapArgument("currentUser", "userId")
                        .injectArgument("userId", SafeToolCallContext::userId)
                        .build());

        assertEquals("PROTECTED_ARGUMENT_IN_SCHEMA", exception.code());
    }

    @Test
    void doesNotCallRawToolWhenPolicyDenies() {
        AtomicBoolean rawCalled = new AtomicBoolean(false);
        List<SafeAuditEvent> auditEvents = new ArrayList<>();
        SafeMcpTool tool = new SafeMcpTool("test", orderToolSpec(),
                (serverName, rawToolName, rawArguments, context) -> {
                    rawCalled.set(true);
                    return CompletableFuture.completedFuture(RawToolResult.text("ok"));
                },
                SafeMcpPolicies.deny("blocked"),
                auditEvents::add);

        CompletionException exception = assertThrows(CompletionException.class,
                () -> tool.callAsync(
                        Map.of("status", "paid"),
                        SafeToolCallContext.builder().userId("user-1").tenantId("tenant-1").build())
                        .toCompletableFuture()
                        .join());

        SafeMcpException cause = assertInstanceOf(SafeMcpException.class, exception.getCause());
        assertEquals("POLICY_DENIED", cause.code());
        assertFalse(rawCalled.get());
        assertEquals(1, auditEvents.size());
        assertFalse(auditEvents.get(0).success());
        assertEquals("POLICY_DENIED", auditEvents.get(0).errorCode());
    }

    @Test
    void auditContainsInjectedNamesButNotInjectedValues() {
        List<SafeAuditEvent> auditEvents = new ArrayList<>();
        SafeMcpTool tool = new SafeMcpTool("test", orderToolSpec(),
                (serverName, rawToolName, rawArguments, context) -> CompletableFuture.completedFuture(
                        RawToolResult.text("ok")),
                SafeMcpPolicies.allow(),
                auditEvents::add);

        tool.callAsync(
                Map.of("status", "paid"),
                SafeToolCallContext.builder()
                        .userId("secret-user")
                        .tenantId("secret-tenant")
                        .build())
                .toCompletableFuture()
                .join();

        SafeAuditEvent event = auditEvents.get(0);
        assertEquals(SetSupport.of("userId", "tenantId"), event.injectedArgumentNames());
        assertFalse(event.injectedArgumentNames().contains("secret-user"));
        assertFalse(event.injectedArgumentNames().contains("secret-tenant"));
    }

    @Test
    void passesSafeContextToRawInvoker() {
        AtomicReference<SafeToolCallContext> capturedContext = new AtomicReference<>();
        SafeMcpTool tool = new SafeMcpTool("test", orderToolSpec(),
                (serverName, rawToolName, rawArguments, context) -> {
                    capturedContext.set(context);
                    return CompletableFuture.completedFuture(RawToolResult.text("ok"));
                });
        SafeToolCallContext context = SafeToolCallContext.builder()
                .userId("user-1")
                .tenantId("tenant-1")
                .build();

        tool.callAsync(Map.of("status", "paid"), context)
                .toCompletableFuture()
                .join();

        assertEquals(context, capturedContext.get());
    }

    private static SafeMcpToolSpec orderToolSpec() {
        return SafeMcpToolSpec.builder("orders", "getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(JsonNodeFactory.instance.objectNode()
                        .put("type", "object")
                        .set("properties", JsonNodeFactory.instance.objectNode()
                                .set("status", JsonNodeFactory.instance.objectNode().put("type", "string"))))
                .mapArgument("status", "orderStatus")
                .injectArgument("userId", SafeToolCallContext::userId)
                .injectArgument("tenantId", SafeToolCallContext::tenantId)
                .readOnly(true)
                .build();
    }

    private static ObjectNode schemaWithProperty(String propertyName) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = JsonNodeFactory.instance.objectNode();
        properties.set(propertyName, JsonNodeFactory.instance.objectNode().put("type", "string"));
        schema.set("properties", properties);
        return schema;
    }

    private static final class SetSupport {
        private static java.util.Set<String> of(String first, String second) {
            java.util.Set<String> values = new java.util.LinkedHashSet<>();
            values.add(first);
            values.add(second);
            return values;
        }
    }
}
