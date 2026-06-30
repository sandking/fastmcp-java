# FastMCP Java

[English](README.md) | [简体中文](README.zh-CN.md)

FastMCP Java is being refocused into a Safe MCP Registration library for Java
agent frameworks. Its primary goal is to register MCP raw tools safely so the
LLM sees only model-facing virtual tools, while protected arguments are injected
from server-side runtime context.
The project boundary is not to build a complete FastMCP server/client framework;
it is to provide safe MCP registration entry points across Java Agent frameworks.

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
  MCP servers, HTTP connection settings, virtual tools, argument mappings, and
  protected argument sources
- `fastmcp-safe-spring-boot-autoconfigure-support`: Spring Boot binding support
  for `fastmcp.safe.*`, shared by framework-specific Boot starters
- AgentScope adapter module for safe virtual tools and Toolkit registration,
  backed by `fastmcp-safe-core`
- AgentScope Boot starter that reuses the shared binding support, creates managed
  AgentScope `McpClientWrapper`s from `fastmcp.safe.*` using `streamable-http`,
  `sse`, or `stdio`, and registers only virtual tools into the application
  `Toolkit`
- Spring AI adapter module for safe virtual `ToolCallback`s, backed by
  `fastmcp-safe-core`
- Spring AI Boot starter that reuses the shared binding support, creates
  managed Spring AI MCP clients from `fastmcp.safe.*` using `streamable-http`,
  `sse`, or `stdio`, and publishes only the primary safe `ToolCallbackProvider`
- Unit tests and GitHub Actions CI

Outside this SDK's current ownership unless a future module explicitly scopes it:

- FastMCP-owned MCP server implementation, public MCP service catalogs, or real
  production MCP endpoints
- Application-level AG-UI, CopilotKit, SSE tool-event streams, logs, and frontend
  event payloads; this SDK does not emit or inspect those events
- Resources and prompts
- Plain classpath package scanning outside Spring
- Full authentication flows, OAuth, secret lifecycle, and production middleware
  owned by the consuming application or MCP server
- Full protocol conformance tests for third-party MCP servers

This repository's tests use local fake MCP servers to validate the SDK contract:
managed client creation, initialization and tool listing, HTTP headers, query
params, cookie/session behavior, virtual tool schemas, protected-argument
injection, and raw-tool hiding. Consuming applications own real MCP smoke tests
against their private deployments, plus any AG-UI/frontend/log leak checks.

## Safe core

Use `fastmcp-safe-core` when you need the framework-neutral safety layer:

- virtual tool name, description, and schema
- virtual-to-raw argument mapping
- protected argument injection
- schema validation that rejects virtual input schemas exposing protected
  arguments
- policy decisions before raw tool invocation
- audit events that record injected argument names but not injected values

Most users should use this through an adapter such as
`fastmcp-agentscope-adapter` or `fastmcp-spring-ai-adapter`. Adapter code should
delegate mapping and protected-argument handling to safe-core instead of
reimplementing it.

## Safe config

Use `fastmcp-safe-config` when application configuration should be owned by this
library instead of by AgentScope or Spring AI directly. It describes MCP server
connection settings and safe virtual tool mappings. The config module itself is
framework-neutral and does not create MCP clients; framework starters such as
`fastmcp-agentscope-boot-starter` and `fastmcp-spring-ai-boot-starter` consume it.

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

The virtual input schema must contain only model-fillable business fields. If an
injected raw argument such as `userId`, or a virtual alias that maps to it, appears
in `inputSchema.properties`, the config/spec builder rejects it before any model
call can be made.

Adapters convert this shared config into framework-specific mappings by supplying
a resolver registry:

```java
SpringAiMcpToolMapping mapping = SpringAiMcpToolMapping.from(getMyOrders,
    Map.of("currentUserId", context -> context.getContext().get("userId")));
```

When multiple configured MCP servers can expose the same raw tool name, pass the
server name explicitly, for example `SpringAiMcpToolMapping.from("orders",
getMyOrders, resolvers)`. Boot starters do this automatically from
`fastmcp.safe.servers.<server>`.

The source name `currentUserId` is configuration, not the sensitive value. The
actual value still comes from framework runtime context at call time.

Spring AI Boot applications can bind the same idea through
`fastmcp-spring-ai-boot-starter`:

