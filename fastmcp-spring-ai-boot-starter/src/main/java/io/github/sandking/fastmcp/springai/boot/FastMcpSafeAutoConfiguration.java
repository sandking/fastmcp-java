package io.github.sandking.fastmcp.springai.boot;

import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import io.github.sandking.fastmcp.springai.FastMcpSpringAiTools;
import io.github.sandking.fastmcp.springai.SpringAiMcpToolMapping;
import io.github.sandking.fastmcp.springai.SpringAiToolArgumentResolver;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

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

    @Bean("fastMcpSafeToolCallbackProvider")
    @Primary
    @ConditionalOnBean(ToolCallbackProvider.class)
    @ConditionalOnMissingBean(name = "fastMcpSafeToolCallbackProvider")
    public ToolCallbackProvider fastMcpSafeToolCallbackProvider(SafeMcpConfiguration configuration,
            ObjectProvider<ToolCallbackProvider> rawProviders,
            Map<String, SpringAiToolArgumentResolver> resolvers) {
        List<ToolCallback> rawCallbacks = rawProviders.orderedStream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .collect(Collectors.toList());
        List<SpringAiMcpToolMapping> mappings = configuration.servers().values().stream()
                .flatMap(server -> server.tools().values().stream())
                .map(tool -> SpringAiMcpToolMapping.from(tool, resolvers))
                .collect(Collectors.toList());
        return FastMcpSpringAiTools.wrap(rawCallbacks, mappings);
    }
}
