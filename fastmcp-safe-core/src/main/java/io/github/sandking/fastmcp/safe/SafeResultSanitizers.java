package io.github.sandking.fastmcp.safe;

public final class SafeResultSanitizers {
    private static final SafeResultSanitizer MODEL_SAFE = (context, request, rawResult) -> {
        if (rawResult == null) {
            return RawToolResult.text("");
        }
        return RawToolResult.builder()
                .content(rawResult.content())
                .build();
    };

    private static final SafeResultSanitizer PASS_THROUGH =
            (context, request, rawResult) -> rawResult == null ? RawToolResult.text("") : rawResult;

    private SafeResultSanitizers() {
    }

    /**
     * Returns the default model-safe sanitizer, preserving only text content and dropping raw metadata and structured
     * content before the result is exposed to the model.
     */
    public static SafeResultSanitizer modelSafe() {
        return MODEL_SAFE;
    }

    /**
     * Returns a sanitizer that passes raw results through unchanged.
     *
     * <p>This can re-expose raw metadata and structured content, including raw tool names, protected arguments, and
     * internal implementation details. Use it only after reviewing that the raw result is safe for model visibility.
     */
    public static SafeResultSanitizer passThrough() {
        return PASS_THROUGH;
    }
}
