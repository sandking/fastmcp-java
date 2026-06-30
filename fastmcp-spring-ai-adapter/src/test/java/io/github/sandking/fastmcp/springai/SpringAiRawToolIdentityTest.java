package io.github.sandking.fastmcp.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

class SpringAiRawToolIdentityTest {
    @Test
    void usesOriginalServerAndToolNamesWhenCallbacksExposeThem() {
        SpringAiRawToolIdentity identity = SpringAiRawToolIdentity.from(
                new OriginalNamesCallback("mcp__orders__getOrdersByUserId", "orders", "getOrdersByUserId"),
                "spring-ai");

        assertEquals("orders", identity.serverName());
        assertEquals("mcp__orders__getOrdersByUserId", identity.definitionToolName());
        assertTrue(identity.originalToolName().isPresent());
        assertEquals("getOrdersByUserId", identity.originalToolName().get());
    }

    @Test
    void fallsBackToDefaultServerAndDefinitionNameWhenOriginalNamesAreUnavailable() {
        SpringAiRawToolIdentity identity = SpringAiRawToolIdentity.from(
                new BasicCallback("getOrdersByUserId"),
                "spring-ai");

        assertEquals("spring-ai", identity.serverName());
        assertEquals("getOrdersByUserId", identity.definitionToolName());
        assertFalse(identity.originalToolName().isPresent());
    }

    @Test
    void ignoresBlankOriginalNames() {
        SpringAiRawToolIdentity identity = SpringAiRawToolIdentity.from(
                new OriginalNamesCallback("getOrdersByUserId", " ", ""),
                "spring-ai");

        assertEquals("spring-ai", identity.serverName());
        assertEquals("getOrdersByUserId", identity.definitionToolName());
        assertFalse(identity.originalToolName().isPresent());
    }

    @Test
    void ignoresOriginalNameAccessorsThatThrow() {
        SpringAiRawToolIdentity identity = SpringAiRawToolIdentity.from(
                new ThrowingOriginalNamesCallback("getOrdersByUserId"),
                "spring-ai");

        assertEquals("spring-ai", identity.serverName());
        assertEquals("getOrdersByUserId", identity.definitionToolName());
        assertFalse(identity.originalToolName().isPresent());
    }

    private static class BasicCallback implements ToolCallback {
        private final ToolDefinition toolDefinition;

        BasicCallback(String name) {
            this.toolDefinition = new DefaultToolDefinition(name, "Raw tool", "{}");
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, new ToolContext(java.util.Map.of()));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return "ok";
        }
    }

    private static final class OriginalNamesCallback extends BasicCallback {
        private final String originalServerName;
        private final String originalToolName;

        private OriginalNamesCallback(String name, String originalServerName, String originalToolName) {
            super(name);
            this.originalServerName = originalServerName;
            this.originalToolName = originalToolName;
        }

        public String getOriginalServerName() {
            return originalServerName;
        }

        public String getOriginalToolName() {
            return originalToolName;
        }
    }

    private static final class ThrowingOriginalNamesCallback extends BasicCallback {
        private ThrowingOriginalNamesCallback(String name) {
            super(name);
        }

        public String getOriginalServerName() {
            throw new IllegalStateException("server name unavailable");
        }

        public String getOriginalToolName() {
            throw new IllegalStateException("tool name unavailable");
        }
    }
}
