package io.github.sandking.fastmcp.agentscope.boot;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.github.sandking.fastmcp.agentscope.FastMcpAgentScopeTools;
import io.github.sandking.fastmcp.agentscope.ToolArgumentResolver;
import io.github.sandking.fastmcp.safe.boot.FastMcpSafeConfigurationFactory;
import io.github.sandking.fastmcp.safe.boot.FastMcpSafeProperties;
import io.github.sandking.fastmcp.safe.config.SafeMcpConfiguration;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "io.agentscope.spring.boot.AgentscopeAutoConfiguration")
@ConditionalOnClass({Toolkit.class, McpClientWrapper.class, FastMcpAgentScopeTools.class})
@ConditionalOnProperty(prefix = "fastmcp.safe", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FastMcpSafeProperties.class)
public class FastMcpAgentScopeSafeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public SafeMcpConfiguration fastMcpSafeConfiguration(FastMcpSafeProperties properties) {
        return new FastMcpSafeConfigurationFactory().create(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    FastMcpAgentScopeManagedClientFactory fastMcpAgentScopeManagedClientFactory() {
        return new FastMcpAgentScopeManagedClientFactory();
    }

    @Bean("fastMcpAgentScopeSafeRegistrar")
    @ConditionalOnBean(Toolkit.class)
    @ConditionalOnMissingBean(name = "fastMcpAgentScopeSafeRegistrar")
    FastMcpAgentScopeSafeRegistrar fastMcpAgentScopeSafeRegistrar(Toolkit toolkit,
            SafeMcpConfiguration configuration,
            FastMcpAgentScopeManagedClientFactory managedClientFactory,
            Map<String, ToolArgumentResolver> resolvers) {
        return new FastMcpAgentScopeSafeRegistrar(toolkit, configuration, managedClientFactory, resolvers);
    }
}
