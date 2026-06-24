# FastMCP AgentScope Adapter Example

This example validates that FastMCP Java tools can be registered into an
AgentScope `Toolkit` without adding AgentScope as a dependency of `fastmcp-core`
or `fastmcp-spring-boot-starter`.

Run it from the repository root with JDK 17 or newer:

```bash
mvn -Pexamples test
```

The adapter is intentionally local to this example. If the integration shape
stays useful, it can later be promoted to a separate optional module.
