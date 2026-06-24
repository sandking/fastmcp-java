# FastMCP Java

FastMCP Java is an experimental Java foundation for building Model Context Protocol
(MCP) servers in the FastMCP style.

The upstream Python project focuses on letting developers declare tools, resources,
and prompts while the framework handles schema, validation, transports, and MCP
lifecycle details. This repository starts the Java port from the smallest useful
server core: register tools, list tools, call tools in memory, and wire those
tools into Spring Boot applications.

## Current scope

Implemented in this initial repository baseline:

- Java 11 Maven multi-module project
- Apache-2.0 license
- Tool registration with JSON Schema input metadata
- Annotation-based tool registration for service objects
- In-memory tool listing and invocation
- Tool result content, structured content, and metadata
- Spring Boot starter that creates a `FastMcpServer` bean and registers Spring
  beans with `@McpTool` methods
- Unit tests and GitHub Actions CI

Not implemented yet:

- MCP HTTP or SSE transport endpoint
- Resources and prompts
- Plain classpath package scanning outside Spring
- Client implementation
- Authentication, lifecycle, and production middleware
- Protocol conformance tests

## Core quick start

Use the core module when you want a plain Java API without Spring dependencies.

```java
import io.github.sandking.fastmcp.FastMcp;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.JsonSchemas;
import io.github.sandking.fastmcp.ToolResult;
import java.util.Map;

var schema = JsonSchemas.object();
JsonSchemas.addProperty(schema, "text", JsonSchemas.string());
JsonSchemas.require(schema, "text");

FastMcpServer server = FastMcp.server("Echo Server")
    .tool("echo", "Echo the input text", schema,
        arguments -> ToolResult.text(arguments.getString("text")));

ToolResult result = server.callTool("echo", Map.of("text", "hello"));
System.out.println(result.content());
```

## Annotation API in plain Java

Keep business methods on normal service objects and register the instance with
FastMCP. The core package does not depend on Spring, so the same API works in
plain Java tests.

```java
import io.github.sandking.fastmcp.AnnotatedToolRegistrar;
import io.github.sandking.fastmcp.FastMcp;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.McpTool;
import io.github.sandking.fastmcp.ToolParam;

class GreetingService {
    @McpTool(name = "greet", description = "Create a greeting")
    String greet(@ToolParam(name = "name", description = "Name to greet") String name) {
        return "Hello " + name + "!";
    }
}

FastMcpServer server = FastMcp.server("Spring Boot MCP");
AnnotatedToolRegistrar.register(server, new GreetingService());
```

If parameter names are not retained by the application compiler, set
`@ToolParam(name = "...")` explicitly or compile with `-parameters`.

## Spring Boot starter

Add the starter module to a Spring Boot application to create a `FastMcpServer`
bean automatically. The starter scans Spring beans for `@McpTool` methods.

```xml
<dependency>
    <groupId>io.github.sandking</groupId>
    <artifactId>fastmcp-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
import io.github.sandking.fastmcp.McpTool;
import io.github.sandking.fastmcp.ToolParam;
import org.springframework.stereotype.Service;

@Service
class GreetingTools {
    @McpTool(name = "greet", description = "Create a greeting")
    String greet(@ToolParam(name = "name", description = "Name to greet") String name) {
        return "Hello " + name + "!";
    }
}
```

```yaml
fastmcp:
  name: Travel Tools
```

The starter currently exposes the populated `FastMcpServer` bean only. It does
not publish an MCP transport endpoint yet.

For programmatic registration, declare a `FastMcpToolCustomizer` bean:

```java
import io.github.sandking.fastmcp.JsonSchemas;
import io.github.sandking.fastmcp.ToolResult;
import io.github.sandking.fastmcp.spring.FastMcpToolCustomizer;
import org.springframework.context.annotation.Bean;

@Bean
FastMcpToolCustomizer pingTool() {
    return server -> server.tool("ping", "Return pong", JsonSchemas.object(),
            arguments -> ToolResult.text("pong"));
}
```

## Build

```bash
mvn test
```

## Package layout

```text
fastmcp-core
  io.github.sandking.fastmcp
    FastMcp          entry point
    FastMcpServer    in-memory server core
    McpTool          method annotation for service-object tools
    ToolParam        method-parameter annotation
    ToolDefinition   tool metadata and handler binding
    ToolArguments    typed argument access
    ToolResult       content, structured content, and metadata
    JsonSchemas      small JSON Schema helper

fastmcp-spring-boot-starter
  io.github.sandking.fastmcp.spring
    FastMcpAutoConfiguration  Spring Boot auto-configuration
    FastMcpProperties         fastmcp.* configuration properties
    FastMcpToolCustomizer     programmatic tool registration hook
```

## Relationship to FastMCP Python

This project is intended to carry a Java implementation inspired by
[FastMCP](https://github.com/PrefectHQ/fastmcp). It is not yet feature-equivalent
with the Python project.
