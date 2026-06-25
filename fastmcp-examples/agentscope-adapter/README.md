# FastMCP AgentScope Adapter Example

This example demonstrates safe registration into an AgentScope `Toolkit`.

```text
raw AgentScope/backend tool: getOrdersByUserId(userId, tenantId, orderStatus, limit)
  -> FastMcpToolMapping
  -> virtual tool visible to the model: get_my_orders(status, limit)
  -> userId and tenantId injected from RuntimeContext
  -> raw tool called only as an internal delegate
```

It covers:

- model-visible tool name and schema are virtual
- `status` is mapped to the raw `orderStatus` argument
- `userId` and `tenantId` are injected from server-side `RuntimeContext`
- model-supplied protected arguments are rejected before the raw tool is called

Run it from the repository root with JDK 17 or newer:

```bash
mvn -Pexamples -pl fastmcp-examples/agentscope-adapter -am test
```

Run all examples:

```bash
mvn -Pexamples test
```

This example uses an in-process raw backend tool so the behavior is deterministic.
It does not start a real MCP server or create an MCP client from FastMCP
configuration.
