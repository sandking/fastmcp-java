package io.github.sandking.fastmcp.agentscope.boot;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.sandking.fastmcp.agentscope.FastMcpAgentScopeTools;
import io.github.sandking.fastmcp.agentscope.FastMcpToolMapping;
import io.github.sandking.fastmcp.agentscope.ToolArgumentResolver;
import io.github.sandking.fastmcp.safe.SafeAuditSink;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class FastMcpAgentScopeSafeRegistrar implements AutoCloseable {
    private final List<McpClientWrapper> managedClients;
    private final SafeAuditSink auditSink;

    FastMcpAgentScopeSafeRegistrar(List<McpClientWrapper> managedClients) {
        this(managedClients, SafeAuditSink.noOp());
    }

    private FastMcpAgentScopeSafeRegistrar(List<McpClientWrapper> managedClients, SafeAuditSink auditSink) {
        this.managedClients = new ArrayList<>(managedClients);
        this.auditSink = auditSink == null ? SafeAuditSink.noOp() : auditSink;
    }

    FastMcpAgentScopeSafeRegistrar(Toolkit toolkit,
            SafeMcpConfiguration configuration,
            FastMcpAgentScopeManagedClientFactory managedClientFactory,
            SafeAuditSink auditSink,
            Map<String, ToolArgumentResolver> resolvers) {
        this(managedClientFactory.createClients(configuration), auditSink);
        try {
            registerManagedClients(toolkit, configuration, resolvers);
        } catch (RuntimeException | Error exception) {
            closeManagedClients(exception);
            throw exception;
        }
    }

    private void registerManagedClients(Toolkit toolkit,
            SafeMcpConfiguration configuration,
            Map<String, ToolArgumentResolver> resolvers) {
        Map<String, McpClientWrapper> clientsByName = clientsByName();
        for (SafeMcpServerConfiguration server : configuration.servers().values()) {
            if (!server.enabled() || server.tools().isEmpty()) {
                continue;
            }
            McpClientWrapper client = clientsByName.get(server.name());
            if (client == null) {
                throw new IllegalStateException("Managed AgentScope MCP client not found for server: "
                        + server.name());
            }
            List<FastMcpToolMapping> mappings = server.tools().values().stream()
                    .map(tool -> FastMcpToolMapping.from(tool, resolvers))
                    .collect(Collectors.toList());
            FastMcpAgentScopeTools.registerMcpClient(toolkit, client, mappings, auditSink).block();
        }
    }

    private Map<String, McpClientWrapper> clientsByName() {
        Map<String, McpClientWrapper> clientsByName = new LinkedHashMap<>();
        for (McpClientWrapper managedClient : managedClients) {
            McpClientWrapper previous = clientsByName.put(managedClient.getName(), managedClient);
            if (previous != null) {
                throw new IllegalStateException("Duplicate AgentScope MCP client name: " + managedClient.getName());
            }
        }
        return clientsByName;
    }

    @Override
    public void close() {
        closeManagedClients(null);
    }

    private void closeManagedClients(Throwable failure) {
        for (McpClientWrapper managedClient : managedClients) {
            try {
                managedClient.close();
            } catch (RuntimeException closeException) {
                if (failure != null) {
                    failure.addSuppressed(closeException);
                } else {
                    throw closeException;
                }
            }
        }
    }
}