```xml
<dependency>
    <groupId>io.github.sandking</groupId>
    <artifactId>fastmcp-spring-ai-boot-starter</artifactId>
    <version>0.1.0</version>
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

HTTP headers, query params, and cookie settings are applied only to the managed
MCP HTTP client created by the boot starters. They are not included in virtual
tool names, descriptions, schemas, or arguments. Sensitive values should use
normal Spring Boot placeholders or deployment-time configuration; FastMCP does
not manage secret lifecycles or provide a secret resolver.

Declare a resolver bean whose name matches the configured source name:

```java
@Bean("currentUserId")
SpringAiToolArgumentResolver currentUserId() {
    return context -> context.getContext().get("userId");
}
```

For SSE transport, use `transport=sse`, set the base `endpoint`, and optionally
override `sse-endpoint` if the server does not use `/sse`:

```properties
fastmcp.safe.servers.orders.transport=sse
fastmcp.safe.servers.orders.endpoint=https://mcp.example.test
fastmcp.safe.servers.orders.sse-endpoint=/events
```

`stdio` remains supported for compatibility, but server-side Spring Boot
deployments should normally prefer `streamable-http` or `sse`.

The starter creates managed Spring AI `McpSyncClient`s, initializes them, turns
their raw MCP tools into internal raw callbacks, wraps them with the configured
safe mappings, and publishes a primary safe provider named
`fastMcpSafeToolCallbackProvider`. The managed raw provider is not published as a
Spring bean. Managed raw callbacks are wrapped per configured server, so the same
raw MCP tool name can exist under different servers without cross-wiring.
Existing external raw Spring AI `ToolCallbackProvider` beans are still supported
for compatibility, but application code should inject and pass the safe provider
to the model.

External provider diagnostics make that compatibility risk visible. This is a
conservative diagnostic: external Spring AI `ToolCallbackProvider` beans are the
main raw-provider exposure risk, although they may include non-raw business
providers. The default is `fail`; set `warn` or `off` only when the application
intentionally uses the external-provider compatibility path and still guarantees
that models receive `fastMcpSafeToolCallbackProvider` instead of raw providers:

```yaml
fastmcp:
  safe:
    diagnostics:
      external-raw-provider: fail # warn | fail | off
```

Both Spring AI and AgentScope Boot starters consume an optional `SafeAuditSink`
bean. Safe tool calls are recorded as `TOOL_CALL` audit events, including the
framework, virtual tool name, raw server/tool names, caller/tenant identifiers
available from the runtime context, and injected argument names. The sink does
not receive injected argument values from FastMCP. Spring AI external raw
provider diagnostics are also recorded as `DIAGNOSTIC` events with
`EXTERNAL_RAW_PROVIDER_PRESENT`.

```java
@Bean
SafeAuditSink fastMcpAuditSink() {
    return event -> logger.info("fastmcp audit type={} framework={} virtualTool={} error={}",
            event.eventType(), event.framework(), event.virtualToolName(), event.errorCode());
}
```

For an external raw-provider compatibility path, set
`fastmcp.safe.servers.<server>.enabled=false` to skip managed client creation
while still using the configured tool mappings to wrap the external provider.

AgentScope Boot applications can use the same `fastmcp.safe.*` configuration by
adding `fastmcp-agentscope-boot-starter` and providing a `Toolkit` bean. The
starter uses AgentScope Java's own `McpClientBuilder` to create internal managed
`McpClientWrapper`s, then registers only mapped virtual tools into that `Toolkit`;
the raw wrappers are not published as Spring beans.

```xml
<dependency>
    <groupId>io.github.sandking</groupId>
    <artifactId>fastmcp-agentscope-boot-starter</artifactId>
    <version>0.1.0</version>
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

If the application also uses the official AgentScope Spring Boot starter, the
`Toolkit` can be provided by AgentScope or by application code. This library does
not create AgentScope agents or models.

The reusable Spring Boot binding code lives in
`fastmcp-safe-spring-boot-autoconfigure-support`. It owns
`FastMcpSafeProperties` and `FastMcpSafeConfigurationFactory`, but it is not a
standalone Agent framework starter and does not create MCP clients by itself.

## Application integration checklist

This is an application-owned checklist, not a claim that FastMCP Java ships a
complete production MCP platform. Production validation does not require this
SDK to implement the MCP protocol. It validates that the safety wrapper remains
the only model-facing tool path when Spring AI, AgentScope, and the underlying
MCP SDKs run against a real MCP service.

