# FastMCP Java

[English](README.md) | [简体中文](README.zh-CN.md)

FastMCP Java 是一个实验性的 Java 基础库，用于以 FastMCP 风格构建
Model Context Protocol (MCP) server。

上游 Python 项目侧重让开发者声明 tools、resources 和 prompts，由框架处理
schema、validation、transport 和 MCP lifecycle 细节。本仓库从最小可用的
Java server core 开始：注册工具、列出工具、在内存中调用工具，并把这些工具接入
Spring Boot 应用。

AgentScope adapter 增加了 FastMCP 特有的安全层：暴露面向模型的虚拟工具，
转换参数，从服务端 runtime context 注入受保护参数，然后再委托给 raw tool。

## 当前范围

这个初始仓库基线已经实现：

- Java 11 Maven 多模块工程
- Apache-2.0 license
- 带 JSON Schema input metadata 的工具注册
- 面向 service object 的注解式工具注册
- 内存态工具列表和工具调用
- 工具结果 content、structured content 和 metadata
- Spring Boot starter：创建 `FastMcpServer` bean，并注册带 `@McpTool` 方法的 Spring bean
- AgentScope adapter module：支持安全虚拟工具和 Toolkit 注册
- Unit tests 和 GitHub Actions CI

尚未实现：

- MCP HTTP 或 SSE transport endpoint
- Resources 和 prompts
- Spring 之外的普通 classpath package scanning
- Client implementation
- Authentication、lifecycle 和 production middleware
- Protocol conformance tests

## Core 快速开始

当你需要一个不依赖 Spring 的普通 Java API 时，使用 core module。

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

## 普通 Java 注解 API

业务方法可以保留在普通 service object 上，然后把实例注册到 FastMCP。
core package 不依赖 Spring，所以同一套 API 也可以用于普通 Java 测试。

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

如果应用编译时没有保留参数名，请显式设置 `@ToolParam(name = "...")`，
或者使用 `-parameters` 编译。

## Spring Boot starter

把 starter module 加到 Spring Boot 应用后，会自动创建一个 `FastMcpServer`
bean。starter 会扫描 Spring bean 上的 `@McpTool` 方法。

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

starter 当前只暴露已填充的 `FastMcpServer` bean，还不会发布 MCP transport endpoint。

如需通过代码注册工具，可以声明一个 `FastMcpToolCustomizer` bean：

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

## 构建

```bash
mvn test
```

## AgentScope adapter

AgentScope 被刻意隔离在 core 和 Spring Boot starter 依赖之外。当运行时是
AgentScope Java，并且模型应该调用安全虚拟工具，而不是 raw backend tools
或 raw AgentScope MCP tools 时，使用 `fastmcp-agentscope-adapter`。

AgentScope Java 2.x 需要 JDK 17 或更高版本，因此 adapter 放在可选 Maven profile 中：

```bash
mvn -Pagentscope test
```

核心路径是：

```text
raw FastMCP tool
  -> FastMcpToolMapping
  -> safe virtual tool schema
  -> AgentScope ToolBase
  -> Toolkit.callTool(...)
  -> injected protected arguments
  -> raw FastMcpServer.callTool(...)
```

例如，raw backend tool 可以仍然是 `getOrdersByUserId(userId, status)`，
但模型只能看到 `get_my_orders(status)`。adapter 会从 `RuntimeContext` 注入
`userId`，然后再委托给 raw tool。

同一个 mapping 也可以包装 raw AgentScope `AgentTool`，包括由 AgentScope MCP
client 创建的工具，例如 `mcp__orders__getOrdersByUserId`，而不把这个 raw tool name
注册到模型可见的 `Toolkit` 中。

```java
FastMcpAgentScopeTools.register(toolkit, server, FastMcpToolMapping.builder("getOrdersByUserId")
    .name("get_my_orders")
    .description("Get orders for the authenticated user.")
    .inputSchema(virtualOrderSchema)
    .injectArgument("userId", param -> param.getRuntimeContext().get(UserContext.class).userId())
    .readOnly(true)
    .build());
```

当工具来自 AgentScope `McpClientWrapper` 时，让 AgentScope 继续处理 MCP 协议细节，
但只注册 mapped virtual tools：

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

`registerMcpClient` 会调用 wrapper 的 `initialize()` 和 `listTools()`，在内部创建
raw AgentScope `McpTool` delegate，并且只把虚拟工具注册到传入的 `Toolkit`。
mapping 的 raw name 可以是 MCP `tools/list` 返回的名称，也可以是 AgentScope 风格的
namespaced form：`mcp__<clientName>__<toolName>`。

运行示例：

```bash
mvn -Pexamples test
```

Example modules 会被 Maven 编译检查，但 deploy 时会跳过。因此发布包只包含 parent
和 library modules。

## 包结构

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

## 与 FastMCP Python 的关系

本项目的目标是承载一个受 [FastMCP](https://github.com/PrefectHQ/fastmcp)
启发的 Java 实现。它目前还没有和 Python 项目达到功能对等。
