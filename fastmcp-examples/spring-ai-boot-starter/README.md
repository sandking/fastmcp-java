# FastMCP Spring AI Boot Starter Example

This example demonstrates the Spring Boot auto-configuration path.

```text
existing raw ToolCallbackProvider bean
  + fastmcp.safe.* mapping properties
  + SpringAiToolArgumentResolver beans
  -> fastMcpSafeToolCallbackProvider primary bean
  -> virtual callback visible to the model: get_my_orders(status, limit)
```

It covers:

- `fastmcp.safe.*` binding into `SafeMcpConfiguration`
- resolver bean names such as `currentUserId` and `currentTenantId`
- a primary safe `ToolCallbackProvider` published by the starter
- raw provider remaining present but not being the provider selected by type
- model-supplied protected arguments rejected before the raw provider is called

Run it from the repository root with JDK 17 or newer:

```bash
mvn -Pexamples -pl fastmcp-examples/spring-ai-boot-starter -am test
```

The starter currently wraps existing raw Spring AI providers. It does not create
MCP clients from `fastmcp.safe.*` server metadata yet.
