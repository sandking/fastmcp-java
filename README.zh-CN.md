# FastMCP Java

[English](README.md) | [简体中文](README.zh-CN.md)

FastMCP Java 正在调整为面向 Java Agent 框架的 Safe MCP Registration
库。它的核心目标是安全注册 MCP raw tools：LLM 只能看到面向模型的虚拟工具，
受保护参数由服务端 runtime context 注入。
本项目的边界不是实现完整 FastMCP server/client 框架，而是在多个 Java
Agent 框架上提供安全 MCP 注册入口。

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
  HTTP 连接配置、virtual tool、argument mapping 和 protected argument source
- `fastmcp-safe-spring-boot-autoconfigure-support`：Spring Boot
  `fastmcp.safe.*` 绑定支持，供具体框架的 Boot starter 复用
- AgentScope adapter module：支持安全虚拟工具和 Toolkit 注册，并由 `fastmcp-safe-core` 承载核心安全逻辑
- AgentScope Boot starter：复用共享绑定支持，可以基于 `fastmcp.safe.*`
  使用 `streamable-http`、`sse` 或 `stdio` 创建 managed AgentScope
  `McpClientWrapper`，并且只向应用提供的 `Toolkit` 注册 virtual tools
- Spring AI adapter module：支持安全虚拟 `ToolCallback`，并由 `fastmcp-safe-core` 承载核心安全逻辑
- Spring AI Boot starter：复用共享绑定支持，可以基于 `fastmcp.safe.*`
  使用 `streamable-http`、`sse` 或 `stdio` 创建 managed Spring AI MCP
  clients，并且只发布 primary 的安全 `ToolCallbackProvider`
- Unit tests 和 GitHub Actions CI

尚未实现：

- 本库自己的 MCP server implementation
- AG-UI SSE stream 和 tool event 链路的端到端验证
- Resources 和 prompts
- Spring 之外的普通 classpath package scanning
- 完整 authentication flow、OAuth、secret lifecycle 和 production middleware
- Protocol conformance tests

## Safe core

当你需要框架无关的安全层时，使用 `fastmcp-safe-core`：

- virtual tool name、description 和 schema
- virtual-to-raw argument mapping
- protected argument injection
- 校验 virtual input schema，拒绝暴露 injected protected arguments
- raw tool 调用前的 policy decision
- audit event 默认只记录 injected argument names，不记录 injected values

大多数用户应该通过 adapter 使用它，例如 `fastmcp-agentscope-adapter`
或 `fastmcp-spring-ai-adapter`。adapter 代码应委托 safe-core 处理 mapping
和 protected-argument，而不是重复实现。

## Safe config

当应用的 MCP 配置也希望由本库承接，而不是直接写在 AgentScope 或 Spring AI
配置里时，使用 `fastmcp-safe-config`。它负责描述 MCP server connection
settings 和安全虚拟工具 mapping。config 模块自身保持框架无关，不创建 MCP
client；`fastmcp-agentscope-boot-starter` 和 `fastmcp-spring-ai-boot-starter`
这类 framework starter 会消费它。

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

virtual input schema 只能包含模型可填写的业务字段。如果 `userId` 这类 injected
raw argument，或者映射到它的 virtual alias 出现在 `inputSchema.properties` 中，
config/spec builder 会在模型调用发生前直接拒绝。

adapter 通过 resolver registry 把这份共享配置转换成框架自己的 mapping：

```java
SpringAiMcpToolMapping mapping = SpringAiMcpToolMapping.from(getMyOrders,
    Map.of("currentUserId", context -> context.getContext().get("userId")));
```

