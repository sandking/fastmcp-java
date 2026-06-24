package io.github.sandking.fastmcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ToolResult {
    private final String content;
    private final Object structuredContent;
    private final Map<String, Object> meta;

    private ToolResult(String content, Object structuredContent, Map<String, Object> meta) {
        this.content = content == null ? "" : content;
        this.structuredContent = structuredContent;
        this.meta = Collections.unmodifiableMap(new LinkedHashMap<>(meta));
    }

    public static ToolResult text(String content) {
        return builder().content(content).build();
    }

    public static ToolResult structured(Object structuredContent) {
        return builder().structuredContent(structuredContent).build();
    }

    public static ToolResult of(String content, Object structuredContent) {
        return builder().content(content).structuredContent(structuredContent).build();
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String content;
        private Object structuredContent;
        private final Map<String, Object> meta = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder structuredContent(Object structuredContent) {
            this.structuredContent = structuredContent;
            return this;
        }

        public Builder meta(String key, Object value) {
            meta.put(FastMcpServer.requireText(key, "key"), value);
            return this;
        }

        public ToolResult build() {
            return new ToolResult(content, structuredContent, meta);
        }
    }
}
