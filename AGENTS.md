Always respond in Chinese-simplified

# fastmcp-java 项目级规则

本文件只记录 `sandking/fastmcp-java` 的仓库专属事实、边界和验证方式。通用执行纪律遵守 `~/.codex/AGENTS.global.md`；Spring Boot 相关改动同时遵守 `~/.codex/AGENTS.springboot.md`。如果 SessionStart 已注入 Spring Boot 专项规则，仍以本文件补充当前仓库事实。

## 1. 当前工程事实

- 这是 Maven 多模块 Java 仓库，根 `pom.xml` 默认只包含：
  - `fastmcp-core`
  - `fastmcp-spring-boot-starter`
- `fastmcp-agentscope-adapter` 不在默认 modules 中，只在 `agentscope` profile 下启用。
- `examples/core`、`examples/agentscope-adapter` 只在 `examples` profile 下启用，并且 example modules 跳过 deploy。
- 默认编译 release 是 Java 11；`fastmcp-agentscope-adapter` 和 `examples/agentscope-adapter` 使用 Java 17，因为 AgentScope Java 2.x 需要 JDK 17+。
- 当前 Spring Boot 版本来自根 POM 的 `spring-boot.version=2.7.18`，不要未经确认升级 Spring Boot、AgentScope、Maven 插件或 JDK release。
- 当前仓库还没有实现 MCP HTTP/SSE transport endpoint、resources、prompts、client implementation、authentication、lifecycle、production middleware、protocol conformance tests。不要在文档或汇报里暗示这些能力已经具备。

## 2. 模块职责边界

- `fastmcp-core`：无 Spring 依赖的最小 FastMCP Java core，负责工具注册、JSON Schema metadata、内存态 `listTools` / `callTool`、注解注册和 `ToolResult` 表达。
- `fastmcp-spring-boot-starter`：Spring Boot auto-configuration，只创建并填充 `FastMcpServer` bean，扫描 Spring bean 上的 `@McpTool` 方法；当前不发布 MCP transport endpoint。
- `fastmcp-agentscope-adapter`：AgentScope Toolkit adapter，核心目标是把 raw FastMCP tool 或 raw AgentScope/MCP tool 包装成模型可见的安全虚拟工具。
- `examples/*`：只作为可编译、可测试示例，不应把 example 中的演示假设反向写成 library API 事实。

## 3. AgentScope / MCP 安全目标

- 本仓库 AgentScope adapter 的首要目标是避免把 raw backend tool、raw FastMCP tool 或 raw AgentScope MCP tool 直接裸露给 LLM。
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
- `FastMcpAgentScopeTools.register(toolkit, server, mappings)` 是包装 raw FastMCP tool 的安全入口。
- `FastMcpAgentScopeTools.register(toolkit, rawAgentTool, mapping)` 是包装 raw AgentScope tool 的安全入口。
- `FastMcpAgentScopeTools.registerMcpClient(toolkit, client, mappings)` 是包装 AgentScope `McpClientWrapper` 的安全入口；它可以让 AgentScope 继续处理 MCP 协议细节，但只向传入的 `Toolkit` 注册 mapped virtual tools。
- 不要在安全场景中直接使用 `toolkit.registerMcpClient(client)` 把 MCP tools 全量注入模型可见 Toolkit，除非本轮任务明确需要复现或对比 AgentScope 原生行为。
- 不要把 `FastMcpAgentScopeTools.register(toolkit, server)` 当作安全入口；该无 mapping 重载会按 raw tool 的原名、原 description 和原 schema 生成工具，适合基础适配或测试，不适合解决 raw tool 暴露问题。
- 修改 adapter 时必须保留并强化以下行为：
  - 模型只能看到 virtual tool
  - virtual schema 不包含 injected protected arguments
  - 模型显式传入 protected argument 时拒绝执行
  - raw tool 只作为内部 delegate 被调用
  - 返回给 AgentScope 的 tool result name 使用 virtual tool name

## 5. AgentScope 依赖核对规则

- 当前 adapter 依赖 `io.agentscope:agentscope-core:2.0.0-RC3`。涉及 `Toolkit`、`ToolBase`、`McpTool`、`McpClientWrapper`、`RuntimeContext`、permission、AG-UI 行为时，优先核对当前依赖字节码或源码，再对照官方文档。
- AgentScope 官方文档可能描述更新版本行为；如果文档与当前依赖不一致，以当前 POM 锁定版本和实际字节码为准，并在汇报中说明差异。
- MCP tool 的 raw name 兼容两类来源：
  - MCP `tools/list` 返回的原始 `tool.name()`
  - AgentScope 风格的 `mcp__<clientName>__<toolName>`
- 不要只看 README 断言实现状态；必须核对当前 Java 源码和测试。

## 6. 验证命令

- 默认 core + Spring Boot starter 验证：

```bash
mvn test
```

- AgentScope adapter 改动必须使用 JDK 17+ 执行：

```bash
mvn -Pagentscope test
```

- example 或 README 示例链路改动需要执行：

```bash
mvn -Pexamples test
```

- 如果本机默认 Maven 使用 Java 11，运行 `-Pagentscope` 或 `-Pexamples` 可能出现 class file version 61 / 55 之类错误；这属于 JDK 运行环境不匹配，应切到 JDK 17+ 后复跑，不要把它误判为 adapter 测试失败。
- CI 当前分两条验证：Java 11 跑 `mvn -B test`，Java 17 跑 `mvn -B -Pagentscope test` 和 `mvn -B -Pexamples test`。本地汇报要说明实际使用的 JDK、profile 和命令。

## 7. 汇报要求补充

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
