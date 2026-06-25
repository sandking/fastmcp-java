package io.github.sandking.fastmcp.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.sandking.fastmcp.safe.SafeMcpException;
import io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

class FastMcpSpringAiToolsTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    void exposesOnlyVirtualToolDefinitionAndInjectsProtectedArgument() throws Exception {
        CapturingToolCallback rawTool = new CapturingToolCallback("getOrdersByUserId", rawOrderSchema());
        ToolCallbackProvider provider = FastMcpSpringAiTools.wrap(new ToolCallback[] { rawTool },
                currentUserOrdersMapping());

        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertEquals(1, callbacks.length);
        assertEquals("get_my_orders", callbacks[0].getToolDefinition().name());
        assertEquals("Get orders for the authenticated user.", callbacks[0].getToolDefinition().description());
        assertFalse(callbacks[0].getToolDefinition().inputSchema().contains("userId"));

        String result = callbacks[0].call("{\"status\":\"PAID\"}",
                new ToolContext(Map.of("userId", "user-123", "tenantId", "tenant-1")));

        assertEquals("orders for user-123 with status PAID", result);
        assertEquals(Map.of("status", "PAID", "userId", "user-123"), rawTool.lastInput());
        assertEquals("tenant-1", rawTool.lastContext().getContext().get("tenantId"));
    }

    @Test
    void rejectsModelSuppliedProtectedArgument() {
        CapturingToolCallback rawTool = new CapturingToolCallback("getOrdersByUserId", rawOrderSchema());
        ToolCallbackProvider provider = FastMcpSpringAiTools.wrap(new ToolCallback[] { rawTool },
                currentUserOrdersMapping());

        SafeMcpException exception = assertThrows(SafeMcpException.class,
                () -> provider.getToolCallbacks()[0].call("{\"status\":\"PAID\",\"userId\":\"attacker\"}",
                        new ToolContext(Map.of("userId", "user-123"))));

        assertEquals("PROTECTED_ARGUMENT_SUPPLIED", exception.code());
    }

    @Test
    void matchesRawCallbackByOriginalToolNameWhenPresent() throws Exception {
        OriginalNameToolCallback rawTool = new OriginalNameToolCallback(
                "mcp__orders__getOrdersByUserId",
                "getOrdersByUserId",
                rawOrderSchema());
        ToolCallbackProvider provider = FastMcpSpringAiTools.wrap(new ToolCallback[] { rawTool },
                currentUserOrdersMapping());

        String result = provider.getToolCallbacks()[0].call("{\"status\":\"PAID\"}",
                new ToolContext(Map.of("userId", "user-123")));

        assertEquals("orders for user-123 with status PAID", result);
        assertEquals(Map.of("status", "PAID", "userId", "user-123"), rawTool.lastInput());
    }

    @Test
    void failsWhenMappedRawToolDoesNotExist() {
        CapturingToolCallback rawTool = new CapturingToolCallback("otherTool", rawOrderSchema());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> FastMcpSpringAiTools.wrap(new ToolCallback[] { rawTool }, currentUserOrdersMapping()));

        assertEquals("Raw Spring AI tool not found: spring-ai/getOrdersByUserId", exception.getMessage());
    }

    @Test
    void requiresExplicitVirtualNameDescriptionAndInputSchema() {
        NullPointerException missingName = assertThrows(NullPointerException.class,
                () -> SpringAiMcpToolMapping.builder("getOrdersByUserId")
                        .description("Get orders for the authenticated user.")
                        .inputSchema(virtualOrderSchema())
                        .build());
        assertEquals("name must not be null", missingName.getMessage());

        NullPointerException missingDescription = assertThrows(NullPointerException.class,
                () -> SpringAiMcpToolMapping.builder("getOrdersByUserId")
                        .name("get_my_orders")
                        .inputSchema(virtualOrderSchema())
                        .build());
        assertEquals("description must not be null", missingDescription.getMessage());

        NullPointerException missingSchema = assertThrows(NullPointerException.class,
                () -> SpringAiMcpToolMapping.builder("getOrdersByUserId")
                        .name("get_my_orders")
                        .description("Get orders for the authenticated user.")
                        .build());
        assertEquals("inputSchema must not be null", missingSchema.getMessage());
    }

    @Test
    void createsMappingFromSharedSafeToolConfiguration() {
        SpringAiMcpToolMapping mapping = SpringAiMcpToolMapping.from(currentUserOrdersToolConfig(),
                Map.of("currentUserId", context -> context.getContext().get("userId")));

        assertEquals("spring-ai", mapping.rawServerName());
        assertEquals("getOrdersByUserId", mapping.rawName());
        assertEquals("get_my_orders", mapping.name());
        assertEquals("Get orders for the authenticated user.", mapping.description());
        assertFalse(mapping.inputSchema().toString().contains("userId"));
        assertEquals(Map.of("status", "orderStatus"), mapping.argumentMappings());
        assertTrue(mapping.readOnly());
        assertEquals("user-123", mapping.injectedArguments().get("userId")
                .resolve(new ToolContext(Map.of("userId", "user-123"))));
    }

    @Test
    void matchesRawCallbacksByServerWhenRawToolNamesCollide() throws Exception {
        ServerNamedToolCallback ordersTool = new ServerNamedToolCallback(
                "mcp__orders__getOrdersByUserId",
                "orders",
                "getOrdersByUserId",
                rawOrderSchema());
        ServerNamedToolCallback catalogTool = new ServerNamedToolCallback(
                "mcp__catalog__getOrdersByUserId",
                "catalog",
                "getOrdersByUserId",
                rawOrderSchema());

        ToolCallbackProvider provider = FastMcpSpringAiTools.wrap(new ToolCallback[] { ordersTool, catalogTool },
                currentUserOrdersMapping("orders", "get_my_orders"),
                currentUserOrdersMapping("catalog", "get_catalog_orders"));
        ToolCallback[] callbacks = provider.getToolCallbacks();

        assertEquals("get_my_orders", callbacks[0].getToolDefinition().name());
        assertEquals("get_catalog_orders", callbacks[1].getToolDefinition().name());
        callbacks[0].call("{\"status\":\"PAID\"}", new ToolContext(Map.of("userId", "orders-user")));
        callbacks[1].call("{\"status\":\"OPEN\"}", new ToolContext(Map.of("userId", "catalog-user")));

        assertEquals(Map.of("status", "PAID", "userId", "orders-user"), ordersTool.lastInput());
        assertEquals(Map.of("status", "OPEN", "userId", "catalog-user"), catalogTool.lastInput());
    }

    private static SpringAiMcpToolMapping currentUserOrdersMapping() {
        return currentUserOrdersMapping(SpringAiMcpToolMapping.DEFAULT_RAW_SERVER_NAME, "get_my_orders");
    }

    private static SpringAiMcpToolMapping currentUserOrdersMapping(String rawServerName, String virtualName) {
        return SpringAiMcpToolMapping.builder(rawServerName, "getOrdersByUserId")
                .name(virtualName)
                .description("Get orders for the authenticated user.")
                .inputSchema(virtualOrderSchema())
                .injectArgument("userId", context -> context.getContext().get("userId"))
                .build();
    }

    private static SafeMcpToolConfiguration currentUserOrdersToolConfig() {
        return SafeMcpToolConfiguration.builder("getOrdersByUserId")
                .name("get_my_orders")
                .description("Get orders for the authenticated user.")
                .inputSchema(virtualOrderSchema())
                .mapArgument("status", "orderStatus")
                .injectArgument("userId", "currentUserId")
                .readOnly(true)
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

    private static String rawOrderSchema() {
        ObjectNode schema = virtualOrderSchema();
        ((ObjectNode) schema.get("properties")).set("userId",
                JsonNodeFactory.instance.objectNode().put("type", "string"));
        schema.withArray("required").add("userId");
        return schema.toString();
    }

    private static class CapturingToolCallback implements ToolCallback {
        private final ToolDefinition toolDefinition;
        private final AtomicReference<Map<String, Object>> lastInput = new AtomicReference<>();
        private final AtomicReference<ToolContext> lastContext = new AtomicReference<>();

        CapturingToolCallback(String name, String inputSchema) {
            this.toolDefinition = new DefaultToolDefinition(name, "Raw order lookup", inputSchema);
        }

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
                lastContext.set(toolContext);
                return "orders for " + input.get("userId") + " with status " + input.get("status");
            } catch (Exception exception) {
                throw new IllegalArgumentException(exception);
            }
        }

        Map<String, Object> lastInput() {
            return lastInput.get();
        }

        ToolContext lastContext() {
            return lastContext.get();
        }
    }

    private static final class OriginalNameToolCallback extends CapturingToolCallback {
        private final String originalToolName;

        private OriginalNameToolCallback(String name, String originalToolName, String inputSchema) {
            super(name, inputSchema);
            this.originalToolName = originalToolName;
        }

        public String getOriginalToolName() {
            return originalToolName;
        }
    }

    private static final class ServerNamedToolCallback extends CapturingToolCallback {
        private final String originalServerName;
        private final String originalToolName;

        private ServerNamedToolCallback(String name, String originalServerName, String originalToolName,
                String inputSchema) {
            super(name, inputSchema);
            this.originalServerName = originalServerName;
            this.originalToolName = originalToolName;
        }

        public String getOriginalServerName() {
            return originalServerName;
        }

        public String getOriginalToolName() {
            return originalToolName;
        }
    }
}