如果多个 MCP server 可能暴露同名 raw tool，需要显式带上 server name，例如
`SpringAiMcpToolMapping.from("orders", getMyOrders, resolvers)`。Boot starter
会自动从 `fastmcp.safe.servers.<server>` 传入 server name。

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
fastmcp.safe.servers.orders.transport=streamable-http
fastmcp.safe.servers.orders.endpoint=https://mcp.example.test/mcp
fastmcp.safe.servers.orders.request-timeout=3s
fastmcp.safe.servers.orders.initialization-timeout=5s
fastmcp.safe.servers.orders.client-name=fastmcp-orders
fastmcp.safe.servers.orders.client-version=0.1.0
fastmcp.safe.servers.orders.http.cookies.enabled=true
fastmcp.safe.servers.orders.http.headers.X-App-Id=orders-agent
fastmcp.safe.servers.orders.http.headers.Authorization=Bearer ${ORDERS_MCP_TOKEN}
fastmcp.safe.servers.orders.http.query-params.region=cn
fastmcp.safe.servers.orders.tools.getOrdersByUserId.name=get_my_orders
fastmcp.safe.servers.orders.tools.getOrdersByUserId.description=Get orders for the authenticated user.
fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.type=object
fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.properties.status.type=string
fastmcp.safe.servers.orders.tools.getOrdersByUserId.input-schema.required[0]=status
fastmcp.safe.servers.orders.tools.getOrdersByUserId.argument-mappings.status=orderStatus
fastmcp.safe.servers.orders.tools.getOrdersByUserId.injected-arguments.userId=currentUserId
fastmcp.safe.servers.orders.tools.getOrdersByUserId.read-only=true
```

HTTP headers、query params 和 cookie 设置只会应用到 boot starter 内部创建的
managed MCP HTTP client，不会进入 virtual tool name、description、schema 或
arguments。敏感值应使用 Spring Boot 占位符或部署环境注入；FastMCP 不负责 secret
生命周期，也不提供 secret resolver。

声明一个 bean，bean name 与配置里的 source name 一致：

```java
@Bean("currentUserId")
SpringAiToolArgumentResolver currentUserId() {
    return context -> context.getContext().get("userId");
}
```

如果使用 SSE transport，设置 `transport=sse`、base `endpoint`，并在服务端不是
`/sse` 时覆盖 `sse-endpoint`：

```properties
fastmcp.safe.servers.orders.transport=sse
fastmcp.safe.servers.orders.endpoint=https://mcp.example.test
fastmcp.safe.servers.orders.sse-endpoint=/events
```

`stdio` 仍然作为兼容路径保留，但服务端部署的 Spring Boot 项目通常应该优先使用
`streamable-http` 或 `sse`。

starter 会创建 managed Spring AI `McpSyncClient`，初始化 client，把这些 MCP raw
tools 转成内部 raw callbacks，再按配置包装成安全 provider，并发布名为
`fastMcpSafeToolCallbackProvider` 的 primary 安全 provider。managed raw provider
不会作为 Spring bean 暴露。managed raw callbacks 会按配置 server 分别包装，因此
不同 server 下可以存在同名 raw MCP tool，不会串到同一个 raw provider。已有外部 raw
Spring AI `ToolCallbackProvider` bean 仍然兼容，但应用侧应该把 safe provider 交给模型。

如果走外部 raw provider 兼容路径，可以设置
`fastmcp.safe.servers.<server>.enabled=false` 跳过 managed client 创建，同时继续用
配置里的 tool mappings 包装外部 provider。

AgentScope Boot 应用可以使用同一份 `fastmcp.safe.*` 配置，只需要引入
`fastmcp-agentscope-boot-starter` 并提供一个 `Toolkit` bean。starter 会用
AgentScope Java 自己的 `McpClientBuilder` 创建内部 managed `McpClientWrapper`，
然后只把 mapped virtual tools 注册进这个 `Toolkit`，不会把 raw
`McpClientWrapper` 暴露为 Spring bean。

```xml
<dependency>
    <groupId>io.github.sandking</groupId>
    <artifactId>fastmcp-agentscope-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
@Bean
Toolkit toolkit() {
    return new Toolkit();
}

