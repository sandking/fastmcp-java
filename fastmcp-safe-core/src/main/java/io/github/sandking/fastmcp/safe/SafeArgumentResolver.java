package io.github.sandking.fastmcp.safe;

@FunctionalInterface
public interface SafeArgumentResolver {
    Object resolve(SafeToolCallContext context);
}
