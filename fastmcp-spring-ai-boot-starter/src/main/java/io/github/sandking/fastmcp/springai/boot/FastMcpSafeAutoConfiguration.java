package io.github.sandking.fastmcp.springai.boot;

import io.github.sandking.fastmcp.safe.SafeAuditSink;
import io.github.sandking.fastmcp.safe.boot.FastMcpSafeConfigurationFactory;
import io.github.sandking.fastmcp.safe.boot.FastMcpSafeProperties;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.springai.SpringAiToolArgumentResolver;
import io.modelcontextprotocol.client.McpSyncClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
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
            FastMcpSafeProperties properties,
            ObjectProvider<SafeAuditSink> auditSinkProvider,
            Map<String, SpringAiToolArgumentResolver> resolvers) {
        SafeAuditSink auditSink = auditSinkProvider.getIfAvailable(SafeAuditSink::noOp);
        List<String> externalRawProviderNames = externalRawToolCallbackProviderNames(beanFactory);
        SpringAiExternalRawProviderDiagnostics.from(properties)
                .diagnose(externalRawProviderNames, auditSink);

        List<FastMcpSpringAiManagedClientFactory.ManagedMcpClient> managedClients =
                managedClientFactory.createClients(configuration);
        try {
            Map<String, ToolCallbackProvider> managedRawProviders = new LinkedHashMap<>();
            for (FastMcpSpringAiManagedClientFactory.ManagedMcpClient managedClient : managedClients) {
                managedRawProviders.put(managedClient.serverName(),
                        managedClientFactory.createRawProvider(List.of(managedClient.client())));
            }
            List<ToolCallbackProvider> externalRawProviders =
                    externalRawToolCallbackProviders(beanFactory, externalRawProviderNames);
            ToolCallbackProvider safeProvider = new SpringAiSafeToolCallbackProviderAssembler().assemble(
                    configuration, managedRawProviders, externalRawProviders, resolvers, auditSink);
            List<McpSyncClient> clients = managedClients.stream()
                    .map(FastMcpSpringAiManagedClientFactory.ManagedMcpClient::client)
                    .collect(Collectors.toList());
            return new FastMcpManagedSpringAiToolCallbackProvider(clients, safeProvider);
        } catch (RuntimeException | Error exception) {
            closeManagedClients(managedClients, exception);
            throw exception;
        }
    }

    private List<String> externalRawToolCallbackProviderNames(ListableBeanFactory beanFactory) {
        List<String> providerNames = new ArrayList<>();
        for (String beanName : beanFactory.getBeanNamesForType(ToolCallbackProvider.class, false, false)) {
            if (!"fastMcpSafeToolCallbackProvider".equals(beanName)) {
                providerNames.add(beanName);
            }
        }
        return providerNames;
    }

    private List<ToolCallbackProvider> externalRawToolCallbackProviders(ListableBeanFactory beanFactory,
            List<String> providerNames) {
        List<ToolCallbackProvider> providers = new ArrayList<>();
        for (String providerName : providerNames) {
            providers.add(beanFactory.getBean(providerName, ToolCallbackProvider.class));
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
