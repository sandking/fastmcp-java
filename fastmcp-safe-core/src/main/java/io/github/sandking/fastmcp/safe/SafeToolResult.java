package io.github.sandking.fastmcp.safe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SafeToolResult {
    private final String toolName;
    private final String content;
    private final Object structuredContent;
    private final Map<String, Object> meta;

    public SafeToolResult(String toolName, RawToolResult rawResult) {
        this.toolName = SafeMcpException.requireText(toolName, "toolName");
        this.content = rawResult == null ? "" : rawResult.content();
        this.structuredContent = rawResult == null ? null : rawResult.structuredContent().orElse(null);
        this.meta = rawResult == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(rawResult.meta()));
    }

    public String toolName() {
        return toolName;
    }

    public String content() {
        return content;
    }

    public Optional<Object> structuredContent() {
        return Optional.ofNullable(structuredContent);
    }

    public Map<String, Object> meta() {
        return meta;
    }
}
