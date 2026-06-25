package io.github.sandking.fastmcp.safe.config;

public final class SafeMcpConfigException extends RuntimeException {
    private final String code;

    public SafeMcpConfigException(String code, String message) {
        super(message);
        this.code = requireText(code, "code", "INVALID_ERROR_CODE");
    }

    public String code() {
        return code;
    }

    static String requireText(String value, String fieldName, String code) {
        if (value == null) {
            throw new SafeMcpConfigException(code, fieldName + " must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new SafeMcpConfigException(code, fieldName + " must not be blank");
        }
        return trimmed;
    }
}
