package io.github.sandking.fastmcp.safe;

public class SafeMcpException extends RuntimeException {
    private final String code;

    public SafeMcpException(String code, String message) {
        super(message);
        this.code = requireText(code, "code");
    }

    public SafeMcpException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = requireText(code, "code");
    }

    public String code() {
        return code;
    }

    static String requireText(String value, String fieldName) {
        if (value == null) {
            throw new NullPointerException(fieldName + " must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
