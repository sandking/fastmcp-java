package io.github.sandking.fastmcp.safe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class RawToolResult {
    private final String content;
    private final Object structuredContent;
    private final Map<String, Object> meta;

    private RawToolResult(Builder builder) {
        this.content = builder.content == null ? "" : builder.content;
        this.structuredContent = builder.structuredContent;
        this.meta = Collections.unmodifiableMap(new LinkedHashMap<>(builder.meta));
    }

    public static RawToolResult text(String content) {
        return builder().content(content).build();
    }

    public static Builder builder() {
        return new Builder();
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

        public Builder meta(String name, Object value) {
            meta.put(SafeMcpException.requireText(name, "name"), value);
            return this;
        }

        public Builder meta(Map<String, ?> values) {
            if (values != null) {
                values.forEach(this::meta);
            }
            return this;
        }

        public RawToolResult build() {
            return new RawToolResult(this);
        }
    }
}
