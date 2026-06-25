# Safe MCP Registration Design

## 背景

当前仓库最初按 "FastMCP Java" 方向起步，已经包含本地 tool registry、Spring Boot starter 和 AgentScope adapter。
经过重新确认，项目核心目标调整为：

> 安全地把 MCP raw tools 注册到 Java Agent 框架中，避免 raw tool name、description、schema、受保护参数和内部调用细节直接暴露给 LLM。

这个目标不是实现一个完整 MCP server，也不是替代 AgentScope 或 Spring AI 的全部 MCP 能力。
本库应该成为统一的 MCP 安全配置和注册入口，底层可以继续使用 AgentScope、Spring AI 或 MCP Java SDK 的客户端能力。

## 当前问题

### Raw MCP tool 直接注册会暴露模型可见面

Agent 框架通常会把工具定义交给模型，工具定义至少包含：

- tool name
- description
- input schema
- read-only 或其他 hint

如果直接注册 MCP client 发现的 raw tools，模型会看到 MCP server 暴露的原始工具名、描述和参数结构。
这会带来几个问题：

- 模型能看到内部接口命名、后端领域模型、权限字段或生产路径。
- 模型可能尝试伪造 `userId`、`tenantId`、`role`、`includeDeleted` 等安全关键参数。
- prompt 不能作为安全边界，无法可靠阻止模型调用不该暴露的 raw tool。
- AG-UI、日志、trace 或前端 tool event 可能进一步泄露 raw 参数和 raw tool 名称。

### 现有 core 和 starter 偏离新目标

现有 `fastmcp-core` 是本地内存态 tool registry，关注本地声明、列出和调用工具。
现有 `fastmcp-spring-boot-starter` 负责扫描 Spring bean 上的 `@McpTool` 并填充 `FastMcpServer`。

它们对 "安全注册外部 MCP raw tools 到 Agent 框架" 不是主链路能力。
继续保留原有叙事会让用户误以为本仓库是完整 Java MCP server framework。

## 目标

### 产品目标

提供一个框架无关的安全 MCP 注册层：

```text
用户配置 fastmcp.safe.*
  -> 本库连接 MCP servers
  -> 本库发现 raw MCP tools
  -> 本库按 allowlist/mapping 生成 virtual tools
  -> 本库把 virtual tools 注册到 AgentScope 或 Spring AI
  -> LLM 只看到 virtual tools
  -> 调用时本库注入服务端上下文参数
  -> 内部 delegate 到 raw MCP tool
```

用户接入本库后，应尽量不需要直接配置：

- `spring.ai.mcp.client.*`
- AgentScope `McpClientBuilder`
- AgentScope `toolkit.registerMcpClient(client)`
- Spring AI raw MCP `ToolCallbackProvider`

这些底层对象可以由本库创建和使用，但不作为用户的主配置面。

### 安全目标

- 默认不暴露任何 raw MCP tool。
- 只有显式配置 mapping 的 raw tool 才能生成 virtual tool。
- LLM 只能看到 virtual name、virtual description、virtual input schema。
- virtual input schema 不包含 injected protected arguments。
- 模型显式传入 protected argument 时拒绝执行。
- raw MCP tool 只作为内部 delegate 调用。
- audit 默认记录字段名和决策，不记录敏感值。
- 框架 adapter 不绕过 safe-core 的 mapping、injection 和 policy。

### 非目标

- 不实现完整 MCP server。
- 不实现 resources/prompts。
- 不替代 AgentScope 或 Spring AI 的 agent runtime。
- 不做自由表达式执行引擎。
- 第一阶段不做复杂 result DLP，只保留 result guard 扩展点。

## 设计原则

1. 本库配置优先，框架原生 MCP 配置作为内部实现细节。
2. 默认拒绝 raw tool 暴露，必须显式 allowlist。
3. Prompt 不是安全边界，身份、租户、权限和资源归属必须来自服务端上下文。
4. 配置文件不能执行任意表达式，只能引用白名单 resolver id。
5. 安全核心不依赖 AgentScope 或 Spring AI。
6. adapter 只做框架对象转换，不复制安全逻辑。
7. 每个 adapter 都必须验证模型可见 tools 只包含 virtual tools。

## 目标模块

```text
fastmcp-safe-core
  统一配置模型
  MCP server spec
  safe tool spec
  argument mapping
  protected argument injection
  policy
  audit
  result guard extension point

fastmcp-agentscope-adapter
  AgentScope Toolkit / ToolBase / McpClientWrapper 适配
  不包含通用 mapping 安全逻辑

fastmcp-spring-ai-adapter
  Spring AI ToolCallback / ToolCallbackProvider / ToolContext 适配
  不直接暴露 raw MCP ToolCallback

fastmcp-spring-boot-starter
  读取 fastmcp.safe.* 配置
  自动装配 safe registry
  按 framework 选择 AgentScope 或 Spring AI adapter

examples/
  agentscope-safe-mcp
  spring-ai-safe-mcp
```

