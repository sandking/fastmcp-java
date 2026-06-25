Always respond in Chinese-simplified

# fastmcp-java 项目级规则

本文件只记录 `sandking/fastmcp-java` 的仓库专属事实、边界和验证方式。通用执行纪律遵守 `~/.codex/AGENTS.global.md`；Spring Boot 相关改动同时遵守 `~/.codex/AGENTS.springboot.md`。如果 SessionStart 已注入 Spring Boot 专项规则，仍以本文件补充当前仓库事实。

## 1. 当前工程事实

- 这是 Maven 多模块 Java 仓库，根 `pom.xml` 默认只包含：
  - `fastmcp-safe-core`
  - `fastmcp-safe-config`
  - `fastmcp-safe-spring-boot-autoconfigure-support`
  - `fastmcp-agentscope-adapter`
  - `fastmcp-agentscope-boot-starter`
  - `fastmcp-spring-ai-adapter`
  - `fastmcp-spring-ai-boot-starter`
- `fastmcp-examples` 是只在 `examples` profile 下启用的示例聚合模块，包含 `fastmcp-examples/agentscope-adapter`、`fastmcp-examples/spring-ai-adapter` 和 `fastmcp-examples/spring-ai-boot-starter`，并且 example modules 跳过 deploy。
- 完整默认 reactor 需要 JDK 17；`fastmcp-safe-core` 和 `fastmcp-safe-config` 仍以 Java 11 release 编译，`fastmcp-safe-spring-boot-autoconfigure-support`、`fastmcp-agentscope-adapter`、`fastmcp-agentscope-boot-starter`、`fastmcp-spring-ai-adapter`、`fastmcp-spring-ai-boot-starter` 和 `fastmcp-examples/*` 使用 Java 17，因为 AgentScope Java 2.x、Spring AI 2.x 和 Spring Boot 4.x 需要 JDK 17+。
- Spring AI Boot starter 使用独立的 `spring-ai-boot.version=4.0.7`，Spring AI adapter 使用 `spring-ai.version=2.0.0`。不要未经确认升级 Spring Boot、Spring AI、AgentScope、Maven 插件或 JDK release。
- 当前仓库的主目标已调整为 Safe MCP Registration：安全地把 MCP raw tools 注册到 Java Agent 框架中，默认只让 LLM 看到 virtual tools。
- 最关键的工程边界：目标不是做完整 FastMCP server/client 框架，而是让本库成为多个 Java Agent 框架上的安全 MCP 注册入口，确保 LLM 只看到 virtual tools。
- MCP client、transport、protocol lifecycle、resources、prompts、stream 等协议能力优先复用目标框架或其底层官方 SDK，例如 Spring AI / MCP Java SDK、AgentScope Java。只有为了让安全注册入口可用且框架缺少必要 glue 时，才允许在 starter 内增加最小兼容代码；这类代码必须保持 package-private、测试覆盖清楚、不得扩展成通用 MCP client/server API。
- 当前仓库已经实现第一版 Spring AI adapter、AgentScope adapter、framework-neutral safe config model、中性的 Spring Boot `fastmcp.safe.*` 绑定支持、Spring AI Boot 基于已有 raw `ToolCallbackProvider` 的安全 provider 包装、Spring AI Boot 基于 `fastmcp.safe.*` 创建 managed MCP clients 的第一版能力，以及 AgentScope Boot 基于 `fastmcp.safe.*` 创建 managed `McpClientWrapper` 并向 `Toolkit` 注册 virtual tools 的第一版能力。managed client 当前支持 `streamable-http`、`sse` 和兼容用的 `stdio`，支持通过 `fastmcp.safe.*` 透传 HTTP headers、query params 和 cookie 开关，会在 starter 内部创建 raw MCP client/callback 并只公开安全注册结果。还没有实现本库自己的 MCP server implementation、AG-UI SSE / tool event 链路验证、resources、prompts、完整 authentication、OAuth、secret resolver / secret lifecycle、production middleware、protocol conformance tests。不要在文档或汇报里暗示这些未验证或未实现能力已经具备。

