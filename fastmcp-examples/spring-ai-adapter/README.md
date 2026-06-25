# FastMCP Spring AI Adapter Example

This example demonstrates manual wrapping of an existing raw Spring AI
`ToolCallbackProvider`.

```text
raw Spring AI callback: getOrdersByUserId(userId, tenantId, orderStatus, limit)
  -> FastMcpSpringAiTools.wrap(...)
  -> virtual callback visible to the model: get_my_orders(status, limit)
  -> userId and tenantId injected from ToolContext
  -> raw callback called only as an internal delegate
```

It covers:

- the model receives only the virtual `ToolCallback`
- `status` is mapped to the raw `orderStatus` argument
- `userId` and `tenantId` are injected from Spring AI `ToolContext`
- model-supplied protected arguments are rejected before the raw callback is called

Run it from the repository root with JDK 17 or newer:

```bash
mvn -Pexamples -pl fastmcp-examples/spring-ai-adapter -am test
```

This example assumes the raw callback/provider already exists. It does not
create MCP clients from FastMCP configuration.
