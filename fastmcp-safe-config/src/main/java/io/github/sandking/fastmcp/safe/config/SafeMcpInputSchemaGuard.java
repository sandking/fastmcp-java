package io.github.sandking.fastmcp.safe.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class SafeMcpInputSchemaGuard {
    private SafeMcpInputSchemaGuard() {
    }

    static void rejectProtectedArguments(JsonNode inputSchema, Map<String, String> argumentMappings,
            Set<String> injectedRawArguments) {
        Set<String> protectedModelNames = new LinkedHashSet<>(injectedRawArguments);
        argumentMappings.forEach((virtualName, rawName) -> {
            if (injectedRawArguments.contains(rawName)) {
                protectedModelNames.add(virtualName);
            }
        });
        rejectProtectedProperties(inputSchema, protectedModelNames);
    }

    private static void rejectProtectedProperties(JsonNode node, Set<String> protectedModelNames) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode properties = node.get("properties");
            if (properties != null && properties.isObject()) {
                rejectProtectedPropertyNames(properties, protectedModelNames);
            }
            node.fields().forEachRemaining(entry -> rejectProtectedProperties(entry.getValue(), protectedModelNames));
            return;
        }
        if (node.isArray()) {
            node.elements().forEachRemaining(element -> rejectProtectedProperties(element, protectedModelNames));
        }
    }

    private static void rejectProtectedPropertyNames(JsonNode properties, Set<String> protectedModelNames) {
        for (String name : protectedModelNames) {
            if (properties.has(name)) {
                throw new SafeMcpConfigException("PROTECTED_ARGUMENT_IN_SCHEMA",
                        "Virtual input schema exposes protected argument: " + name);
            }
        }
    }
}
