package io.github.sandking.fastmcp.spring;

import io.github.sandking.fastmcp.AnnotatedToolRegistrar;
import io.github.sandking.fastmcp.FastMcp;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.McpTool;
import java.lang.reflect.Method;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(FastMcpServer.class)
@ConditionalOnProperty(prefix = "fastmcp", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FastMcpProperties.class)
public class FastMcpAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public FastMcpServer fastMcpServer(ApplicationContext context, FastMcpProperties properties,
            ObjectProvider<FastMcpToolCustomizer> customizers) {
        FastMcpServer server = FastMcp.server(properties.getName());
        registerAnnotatedTools(context, server);
        customizers.orderedStream().forEach(customizer -> customizer.customize(server));
        return server;
    }

    private void registerAnnotatedTools(ApplicationContext context, FastMcpServer server) {
        String[] beanNames = context.getBeanNamesForType(Object.class, false, false);
        for (String beanName : beanNames) {
            Class<?> beanType = context.getType(beanName);
            if (beanType != null && hasMcpToolMethod(beanType)) {
                AnnotatedToolRegistrar.register(server, context.getBean(beanName));
            }
        }
    }

    private boolean hasMcpToolMethod(Class<?> beanType) {
        for (Method method : beanType.getDeclaredMethods()) {
            if (method.isAnnotationPresent(McpTool.class)) {
                return true;
            }
        }
        return false;
    }
}
