package io.github.sandking.fastmcp.safe;

@FunctionalInterface
public interface SafeMcpPolicy {
    SafePolicyDecision evaluate(SafeToolCallContext context, SafeToolCallRequest request);
}
