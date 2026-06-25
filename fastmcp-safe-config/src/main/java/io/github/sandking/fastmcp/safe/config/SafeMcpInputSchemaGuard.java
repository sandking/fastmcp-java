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
        JsonNode properties = inputSchema.get("properties");
        if (properties == null || !properties.isObject()) {
            return;
        }
        Set<String> protectedModelNames = new LinkedHashSet<>(injectedRawArguments);
        argumentMappings.forEach((virtualName, rawName) -> {
            if (injectedRawArguments.contains(rawName)) {
                protectedModelNames.add(virtualName);
            }
        });
        for (String name : protectedModelNames) {
            if (properties.has(name)) {
                throw new SafeMcpConfigException("PROTECTED_ARGUMENT_IN_SCHEMA",
                        "Virtual input schema exposes protected argument: " + name);
            }
        }
    }
}
