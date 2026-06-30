package io.github.sandking.fastmcp.springai.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.sandking.fastmcp.safe.SafeAuditSink;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration;
import io.github.sandking.fastmcp.springai.SpringAiToolArgumentResolver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

class SpringAiSafeToolCallbackProviderAssemblerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    void usesManagedRawProviderForMatchingServerBeforeExternalProvider() {
        CapturingToolCallback managedRawTool = new CapturingToolCallback("managed");
        CapturingToolCallback externalRawTool = new CapturingToolCallback("external");

        ToolCallbackProvider safeProvider = new SpringAiSafeToolCallbackProviderAssembler().assemble(
                configuration("orders"),
                Map.of("orders", ToolCallbackProvider.from(managedRawTool)),
                List.of(ToolCallbackProvider.from(externalRawTool)),
                resolvers(),
                SafeAuditSink.noOp());

        String result = safeProvider.getToolCallbacks()[0].call("{\"status\":\"PAID\"}",
                new ToolContext(Map.of("userId", "user-123")));

        assertThat(result).isEqualTo("managed:user-123:PAID");
        assertThat(managedRawTool.lastInput()).containsEntry("orderStatus", "PAID")
                .containsEntry("userId", "user-123");
        assertThat(externalRawTool.lastInput()).isNull();
    }

    @Test
    void usesExternalRawProviderWhenServerHasNoManagedProvider() {
        CapturingToolCallback externalRawTool = new CapturingToolCallback("external");

        ToolCallbackProvider safeProvider = new SpringAiSafeToolCallbackProviderAssembler().assemble(
                configuration("orders"),
                Map.of(),
                List.of(ToolCallbackProvider.from(externalRawTool)),
                resolvers(),
                SafeAuditSink.noOp());

        String result = safeProvider.getToolCallbacks()[0].call("{\"status\":\"PAID\"}",
                new ToolContext(Map.of("userId", "user-123")));

        assertThat(result).isEqualTo("external:user-123:PAID");
        assertThat(externalRawTool.lastInput()).containsEntry("orderStatus", "PAID")
                .containsEntry("userId", "user-123");
    }

    private static SafeMcpConfiguration configuration(String serverName) {
        return SafeMcpConfiguration.builder()
                .server(SafeMcpServerConfiguration.builder(serverName)
                        .tool(SafeMcpToolConfiguration.builder("getOrdersByUserId")
                                .name("get_my_orders")
                                .description("Get orders for the authenticated user.")
                                .inputSchema(virtualOrderSchema())
                                .mapArgument("status", "orderStatus")
                                .injectArgument("userId", "currentUserId")
                                .build())
                        .build())
                .build();
    }

    private static Map<String, SpringAiToolArgumentResolver> resolvers() {
        return Map.of("currentUserId", context -> context.getContext().get("userId"));
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

    private static final class CapturingToolCallback implements ToolCallback {
        private final String source;
        private final AtomicReference<Map<String, Object>> lastInput = new AtomicReference<>();
        private final ToolDefinition toolDefinition = new DefaultToolDefinition("getOrdersByUserId",
                "Raw order lookup",
                "{\"type\":\"object\",\"properties\":{\"orderStatus\":{\"type\":\"string\"},"
                        + "\"userId\":{\"type\":\"string\"}},\"required\":[\"orderStatus\",\"userId\"]}");

        private CapturingToolCallback(String source) {
            this.source = source;
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
                return source + ":" + input.get("userId") + ":" + input.get("orderStatus");
            } catch (Exception exception) {
                throw new IllegalArgumentException(exception);
            }
        }

        Map<String, Object> lastInput() {
            return lastInput.get();
        }
    }
}
