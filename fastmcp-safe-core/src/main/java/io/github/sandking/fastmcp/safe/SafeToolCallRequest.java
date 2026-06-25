package io.github.sandking.fastmcp.safe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SafeToolCallRequest {
    private final String virtualToolName;
    private final String rawServerName;
    private final String rawToolName;
    private final Map<String, Object> rawArguments;
    private final boolean readOnly;

    public SafeToolCallRequest(String virtualToolName, String rawServerName, String rawToolName,
            Map<String, Object> rawArguments, boolean readOnly) {
        this.virtualToolName = SafeMcpException.requireText(virtualToolName, "virtualToolName");
        this.rawServerName = SafeMcpException.requireText(rawServerName, "rawServerName");
        this.rawToolName = SafeMcpException.requireText(rawToolName, "rawToolName");
        this.rawArguments = Collections.unmodifiableMap(new LinkedHashMap<>(rawArguments));
        this.readOnly = readOnly;
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

    public Map<String, Object> rawArguments() {
        return rawArguments;
    }

    public boolean readOnly() {
        return readOnly;
    }
}