## 2. 模块职责边界

- `fastmcp-safe-core`：框架无关的 Safe MCP Registration 核心，负责 virtual tool spec、virtual-to-raw argument mapping、protected argument injection、policy、raw invoker 和 audit event。
- `fastmcp-safe-config`：框架无关的 Safe MCP Registration 配置模型，负责描述 MCP server、HTTP 连接配置、virtual tool、argument mapping 和 protected argument source name。它只做不可变配置表达和校验，不创建 MCP client，不绑定 Spring Boot properties。
- `fastmcp-safe-spring-boot-autoconfigure-support`：框架 adapter 无关的 Spring Boot 4.x 配置绑定支持，负责 `fastmcp.safe.*` 的 `@ConfigurationProperties` 模型和 `SafeMcpConfiguration` factory。它不创建 MCP client，不包装 AgentScope 或 Spring AI 工具，也不是应用侧直接使用的 starter。
- `fastmcp-agentscope-adapter`：AgentScope Toolkit adapter，核心目标是把 raw backend tool 或 raw AgentScope/MCP tool 包装成模型可见的安全虚拟工具；通用 mapping/injection/policy 行为应委托 `fastmcp-safe-core`。
- `fastmcp-agentscope-boot-starter`：Spring Boot 4.x + AgentScope Java 2.x auto-configuration，复用 `fastmcp-safe-spring-boot-autoconfigure-support` 绑定 `fastmcp.safe.*` 并创建 `SafeMcpConfiguration`，把配置转换为 AgentScope mapping。它可以基于配置创建 managed AgentScope `McpClientWrapper`，支持 `streamable-http`、`sse` 和兼容用的 `stdio`，并把 managed raw MCP clients 留在 auto-configuration 内部，只向应用提供的 `Toolkit` 注册 virtual tools。MCP client 和 transport 行为应委托 AgentScope Java / MCP Java SDK；starter 只负责配置 glue、session/cookie 等必要 HTTP client 定制、安全包装和 raw client 隐藏。它不创建 AgentScope agent/model，也不拥有 `Toolkit` 生命周期；应用或 AgentScope 自身 auto-configuration 需要提供 `Toolkit` bean。
- `fastmcp-spring-ai-adapter`：Spring AI `ToolCallback` adapter，核心目标是把 raw Spring AI `ToolCallback` / `ToolCallbackProvider` 包装成模型可见的安全虚拟 callback；通用 mapping/injection/policy 行为应委托 `fastmcp-safe-core`。当前它只包装已有 callback，不负责创建 MCP client。
- `fastmcp-spring-ai-boot-starter`：Spring Boot 4.x + Spring AI 2.x auto-configuration，复用 `fastmcp-safe-spring-boot-autoconfigure-support` 绑定 `fastmcp.safe.*` 并创建 `SafeMcpConfiguration`，把配置转换为 Spring AI mapping。它可以基于配置创建 managed Spring AI `McpSyncClient`，支持 `streamable-http`、`sse` 和兼容用的 `stdio`，并把 managed raw MCP callbacks 留在 auto-configuration 内部，只发布 primary 的 `fastMcpSafeToolCallbackProvider`。MCP client 和 transport 行为应优先委托 Spring AI / MCP Java SDK；starter 只负责配置 glue、session/cookie 等必要 HTTP client 定制、安全包装和 raw provider 隐藏。它仍兼容外部 raw `ToolCallbackProvider` bean；如果应用自行把这些外部 raw provider bean 直接交给模型，仍可能绕过安全 provider。
- `fastmcp-examples/*`：只作为可编译、可测试示例，不应把 example 中的演示假设反向写成 library API 事实。

## 3. AgentScope / MCP 安全目标

