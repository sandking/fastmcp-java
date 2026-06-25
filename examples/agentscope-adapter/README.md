# FastMCP AgentScope Adapter Example

This example validates the safe virtual tool path:

```text
raw AgentScope/backend tool with userId
  -> virtual AgentScope tool without userId
  -> userId injected from server-side RuntimeContext
  -> delegate back to the raw tool
```

The goal is to avoid exposing identity-sensitive arguments such as `userId`,
`memberId`, or `tenantId` to the model.

Run it from the repository root with JDK 17 or newer:

```bash
mvn -Pexamples test
```

The adapter implementation lives in `fastmcp-agentscope-adapter`. The example is
only a runnable usage check and is skipped during Maven deploy.