现有 `fastmcp-core` 应删除或改造成 `fastmcp-safe-core`。
现有 `fastmcp-spring-boot-starter` 应删除原本本地 `FastMcpServer` 注册职责，改为安全注册 starter。

## 统一配置模型

建议配置前缀：

```yaml
fastmcp:
  safe:
    enabled: true
    framework: spring-ai
    mcp:
      servers:
        orders:
          transport: streamable-http
          url: https://orders.example.com/mcp
          headers:
            Authorization: ${ORDERS_MCP_TOKEN}
          timeout: 20s
          session:
            cookies: true
        filesystem:
          transport: stdio
          command: mcp-server-filesystem
          args:
            - --root
            - /data/safe-root
    policies:
      authenticated-user:
        type: allow-when-authenticated
      read-only:
        type: read-only
    tools:
      get_my_orders:
        server: orders
        raw-name: getOrdersByUserId
        description: Get orders for the authenticated user.
        input-schema:
          type: object
          properties:
            status:
              type: string
          required:
            - status
        arguments:
          status: status
        inject:
          userId: currentUserId
          tenantId: currentTenantId
        read-only: true
        policy: authenticated-user
```

### 配置语义

- `framework`: `agentscope`、`spring-ai` 或 `auto`。
- `mcp.servers`: 本库管理的 MCP server 连接定义。
- `tools.<virtualName>`: LLM 可见的 virtual tool 定义。
- `tools.<virtualName>.server`: raw MCP tool 所属 server。
- `tools.<virtualName>.raw-name`: raw MCP `tools/list` 返回的工具名。
- `input-schema`: LLM 可见 schema，只能包含模型允许填写的字段。
- `arguments`: virtual 参数到 raw 参数的普通映射。
- `inject`: raw 参数到 resolver id 的安全注入映射。
- `policy`: 调用 raw tool 前执行的 policy id。
- `read-only`: adapter 可传给底层框架的 hint，但不能替代 policy。

## 核心抽象

### SafeMcpProperties

Spring Boot 绑定对象，对应 `fastmcp.safe.*`。
只负责承载配置，不执行框架逻辑。

### SafeMcpServerSpec

描述 MCP server 连接。

字段：

- `name`
- `transport`
- `url`
- `command`
- `args`
- `headers`
- `timeout`
- `session`

`session.cookies` 用于 streamable-http/SSE 场景，避免 MCP server 依赖 cookie 时出现后续 `tools/list` 或 `tools/call` 找不到 session。

### SafeMcpToolSpec

描述 virtual tool 和 raw tool 的关系。

字段：

- `name`
- `description`
- `inputSchema`
- `serverName`
- `rawName`
- `argumentMappings`
- `injectedArguments`
- `readOnly`
- `concurrencySafe`
- `policyId`

### SafeArgumentResolver

服务端安全参数注入接口。

```java
public interface SafeArgumentResolver {
    Object resolve(SafeToolCallContext context);
}
```

配置中只能引用 resolver id。
禁止在 YAML 中执行 SpEL、JavaScript、OGNL 或任意表达式。

### SafeToolCallContext

框架无关的调用上下文门面。

第一版能力：

- `String userId()`
- `String tenantId()`
- `Object attribute(String name)`
- `<T> T frameworkContext(Class<T> type)`
- `Map<String, Object> toolContext()`

AgentScope adapter 从 `RuntimeContext` 构造。
Spring AI adapter 从 `ToolContext`、Spring Security 或调用选项构造。

### SafeMcpPolicy

调用 raw tool 前的 policy 接口。

```java
public interface SafeMcpPolicy {
    SafePolicyDecision evaluate(SafeToolCallContext context, SafeToolCallRequest request);
}
```

第一阶段支持：

- `allow`
- `deny`
- `allow-when-authenticated`
- `read-only`

后续可扩展：

- `require-approval`
- `tenant-scope`
- `rate-limit`
- `role-based`

### RawToolInvoker

框架无关的 raw MCP tool 调用接口。
safe-core 不依赖 Reactor 或 Spring 类型，异步模型使用 JDK `CompletionStage`。

```java
public interface RawToolInvoker {
    CompletionStage<RawToolResult> callAsync(
            String serverName,
            String rawToolName,
            Map<String, Object> rawArguments);
}
```

AgentScope adapter 可以用 `McpClientWrapper` / internal `McpTool` delegate 实现。
Spring AI adapter 可以用 Spring AI MCP client 或 MCP Java SDK client 实现。

### SafeTool

safe-core 中的执行模型。

流程：

