package io.github.sandking.fastmcp.springai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sandking.fastmcp.safe.RawToolResult;
import io.github.sandking.fastmcp.safe.SafeAuditSink;
import io.github.sandking.fastmcp.safe.SafeMcpException;
import io.github.sandking.fastmcp.safe.SafeMcpTool;
import io.github.sandking.fastmcp.safe.SafeMcpPolicies;
import io.github.sandking.fastmcp.safe.SafeMcpToolSpec;
import io.github.sandking.fastmcp.safe.SafeToolCallContext;
import io.github.sandking.fastmcp.safe.SafeToolResult;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

public final class FastMcpSpringAiTools {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private FastMcpSpringAiTools() {
    }

    public static ToolCallbackProvider wrap(ToolCallbackProvider rawProvider, SpringAiMcpToolMapping... mappings) {
        Objects.requireNonNull(rawProvider, "rawProvider must not be null");
        return wrap(List.of(rawProvider.getToolCallbacks()), List.of(mappings));
    }

    public static ToolCallbackProvider wrap(ToolCallbackProvider rawProvider, List<SpringAiMcpToolMapping> mappings) {
        Objects.requireNonNull(rawProvider, "rawProvider must not be null");
        return wrap(List.of(rawProvider.getToolCallbacks()), mappings);
    }

    public static ToolCallbackProvider wrap(ToolCallback[] rawCallbacks, SpringAiMcpToolMapping... mappings) {
        return wrap(List.of(rawCallbacks), List.of(mappings));
    }

    public static ToolCallbackProvider wrap(List<ToolCallback> rawCallbacks, List<SpringAiMcpToolMapping> mappings) {
        return wrap(SpringAiMcpToolMapping.DEFAULT_RAW_SERVER_NAME, rawCallbacks, mappings);
    }

    public static ToolCallbackProvider wrap(String defaultRawServerName, List<ToolCallback> rawCallbacks,
            List<SpringAiMcpToolMapping> mappings) {
        SpringAiMcpToolMapping.requireText(defaultRawServerName, "defaultRawServerName");
        Objects.requireNonNull(rawCallbacks, "rawCallbacks must not be null");
        Objects.requireNonNull(mappings, "mappings must not be null");

        Map<String, ToolCallback> rawTools = new LinkedHashMap<>();
        for (ToolCallback rawCallback : rawCallbacks) {
            Objects.requireNonNull(rawCallback, "rawCallback must not be null");
            String rawServerName = originalServerName(rawCallback).orElse(defaultRawServerName);
            putRawTool(rawTools, rawServerName, rawCallback.getToolDefinition().name(), rawCallback);
            originalToolName(rawCallback).ifPresent(name -> putRawTool(rawTools, rawServerName, name, rawCallback));
        }

        List<ToolCallback> safeCallbacks = new ArrayList<>();
        for (SpringAiMcpToolMapping mapping : mappings) {
            SpringAiMcpToolMapping safeMapping = Objects.requireNonNull(mapping, "mapping must not be null");
            ToolCallback rawCallback = rawTools.get(rawKey(safeMapping.rawServerName(), safeMapping.rawName()));
            if (rawCallback == null) {
                throw new IllegalArgumentException("Raw Spring AI tool not found: "
                        + safeMapping.rawServerName() + "/" + safeMapping.rawName());
            }
            safeCallbacks.add(new SafeSpringAiToolCallback(rawCallback, safeMapping));
        }
        return ToolCallbackProvider.from(safeCallbacks);
    }

    private static java.util.Optional<String> originalToolName(ToolCallback rawCallback) {
        return reflectedString(rawCallback, "getOriginalToolName");
    }

    private static java.util.Optional<String> originalServerName(ToolCallback rawCallback) {
        return reflectedString(rawCallback, "getOriginalServerName");
    }

