package io.github.sandking.fastmcp.springai.boot;

import io.github.sandking.fastmcp.safe.boot.FastMcpSafeConfigurationFactory;
import io.github.sandking.fastmcp.safe.boot.FastMcpSafeProperties;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.springai.FastMcpSpringAiTools;
import io.github.sandking.fastmcp.springai.SpringAiMcpToolMapping;
import io.github.sandking.fastmcp.springai.SpringAiToolArgumentResolver;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.ArrayList;
import java.util.Arrays;
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
        List<McpSyncClient> managedClients = managedClientFactory.createClients(configuration);
        try {
            ToolCallbackProvider managedRawProvider = managedClientFactory.createRawProvider(managedClients);
            List<ToolCallback> rawCallbacks = new ArrayList<>();
            rawCallbacks.addAll(rawToolCallbackProviders(beanFactory).stream()
                    .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                    .collect(Collectors.toList()));
            rawCallbacks.addAll(Arrays.asList(managedRawProvider.getToolCallbacks()));
            List<SpringAiMcpToolMapping> mappings = configuration.servers().values().stream()
                    .flatMap(server -> server.tools().values().stream())
                    .map(tool -> SpringAiMcpToolMapping.from(tool, resolvers))
                    .collect(Collectors.toList());
            ToolCallbackProvider safeProvider = FastMcpSpringAiTools.wrap(rawCallbacks, mappings);
            return new FastMcpManagedSpringAiToolCallbackProvider(managedClients, safeProvider);
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

    private void closeManagedClients(List<McpSyncClient> managedClients, Throwable failure) {
        for (McpSyncClient managedClient : managedClients) {
            try {
                managedClient.closeGracefully();
            } catch (RuntimeException closeException) {
                failure.addSuppressed(closeException);
            }
        }
    }
}