1. 接收 virtual tool call。
2. 校验输入字段不包含 protected raw 参数或 protected virtual 参数。
3. 按 `argumentMappings` 生成 raw arguments。
4. 用 `SafeArgumentResolver` 注入 protected arguments。
5. 执行 `SafeMcpPolicy`。
6. 调用 `RawToolInvoker.callAsync`。
7. 通过 result guard 转换结果。
8. 记录 audit event。

## AgentScope adapter 设计

AgentScope adapter 的职责是把 `SafeTool` 包装成 AgentScope `ToolBase`，并注册到 `Toolkit`。

### 初始化链路

```text
SafeMcpProperties
  -> AgentScopeMcpClientFactory
  -> Map<String, McpClientWrapper>
  -> listTools()
  -> 校验每个 SafeMcpToolSpec.rawName 存在
  -> AgentScopeSafeTool extends ToolBase
  -> toolkit.registerAgentTool(virtualTool)
```

### 调用链路

```text
LLM 调 virtual tool
  -> AgentScope ToolBase.callAsync
  -> SafeTool.call
  -> SafeToolCallContext.from(RuntimeContext)
  -> RawToolInvoker.callAsync
  -> MCP raw tool
  -> ToolResultBlock 使用 virtual name
```

### 必须避免

- 不调用 `toolkit.registerMcpClient(client)` 暴露 raw MCP tools。
- 不使用 raw tool description 作为 virtual description 的默认值，除非配置显式允许。
- 不把 injected arguments 写进 virtual input schema。
- 不把 raw argument JSON 写入模型可见 event。

## Spring AI adapter 设计

Spring AI adapter 的职责是生成安全 `ToolCallback` 和 `ToolCallbackProvider`。

### 初始化链路

```text
SafeMcpProperties
  -> SpringAiMcpClientFactory
  -> raw MCP metadata
  -> 校验 SafeMcpToolSpec.rawName 存在
  -> SafeToolCallback
  -> SafeToolCallbackProvider
```

### 调用链路

```text
ChatClient / ChatModel 使用 SafeToolCallbackProvider
  -> 模型只看到 SafeToolCallback.getToolDefinition()
  -> ToolCallback.call(...)
  -> SafeTool.call
  -> SafeToolCallContext.from(ToolContext)
  -> RawToolInvoker.callAsync
  -> MCP raw tool
```

### Spring AI 关键约束

- 不把 Spring AI MCP starter 自动生成的 raw `ToolCallbackProvider` 暴露给 `ChatClient`。
- 本库可以复用 Spring AI MCP client 的 transport 和 lifecycle，但输出给模型的必须是 `SafeToolCallback`。
- `ToolContext` 可作为注入来源，因为它不是模型可见 tool schema 的一部分。
- 如果用户直接配置了 `spring.ai.mcp.client.toolcallback.enabled=true`，starter 应给出明确警告或提供关闭建议，避免 raw callbacks 与 safe callbacks 同时存在。

## Spring Boot starter 设计

新的 starter 只服务 `fastmcp.safe.*`。

### 自动装配对象

- `SafeMcpProperties`
- `SafeArgumentResolverRegistry`
- `SafeMcpPolicyRegistry`
- `SafeMcpToolRegistry`
- `AgentScopeSafeMcpRegistrar`，当 AgentScope 类存在且 framework 是 `agentscope` 或 `auto`
- `SpringAiSafeMcpToolCallbackProvider`，当 Spring AI 类存在且 framework 是 `spring-ai` 或 `auto`

### framework=auto 规则

- 只有 AgentScope 存在：使用 AgentScope。
- 只有 Spring AI 存在：使用 Spring AI。
- 两者都存在：启动失败，要求用户显式设置 `framework`。
- 两者都不存在：启动失败，提示添加 adapter 依赖。

## 错误处理

启动期错误：

- duplicate virtual tool name
- unknown MCP server
- unsupported transport
- missing raw tool
- unknown resolver id
- unknown policy id
- protected argument appears in virtual schema
- both AgentScope and Spring AI detected with `framework=auto`

运行期错误：

- model supplied protected argument
- resolver failed
- policy denied
- raw tool invocation failed
- result guard rejected output

错误返回给框架时必须使用 virtual tool name。
日志中可以记录 raw tool name，但默认不得记录 injected argument value。

## Audit

第一阶段 audit event 字段：

- timestamp
- framework
- virtualToolName
- rawServerName
- rawToolName
- callerId
- tenantId
- injectedArgumentNames
- policyId
- policyDecision
- success
- errorCode

默认不记录：

- injected argument values
- authorization header
- raw result body
- full prompt

## Result guard

第一阶段只提供接口和默认 no-op 实现。

后续可扩展：

- 最大返回长度
- 结构化字段 allowlist
- 敏感字段脱敏
- tool result prompt injection marker
- 二次 policy 检查

## 测试策略

### safe-core