- 本仓库 AgentScope adapter 的首要目标是避免把 raw backend tool 或 raw AgentScope MCP tool 直接裸露给 LLM。
- Prompt 不是安全边界。模型输出只能视为 tool call 建议，不能作为授权、身份、租户或资源归属依据。
- 安全关键字段必须来自服务端确定性上下文，例如 `RuntimeContext` 中的用户、租户、权限或业务上下文；不要让模型通过 JSON 参数传 `userId`、`tenantId`、`memberId`、`role`、`includeDeleted` 等安全关键字段。
- 工具名、description、input schema 都默认会被模型看到；不要把内部系统名、数据库表名、生产环境标识、权限模型、后端接口路径或敏感参数暴露在模型可见 metadata 中。
- 如果涉及 AG-UI、CopilotKit、frontend tools 或 streaming tool events，还要检查 tool name、tool args、tool result、metadata、日志和前端事件是否泄露 raw tool 名称、注入后的安全字段或后端原始返回。

## 4. 安全注册入口

- 面向模型的安全路径必须显式使用 `FastMcpToolMapping`：
  - 设置模型可见的虚拟 `name` 和 `description`
  - 提供只包含模型可填业务字段的虚拟 `inputSchema`
  - 使用 `mapArgument` 做普通业务字段映射
  - 使用 `injectArgument` 注入受保护 raw 参数
- `FastMcpAgentScopeTools.register(toolkit, rawAgentTool, mapping)` 是包装 raw AgentScope tool 的安全入口。
- `FastMcpAgentScopeTools.registerMcpClient(toolkit, client, mappings)` 是包装 AgentScope `McpClientWrapper` 的安全入口；它可以让 AgentScope 继续处理 MCP 协议细节，但只向传入的 `Toolkit` 注册 mapped virtual tools。
- 不要在安全场景中直接使用 `toolkit.registerMcpClient(client)` 把 MCP tools 全量注入模型可见 Toolkit，除非本轮任务明确需要复现或对比 AgentScope 原生行为。
- AgentScope Boot starter 的安全入口是 `fastmcp.safe.*` 配置加 `ToolArgumentResolver` bean：
  - 服务端 Spring Boot 部署优先使用 `transport=streamable-http` 加 `endpoint`，或 `transport=sse` 加 `endpoint` / `sse-endpoint`；`stdio` 只作为兼容路径保留
  - `fastmcp.safe.servers.<server>.tools.<rawTool>.name`、`description`、`input-schema` 必须是模型可见的 virtual metadata
  - `argument-mappings` 只做普通业务字段映射
  - `injected-arguments` 的值只能是 resolver bean name，例如 `currentMemberId`
  - 真实敏感值必须由 `ToolArgumentResolver` 从 AgentScope `ToolCallParam` / `RuntimeContext` 解析，不能写入配置
  - `http.headers` 和 `http.query-params` 只作为 MCP client HTTP 连接配置透传给 AgentScope builder，不属于模型可见 metadata；如有敏感值，使用 Spring Boot 占位符或部署环境注入，本库不提供 secret resolver
  - `http.cookies.enabled` 默认是 `true`，用于 streamable-http / SSE 的会话粘性；关闭前必须确认目标 MCP server 不依赖 cookie session
  - auto-configuration 创建的 managed raw `McpClientWrapper` 不作为 Spring bean 暴露；它只把 mapped virtual tools 注册进应用提供的 `Toolkit`
  - 如果应用自己调用 `toolkit.registerMcpClient(client)`，仍然会绕过本库安全入口
- 修改 adapter 时必须保留并强化以下行为：
  - 模型只能看到 virtual tool
  - virtual schema 不包含 injected protected arguments
  - 模型显式传入 protected argument 时拒绝执行
  - raw tool 只作为内部 delegate 被调用
  - 返回给 AgentScope 的 tool result name 使用 virtual tool name
