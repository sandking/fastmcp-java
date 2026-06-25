Always respond in Chinese-simplified

# fastmcp-java 项目级规则

本文件只记录 `sandking/fastmcp-java` 的仓库专属事实、边界和验证方式。通用执行纪律遵守 `~/.codex/AGENTS.global.md`；Spring Boot 相关改动同时遵守 `~/.codex/AGENTS.springboot.md`。如果 SessionStart 已注入 Spring Boot 专项规则，仍以本文件补充当前仓库事实。

## 1. 当前工程事实

- 这是 Maven 多模块 Java 仓库，根 `pom.xml` 默认只包含：
  - `fastmcp-safe-core`
  - `fastmcp-safe-config`
  - `fastmcp-agentscope-adapter`
  - `fastmcp-spring-ai-adapter`
  - `fastmcp-spring-ai-boot-starter`
- `examples/agentscope-adapter` 只在 `examples` profile 下启用，并且 example modules 跳过 deploy。
- 完整默认 reactor 需要 JDK 17；`fastmcp-safe-core` 和 `fastmcp-safe-config` 仍以 Java 11 release 编译，`fastmcp-agentscope-adapter`、`fastmcp-spring-ai-adapter`、`fastmcp-spring-ai-boot-starter` 和 `examples/agentscope-adapter` 使用 Java 17，因为 AgentScope Java 2.x、Spring AI 2.x 和 Spring Boot 4.x 需要 JDK 17+。
- Spring AI Boot starter 使用独立的 `spring-ai-boot.version=4.0.7`，Spring AI adapter 使用 `spring-ai.version=2.0.0`。不要未经确认升级 Spring Boot、Spring AI、AgentScope、Maven 插件或 JDK release。
- 当前仓库的主目标已调整为 Safe MCP Registration：安全地把 MCP raw tools 注册到 Java Agent 框架中，默认只让 LLM 看到 virtual tools。
- 当前仓库已经实现第一版 Spring AI adapter、framework-neutral safe config model、Spring AI Boot `fastmcp.safe.*` 配置绑定和基于已有 raw `ToolCallbackProvider` 的安全 provider 包装；但还没有实现基于本库配置自动创建 MCP client、AgentScope Spring Boot auto-configuration、MCP HTTP/SSE transport endpoint、resources、prompts、client implementation、authentication、lifecycle、production middleware、protocol conformance tests。不要在文档或汇报里暗示这些能力已经具备。

## 2. 模块职责边界

- `fastmcp-safe-core`：框架无关的 Safe MCP Registration 核心，负责 virtual tool spec、virtual-to-raw argument mapping、protected argument injection、policy、raw invoker 和 audit event。
- `fastmcp-safe-config`：框架无关的 Safe MCP Registration 配置模型，负责描述 MCP server、virtual tool、argument mapping 和 protected argument source name。它只做不可变配置表达和校验，不创建 MCP client，不绑定 Spring Boot properties。
- `fastmcp-agentscope-adapter`：AgentScope Toolkit adapter，核心目标是把 raw backend tool 或 raw AgentScope/MCP tool 包装成模型可见的安全虚拟工具；通用 mapping/injection/policy 行为应委托 `fastmcp-safe-core`。
- `fastmcp-spring-ai-adapter`：Spring AI `ToolCallback` adapter，核心目标是把 raw Spring AI `ToolCallback` / `ToolCallbackProvider` 包装成模型可见的安全虚拟 callback；通用 mapping/injection/policy 行为应委托 `fastmcp-safe-core`。当前它只包装已有 callback，不负责创建 MCP client。
- `fastmcp-spring-ai-boot-starter`：Spring Boot 4.x + Spring AI 2.x auto-configuration，负责绑定 `fastmcp.safe.*`、创建 `SafeMcpConfiguration`、把配置转换为 Spring AI mapping，并包装已有 raw `ToolCallbackProvider` bean 为 primary 的 `fastMcpSafeToolCallbackProvider`。当前它不创建 MCP client；如果应用把所有 raw provider bean 直接交给模型，仍可能绕过安全 provider，后续 MCP client 创建阶段需要隐藏 raw provider。
- `examples/*`：只作为可编译、可测试示例，不应把 example 中的演示假设反向写成 library API 事实。

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
  - `fastmcp.safe.servers.<server>.tools.<rawTool>.name`、`description`、`input-schema` 必须是模型可见的 virtual metadata
  - `argument-mappings` 只做普通业务字段映射
  - `injected-arguments` 的值只能是 resolver bean name，例如 `currentUserId`
  - 真实敏感值必须由 `SpringAiToolArgumentResolver` 从 `ToolContext` 解析，不能写入配置
  - auto-configuration 发布的 `fastMcpSafeToolCallbackProvider` 是 primary，但 raw provider bean 仍然可能存在；应用侧不要把 raw provider 集合直接交给模型

## 5. AgentScope 依赖核对规则

- 当前 adapter 依赖 `io.agentscope:agentscope-core:2.0.0-RC3`。涉及 `Toolkit`、`ToolBase`、`McpTool`、`McpClientWrapper`、`RuntimeContext`、permission、AG-UI 行为时，优先核对当前依赖字节码或源码，再对照官方文档。
- AgentScope 官方文档可能描述更新版本行为；如果文档与当前依赖不一致，以当前 POM 锁定版本和实际字节码为准，并在汇报中说明差异。
- MCP tool 的 raw name 兼容两类来源：
  - MCP `tools/list` 返回的原始 `tool.name()`
  - AgentScope 风格的 `mcp__<clientName>__<toolName>`
- 不要只看 README 断言实现状态；必须核对当前 Java 源码和测试。

## 6. Spring AI 依赖核对规则

- 当前 Spring AI adapter 依赖 `org.springframework.ai:spring-ai-model:2.0.0`，模块 release 为 Java 17。
- Spring AI 2.x 依赖 Spring Framework 7.x 等较新版本线；Spring AI Boot starter 使用 Spring Boot 4.0.7。不要让其他 Spring Boot BOM 在根 POM 全局生效，否则可能把 Spring AI / Boot 4 transitive dependencies 降到不兼容版本。
- 涉及 `ToolCallback`、`ToolCallbackProvider`、`ToolDefinition`、`ToolContext` 或 Spring AI MCP callback 行为时，优先核对当前依赖字节码或源码，再对照官方文档。
- 当前 Spring AI adapter 通过反射兼容带 `getOriginalToolName()` 的 MCP callback；mapping raw name 可以是 `ToolDefinition.name()`，也可以是 MCP callback 暴露的 original tool name。
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
