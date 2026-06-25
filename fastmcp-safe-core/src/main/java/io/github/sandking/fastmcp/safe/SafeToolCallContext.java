package io.github.sandking.fastmcp.safe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SafeToolCallContext {
    private final String userId;
    private final String tenantId;
    private final Map<String, Object> attributes;
    private final Map<String, Object> toolContext;
    private final Map<Class<?>, Object> frameworkContexts;

    private SafeToolCallContext(Builder builder) {
        this.userId = builder.userId;
        this.tenantId = builder.tenantId;
        this.attributes = unmodifiableCopy(builder.attributes);
        this.toolContext = unmodifiableCopy(builder.toolContext);
        this.frameworkContexts = Collections.unmodifiableMap(new LinkedHashMap<>(builder.frameworkContexts));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String userId() {
        return userId;
    }

    public String tenantId() {
        return tenantId;
    }

    public Object attribute(String name) {
        return attributes.get(SafeMcpException.requireText(name, "name"));
    }

    public Map<String, Object> toolContext() {
        return toolContext;
    }

    public <T> T frameworkContext(Class<T> type) {
        Object value = frameworkContexts.get(type);
        return value == null ? null : type.cast(value);
    }

    private static Map<String, Object> unmodifiableCopy(Map<String, Object> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static final class Builder {
        private String userId;
        private String tenantId;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final Map<String, Object> toolContext = new LinkedHashMap<>();
        private final Map<Class<?>, Object> frameworkContexts = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder attribute(String name, Object value) {
            attributes.put(SafeMcpException.requireText(name, "name"), value);
            return this;
        }

        public Builder toolContext(String name, Object value) {
            toolContext.put(SafeMcpException.requireText(name, "name"), value);
            return this;
        }

        public <T> Builder frameworkContext(Class<T> type, T value) {
            if (type == null) {
                throw new NullPointerException("type must not be null");
            }
            frameworkContexts.put(type, value);
            return this;
        }

        public SafeToolCallContext build() {
            return new SafeToolCallContext(this);
        }
    }
}
