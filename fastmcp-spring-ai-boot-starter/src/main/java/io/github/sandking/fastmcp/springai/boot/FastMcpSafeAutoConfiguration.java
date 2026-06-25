package io.github.sandking.fastmcp.springai.boot;

import io.github.sandking.fastmcp.safe.boot.FastMcpSafeConfigurationFactory;
import io.github.sandking.fastmcp.safe.boot.FastMcpSafeProperties;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import io.github.sandking.fastmcp.springai.FastMcpSpringAiTools;
import io.github.sandking.fastmcp.springai.SpringAiMcpToolMapping;
import io.github.sandking.fastmcp.springai.SpringAiToolArgumentResolver;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

@AutoConfiguration
@ConditionalOnClass(ToolCallbackProvider.class)
@ConditionalOnProperty(prefix = "fastmcp.safe", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FastMcpSafeProperties.class)
public class FastMcpSafeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public SafeMcpConfiguration fastMcpSafeConfiguration(FastMcpSafeProperties properties) {
        return new FastMcpSafeConfigurationFactory().create(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    FastMcpSpringAiManagedClientFactory fastMcpSpringAiManagedClientFactory() {
        return new FastMcpSpringAiManagedClientFactory();
    }

    @Bean("fastMcpSafeToolCallbackProvider")
    @Primary
    @ConditionalOnMissingBean(name = "fastMcpSafeToolCallbackProvider")
    public ToolCallbackProvider fastMcpSafeToolCallbackProvider(SafeMcpConfiguration configuration,
            ListableBeanFactory beanFactory,
            FastMcpSpringAiManagedClientFactory managedClientFactory,
            Map<String, SpringAiToolArgumentResolver> resolvers) {
        List<FastMcpSpringAiManagedClientFactory.ManagedMcpClient> managedClients =
                managedClientFactory.createClients(configuration);
        try {
            Map<String, ToolCallbackProvider> managedRawProviders = new LinkedHashMap<>();
            for (FastMcpSpringAiManagedClientFactory.ManagedMcpClient managedClient : managedClients) {
                managedRawProviders.put(managedClient.serverName(),
                        managedClientFactory.createRawProvider(List.of(managedClient.client())));
            }
            List<ToolCallback> externalRawCallbacks = rawToolCallbackProviders(beanFactory).stream()
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
                        FastMcpSpringAiTools.wrap(server.name(), rawCallbacks, mappings).getToolCallbacks()));
            }
            ToolCallbackProvider safeProvider = ToolCallbackProvider.from(safeCallbacks);
            List<McpSyncClient> clients = managedClients.stream()
                    .map(FastMcpSpringAiManagedClientFactory.ManagedMcpClient::client)
                    .collect(Collectors.toList());
            return new FastMcpManagedSpringAiToolCallbackProvider(clients, safeProvider);
        } catch (RuntimeException | Error exception) {
            closeManagedClients(managedClients, exception);
            throw exception;
        }
    }

    private List<ToolCallbackProvider> rawToolCallbackProviders(ListableBeanFactory beanFactory) {
        List<ToolCallbackProvider> providers = new ArrayList<>();
        for (String beanName : beanFactory.getBeanNamesForType(ToolCallbackProvider.class, false, false)) {
            if (!"fastMcpSafeToolCallbackProvider".equals(beanName)) {
                providers.add(beanFactory.getBean(beanName, ToolCallbackProvider.class));
            }
        }
        AnnotationAwareOrderComparator.sort(providers);
        return providers;
    }

    private void closeManagedClients(List<FastMcpSpringAiManagedClientFactory.ManagedMcpClient> managedClients,
            Throwable failure) {
        for (FastMcpSpringAiManagedClientFactory.ManagedMcpClient managedClient : managedClients) {
            try {
                managedClient.client().closeGracefully();
            } catch (RuntimeException closeException) {
                failure.addSuppressed(closeException);
            }
        }
    }
}
