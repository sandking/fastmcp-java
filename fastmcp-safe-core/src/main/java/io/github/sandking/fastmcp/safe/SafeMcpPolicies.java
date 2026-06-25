package io.github.sandking.fastmcp.safe;

public final class SafeMcpPolicies {
    private SafeMcpPolicies() {
    }

    public static SafeMcpPolicy allow() {
        return (context, request) -> SafePolicyDecision.allow("allowed");
    }

    public static SafeMcpPolicy deny(String reason) {
        return (context, request) -> SafePolicyDecision.deny(reason);
    }

    public static SafeMcpPolicy allowWhenAuthenticated() {
        return (context, request) -> context != null && context.userId() != null && !context.userId().trim().isEmpty()
                ? SafePolicyDecision.allow("authenticated")
                : SafePolicyDecision.deny("authentication required");
    }

    public static SafeMcpPolicy readOnly() {
        return (context, request) -> request.readOnly()
                ? SafePolicyDecision.allow("read-only")
                : SafePolicyDecision.deny("tool is not read-only");
    }
}
