package io.github.sandking.fastmcp.safe;

/**
 * Sanitizes raw tool results before they are returned to the model-visible safe tool result.
 *
 * <p>The default sanitizer is model-safe: it preserves text content and drops raw metadata and structured content.
 * Implementations may receive a {@code null} raw result, and {@link SafeMcpTool} treats a {@code null} return value as
 * an empty result.
 */
@FunctionalInterface
public interface SafeResultSanitizer {
    RawToolResult sanitize(SafeToolCallContext context, SafeToolCallRequest request, RawToolResult rawResult);
}
