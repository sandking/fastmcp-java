# FastMCP Java

FastMCP Java is an experimental Java foundation for building Model Context Protocol
(MCP) servers in the FastMCP style.

The upstream Python project focuses on letting developers declare tools, resources,
and prompts while the framework handles schema, validation, transports, and MCP
lifecycle details. This repository starts the Java port from the smallest useful
server core: register tools, list tools, call tools in memory, and wire those
tools into Spring Boot applications.

The AgentScope adapter adds the FastMCP-specific safety layer: expose a
model-facing virtual tool, transform its arguments, inject protected arguments
from server-side runtime context, then delegate back to the raw tool.

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
- AgentScope adapter module for safe virtual tools and Toolkit registration
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

## AgentScope adapter

AgentScope is intentionally kept out of the core and Spring Boot starter
dependencies. Use `fastmcp-agentscope-adapter` when the runtime is AgentScope
Java and the model should call safe virtual tools instead of raw backend tools
or raw AgentScope MCP tools.

AgentScope Java 2.x requires JDK 17 or newer, so the adapter is behind an
optional Maven profile:

```bash
mvn -Pagentscope test
```

The core path is:

```text
raw FastMCP tool
  -> FastMcpToolMapping
  -> safe virtual tool schema
  -> AgentScope ToolBase
  -> Toolkit.callTool(...)
  -> injected protected arguments
  -> raw FastMcpServer.callTool(...)
```

For example, a raw backend tool can remain `getOrdersByUserId(userId, status)`,
while the model only sees `get_my_orders(status)`. The adapter injects `userId`
from `RuntimeContext` before delegating to the raw tool.

The same mapping can also wrap a raw AgentScope `AgentTool`, including tools
created from AgentScope MCP clients such as `mcp__orders__getOrdersByUserId`,
without registering that raw tool name into the model-facing `Toolkit`.

```java
FastMcpAgentScopeTools.register(toolkit, server, FastMcpToolMapping.builder("getOrdersByUserId")
    .name("get_my_orders")
    .description("Get orders for the authenticated user.")
    .inputSchema(virtualOrderSchema)
    .injectArgument("userId", param -> param.getRuntimeContext().get(UserContext.class).userId())
    .readOnly(true)
    .build());
```

When tools come from an AgentScope `McpClientWrapper`, let AgentScope keep
handling MCP protocol details and register only the mapped virtual tools:

```java
McpClientWrapper ordersMcpClient = mcpClientFactory.create(serverProperties);

FastMcpAgentScopeTools.registerMcpClient(toolkit, ordersMcpClient, List.of(
    FastMcpToolMapping.builder("mcp__orders__getOrdersByUserId")
        .name("get_my_orders")
        .description("Get orders for the authenticated user.")
        .inputSchema(virtualOrderSchema)
        .injectArgument("userId",
            param -> param.getRuntimeContext().get(UserContext.class).userId())
        .readOnly(true)
        .build()
)).block();
```

`registerMcpClient` calls `initialize()` and `listTools()` on the wrapper, creates
raw AgentScope `McpTool` delegates internally, and registers only the virtual
tools into the supplied `Toolkit`. The mapping raw name may be the MCP
`tools/list` name, or the AgentScope-style namespaced form
`mcp__<clientName>__<toolName>`.

Run the example with:

```bash
mvn -Pexamples test
```

Example modules are compile-checked by Maven but are skipped during deploy, so
package publishing only includes the parent and library modules.

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

fastmcp-agentscope-adapter
  io.github.sandking.fastmcp.agentscope
    FastMcpAgentScopeTools    registers FastMCP tools into AgentScope Toolkit
    FastMcpToolMapping        virtual-to-raw tool and argument mapping
    ToolArgumentResolver      resolves injected protected arguments

examples/core
  io.github.sandking.fastmcp.examples.core
    EchoServer        plain Java tool registration example
    AnnotatedService  annotation-based registration example

examples/agentscope-adapter
  io.github.sandking.fastmcp.examples.agentscope
    FastMcpAgentScopeExample  minimal runnable example class
```

## Relationship to FastMCP Python

This project is intended to carry a Java implementation inspired by
[FastMCP](https://github.com/PrefectHQ/fastmcp). It is not yet feature-equivalent
with the Python project.