    private static java.util.Optional<String> reflectedString(ToolCallback rawCallback, String methodName) {
        try {
            Method method = rawCallback.getClass().getMethod(methodName);
            Object value = method.invoke(rawCallback);
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return java.util.Optional.of((String) value);
            }
            return java.util.Optional.empty();
        } catch (ReflectiveOperationException exception) {
            return java.util.Optional.empty();
        }
    }

    private static String rawKey(String rawServerName, String rawToolName) {
        return SpringAiMcpToolMapping.requireText(rawServerName, "rawServerName") + "\n"
                + SpringAiMcpToolMapping.requireText(rawToolName, "rawToolName");
    }

    private static void putRawTool(Map<String, ToolCallback> rawTools, String rawServerName, String rawToolName,
            ToolCallback rawCallback) {
        String key = rawKey(rawServerName, rawToolName);
        ToolCallback previous = rawTools.putIfAbsent(key, rawCallback);
        if (previous != null && previous != rawCallback) {
            throw new IllegalArgumentException("Duplicate raw Spring AI tool: " + rawServerName + "/" + rawToolName);
        }
    }

    private static final class SafeSpringAiToolCallback implements ToolCallback {
        private final ToolCallback rawCallback;
        private final ToolDefinition toolDefinition;
        private final SafeMcpTool safeTool;

        private SafeSpringAiToolCallback(ToolCallback rawCallback, SpringAiMcpToolMapping mapping) {
            this.rawCallback = rawCallback;
            this.toolDefinition = new DefaultToolDefinition(mapping.name(), description(mapping),
                    inputSchema(mapping));
            this.safeTool = new SafeMcpTool("spring-ai", toSafeSpec(rawCallback, mapping),
                    (serverName, rawToolName, rawArguments, context) -> CompletableFuture.completedFuture(
                            RawToolResult.text(rawCallback.call(toJson(rawArguments), toolContext(context)))),
                    SafeMcpPolicies.allow(),
                    SafeAuditSink.noOp(),
                    mapping.resultSanitizer());
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return rawCallback.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, new ToolContext(Map.of()));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                SafeToolResult result = safeTool.callAsync(input(toolInput), safeContext(toolContext))
                        .toCompletableFuture()
                        .join();
                return result.content();
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new SafeMcpException("SPRING_AI_TOOL_FAILED", cause.getMessage(), cause);
            }
        }

        private static ToolContext toolContext(SafeToolCallContext safeContext) {
            ToolContext context = safeContext.frameworkContext(ToolContext.class);
            return context == null ? new ToolContext(Map.of()) : context;
        }
    }

    private static SafeMcpToolSpec toSafeSpec(ToolCallback rawCallback, SpringAiMcpToolMapping mapping) {
        SafeMcpToolSpec.Builder builder = SafeMcpToolSpec.builder(mapping.rawServerName(), mapping.rawName())
                .name(mapping.name())
                .description(description(mapping))
                .inputSchema(mapping.inputSchema())
                .readOnly(mapping.readOnly())
                .concurrencySafe(mapping.concurrencySafe());
        mapping.argumentMappings().forEach(builder::mapArgument);
        mapping.injectedArguments().forEach((rawName, resolver) -> builder.injectArgument(rawName,
                context -> resolver.resolve(context.frameworkContext(ToolContext.class))));
        return builder.build();
    }

    private static String description(SpringAiMcpToolMapping mapping) {
        return mapping.description();
    }

    private static String inputSchema(SpringAiMcpToolMapping mapping) {
        return mapping.inputSchema().toString();
    }

    private static Map<String, Object> input(String toolInput) {
        if (toolInput == null || toolInput.trim().isEmpty()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(toolInput, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new SafeMcpException("INVALID_TOOL_INPUT", "Tool input must be a JSON object", exception);
        }
    }

    private static SafeToolCallContext safeContext(ToolContext toolContext) {
        ToolContext context = toolContext == null ? new ToolContext(Map.of()) : toolContext;
        SafeToolCallContext.Builder builder = SafeToolCallContext.builder()
                .frameworkContext(ToolContext.class, context);
        for (Map.Entry<String, Object> entry : context.getContext().entrySet()) {
            builder.toolContext(entry.getKey(), entry.getValue())
                    .attribute(entry.getKey(), entry.getValue());
        }
        Object userId = context.getContext().get("userId");
        if (userId instanceof String) {
            builder.userId((String) userId);
        }
        Object tenantId = context.getContext().get("tenantId");
        if (tenantId instanceof String) {
            builder.tenantId((String) tenantId);
        }
        return builder.build();
    }

    private static String toJson(Map<String, Object> rawArguments) {
        try {
            return OBJECT_MAPPER.writeValueAsString(rawArguments);
        } catch (JsonProcessingException exception) {
            throw new SafeMcpException("RAW_ARGUMENT_SERIALIZATION_FAILED",
                    "Failed to serialize raw tool arguments", exception);
        }
    }

}
