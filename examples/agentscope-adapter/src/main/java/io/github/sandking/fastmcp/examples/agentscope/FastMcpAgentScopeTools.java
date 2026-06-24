package io.github.sandking.fastmcp.examples.agentscope;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.ToolDefinition;
import io.github.sandking.fastmcp.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class FastMcpAgentScopeTools {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private FastMcpAgentScopeTools() {
    }

    public static void register(Toolkit toolkit, FastMcpServer server) {
        Objects.requireNonNull(toolkit, "toolkit must not be null");
        Objects.requireNonNull(server, "server must not be null");

        for (ToolDefinition tool : server.listTools()) {
            toolkit.registerAgentTool(new FastMcpToolAdapter(server, tool));
        }
    }

    private static final class FastMcpToolAdapter extends ToolBase {
        private final FastMcpServer server;
        private final ToolDefinition tool;

        private FastMcpToolAdapter(FastMcpServer server, ToolDefinition tool) {
            super(ToolBase.builder()
                    .name(tool.name())
                    .description(tool.description())
                    .inputSchema(OBJECT_MAPPER.convertValue(tool.inputSchema(), MAP_TYPE)));
            this.server = server;
            this.tool = tool;
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.fromCallable(() -> toAgentScopeResult(param, server.callTool(tool.name(), input(param))));
        }

        private Map<String, Object> input(ToolCallParam param) {
            if (param.getInput() != null) {
                return param.getInput();
            }
            if (param.getToolUseBlock() != null && param.getToolUseBlock().getInput() != null) {
                return param.getToolUseBlock().getInput();
            }
            return Map.of();
        }

        private ToolResultBlock toAgentScopeResult(ToolCallParam param, ToolResult result) {
            Map<String, Object> metadata = new LinkedHashMap<>(result.meta());
            result.structuredContent().ifPresent(value -> metadata.put("structuredContent", value));

            return ToolResultBlock.of(id(param), tool.name(),
                    List.of(TextBlock.builder().text(result.content()).build()), metadata);
        }

        private String id(ToolCallParam param) {
            if (param.getToolUseBlock() == null) {
                return "";
            }
            return param.getToolUseBlock().getId();
        }
    }
}
