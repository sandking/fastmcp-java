package io.github.sandking.fastmcp.safe;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SafeMcpToolSpec {
    private final String name;
    private final String description;
    private final JsonNode inputSchema;
    private final String rawServerName;
    private final String rawToolName;
    private final Map<String, String> argumentMappings;
    private final Map<String, SafeArgumentResolver> injectedArguments;
    private final boolean readOnly;
    private final boolean concurrencySafe;
    private final String policyId;

    private SafeMcpToolSpec(Builder builder) {
        this.name = SafeMcpException.requireText(builder.name, "name");
        this.description = builder.description == null ? "" : builder.description;
        this.inputSchema = builder.inputSchema == null ? null : builder.inputSchema.deepCopy();
        this.rawServerName = SafeMcpException.requireText(builder.rawServerName, "rawServerName");
        this.rawToolName = SafeMcpException.requireText(builder.rawToolName, "rawToolName");
        this.argumentMappings = Collections.unmodifiableMap(new LinkedHashMap<>(builder.argumentMappings));
        this.injectedArguments = Collections.unmodifiableMap(new LinkedHashMap<>(builder.injectedArguments));
        SafeInputSchemaGuard.rejectProtectedArguments(this.inputSchema, this.argumentMappings,
                this.injectedArguments.keySet());
        this.readOnly = builder.readOnly;
        this.concurrencySafe = builder.concurrencySafe;
        this.policyId = builder.policyId == null ? "allow" : builder.policyId;
    }

    public static Builder builder(String rawServerName, String rawToolName) {
        return new Builder(rawServerName, rawToolName);
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public JsonNode inputSchema() {
        return inputSchema == null ? null : inputSchema.deepCopy();
    }

    public String rawServerName() {
        return rawServerName;
    }

    public String rawToolName() {
        return rawToolName;
    }

    public Map<String, String> argumentMappings() {
        return argumentMappings;
    }

    public Map<String, SafeArgumentResolver> injectedArguments() {
        return injectedArguments;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public boolean concurrencySafe() {
        return concurrencySafe;
    }

    public String policyId() {
        return policyId;
    }

    public static final class Builder {
        private String name;
        private String description;
        private JsonNode inputSchema;
        private final String rawServerName;
        private final String rawToolName;
        private final Map<String, String> argumentMappings = new LinkedHashMap<>();
        private final Map<String, SafeArgumentResolver> injectedArguments = new LinkedHashMap<>();
        private boolean readOnly;
        private boolean concurrencySafe;
        private String policyId;

        private Builder(String rawServerName, String rawToolName) {
            this.rawServerName = rawServerName;
            this.rawToolName = rawToolName;
            this.name = rawToolName;
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
            this.inputSchema = inputSchema == null ? null : inputSchema.deepCopy();
            return this;
        }

        public Builder mapArgument(String virtualName, String rawName) {
            argumentMappings.put(SafeMcpException.requireText(virtualName, "virtualName"),
                    SafeMcpException.requireText(rawName, "rawName"));
            return this;
        }

        public Builder injectArgument(String rawName, SafeArgumentResolver resolver) {
            if (resolver == null) {
                throw new NullPointerException("resolver must not be null");
            }
            injectedArguments.put(SafeMcpException.requireText(rawName, "rawName"), resolver);
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

        public Builder policyId(String policyId) {
            this.policyId = SafeMcpException.requireText(policyId, "policyId");
            return this;
        }

        public SafeMcpToolSpec build() {
            return new SafeMcpToolSpec(this);
        }
    }
}
