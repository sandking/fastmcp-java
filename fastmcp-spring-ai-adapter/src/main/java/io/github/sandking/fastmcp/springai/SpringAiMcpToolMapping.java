package io.github.sandking.fastmcp.springai;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.sandking.fastmcp.safe.SafeResultSanitizer;
import io.github.sandking.fastmcp.safe.SafeResultSanitizers;
import io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SpringAiMcpToolMapping {
    public static final String DEFAULT_RAW_SERVER_NAME = "spring-ai";

    private final String rawServerName;
    private final String rawName;
    private final String name;
    private final String description;
    private final JsonNode inputSchema;
    private final Map<String, String> argumentMappings;
    private final Map<String, SpringAiToolArgumentResolver> injectedArguments;
    private final boolean readOnly;
    private final boolean concurrencySafe;
    private final SafeResultSanitizer resultSanitizer;

    private SpringAiMcpToolMapping(Builder builder) {
        this.rawServerName = requireText(builder.rawServerName, "rawServerName");
        this.rawName = requireText(builder.rawName, "rawName");
        this.name = requireText(builder.name, "name");
        this.description = requireText(builder.description, "description");
        this.inputSchema = Objects.requireNonNull(builder.inputSchema, "inputSchema must not be null").deepCopy();
        this.argumentMappings = Collections.unmodifiableMap(new LinkedHashMap<>(builder.argumentMappings));
        this.injectedArguments = Collections.unmodifiableMap(new LinkedHashMap<>(builder.injectedArguments));
        this.readOnly = builder.readOnly;
        this.concurrencySafe = builder.concurrencySafe;
        this.resultSanitizer = builder.resultSanitizer;
    }

    public static Builder builder(String rawName) {
        return new Builder(DEFAULT_RAW_SERVER_NAME, rawName);
    }

    public static Builder builder(String rawServerName, String rawName) {
        return new Builder(rawServerName, rawName);
    }

    public static SpringAiMcpToolMapping from(
            SafeMcpToolConfiguration configuration, Map<String, SpringAiToolArgumentResolver> resolvers) {
        return from(DEFAULT_RAW_SERVER_NAME, configuration, resolvers);
    }

    public static SpringAiMcpToolMapping from(String rawServerName,
            SafeMcpToolConfiguration configuration, Map<String, SpringAiToolArgumentResolver> resolvers) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(resolvers, "resolvers must not be null");
        Builder builder = builder(rawServerName, configuration.rawName())
                .name(configuration.name())
                .description(configuration.description())
                .inputSchema(configuration.inputSchema())
                .readOnly(configuration.readOnly())
                .concurrencySafe(configuration.concurrencySafe());
        configuration.argumentMappings().forEach(builder::mapArgument);
        configuration.injectedArguments().forEach((rawName, sourceName) -> {
            SpringAiToolArgumentResolver resolver = resolvers.get(sourceName);
            if (resolver == null) {
                throw new IllegalArgumentException("Injected argument resolver not found: " + sourceName);
            }
            builder.injectArgument(rawName, resolver);
        });
        return builder.build();
    }

    public String rawServerName() {
        return rawServerName;
    }

    public String rawName() {
        return rawName;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public JsonNode inputSchema() {
        return inputSchema.deepCopy();
    }

    public Map<String, String> argumentMappings() {
        return argumentMappings;
    }

    public Map<String, SpringAiToolArgumentResolver> injectedArguments() {
        return injectedArguments;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public boolean concurrencySafe() {
        return concurrencySafe;
    }

    public SafeResultSanitizer resultSanitizer() {
        return resultSanitizer;
    }

    static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    public static final class Builder {
        private final String rawServerName;
        private final String rawName;
        private String name;
        private String description;
        private JsonNode inputSchema;
        private final Map<String, String> argumentMappings = new LinkedHashMap<>();
        private final Map<String, SpringAiToolArgumentResolver> injectedArguments = new LinkedHashMap<>();
        private boolean readOnly;
        private boolean concurrencySafe;
        private SafeResultSanitizer resultSanitizer = SafeResultSanitizers.modelSafe();

        private Builder(String rawServerName, String rawName) {
            this.rawServerName = requireText(rawServerName, "rawServerName");
            this.rawName = requireText(rawName, "rawName");
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description == null ? "" : description.trim();
            return this;
        }

        public Builder inputSchema(JsonNode inputSchema) {
            this.inputSchema = Objects.requireNonNull(inputSchema, "inputSchema must not be null").deepCopy();
            return this;
        }

        public Builder mapArgument(String virtualName, String rawName) {
            argumentMappings.put(requireText(virtualName, "virtualName"), requireText(rawName, "rawName"));
            return this;
        }

        public Builder injectArgument(String rawName, SpringAiToolArgumentResolver resolver) {
            injectedArguments.put(requireText(rawName, "rawName"),
                    Objects.requireNonNull(resolver, "resolver must not be null"));
            return this;
        }

        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public Builder concurrencySafe(boolean concurrencySafe) {
            this.concurrencySafe = concurrencySafe;
            return this;
        }

        public Builder resultSanitizer(SafeResultSanitizer resultSanitizer) {
            this.resultSanitizer = Objects.requireNonNull(resultSanitizer, "resultSanitizer must not be null");
            return this;
        }

        public SpringAiMcpToolMapping build() {
            return new SpringAiMcpToolMapping(this);
        }
    }
}
