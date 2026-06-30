package io.github.sandking.fastmcp.springai;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import org.springframework.ai.tool.ToolCallback;

final class SpringAiRawToolIdentity {
    private final String serverName;
    private final String definitionToolName;
    private final Optional<String> originalToolName;

    private SpringAiRawToolIdentity(String serverName, String definitionToolName, Optional<String> originalToolName) {
        this.serverName = SpringAiMcpToolMapping.requireText(serverName, "serverName");
        this.definitionToolName = SpringAiMcpToolMapping.requireText(definitionToolName, "definitionToolName");
        this.originalToolName = Objects.requireNonNull(originalToolName, "originalToolName must not be null");
    }

    static SpringAiRawToolIdentity from(ToolCallback rawCallback, String defaultRawServerName) {
        Objects.requireNonNull(rawCallback, "rawCallback must not be null");
        String serverName = reflectedString(rawCallback, "getOriginalServerName")
                .orElse(SpringAiMcpToolMapping.requireText(defaultRawServerName, "defaultRawServerName"));
        return new SpringAiRawToolIdentity(serverName, rawCallback.getToolDefinition().name(),
                reflectedString(rawCallback, "getOriginalToolName"));
    }

    String serverName() {
        return serverName;
    }

    String definitionToolName() {
        return definitionToolName;
    }

    Optional<String> originalToolName() {
        return originalToolName;
    }

    private static Optional<String> reflectedString(ToolCallback rawCallback, String methodName) {
        try {
            Method method = rawCallback.getClass().getMethod(methodName);
            Object value = method.invoke(rawCallback);
            if (value instanceof String && !((String) value).trim().isEmpty()) {
                return Optional.of((String) value);
            }
            return Optional.empty();
        } catch (ReflectiveOperationException exception) {
            return Optional.empty();
        }
    }
}
