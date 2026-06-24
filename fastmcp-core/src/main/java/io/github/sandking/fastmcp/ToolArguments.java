package io.github.sandking.fastmcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolArguments {
    private final Map<String, Object> values;

    ToolArguments(Map<String, ?> values) {
        Map<String, ?> safeValues = values == null ? Collections.emptyMap() : values;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(safeValues));
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public Object get(String name) {
        return values.get(name);
    }

    public String getString(String name) {
        Object value = require(name);
        if (value instanceof String) {
            return (String) value;
        }
        throw typeError(name, "String", value);
    }

    public int getInteger(String name) {
        Object value = require(name);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw typeError(name, "Number", value);
    }

    public boolean getBoolean(String name) {
        Object value = require(name);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw typeError(name, "Boolean", value);
    }

    public boolean contains(String name) {
        return values.containsKey(name);
    }

    private Object require(String name) {
        if (!values.containsKey(name)) {
            throw new IllegalArgumentException("Missing argument: " + name);
        }
        return values.get(name);
    }

    private IllegalArgumentException typeError(String name, String expected, Object actual) {
        String actualType = actual == null ? "null" : actual.getClass().getSimpleName();
        return new IllegalArgumentException("Argument " + name + " must be " + expected + ", got " + actualType);
    }
}
