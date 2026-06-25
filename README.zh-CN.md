# FastMCP Java

[English](README.md) | [简体中文](README.zh-CN.md)

FastMCP Java 正在调整为面向 Java Agent 框架的 Safe MCP Registration
库。它的核心目标是安全注册 MCP raw tools：LLM 只能看到面向模型的虚拟工具，
受保护参数由服务端 runtime context 注入。

```text
MCP raw tools
  -> safe mapping 和 protected-argument injection
  -> framework adapter
  -> AgentScope / Spring AI virtual tools
  -> LLM 只能看到 virtual tools
```

当前已实现的第一批框架 adapter 是 AgentScope 和 Spring AI。两条 adapter
路径都会只暴露安全虚拟工具，把 raw MCP/backend tools 留作内部 delegate。

## 当前范围

当前已经实现：

- Maven 多模块工程。完整 reactor 需要 JDK 17；`fastmcp-safe-core` 和
  `fastmcp-safe-config` 仍保持 Java 11 release 兼容。
- Apache-2.0 license
- `fastmcp-safe-core`：框架无关的安全 tool mapping、受保护参数注入、
  policy decision、raw invoker abstraction 和 audit event
- `fastmcp-safe-config`：框架无关的不可变配置模型，用于描述 MCP server、
  virtual tool、argument mapping 和 protected argument source
- AgentScope adapter module：支持安全虚拟工具和 Toolkit 注册，并由 `fastmcp-safe-core` 承载核心安全逻辑
- Spring AI adapter module：支持安全虚拟 `ToolCallback`，并由 `fastmcp-safe-core` 承载核心安全逻辑
- Spring AI Boot starter：绑定 `fastmcp.safe.*`，创建 `SafeMcpConfiguration`，
  并基于已有 raw provider 发布 primary 的安全 `ToolCallbackProvider`
- Unit tests 和 GitHub Actions CI

尚未实现：

- 基于本库配置自动创建 MCP client
- AgentScope Spring Boot auto-configuration
- MCP HTTP 或 SSE transport endpoint
- Resources 和 prompts
- Spring 之外的普通 classpath package scanning
- Client implementation
- Authentication、lifecycle 和 production middleware
- Protocol conformance tests

## Safe core

当你需要框架无关的安全层时，使用 `fastmcp-safe-core`：

- virtual tool name、description 和 schema
- virtual-to-raw argument mapping
- protected argument injection
- raw tool 调用前的 policy decision
- audit event 默认只记录 injected argument names，不记录 injected values

大多数用户应该通过 adapter 使用它，例如 `fastmcp-agentscope-adapter`
或 `fastmcp-spring-ai-adapter`。adapter 代码应委托 safe-core 处理 mapping
和 protected-argument，而不是重复实现。

## Safe config

当应用的 MCP 配置也希望由本库承接，而不是直接写在 AgentScope 或 Spring AI
配置里时，使用 `fastmcp-safe-config`。它负责描述 MCP server 和安全虚拟工具
mapping，但当前还不会创建真实 MCP client。

```java
SafeMcpToolConfiguration getMyOrders = SafeMcpToolConfiguration.builder("getOrdersByUserId")
    .name("get_my_orders")
    .description("Get orders for the authenticated user.")
    .inputSchema(virtualOrderSchema)
    .mapArgument("status", "orderStatus")
    .injectArgument("userId", "currentUserId")
    .readOnly(true)
    .build();
```

adapter 通过 resolver registry 把这份共享配置转换成框架自己的 mapping：

```java
SpringAiMcpToolMapping mapping = SpringAiMcpToolMapping.from(getMyOrders,
    Map.of("currentUserId", context -> context.getContext().get("userId")));
```

`currentUserId` 是配置里的 source name，不是敏感值本身。真实值仍然在工具调用时
从框架 runtime context 中解析。

Spring AI Boot 应用可以通过 `fastmcp-spring-ai-boot-starter` 绑定同一套配置：

```xml
<dependency>
    <groupId>io.github.sandking</groupId>
    <artifactId>fastmcp-spring-ai-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```properties
fastmcp.safe.servers.orders.transport=stdio
fastmcp.safe.servers.orders.command=node
fastmcp.safe.servers.orders.arguments[0]=orders-mcp.js
fastmcp.safe.servers.orders.tools.getOrdersByUserId.name=get_my_orders
fastmcp.safe.servers.orders.tools.getOrdersByUserId.description=Get orders for the authenticated user.
fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.type=object
fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.properties.status.type=string
fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.required[0]=status
fastmcp.safe.servers.orders.tools.getOrdersByUserId.argument-mappings.status=orderStatus
fastmcp.safe.servers.orders.tools.getOrdersByUserId.injected-arguments.userId=currentUserId
fastmcp.safe.servers.orders.tools.getOrdersByUserId.read-only=true
```

声明一个 bean，bean name 与配置里的 source name 一致：

```java
@Bean("currentUserId")
SpringAiToolArgumentResolver currentUserId() {
    return context -> context.getContext().get("userId");
}
```

starter 当前会包装已有 raw Spring AI `ToolCallbackProvider` bean，并发布一个名为
`fastMcpSafeToolCallbackProvider` 的 primary 安全 provider。它还不会创建 MCP client。

## 构建

```bash
mvn test
```

默认 reactor 包含 safe core、共享配置、AgentScope adapter、Spring AI adapter
和 Spring AI Boot starter。请使用 JDK 17 执行：

```bash
mvn test
```

## AgentScope adapter

当运行时是 AgentScope Java，并且模型应该调用安全虚拟工具，而不是 raw backend
tools 或 raw AgentScope MCP tools 时，使用 `fastmcp-agentscope-adapter`。
AgentScope Java 2.x 需要 JDK 17 或更高版本，因此完整仓库构建也需要 JDK 17。

核心路径是：

```text
raw AgentScope/backend tool 或 raw MCP tool
  -> FastMcpToolMapping
  -> fastmcp-safe-core
  -> safe virtual tool schema and protected-argument injection
  -> AgentScope ToolBase
  -> Toolkit.callTool(...)
  -> internal raw delegate
