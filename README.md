# FastMCP Java

[English](README.md) | [简体中文](README.zh-CN.md)

FastMCP Java is being refocused into a Safe MCP Registration library for Java
agent frameworks. Its primary goal is to register MCP raw tools safely so the
LLM sees only model-facing virtual tools, while protected arguments are injected
from server-side runtime context.

```text
MCP raw tools
  -> safe mapping and protected-argument injection
  -> framework adapter
  -> AgentScope / Spring AI virtual tools
  -> LLM sees only virtual tools
```

The first implemented framework adapters are AgentScope and Spring AI. Both
adapter paths expose safe virtual tools while keeping raw MCP/backend tools as
internal delegates.

## Current scope

Implemented now:

- Maven multi-module project. The full reactor requires JDK 17; `fastmcp-safe-core`
  and `fastmcp-safe-config` still compile with Java 11 release compatibility.
- Apache-2.0 license
- `fastmcp-safe-core`: framework-neutral safe tool mapping, protected argument
  injection, policy decisions, raw invoker abstraction, and audit events
- `fastmcp-safe-config`: framework-neutral immutable configuration model for
  MCP servers, virtual tools, argument mappings, and protected argument sources
- AgentScope adapter module for safe virtual tools and Toolkit registration,
  backed by `fastmcp-safe-core`
- Spring AI adapter module for safe virtual `ToolCallback`s, backed by
  `fastmcp-safe-core`
- Spring AI Boot starter that binds `fastmcp.safe.*`, creates
  `SafeMcpConfiguration`, and publishes a primary safe `ToolCallbackProvider`
  from existing raw providers
- Unit tests and GitHub Actions CI

Not implemented yet:

- Automatic MCP client creation from FastMCP-owned configuration
- AgentScope Spring Boot auto-configuration
- MCP HTTP or SSE transport endpoint
- Resources and prompts
- Plain classpath package scanning outside Spring
- Client implementation
- Authentication, lifecycle, and production middleware
- Protocol conformance tests

## Safe core

Use `fastmcp-safe-core` when you need the framework-neutral safety layer:

- virtual tool name, description, and schema
- virtual-to-raw argument mapping
- protected argument injection
- policy decisions before raw tool invocation
- audit events that record injected argument names but not injected values

Most users should use this through an adapter such as
`fastmcp-agentscope-adapter` or `fastmcp-spring-ai-adapter`. Adapter code should
delegate mapping and protected-argument handling to safe-core instead of
reimplementing it.

## Safe config

Use `fastmcp-safe-config` when application configuration should be owned by this
library instead of by AgentScope or Spring AI directly. It describes MCP servers
and safe virtual tool mappings, but it does not create MCP clients yet.

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

Adapters convert this shared config into framework-specific mappings by supplying
a resolver registry:

```java
SpringAiMcpToolMapping mapping = SpringAiMcpToolMapping.from(getMyOrders,
    Map.of("currentUserId", context -> context.getContext().get("userId")));
```

The source name `currentUserId` is configuration, not the sensitive value. The
actual value still comes from framework runtime context at call time.

Spring AI Boot applications can bind the same idea through
`fastmcp-spring-ai-boot-starter`:

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

Declare a resolver bean whose name matches the configured source name:

```java
@Bean("currentUserId")
SpringAiToolArgumentResolver currentUserId() {
    return context -> context.getContext().get("userId");
}
```

The starter currently wraps existing raw Spring AI `ToolCallbackProvider` beans
and publishes a primary safe provider named `fastMcpSafeToolCallbackProvider`.
It does not create MCP clients yet.

## Build

```bash
mvn test
```

The default reactor includes the safe core, shared config, AgentScope adapter,
Spring AI adapter, and Spring AI Boot starter. Run it with JDK 17:

```bash
mvn test
```

## AgentScope adapter

Use `fastmcp-agentscope-adapter` when the runtime is AgentScope Java and the
model should call safe virtual tools instead of raw backend tools or raw
AgentScope MCP tools. AgentScope Java 2.x requires JDK 17 or newer, so the
full repository build also requires JDK 17.

The core path is:

```text
raw AgentScope/backend tool or raw MCP tool
  -> FastMcpToolMapping
  -> fastmcp-safe-core
  -> safe virtual tool schema and protected-argument injection
  -> AgentScope ToolBase
  -> Toolkit.callTool(...)
  -> internal raw delegate
```

For example, a raw backend tool can remain `getOrdersByUserId(userId, status)`,
while the model only sees `get_my_orders(status)`. The adapter injects `userId`
from `RuntimeContext` before delegating to the raw tool.

The same mapping can also wrap a raw AgentScope `AgentTool`, including tools
created from AgentScope MCP clients such as `mcp__orders__getOrdersByUserId`,
without registering that raw tool name into the model-facing `Toolkit`.

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

## Spring AI adapter

The Spring AI adapter targets Spring AI `2.0.0` and wraps existing raw
`ToolCallback`s or `ToolCallbackProvider`s into model-facing safe virtual
callbacks.

The adapter does not create MCP clients from configuration yet. Build the MCP
client/provider using Spring AI or your application wiring, then wrap the raw
callbacks before giving them to the model:

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

The model sees only `get_my_orders(status)`. If it supplies `userId` itself, the
safe-core layer rejects the call. The raw callback receives `userId` from
Spring AI `ToolContext`.

If you use Spring Boot with Spring AI, `fastmcp-spring-ai-boot-starter` can bind
the same mapping through `fastmcp.safe.*` and publish a primary safe
`ToolCallbackProvider`. It still expects raw providers to exist; automatic MCP
client creation is a later phase.

Run the example with:

```bash
mvn -Pexamples test
```

Example modules are compile-checked by Maven but are skipped during deploy, so
package publishing only includes the parent and library modules.

## Package layout

```text
fastmcp-safe-core
  io.github.sandking.fastmcp.safe
    SafeMcpTool        executes virtual-to-raw safe calls
    SafeMcpToolSpec    virtual tool and raw tool mapping
    SafeToolCallContext framework-neutral call context
    SafeArgumentResolver protected argument resolver
    SafeMcpPolicy      policy hook before raw invocation
    RawToolInvoker     framework-neutral raw tool delegate
    SafeAuditEvent     audit event without sensitive injected values

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

## Relationship to FastMCP Python

This project is intended to carry a Java implementation inspired by
[FastMCP](https://github.com/PrefectHQ/fastmcp). It is not yet feature-equivalent
with the Python project.
