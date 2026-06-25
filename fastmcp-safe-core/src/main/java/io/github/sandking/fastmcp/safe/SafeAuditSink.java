package io.github.sandking.fastmcp.safe;

@FunctionalInterface
public interface SafeAuditSink {
    void record(SafeAuditEvent event);

    static SafeAuditSink noOp() {
        return event -> {
        };
    }
}
