package io.github.sandking.fastmcp.agentscope;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpTool;
import io.github.sandking.fastmcp.safe.RawToolResult;
import io.github.sandking.fastmcp.safe.SafeMcpTool;
import io.github.sandking.fastmcp.safe.SafeMcpToolSpec;
import io.github.sandking.fastmcp.safe.SafeToolCallContext;
import io.github.sandking.fastmcp.safe.SafeToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
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
                .then(Mono.defer(client::listTools))
                .doOnNext(tools -> registerMappedMcpTools(toolkit, client, tools, mappings))
                .then();
    }

    private static final class AgentScopeToolAdapter extends ToolBase {
        private final AgentTool rawTool;
        private final FastMcpToolMapping mapping;
        private final String rawServerName;

        private AgentScopeToolAdapter(AgentTool rawTool, FastMcpToolMapping mapping) {
            this(rawTool, mapping, rawServerName(rawTool));
        }

        private AgentScopeToolAdapter(AgentTool rawTool, FastMcpToolMapping mapping, String rawServerName) {
            super(ToolBase.builder()
                    .name(mapping.name())
                    .description(description(rawTool, mapping))
                    .inputSchema(inputSchema(rawTool, mapping))
                    .readOnly(readOnly(rawTool, mapping))
                    .concurrencySafe(concurrencySafe(rawTool, mapping)));
            this.rawTool = rawTool;
            this.mapping = mapping;
            this.rawServerName = rawServerName;
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            SafeMcpTool safeTool = new SafeMcpTool("agentscope",
                    toSafeSpec(mapping, rawServerName, rawTool.getName(), description(rawTool, mapping), null,
                            readOnly(rawTool, mapping), concurrencySafe(rawTool, mapping)),
                    (serverName, rawToolName, rawArguments) -> rawTool.callAsync(toRawParam(param, rawArguments))
                            .map(FastMcpAgentScopeTools::toRawToolResult)
                            .toFuture());
            return Mono.fromCompletionStage(safeTool.callAsync(input(param), safeContext(param)))
                    .map(result -> toAgentScopeResult(id(param), result));
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
            toolkit.registerAgentTool(new AgentScopeToolAdapter(toMcpTool(client, rawTool), mapping, client.getName()));
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

    private static Map<String, Object> input(ToolCallParam param) {
        if (param != null && param.getInput() != null) {
            return param.getInput();
        }
        if (param != null && param.getToolUseBlock() != null && param.getToolUseBlock().getInput() != null) {
            return param.getToolUseBlock().getInput();
        }
        return Map.of();
    }

    private static SafeToolCallContext safeContext(ToolCallParam param) {
        SafeToolCallContext.Builder builder = SafeToolCallContext.builder()
                .frameworkContext(ToolCallParam.class, param);
        if (param != null && param.getRuntimeContext() != null) {
            RuntimeContext runtimeContext = param.getRuntimeContext();
            builder.userId(runtimeContext.getUserId())
                    .frameworkContext(RuntimeContext.class, runtimeContext);
        }
        return builder.build();
    }

    private static SafeMcpToolSpec toSafeSpec(FastMcpToolMapping mapping, String rawServerName, String rawToolName,
            String description, JsonNode inputSchema, boolean readOnly, boolean concurrencySafe) {
        SafeMcpToolSpec.Builder builder = SafeMcpToolSpec.builder(rawServerName, rawToolName)
                .name(mapping.name())
                .description(description)
                .inputSchema(inputSchema)
                .readOnly(readOnly)
                .concurrencySafe(concurrencySafe);
        mapping.argumentMappings().forEach(builder::mapArgument);
        mapping.injectedArguments().forEach((rawName, resolver) -> builder.injectArgument(rawName,
                context -> resolver.resolve(context.frameworkContext(ToolCallParam.class))));
        return builder.build();
    }

    private static RawToolResult toRawToolResult(ToolResultBlock result) {
        RawToolResult.Builder builder = RawToolResult.builder()
                .content(textContent(result))
                .meta(result.getMetadata());
        return builder.build();
    }

    private static String textContent(ToolResultBlock result) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : result.getOutput()) {
            if (block instanceof TextBlock) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(((TextBlock) block).getText());
            }
        }
        return text.toString();
    }

    private static ToolResultBlock toAgentScopeResult(String id, SafeToolResult result) {
        Map<String, Object> metadata = new LinkedHashMap<>(result.meta());
        result.structuredContent().ifPresent(value -> metadata.put("structuredContent", value));
        return ToolResultBlock.of(id, result.toolName(),
                List.of(TextBlock.builder().text(result.content()).build()), metadata);
    }

    private static String rawServerName(AgentTool rawTool) {
        if (rawTool instanceof ToolBase && ((ToolBase) rawTool).isMcp()
                && ((ToolBase) rawTool).getMcpName() != null
                && !((ToolBase) rawTool).getMcpName().trim().isEmpty()) {
            return ((ToolBase) rawTool).getMcpName();
        }
        return "agentscope";
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
            throw new IllegalStateException("Failed to serialize raw tool arguments", exception);
        }
    }
}
