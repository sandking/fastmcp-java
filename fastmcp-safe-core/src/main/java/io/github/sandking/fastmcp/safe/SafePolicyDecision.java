package io.github.sandking.fastmcp.safe;

public final class SafePolicyDecision {
    private final boolean allowed;
    private final String reason;

    private SafePolicyDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason == null ? "" : reason;
    }

    public static SafePolicyDecision allow(String reason) {
        return new SafePolicyDecision(true, reason);
    }

    public static SafePolicyDecision deny(String reason) {
        return new SafePolicyDecision(false, reason);
    }

    public boolean allowed() {
        return allowed;
    }

    public String reason() {
        return reason;
    }
}
