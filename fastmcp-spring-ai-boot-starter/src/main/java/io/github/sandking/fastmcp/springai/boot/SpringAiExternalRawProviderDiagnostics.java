package io.github.sandking.fastmcp.springai.boot;

import io.github.sandking.fastmcp.safe.SafeAuditEvent;
import io.github.sandking.fastmcp.safe.SafeAuditSink;
import io.github.sandking.fastmcp.safe.boot.FastMcpSafeProperties;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SpringAiExternalRawProviderDiagnostics {
    private static final Logger logger = LoggerFactory.getLogger(SpringAiExternalRawProviderDiagnostics.class);
    private static final String MESSAGE =
            "External raw Spring AI ToolCallbackProvider beans are present: %s; ensure models receive "
                    + "fastMcpSafeToolCallbackProvider, not raw providers. This is a conservative diagnostic: "
                    + "external ToolCallbackProvider beans are the main raw-provider exposure risk, "
                    + "although they may include non-raw business providers.";

    private final String mode;

    private SpringAiExternalRawProviderDiagnostics(String mode) {
        this.mode = mode;
    }

    static SpringAiExternalRawProviderDiagnostics from(FastMcpSafeProperties properties) {
        return new SpringAiExternalRawProviderDiagnostics(normalize(properties.getDiagnostics()
                .getExternalRawProvider()));
    }

    void diagnose(List<String> externalRawProviderNames, SafeAuditSink auditSink) {
        if (externalRawProviderNames.isEmpty() || "off".equals(mode)) {
            return;
        }
        SafeAuditSink sink = auditSink == null ? SafeAuditSink.noOp() : auditSink;
        String message = String.format(MESSAGE, externalRawProviderNames);
        sink.record(SafeAuditEvent.diagnostic("spring-ai",
                "EXTERNAL_RAW_PROVIDER_PRESENT",
                "fastMcpSafeToolCallbackProvider",
                Map.of("providerNames", String.join(",", externalRawProviderNames), "mode", mode)));
        if ("fail".equals(mode)) {
            throw new IllegalStateException(message);
        }
        logger.warn(message);
    }

    private static String normalize(String mode) {
        String normalizedMode = mode == null ? "fail" : mode.trim().toLowerCase(Locale.ROOT);
        if (!"warn".equals(normalizedMode) && !"fail".equals(normalizedMode) && !"off".equals(normalizedMode)) {
            throw new IllegalArgumentException("Unsupported fastmcp.safe.diagnostics.external-raw-provider: " + mode);
        }
        return normalizedMode;
    }
}