@Bean("currentUserId")
ToolArgumentResolver currentUserId() {
    return param -> param.getRuntimeContext().get(UserContext.class).userId();
}
```

如果项目同时使用 AgentScope 官方 Spring Boot starter，也可以由 AgentScope
starter 或应用自身提供 `Toolkit`；本库不负责创建 AgentScope agent/model。

可复用的 Spring Boot 绑定代码位于
`fastmcp-safe-spring-boot-autoconfigure-support`。它负责
`FastMcpSafeProperties` 和 `FastMcpSafeConfigurationFactory`，但它不是独立的
Agent 框架 starter，也不会自行创建 MCP client。

## 构建

```bash
mvn test
```

默认 reactor 包含 safe core、共享配置、共享 Spring Boot 绑定支持、AgentScope
adapter、AgentScope Boot starter、Spring AI adapter 和 Spring AI Boot starter。请使用 JDK 17 执行：

```bash
mvn test
```

公开测试只使用本地 fake MCP server。不要把真实公司域名、真实 MCP endpoint
或真实业务 tool 名称提交到本仓库。

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

如果你使用 Spring Boot + AgentScope，`fastmcp-agentscope-boot-starter` 可以通过
`fastmcp.safe.*` 绑定同一份 mapping，为 `streamable-http`、`sse` 或 `stdio`
创建 managed `McpClientWrapper`，并向应用提供的 `Toolkit` 注册 virtual tools。

## Spring AI adapter

Spring AI adapter 面向 Spring AI `2.0.0`，负责把已有 raw `ToolCallback` 或
`ToolCallbackProvider` 包装成模型可见的安全虚拟 callback。

adapter module 本身不会基于配置自动创建 MCP client。如果你没有使用 Boot starter，
可以继续用 Spring AI 或应用自身 wiring 创建 MCP client/provider，然后在交给模型前
包装 raw callbacks：

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
`fastmcp.safe.*` 绑定同一份 mapping，为 `streamable-http`、`sse` 或 `stdio`
创建 managed MCP clients，并发布 primary 的安全 `ToolCallbackProvider`。
`fastmcp.safe.*` 绑定会委托给共享 Spring Boot support module。

`examples` profile 当前包含三条可运行的接入路径验证：

- `fastmcp-examples/agentscope-adapter`：把虚拟 AgentScope tool 注册进 `Toolkit`，
  演示 `status -> orderStatus` 参数映射，从 `RuntimeContext` 注入 `userId`
  和 `tenantId`，并拒绝模型传入受保护参数。
- `fastmcp-examples/spring-ai-adapter`：把已有 raw Spring AI `ToolCallbackProvider`
  包装成安全 provider，并从 `ToolContext` 注入受保护参数。
- `fastmcp-examples/spring-ai-boot-starter`：绑定 `fastmcp.safe.*`，声明
  `currentUserId` 等 resolver bean，并验证 starter 基于已有 raw provider
  发布 primary 的 `fastMcpSafeToolCallbackProvider`。managed MCP client 路径，包括
  streamable HTTP transport 和按 server 隔离 raw provider，当前由 starter 测试覆盖，
  后续补真实 MCP server 示例。

运行全部示例：

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

fastmcp-safe-spring-boot-autoconfigure-support
  io.github.sandking.fastmcp.safe.boot
    FastMcpSafeProperties        fastmcp.safe.* configuration properties
    FastMcpSafeConfigurationFactory properties-to-safe-config converter

fastmcp-agentscope-adapter
  io.github.sandking.fastmcp.agentscope
    FastMcpAgentScopeTools    registers safe virtual tools into AgentScope Toolkit
    FastMcpToolMapping        virtual-to-raw tool and argument mapping
    ToolArgumentResolver      resolves injected protected arguments

fastmcp-agentscope-boot-starter
  io.github.sandking.fastmcp.agentscope.boot
    FastMcpAgentScopeSafeAutoConfiguration Spring Boot auto-configuration
    FastMcpAgentScopeManagedClientFactory creates managed AgentScope MCP clients
    FastMcpAgentScopeHttpClientSupport package-private HTTP/SSE client glue
    FastMcpAgentScopeSafeRegistrar registers virtual tools into Toolkit

fastmcp-spring-ai-adapter
  io.github.sandking.fastmcp.springai
    FastMcpSpringAiTools       wraps raw ToolCallbacks as safe virtual callbacks
    SpringAiMcpToolMapping     virtual-to-raw tool and argument mapping
    SpringAiToolArgumentResolver resolves injected protected arguments

fastmcp-spring-ai-boot-starter
  io.github.sandking.fastmcp.springai.boot
    FastMcpSafeAutoConfiguration Spring Boot auto-configuration
    FastMcpSpringAiManagedClientFactory creates managed Spring AI MCP clients
    FastMcpSpringAiHttpTransportSupport package-private HTTP/SSE transport glue
    FastMcpManagedSpringAiToolCallbackProvider closes managed clients

fastmcp-examples/agentscope-adapter
  io.github.sandking.fastmcp.examples.agentscope
    FastMcpAgentScopeExample  Toolkit 注册与 RuntimeContext 注入示例

fastmcp-examples/spring-ai-adapter
  io.github.sandking.fastmcp.examples.springai
    FastMcpSpringAiExample    ToolCallbackProvider 包装与 ToolContext 注入示例

fastmcp-examples/spring-ai-boot-starter
  io.github.sandking.fastmcp.examples.springai.boot
    FastMcpSpringAiBootExample fastmcp.safe.* 绑定与 resolver bean 示例
```

## 与 FastMCP Python 的关系

本项目的目标是承载一个受 [FastMCP](https://github.com/PrefectHQ/fastmcp)
启发的 Java 实现。它目前还没有和 Python 项目达到功能对等。
