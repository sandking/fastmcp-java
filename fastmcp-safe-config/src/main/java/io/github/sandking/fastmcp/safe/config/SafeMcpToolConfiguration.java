package io.github.sandking.fastmcp.safe.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SafeMcpToolConfiguration {
    private final String rawName;
    private final String name;
    private final String description;
    private final JsonNode inputSchema;
    private final Map<String, String> argumentMappings;
    private final Map<String, String> injectedArguments;
    private final boolean readOnly;
    private final boolean concurrencySafe;

    private SafeMcpToolConfiguration(Builder builder) {
        this.rawName = SafeMcpConfigException.requireText(builder.rawName, "rawName", "INVALID_RAW_TOOL_NAME");
        this.name = SafeMcpConfigException.requireText(builder.name, "name", "INVALID_TOOL_NAME");
        this.description = SafeMcpConfigException.requireText(builder.description, "description",
                "INVALID_TOOL_DESCRIPTION");
        if (builder.inputSchema == null) {
            throw new SafeMcpConfigException("INVALID_TOOL_SCHEMA", "inputSchema must not be null");
        }
        this.inputSchema = builder.inputSchema.deepCopy();
        this.argumentMappings = Collections.unmodifiableMap(new LinkedHashMap<>(builder.argumentMappings));
        this.injectedArguments = Collections.unmodifiableMap(new LinkedHashMap<>(builder.injectedArguments));
        this.readOnly = builder.readOnly;
        this.concurrencySafe = builder.concurrencySafe;
    }

    public static Builder builder(String rawName) {
        return new Builder(rawName);
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

    public Map<String, String> injectedArguments() {
        return injectedArguments;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public boolean concurrencySafe() {
        return concurrencySafe;
    }

    public static final class Builder {
        private final String rawName;
        private String name;
        private String description;
        private JsonNode inputSchema;
        private final Map<String, String> argumentMappings = new LinkedHashMap<>();
        private final Map<String, String> injectedArguments = new LinkedHashMap<>();
        private boolean readOnly;
        private boolean concurrencySafe;

        private Builder(String rawName) {
            this.rawName = SafeMcpConfigException.requireText(rawName, "rawName", "INVALID_RAW_TOOL_NAME");
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(JsonNode inputSchema) {
            if (inputSchema == null) {
                throw new SafeMcpConfigException("INVALID_TOOL_SCHEMA", "inputSchema must not be null");
            }
            this.inputSchema = inputSchema.deepCopy();
            return this;
        }

        public Builder mapArgument(String virtualName, String rawName) {
            String virtual = SafeMcpConfigException.requireText(virtualName, "virtualName",
                    "INVALID_ARGUMENT_MAPPING");
            String raw = SafeMcpConfigException.requireText(rawName, "rawName", "INVALID_ARGUMENT_MAPPING");
            argumentMappings.put(virtual, raw);
            return this;
        }

        public Builder injectArgument(String rawName, String sourceName) {
            String raw = SafeMcpConfigException.requireText(rawName, "rawName", "INVALID_INJECTED_ARGUMENT");
            String source = SafeMcpConfigException.requireText(sourceName, "sourceName", "INVALID_INJECTED_ARGUMENT");
            injectedArguments.put(raw, source);
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

        public SafeMcpToolConfiguration build() {
            return new SafeMcpToolConfiguration(this);
        }
    }
}
