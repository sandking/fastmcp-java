package io.github.sandking.fastmcp.agentscope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpTool;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.ToolDefinition;
import io.github.sandking.fastmcp.ToolException;
import io.github.sandking.fastmcp.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
import java.util.ArrayList;
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
        Objects.requireNonNull(server, "server must not be null");
        List<FastMcpToolMapping> mappings = new ArrayList<>();
        for (ToolDefinition tool : server.listTools()) {
            mappings.add(FastMcpToolMapping.builder(tool.name())
                    .name(tool.name())
                    .description(tool.description())
                    .inputSchema(tool.inputSchema())
                    .build());
        }
        register(toolkit, server, mappings);
    }

    public static void register(Toolkit toolkit, FastMcpServer server, FastMcpToolMapping... mappings) {
        register(toolkit, server, List.of(mappings));
    }

    public static void register(Toolkit toolkit, AgentTool rawTool, FastMcpToolMapping mapping) {
        Objects.requireNonNull(toolkit, "toolkit must not be null");
        Objects.requireNonNull(rawTool, "rawTool must not be null");
        Objects.requireNonNull(mapping, "mapping must not be null");
        if (!rawTool.getName().equals(mapping.rawName())) {
            throw new IllegalArgumentException("Raw AgentScope tool name does not match mapping: " + mapping.rawName());
        }
        toolkit.registerAgentTool(new AgentScopeToolAdapter(rawTool, mapping));
    }

    public static Mono<Void> registerMcpClient(
            Toolkit toolkit, McpClientWrapper client, FastMcpToolMapping... mappings) {
        return registerMcpClient(toolkit, client, List.of(mappings));
    }

    public static Mono<Void> registerMcpClient(
            Toolkit toolkit, McpClientWrapper client, List<FastMcpToolMapping> mappings) {
        Objects.requireNonNull(toolkit, "toolkit must not be null");
        Objects.requireNonNull(client, "client must not be null");
        Objects.requireNonNull(mappings, "mappings must not be null");

        return client.initialize()
                .then(client.listTools())
                .doOnNext(tools -> registerMappedMcpTools(toolkit, client, tools, mappings))
                .then();
    }

    public static void register(Toolkit toolkit, FastMcpServer server, List<FastMcpToolMapping> mappings) {
        Objects.requireNonNull(toolkit, "toolkit must not be null");
        Objects.requireNonNull(server, "server must not be null");
        Objects.requireNonNull(mappings, "mappings must not be null");

        Map<String, ToolDefinition> rawTools = new LinkedHashMap<>();
        for (ToolDefinition tool : server.listTools()) {
            rawTools.put(tool.name(), tool);
        }
        for (FastMcpToolMapping mapping : mappings) {
            ToolDefinition rawTool = rawTools.get(Objects.requireNonNull(mapping, "mapping must not be null").rawName());
            if (rawTool == null) {
                throw new IllegalArgumentException("Raw tool not found: " + mapping.rawName());
            }
            toolkit.registerAgentTool(new FastMcpToolAdapter(server, rawTool, mapping));
        }
    }

    private static final class FastMcpToolAdapter extends ToolBase {
        private final FastMcpServer server;
        private final ToolDefinition rawTool;
        private final FastMcpToolMapping mapping;

        private FastMcpToolAdapter(FastMcpServer server, ToolDefinition rawTool, FastMcpToolMapping mapping) {
            super(ToolBase.builder()
                    .name(mapping.name())
                    .description(description(rawTool, mapping))
                    .inputSchema(toMap(inputSchema(rawTool, mapping)))
                    .readOnly(mapping.readOnly())
                    .concurrencySafe(mapping.concurrencySafe()));
            this.server = server;
            this.rawTool = rawTool;
            this.mapping = mapping;
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.fromCallable(() -> {
                Map<String, Object> rawArguments = toRawArguments(param);
                ToolResult result = server.callTool(rawTool.name(), rawArguments);
                return toAgentScopeResult(param, result);
            });
        }

        private Map<String, Object> toRawArguments(ToolCallParam param) {
            return FastMcpAgentScopeTools.toRawArguments(mapping, param);
        }

        private ToolResultBlock toAgentScopeResult(ToolCallParam param, ToolResult result) {
            Map<String, Object> metadata = new LinkedHashMap<>(result.meta());
            result.structuredContent().ifPresent(value -> metadata.put("structuredContent", value));

            return ToolResultBlock.of(id(param), mapping.name(),
                    List.of(TextBlock.builder().text(result.content()).build()), metadata);
        }
    }

    private static final class AgentScopeToolAdapter extends ToolBase {
        private final AgentTool rawTool;
        private final FastMcpToolMapping mapping;

        private AgentScopeToolAdapter(AgentTool rawTool, FastMcpToolMapping mapping) {
            super(ToolBase.builder()
                    .name(mapping.name())
                    .description(description(rawTool, mapping))
                    .inputSchema(inputSchema(rawTool, mapping))
                    .readOnly(readOnly(rawTool, mapping))
                    .concurrencySafe(concurrencySafe(rawTool, mapping)));
            this.rawTool = rawTool;
            this.mapping = mapping;
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            Map<String, Object> rawArguments = toRawArguments(mapping, param);
            return rawTool.callAsync(toRawParam(param, rawArguments))
                    .map(result -> result.withIdAndName(id(param), mapping.name()));
        }

        private ToolCallParam toRawParam(ToolCallParam param, Map<String, Object> rawArguments) {
            ToolCallParam.Builder builder = ToolCallParam.builder()
                    .toolUseBlock(ToolUseBlock.builder()
                            .id(id(param))
                            .name(rawTool.getName())
                            .input(rawArguments)
                            .content(toJson(rawArguments))
                            .metadata(metadata(param))
                            .build())
                    .input(rawArguments);
            if (param != null) {
                builder.agent(param.getAgent())
                        .runtimeContext(param.getRuntimeContext())
                        .emitter(param.getEmitter());
            }
            return builder.build();
        }
    }

    private static void registerMappedMcpTools(
            Toolkit toolkit,
            McpClientWrapper client,
            List<McpSchema.Tool> tools,
            List<FastMcpToolMapping> mappings) {
        Map<String, McpSchema.Tool> rawTools = new LinkedHashMap<>();
        for (McpSchema.Tool tool : tools) {
            rawTools.put(tool.name(), tool);
            rawTools.put(namespacedMcpToolName(client, tool.name()), tool);
        }

        for (FastMcpToolMapping mapping : mappings) {
            McpSchema.Tool rawTool = rawTools.get(Objects.requireNonNull(mapping, "mapping must not be null").rawName());
            if (rawTool == null) {
                throw new IllegalArgumentException("Raw MCP tool not found: " + mapping.rawName());
            }
            toolkit.registerAgentTool(new AgentScopeToolAdapter(toMcpTool(client, rawTool), mapping));
        }
    }

    private static AgentTool toMcpTool(McpClientWrapper client, McpSchema.Tool tool) {
        boolean readOnly = tool.annotations() != null && Boolean.TRUE.equals(tool.annotations().readOnlyHint());
        Map<String, Object> outputSchema = tool.outputSchema() == null
                ? null
                : new LinkedHashMap<>(tool.outputSchema());
        return new McpTool(
                tool.name(),
                tool.description() == null ? "" : tool.description(),
                McpTool.convertMcpSchemaToParameters(tool.inputSchema(), Collections.emptySet()),
                outputSchema,
                client,
                null,
                client.getName(),
                readOnly);
    }

    private static String namespacedMcpToolName(McpClientWrapper client, String toolName) {
        return "mcp__" + client.getName() + "__" + toolName;
    }

    private static String description(ToolDefinition rawTool, FastMcpToolMapping mapping) {
        if (mapping.description() != null) {
            return mapping.description();
        }
        return rawTool.description();
    }

    private static JsonNode inputSchema(ToolDefinition rawTool, FastMcpToolMapping mapping) {
        JsonNode schema = mapping.inputSchema();
        return schema == null ? rawTool.inputSchema() : schema;
    }

    private static Map<String, Object> toMap(JsonNode schema) {
        return OBJECT_MAPPER.convertValue(schema, MAP_TYPE);
    }

    private static Map<String, Object> inputSchema(AgentTool rawTool, FastMcpToolMapping mapping) {
        JsonNode schema = mapping.inputSchema();
        if (schema != null) {
            return toMap(schema);
        }
        Map<String, Object> parameters = rawTool.getParameters();
        return parameters == null ? Map.of() : new LinkedHashMap<>(parameters);
    }

    private static String description(AgentTool rawTool, FastMcpToolMapping mapping) {
        if (mapping.description() != null) {
            return mapping.description();
        }
        return rawTool.getDescription();
    }

    private static boolean readOnly(AgentTool rawTool, FastMcpToolMapping mapping) {
        return mapping.readOnly() || rawTool instanceof ToolBase && ((ToolBase) rawTool).isReadOnly();
    }

    private static boolean concurrencySafe(AgentTool rawTool, FastMcpToolMapping mapping) {
        return mapping.concurrencySafe() || rawTool instanceof ToolBase && ((ToolBase) rawTool).isConcurrencySafe();
    }

    private static Map<String, Object> toRawArguments(FastMcpToolMapping mapping, ToolCallParam param) {
        Map<String, Object> input = input(param);
        Map<String, Object> rawArguments = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String virtualName = entry.getKey();
            String rawName = mapping.argumentMappings().getOrDefault(virtualName, virtualName);
            if (mapping.injectedArguments().containsKey(virtualName)
                    || mapping.injectedArguments().containsKey(rawName)) {
                throw new ToolException("Model supplied protected argument: " + virtualName);
            }
            rawArguments.put(rawName, entry.getValue());
        }

        for (Map.Entry<String, ToolArgumentResolver> entry : mapping.injectedArguments().entrySet()) {
            rawArguments.put(entry.getKey(), entry.getValue().resolve(param));
        }

        return rawArguments;
    }

    private static Map<String, Object> input(ToolCallParam param) {
        if (param != null && param.getInput() != null) {
            return param.getInput();
        }
        if (param != null && param.getToolUseBlock() != null && param.getToolUseBlock().getInput() != null) {
            return param.getToolUseBlock().getInput();
        }
        return Map.of();
    }

    private static Map<String, Object> metadata(ToolCallParam param) {
        if (param == null || param.getToolUseBlock() == null || param.getToolUseBlock().getMetadata() == null) {
            return Map.of();
        }
        return param.getToolUseBlock().getMetadata();
    }

    private static String id(ToolCallParam param) {
        if (param == null || param.getToolUseBlock() == null) {
            return "";
        }
        return param.getToolUseBlock().getId();
    }

    private static String toJson(Map<String, Object> rawArguments) {
        try {
            return OBJECT_MAPPER.writeValueAsString(rawArguments);
        } catch (JsonProcessingException exception) {
            throw new ToolException("Failed to serialize raw tool arguments", exception);
        }
    }
}
