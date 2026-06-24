# FastMCP Java

FastMCP Java is an experimental Java foundation for building Model Context Protocol
(MCP) servers in the FastMCP style.

The upstream Python project focuses on letting developers declare tools, resources,
and prompts while the framework handles schema, validation, transports, and MCP
lifecycle details. This repository starts the Java port from the smallest useful
server core: register tools, list tools, and call tools in memory.

## Current scope

Implemented in this initial repository baseline:

- Java 11 Maven project
- Apache-2.0 license
- Tool registration with JSON Schema input metadata
- Annotation-based tool registration for service objects
- In-memory tool listing and invocation
- Tool result content, structured content, and metadata
- Unit tests and GitHub Actions CI

Not implemented yet:

- MCP stdio, SSE, or streamable HTTP transports
- Resources and prompts
- Java annotation scanning
- Client implementation
- Authentication, lifecycle, and production middleware
- Protocol conformance tests

## Quick start

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

## Annotation API

For Spring Boot services, keep your business methods on normal beans and register
the bean instance with FastMCP. The core package does not depend on Spring, so the
same API also works in plain Java tests.

```java
import io.github.sandking.fastmcp.AnnotatedToolRegistrar;
import io.github.sandking.fastmcp.FastMcp;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.McpTool;
import io.github.sandking.fastmcp.ToolParam;

class GreetingService {
    @McpTool(name = "greet", description = "Create a greeting")
    String greet(@ToolParam(description = "Name to greet") String name) {
        return "Hello " + name + "!";
    }
}

FastMcpServer server = FastMcp.server("Spring Boot MCP");
AnnotatedToolRegistrar.register(server, new GreetingService());
```

If parameter names are not retained by the application compiler, set
`@ToolParam(name = "...")` explicitly or compile with `-parameters`.

## Build

```bash
mvn test
```

## Package layout

```text
io.github.sandking.fastmcp
  FastMcp          entry point
  FastMcpServer    in-memory server core
  McpTool          method annotation for service-object tools
  ToolParam        method-parameter annotation
  ToolDefinition   tool metadata and handler binding
  ToolArguments    typed argument access
  ToolResult       content, structured content, and metadata
  JsonSchemas      small JSON Schema helper
```

## Relationship to FastMCP Python

This project is intended to carry a Java implementation inspired by
[FastMCP](https://github.com/PrefectHQ/fastmcp). It is not yet feature-equivalent
with the Python project.
