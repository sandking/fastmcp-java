import io.github.sandking.fastmcp.AnnotatedToolRegistrar;
import io.github.sandking.fastmcp.FastMcp;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.McpTool;
import io.github.sandking.fastmcp.ToolParam;
import io.github.sandking.fastmcp.ToolResult;
import java.util.Map;

public final class AnnotatedService {
    private AnnotatedService() {
    }

    public static void main(String[] args) {
        FastMcpServer server = FastMcp.server("Annotated Demo");
        AnnotatedToolRegistrar.register(server, new GreetingService());

        ToolResult result = server.callTool("greet", Map.of("name", "Ada"));
        System.out.println(result.content());
    }

    static final class GreetingService {
        @McpTool(name = "greet", description = "Create a greeting")
        String greet(@ToolParam(description = "Name to greet") String name) {
            return "Hello " + name + "!";
        }
    }
}
