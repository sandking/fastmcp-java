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
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(FastMcpSafeAutoConfiguration.class);
    private static final String EXTERNAL_RAW_PROVIDER_MESSAGE =
            "External raw Spring AI ToolCallbackProvider beans are present: %s; ensure models receive "
                    + "fastMcpSafeToolCallbackProvider, not raw providers. This is a conservative diagnostic: "
                    + "external ToolCallbackProvider beans are the main raw-provider exposure risk, "
                    + "although they may include non-raw business providers.";

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
            Map<String, SpringAiToolArgumentResolver> resolvers) {
        String externalRawProviderDiagnosticsMode =
                normalizeExternalRawProviderDiagnosticsMode(properties.getDiagnostics().getExternalRawProvider());
        List<String> externalRawProviderNames = externalRawToolCallbackProviderNames(beanFactory);
        diagnoseExternalRawProviders(externalRawProviderDiagnosticsMode, externalRawProviderNames);

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

    private String normalizeExternalRawProviderDiagnosticsMode(String mode) {
        String normalizedMode = mode == null ? "warn" : mode.trim().toLowerCase(Locale.ROOT);
        if (!"warn".equals(normalizedMode) && !"fail".equals(normalizedMode) && !"off".equals(normalizedMode)) {
            throw new IllegalArgumentException("Unsupported fastmcp.safe.diagnostics.external-raw-provider: " + mode);
        }
        return normalizedMode;
    }

    private void diagnoseExternalRawProviders(String mode, List<String> externalRawProviderNames) {
        if (externalRawProviderNames.isEmpty() || "off".equals(mode)) {
            return;
        }
        String message = String.format(EXTERNAL_RAW_PROVIDER_MESSAGE, externalRawProviderNames);
        if ("fail".equals(mode)) {
            throw new IllegalStateException(message);
        }
        logger.warn(message);
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