- 不要在 AgentScope adapter 中重新实现通用 protected-argument mapping/injection/policy 逻辑；这些逻辑属于 `fastmcp-safe-core`。
- 如果 adapter 从 `fastmcp-safe-config` 构造 mapping，配置里的 injected argument value 只能是 resolver source name，例如 `currentUserId`；真实 `userId`、`tenantId` 等敏感值必须继续由 adapter 的 resolver registry 从运行时上下文解析，不能写入配置文件。
- Spring AI 安全入口必须显式使用 `SpringAiMcpToolMapping` 和 `FastMcpSpringAiTools.wrap(...)`：
  - 设置模型可见的虚拟 `name` 和 `description`
  - 提供只包含模型可填业务字段的虚拟 `inputSchema`
  - 使用 `mapArgument` 做普通业务字段映射
  - 使用 `injectArgument` 从 Spring AI `ToolContext` 注入受保护 raw 参数
- 不要在安全场景中直接把 raw Spring AI MCP `ToolCallbackProvider` 交给模型，除非本轮任务明确需要复现或对比 Spring AI 原生行为。
- 修改 Spring AI adapter 时必须保留并强化以下行为：
  - 模型只能看到 virtual `ToolCallback`
  - virtual schema 不包含 injected protected arguments
  - 模型显式传入 protected argument 时拒绝执行
  - raw callback 只作为内部 delegate 被调用
  - raw callback 调用时继续接收原始 Spring AI `ToolContext`
- 不要在 Spring AI adapter 中重新实现通用 protected-argument mapping/injection/policy 逻辑；这些逻辑属于 `fastmcp-safe-core`。
- Spring AI Boot starter 的安全入口是 `fastmcp.safe.*` 配置加 resolver bean：
  - 服务端 Spring Boot 部署优先使用 `transport=streamable-http` 加 `endpoint`，或 `transport=sse` 加 `endpoint` / `sse-endpoint`；`stdio` 只作为兼容路径保留
  - `fastmcp.safe.servers.<server>.enabled=false` 只阻止 starter 为该 server 创建 managed MCP client；tool mapping 仍可用于包装应用自己提供的外部 raw `ToolCallbackProvider`
  - `fastmcp.safe.servers.<server>.tools.<rawTool>.name`、`description`、`input-schema` 必须是模型可见的 virtual metadata
  - `argument-mappings` 只做普通业务字段映射
  - `injected-arguments` 的值只能是 resolver bean name，例如 `currentUserId`
  - 真实敏感值必须由 `SpringAiToolArgumentResolver` 从 `ToolContext` 解析，不能写入配置
  - `http.headers` 和 `http.query-params` 只作为 MCP client HTTP 连接配置透传给 Spring AI / MCP Java SDK transport，不属于模型可见 metadata；如有敏感值，使用 Spring Boot 占位符或部署环境注入，本库不提供 secret resolver
  - `http.cookies.enabled` 默认是 `true`，用于 streamable-http / SSE 的会话粘性；关闭前必须确认目标 MCP server 不依赖 cookie session
  - auto-configuration 创建的 managed raw provider 不作为 Spring bean 暴露；发布的 `fastMcpSafeToolCallbackProvider` 是 primary
  - 外部 raw provider bean 仍然可能存在；应用侧不要把 raw provider 集合直接交给模型

## 5. AgentScope 依赖核对规则

- 当前 adapter 依赖 `io.agentscope:agentscope-core:2.0.0-RC3`。涉及 `Toolkit`、`ToolBase`、`McpTool`、`McpClientWrapper`、`RuntimeContext`、permission、AG-UI 行为时，优先核对当前依赖字节码或源码，再对照官方文档。
- 当前 AgentScope Boot starter 不依赖或包装 AgentScope Spring Boot starter 的 model/agent 配置；它只要求 Spring context 中存在 `Toolkit` bean，然后基于 `fastmcp.safe.*` 创建内部 managed `McpClientWrapper` 并注册 virtual tools。
- AgentScope `McpClientBuilder` 已提供 `streamableHttpTransport`、`sseTransport`、`stdioTransport` 和 HTTP client customizer。不要在本仓库实现自有 AgentScope MCP transport；如需 session/cookie 支持，只能通过 builder 暴露的 HTTP client customizer 做最小配置。
- AgentScope 官方文档可能描述更新版本行为；如果文档与当前依赖不一致，以当前 POM 锁定版本和实际字节码为准，并在汇报中说明差异。
- MCP tool 的 raw name 兼容两类来源：
  - MCP `tools/list` 返回的原始 `tool.name()`
  - AgentScope 风格的 `mcp__<clientName>__<toolName>`
