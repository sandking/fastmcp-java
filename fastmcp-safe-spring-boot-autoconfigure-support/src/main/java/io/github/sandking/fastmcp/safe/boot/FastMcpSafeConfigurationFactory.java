package io.github.sandking.fastmcp.safe.boot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpToolConfiguration;
import java.util.Map;

public final class FastMcpSafeConfigurationFactory {
    private final ObjectMapper objectMapper;

    public FastMcpSafeConfigurationFactory() {
        this(new ObjectMapper());
    }

    public FastMcpSafeConfigurationFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SafeMcpConfiguration create(FastMcpSafeProperties properties) {
        SafeMcpConfiguration.Builder configuration = SafeMcpConfiguration.builder();
        properties.getServers().forEach((serverName, serverProperties) ->
                configuration.server(server(serverName, serverProperties)));
        return configuration.build();
    }

    private SafeMcpServerConfiguration server(String serverName, FastMcpSafeProperties.Server properties) {
        SafeMcpServerConfiguration.Builder server = SafeMcpServerConfiguration.builder(serverName);
        server.enabled(properties.isEnabled());
        if (hasText(properties.getTransport())) {
            server.transport(properties.getTransport());
        }
        if (hasText(properties.getCommand())) {
            server.command(properties.getCommand());
        }
        if (hasText(properties.getEndpoint())) {
            server.endpoint(properties.getEndpoint());
        }
        if (hasText(properties.getSseEndpoint())) {
            server.sseEndpoint(properties.getSseEndpoint());
        }
        if (properties.getRequestTimeout() != null) {
            server.requestTimeout(properties.getRequestTimeout());
        }
        if (properties.getInitializationTimeout() != null) {
            server.initializationTimeout(properties.getInitializationTimeout());
        }
        if (hasText(properties.getClientName())) {
            server.clientName(properties.getClientName());
        }
        if (hasText(properties.getClientVersion())) {
            server.clientVersion(properties.getClientVersion());
        }
        properties.getArguments().forEach(server::argument);
        properties.getEnvironment().forEach(server::environment);
        properties.getHttp().getHeaders().forEach(server::httpHeader);
        properties.getHttp().getQueryParams().forEach(server::httpQueryParam);
        server.httpCookiesEnabled(properties.getHttp().getCookies().isEnabled());
        properties.getTools().forEach((toolKey, toolProperties) -> server.tool(tool(toolKey, toolProperties)));
        return server.build();
    }

    private SafeMcpToolConfiguration tool(String toolKey, FastMcpSafeProperties.Tool properties) {
        String rawName = hasText(properties.getRawName()) ? properties.getRawName() : toolKey;
        SafeMcpToolConfiguration.Builder tool = SafeMcpToolConfiguration.builder(rawName)
                .name(properties.getName())
                .description(properties.getDescription())
                .inputSchema(schema(properties.getInputSchema()))
                .readOnly(properties.isReadOnly())
                .concurrencySafe(properties.isConcurrencySafe());
        properties.getArgumentMappings().forEach(tool::mapArgument);
        properties.getInjectedArguments().forEach(tool::injectArgument);
        return tool.build();
    }

    private JsonNode schema(Map<String, Object> inputSchema) {
        return objectMapper.valueToTree(inputSchema);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
