package io.github.sandking.fastmcp.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.McpTool;
import io.github.sandking.fastmcp.ToolParam;
import io.github.sandking.fastmcp.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class FastMcpAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastMcpAutoConfiguration.class));

    @Test
    void createsServerAndRegistersAnnotatedBeans() {
        contextRunner
                .withUserConfiguration(GreetingConfiguration.class)
                .withPropertyValues("fastmcp.name=Spring Tools")
                .run(context -> {
                    FastMcpServer server = context.getBean(FastMcpServer.class);

                    assertThat(server.name()).isEqualTo("Spring Tools");
                    assertThat(server.listTools()).extracting("name").containsExactly("greet");

                    ToolResult result = server.callTool("greet", Map.of("name", "Ada"));

                    assertThat(result.content()).isEqualTo("Hello Ada!");
                });
    }

    @Test
    void allowsProgrammaticCustomization() {
        contextRunner
                .withUserConfiguration(CustomizerConfiguration.class)
                .run(context -> {
                    FastMcpServer server = context.getBean(FastMcpServer.class);

                    ToolResult result = server.callTool("ping", Map.of());

                    assertThat(result.content()).isEqualTo("pong");
                });
    }

    @Test
    void backsOffWhenDisabled() {
        contextRunner
                .withPropertyValues("fastmcp.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FastMcpServer.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class GreetingConfiguration {
        @Bean
        GreetingTool greetingTool() {
            return new GreetingTool();
        }
    }

    static class GreetingTool {
        @McpTool(name = "greet", description = "Create a greeting")
        String greet(@ToolParam(description = "Name to greet") String name) {
            return "Hello " + name + "!";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomizerConfiguration {
        @Bean
        FastMcpToolCustomizer pingTool() {
            return server -> server.tool("ping", "Return pong",
                    io.github.sandking.fastmcp.JsonSchemas.object(), arguments -> ToolResult.text("pong"));
        }
    }
}