```

例如，raw backend tool 可以仍然是 `getOrdersByUserId(userId, status)`，
但模型只能看到 `get_my_orders(status)`。adapter 会从 `RuntimeContext` 注入
`userId`，然后再委托给 raw tool。

同一个 mapping 也可以包装 raw AgentScope `AgentTool`，包括由 AgentScope MCP
client 创建的工具，例如 `mcp__orders__getOrdersByUserId`，而不把这个 raw tool name
注册到模型可见的 `Toolkit` 中。

```java
AgentTool rawOrderTool = createRawOrderTool();

FastMcpAgentScopeTools.register(toolkit, rawOrderTool, FastMcpToolMapping.builder("getOrdersByUserId")
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

## Spring AI adapter

Spring AI adapter 面向 Spring AI `2.0.0`，负责把已有 raw `ToolCallback` 或
`ToolCallbackProvider` 包装成模型可见的安全虚拟 callback。

adapter 目前还不会基于配置自动创建 MCP client。你可以继续用 Spring AI 或应用
自身 wiring 创建 MCP client/provider，然后在交给模型前包装 raw callbacks：

```java
ToolCallbackProvider rawProvider = springAiMcpToolCallbacks();

ToolCallbackProvider safeProvider = FastMcpSpringAiTools.wrap(rawProvider, List.of(
    SpringAiMcpToolMapping.builder("getOrdersByUserId")
        .name("get_my_orders")
        .description("Get orders for the authenticated user.")
        .inputSchema(virtualOrderSchema)
        .injectArgument("userId", context -> context.getContext().get("userId"))
        .build()
));
```

模型只能看到 `get_my_orders(status)`。如果模型自己传 `userId`，safe-core
会拒绝调用；raw callback 收到的 `userId` 来自 Spring AI `ToolContext`。

如果你使用 Spring Boot + Spring AI，`fastmcp-spring-ai-boot-starter` 可以通过
`fastmcp.safe.*` 绑定同一份 mapping，并发布 primary 的安全 `ToolCallbackProvider`。
它仍然要求 raw provider 已经存在；自动创建 MCP client 属于后续阶段。

运行示例：

```bash
mvn -Pexamples test
```

Example modules 会被 Maven 编译检查，但 deploy 时会跳过。因此发布包只包含 parent
和 library modules。

## 包结构

```text
fastmcp-safe-core
  io.github.sandking.fastmcp.safe
    SafeMcpTool        执行 virtual-to-raw 安全调用
    SafeMcpToolSpec    virtual tool 和 raw tool mapping
    SafeToolCallContext 框架无关调用上下文
    SafeArgumentResolver protected argument resolver
    SafeMcpPolicy      raw 调用前的 policy hook
    RawToolInvoker     框架无关 raw tool delegate
    SafeAuditEvent     不记录敏感 injected values 的 audit event

fastmcp-safe-config
  io.github.sandking.fastmcp.safe.config
    SafeMcpConfiguration       top-level server configuration collection
    SafeMcpServerConfiguration MCP server connection metadata and tools
    SafeMcpToolConfiguration   safe virtual tool mapping configuration
    SafeMcpConfigException     validation error with stable code

fastmcp-agentscope-adapter
  io.github.sandking.fastmcp.agentscope
    FastMcpAgentScopeTools    registers safe virtual tools into AgentScope Toolkit
    FastMcpToolMapping        virtual-to-raw tool and argument mapping
    ToolArgumentResolver      resolves injected protected arguments

fastmcp-spring-ai-adapter
  io.github.sandking.fastmcp.springai
    FastMcpSpringAiTools       wraps raw ToolCallbacks as safe virtual callbacks
    SpringAiMcpToolMapping     virtual-to-raw tool and argument mapping
    SpringAiToolArgumentResolver resolves injected protected arguments

fastmcp-spring-ai-boot-starter
  io.github.sandking.fastmcp.springai.boot
    FastMcpSafeAutoConfiguration Spring Boot auto-configuration
    FastMcpSafeProperties        fastmcp.safe.* configuration properties
    FastMcpSafeConfigurationFactory properties-to-safe-config converter

examples/agentscope-adapter
  io.github.sandking.fastmcp.examples.agentscope
    FastMcpAgentScopeExample  minimal runnable example class
```

## 与 FastMCP Python 的关系

本项目的目标是承载一个受 [FastMCP](https://github.com/PrefectHQ/fastmcp)
启发的 Java 实现。它目前还没有和 Python 项目达到功能对等。
