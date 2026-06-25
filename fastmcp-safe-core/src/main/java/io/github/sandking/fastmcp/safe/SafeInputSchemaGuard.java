package io.github.sandking.fastmcp.safe;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class SafeInputSchemaGuard {
    private SafeInputSchemaGuard() {
    }

    static void rejectProtectedArguments(JsonNode inputSchema, Map<String, String> argumentMappings,
            Set<String> injectedRawArguments) {
        if (inputSchema == null) {
            return;
        }
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
                throw new SafeMcpException("PROTECTED_ARGUMENT_IN_SCHEMA",
                        "Virtual input schema exposes protected argument: " + name);
            }
        }
    }
}
