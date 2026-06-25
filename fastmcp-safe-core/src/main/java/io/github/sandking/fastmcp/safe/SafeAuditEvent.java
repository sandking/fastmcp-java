package io.github.sandking.fastmcp.safe;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SafeAuditEvent {
    private final Instant timestamp;
    private final String framework;
    private final String virtualToolName;
    private final String rawServerName;
    private final String rawToolName;
    private final String callerId;
    private final String tenantId;
    private final Set<String> injectedArgumentNames;
    private final String policyId;
    private final String policyDecision;
    private final boolean success;
    private final String errorCode;

    private SafeAuditEvent(Builder builder) {
        this.timestamp = builder.timestamp == null ? Instant.now() : builder.timestamp;
        this.framework = builder.framework;
        this.virtualToolName = builder.virtualToolName;
        this.rawServerName = builder.rawServerName;
        this.rawToolName = builder.rawToolName;
        this.callerId = builder.callerId;
        this.tenantId = builder.tenantId;
        this.injectedArgumentNames = Collections.unmodifiableSet(new LinkedHashSet<>(builder.injectedArgumentNames));
        this.policyId = builder.policyId;
        this.policyDecision = builder.policyDecision;
        this.success = builder.success;
        this.errorCode = builder.errorCode;
    }

    static Builder builder() {
        return new Builder();
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String framework() {
        return framework;
    }

    public String virtualToolName() {
        return virtualToolName;
    }

    public String rawServerName() {
        return rawServerName;
    }

    public String rawToolName() {
        return rawToolName;
    }

    public String callerId() {
        return callerId;
    }

    public String tenantId() {
        return tenantId;
    }

    public Set<String> injectedArgumentNames() {
        return injectedArgumentNames;
    }

    public String policyId() {
        return policyId;
    }

    public String policyDecision() {
        return policyDecision;
    }

    public boolean success() {
        return success;
    }

    public String errorCode() {
        return errorCode;
    }

    static final class Builder {
        private Instant timestamp;
        private String framework;
        private String virtualToolName;
        private String rawServerName;
        private String rawToolName;
        private String callerId;
        private String tenantId;
        private final Set<String> injectedArgumentNames = new LinkedHashSet<>();
        private String policyId;
        private String policyDecision;
        private boolean success;
        private String errorCode;

        Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        Builder framework(String framework) {
            this.framework = framework;
            return this;
        }

        Builder virtualToolName(String virtualToolName) {
            this.virtualToolName = virtualToolName;
            return this;
        }

        Builder rawServerName(String rawServerName) {
            this.rawServerName = rawServerName;
            return this;
        }

        Builder rawToolName(String rawToolName) {
            this.rawToolName = rawToolName;
            return this;
        }

        Builder callerId(String callerId) {
            this.callerId = callerId;
            return this;
        }

        Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        Builder injectedArgumentNames(Set<String> injectedArgumentNames) {
            this.injectedArgumentNames.addAll(injectedArgumentNames);
            return this;
        }

        Builder policyId(String policyId) {
            this.policyId = policyId;
            return this;
        }

        Builder policyDecision(String policyDecision) {
            this.policyDecision = policyDecision;
            return this;
        }

        Builder success(boolean success) {
            this.success = success;
            return this;
        }

        Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        SafeAuditEvent build() {
            return new SafeAuditEvent(this);
        }
    }
}
