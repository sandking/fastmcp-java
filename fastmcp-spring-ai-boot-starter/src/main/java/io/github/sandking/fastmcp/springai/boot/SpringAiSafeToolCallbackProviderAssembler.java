package io.github.sandking.fastmcp.springai.boot;

import io.github.sandking.fastmcp.safe.SafeAuditSink;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import io.github.sandking.fastmcp.springai.FastMcpSpringAiTools;
import io.github.sandking.fastmcp.springai.SpringAiMcpToolMapping;
import io.github.sandking.fastmcp.springai.SpringAiToolArgumentResolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

final class SpringAiSafeToolCallbackProviderAssembler {
    ToolCallbackProvider assemble(SafeMcpConfiguration configuration,
            Map<String, ToolCallbackProvider> managedRawProviders,
            List<ToolCallbackProvider> externalRawProviders,
            Map<String, SpringAiToolArgumentResolver> resolvers,
            SafeAuditSink auditSink) {
        List<ToolCallback> externalRawCallbacks = externalRawProviders.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .collect(Collectors.toList());
        List<ToolCallback> safeCallbacks = new ArrayList<>();
        for (SafeMcpServerConfiguration server : configuration.servers().values()) {
            if (server.tools().isEmpty()) {
                continue;
            }
            List<ToolCallback> rawCallbacks = managedRawProviders.containsKey(server.name())
                    ? Arrays.asList(managedRawProviders.get(server.name()).getToolCallbacks())
                    : externalRawCallbacks;
            List<SpringAiMcpToolMapping> mappings = server.tools().values().stream()
                    .map(tool -> SpringAiMcpToolMapping.from(server.name(), tool, resolvers))
                    .collect(Collectors.toList());
            safeCallbacks.addAll(Arrays.asList(
                    FastMcpSpringAiTools.wrap(server.name(), rawCallbacks, mappings, auditSink)
                            .getToolCallbacks()));
        }
        return ToolCallbackProvider.from(safeCallbacks);
    }
}