- 不要只看 README 断言实现状态；必须核对当前 Java 源码和测试。

## 6. Spring AI 依赖核对规则

- 当前 Spring AI adapter 依赖 `org.springframework.ai:spring-ai-model:2.0.0`，模块 release 为 Java 17。Spring AI Boot starter 额外依赖 `org.springframework.ai:spring-ai-mcp:2.0.0` 来创建 managed MCP clients。
- Spring AI 2.x 依赖 Spring Framework 7.x 等较新版本线；Spring AI Boot starter 使用 Spring Boot 4.0.7。不要让其他 Spring Boot BOM 在根 POM 全局生效，否则可能把 Spring AI / Boot 4 transitive dependencies 降到不兼容版本。
- 涉及 `ToolCallback`、`ToolCallbackProvider`、`ToolDefinition`、`ToolContext` 或 Spring AI MCP callback 行为时，优先核对当前依赖字节码或源码，再对照官方文档。
- 当前 Spring AI adapter 通过反射兼容带 `getOriginalToolName()` 的 MCP callback；mapping raw name 可以是 `ToolDefinition.name()`，也可以是 MCP callback 暴露的 original tool name。
- 当前根 POM 的 `jackson.version` 仍是 `2.17.2`，但 `jackson.annotations.version` 单独固定为 `2.21`，这是为了满足 MCP SDK 2.0.0 的 Jackson3 transport 初始化路径对 `JsonSerializeAs` 的运行时需求；不要未经验证把 `jackson-annotations` 降回 `2.17.x` 或其它不包含该类的版本。Spring AI Boot starter 也显式依赖 `com.fasterxml.jackson.core:jackson-annotations` 来表达这个 runtime requirement。
- 不要把 README 或计划文档当作唯一事实源；必须核对当前 Java 源码、POM 和测试。

## 7. 验证命令

- 默认 full reactor 验证，必须使用 JDK 17+：

```bash
mvn test
```

- 如果只需要核对 Java 11 兼容的 safe-core / safe-config，可执行：

```bash
mvn -pl fastmcp-safe-core,fastmcp-safe-config -am test
```

- example 或 README 示例链路改动需要执行：

```bash
mvn -Pexamples test
```

- 如果本机默认 Maven 使用 Java 11，运行 `mvn test` 或 `mvn -Pexamples test` 可能出现 class file version 61 / 55、`不支持发行版本 17` 之类错误；这属于 JDK 运行环境不匹配，应切到 JDK 17+ 后复跑，不要把它误判为 adapter 测试失败。
- CI 当前分两条验证：Java 11 跑 `mvn -B -pl fastmcp-safe-core,fastmcp-safe-config -am test`，覆盖 Java 11 兼容模块；Java 17 跑 `mvn -B test` 和 `mvn -B -Pexamples test`。本地汇报要说明实际使用的 JDK、profile 和命令。
- 不要在仓库内提交真实公司域名、真实 MCP endpoint、真实业务 tool 名称或真实业务 smoke test。需要联调真实 MCP server 时，应在本地临时命令、私有环境变量或不提交的外部脚本中完成。

## 8. 汇报要求补充

- 代码或行为改动后，按影响范围说明：
  - 修改了哪些 module
  - 使用了哪个 JDK 和 Maven profile
  - 跑了哪些测试命令
  - 是否只完成命令行验证，是否未验证 IDE 编译
- 纯文档或规则改动不强制跑 Maven；但必须核对文件路径、模块名称、profile、JDK 版本和术语是否与当前仓库一致。
- 安全相关结论要区分：
  - 已由源码和测试验证的事实
  - 由 AgentScope 官方文档支持的语义
  - 仍需真实 MCP server、AG-UI SSE、前端事件或生产日志验证的风险点