Before using a configured server in production:

- Verify the model-facing tool list contains only virtual names such as
  `get_my_orders`, never raw MCP names such as `getOrdersByUserId` or
  `mcp__orders__getOrdersByUserId`.
- Verify virtual input schemas contain only model-fillable business arguments,
  and do not expose protected arguments such as `userId`, `tenantId`, `role`, or
  `includeDeleted`.
- Resolve protected values from server-side runtime context through resolver
  beans; do not put sensitive values into `fastmcp.safe.*` configuration.
- For Spring AI production deployments, set
  `fastmcp.safe.diagnostics.external-raw-provider=fail` or keep the default
  fail-closed behavior unless the application intentionally uses the documented
  external-provider compatibility path.
- Pass the safe provider, for example `fastMcpSafeToolCallbackProvider`, to the
  model. Do not pass every `ToolCallbackProvider` bean as a collection unless raw
  providers have been filtered out.
- Configure a `SafeAuditSink` and verify audits contain virtual/raw tool names,
  caller/tenant identifiers, and injected argument names, but not injected
  argument values.
- If the application streams tool events to AG-UI, CopilotKit, logs, or a
  frontend, verify those events do not leak raw tool names, injected values, raw
  arguments, or backend-only metadata.
- Run real MCP smoke tests from private deployment configuration only. Do not
  commit real company domains, real MCP endpoints, credentials, or business tool
  names to this repository.

## Build

```bash
mvn test
```

The default reactor includes the safe core, shared config, shared Spring Boot
binding support, AgentScope adapter, AgentScope Boot starter, Spring AI adapter,
and Spring AI Boot starter. Run it with JDK 17:

```bash
mvn test
```

Public tests use local fake MCP servers only. Do not commit real company domains,
real MCP endpoints, or real business tool names to this repository.

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

For Spring Boot with AgentScope, `fastmcp-agentscope-boot-starter` can bind the
same mapping through `fastmcp.safe.*`, create managed `McpClientWrapper`s for
`streamable-http`, `sse`, or `stdio`, and register virtual tools into the
application `Toolkit`.

## Spring AI adapter

The Spring AI adapter targets Spring AI `2.0.0` and wraps existing raw
`ToolCallback`s or `ToolCallbackProvider`s into model-facing safe virtual
callbacks.

The adapter module alone does not create MCP clients from configuration. If you
are not using the Boot starter, build the MCP client/provider using Spring AI or
your application wiring, then wrap the raw callbacks before giving them to the
model:

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
the same mapping through `fastmcp.safe.*`, create managed MCP clients for
`streamable-http`, `sse`, or `stdio`, and publish a primary safe
`ToolCallbackProvider`. The `fastmcp.safe.*` binding is delegated to the shared
Spring Boot support module.

The `examples` profile contains runnable checks for the three current
integration paths:

- `fastmcp-examples/agentscope-adapter`: registers a virtual AgentScope tool into a
  `Toolkit`, maps `status -> orderStatus`, injects `userId` and `tenantId` from
  `RuntimeContext`, and rejects model-supplied protected arguments.
- `fastmcp-examples/spring-ai-adapter`: wraps an existing raw Spring AI
  `ToolCallbackProvider` as a safe provider and injects protected arguments from
  `ToolContext`.
- `fastmcp-examples/spring-ai-boot-starter`: binds `fastmcp.safe.*`, declares resolver
  beans such as `currentUserId`, and verifies that the starter publishes the
  primary `fastMcpSafeToolCallbackProvider` from an existing raw provider and a
  local fake `streamable-http` MCP server. The examples do not require or publish
  real MCP endpoints; deployment-specific smoke tests belong to consuming
  applications.

Run all examples with:

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
    FastMcpAgentScopeExample  Toolkit registration and RuntimeContext injection

fastmcp-examples/spring-ai-adapter
  io.github.sandking.fastmcp.examples.springai
    FastMcpSpringAiExample    ToolCallbackProvider wrapping and ToolContext injection

fastmcp-examples/spring-ai-boot-starter
  io.github.sandking.fastmcp.examples.springai.boot
    FastMcpSpringAiBootExample fastmcp.safe.* binding and resolver beans
```

## Relationship to FastMCP Python

This project is intended to carry a Java implementation inspired by
[FastMCP](https://github.com/PrefectHQ/fastmcp). It is not yet feature-equivalent
with the Python project.
