package io.github.sandking.fastmcp.springai.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.sandking.fastmcp.safe.SafeAuditEvent;
import io.github.sandking.fastmcp.safe.boot.FastMcpSafeProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringAiExternalRawProviderDiagnosticsTest {
    @Test
    void defaultModeRecordsDiagnosticAuditAndThrows() {
        SpringAiExternalRawProviderDiagnostics diagnostics = SpringAiExternalRawProviderDiagnostics.from(
                new FastMcpSafeProperties());
        List<SafeAuditEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> diagnostics.diagnose(List.of("rawOrderToolProvider"), events::add))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("External raw Spring AI ToolCallbackProvider beans are present")
                .hasMessageContaining("rawOrderToolProvider");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).details()).containsEntry("mode", "fail");
    }

    @Test
    void failModeRecordsDiagnosticAuditAndThrows() {
        SpringAiExternalRawProviderDiagnostics diagnostics = SpringAiExternalRawProviderDiagnostics.from(
                properties("fail"));
        List<SafeAuditEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> diagnostics.diagnose(List.of("rawOrderToolProvider"), events::add))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("External raw Spring AI ToolCallbackProvider beans are present")
                .hasMessageContaining("rawOrderToolProvider");

        assertThat(events).hasSize(1);
        SafeAuditEvent event = events.get(0);
        assertThat(event.eventType()).isEqualTo("DIAGNOSTIC");
        assertThat(event.framework()).isEqualTo("spring-ai");
        assertThat(event.virtualToolName()).isEqualTo("fastMcpSafeToolCallbackProvider");
        assertThat(event.errorCode()).isEqualTo("EXTERNAL_RAW_PROVIDER_PRESENT");
        assertThat(event.details()).containsEntry("providerNames", "rawOrderToolProvider")
                .containsEntry("mode", "fail");
    }

    @Test
    void offModeSkipsDiagnostics() {
        SpringAiExternalRawProviderDiagnostics diagnostics = SpringAiExternalRawProviderDiagnostics.from(
                properties("off"));
        List<SafeAuditEvent> events = new ArrayList<>();

        diagnostics.diagnose(List.of("rawOrderToolProvider"), events::add);

        assertThat(events).isEmpty();
    }

    @Test
    void blankOrUnknownModeFailsClearly() {
        assertThatThrownBy(() -> SpringAiExternalRawProviderDiagnostics.from(properties("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported fastmcp.safe.diagnostics.external-raw-provider:");

        assertThatThrownBy(() -> SpringAiExternalRawProviderDiagnostics.from(properties("block")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported fastmcp.safe.diagnostics.external-raw-provider: block");
    }

    private static FastMcpSafeProperties properties(String externalRawProviderMode) {
        FastMcpSafeProperties properties = new FastMcpSafeProperties();
        properties.getDiagnostics().setExternalRawProvider(externalRawProviderMode);
        return properties;
    }
}