- 配置绑定和校验。
- raw tool allowlist。
- virtual schema 不包含 protected arguments。
- 模型传 protected argument 时拒绝。
- argument mapping 正确。
- resolver 注入正确。
- policy allow/deny 正确。
- audit 不记录敏感值。

### AgentScope adapter

- Toolkit 注册后只包含 virtual tools。
- raw MCP tool name、description、schema 不出现在 model-facing tools。
- RuntimeContext 注入成功。
- raw tool delegate 被调用。
- ToolResultBlock 使用 virtual name。
- AG-UI/tool event 场景不泄露 raw args，需要用真实或近真实流式测试补充。

### Spring AI adapter

- `ToolCallbackProvider#getToolCallbacks()` 只返回 virtual callbacks。
- `ToolDefinition` 使用 virtual name、description、schema。
- raw Spring AI MCP callbacks 不进入 ChatClient。
- `ToolContext` 注入成功。
- raw tool delegate 被调用。
- streamable-http server 需要验证 cookie/session 保持。

### CI

- 默认 profile：构建 `fastmcp-safe-core`。
- AgentScope profile：JDK 17，跑 AgentScope adapter。
- Spring AI profile：按 Spring AI 版本要求选择 JDK，跑 Spring AI adapter。
- examples profile：跑 AgentScope 和 Spring AI 示例。

## 迁移计划

### Phase 1: 建立 safe-core

- 新建 `fastmcp-safe-core`。
- 定义配置模型、tool spec、resolver、policy、audit、raw invoker 接口。
- 写 core 单元测试。

### Phase 2: 重构 AgentScope adapter

- 把现有 `FastMcpToolMapping` 的通用逻辑下沉到 safe-core。
- AgentScope adapter 只保留框架转换。
- 保留 `registerMcpClient` 安全路径。
- 弱化或删除 raw pass-through API。

### Phase 3: 新增 Spring AI adapter

- 新建 `fastmcp-spring-ai-adapter`。
- 实现 `SafeToolCallback` 和 `SafeToolCallbackProvider`。
- 增加 Spring AI MCP raw delegate 调用测试。

### Phase 4: 重做 Spring Boot starter

- 将 starter 从本地 `FastMcpServer` 自动装配改为 `fastmcp.safe.*` 自动装配。
- 增加 framework auto-detection。
- 增加 raw callback 暴露冲突检测。

### Phase 5: 示例和文档

- README 改为 Safe MCP Registration 主叙事。
- 增加 `README.zh-CN.md` 同步说明。
- 增加 AgentScope 示例。
- 增加 Spring AI 示例。
- 增加安全指南。

### Phase 6: 删除旧叙事

- 删除或废弃本地 `FastMcpServer`、`AnnotatedToolRegistrar`、旧 starter 示例。
- 如果为了兼容保留旧 artifact，必须明确标记 deprecated，不作为主链路推荐。

## 发布策略

初期 artifact 建议：

- `fastmcp-safe-core`
- `fastmcp-agentscope-adapter`
- `fastmcp-spring-ai-adapter`
- `fastmcp-spring-boot-starter`

不建议发布旧 `fastmcp-java` core artifact 作为主入口。
版本仍可从 `0.1.0-SNAPSHOT` 开始，但 README 必须明确是安全注册库，不是完整 MCP server framework。

## 验收标准

### 用户视角

用户只配置 `fastmcp.safe.*`，不直接配置 AgentScope 或 Spring AI 的 MCP client。

### AgentScope

- `Toolkit` 中只存在 configured virtual tools。
- raw MCP tools 不会因本库注册而进入模型可见 schema。
- LLM 不能传 protected arguments。
- `RuntimeContext` 注入的参数能到达 raw MCP tool。

### Spring AI

- `ToolCallbackProvider` 只返回 configured virtual callbacks。
- `ToolDefinition` 不包含 raw metadata。
- `ToolContext` 注入的参数能到达 raw MCP tool。
- raw MCP callback provider 不与 safe provider 同时暴露给模型。

### 安全

- raw name、raw description、raw input schema 默认不出现在模型可见面。
- injected argument value 默认不出现在日志、audit 和 tool event。
- policy deny 时不会调用 raw MCP tool。

## 仍需实施前核对的事实

- 当前锁定的 AgentScope Java 版本中 `McpClientWrapper`、`McpTool`、`Toolkit` 的真实 API。
- 目标 Spring AI 版本线，尤其是 1.1.x 与 2.0.x 的 `ToolCallback`、`ToolCallbackProvider`、MCP client API 差异。
- Spring AI streamable-http transport 的 session/cookie 支持方式。
- 第一版 raw tool invocation 使用 JDK `CompletionStage`；如果具体框架只提供同步 API，由 adapter 在边界内转换。
- artifact 命名是否保持 `fastmcp-*`，还是改成更明确的 `safe-mcp-*`。
