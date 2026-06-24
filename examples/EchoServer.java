import io.github.sandking.fastmcp.FastMcp;
import io.github.sandking.fastmcp.FastMcpServer;
import io.github.sandking.fastmcp.JsonSchemas;
import io.github.sandking.fastmcp.ToolResult;
import java.util.Map;

public final class EchoServer {
    private EchoServer() {
    }

    public static void main(String[] args) {
        var schema = JsonSchemas.object();
        JsonSchemas.addProperty(schema, "text", JsonSchemas.string());
        JsonSchemas.require(schema, "text");

        FastMcpServer server = FastMcp.server("Echo Server")
                .tool("echo", "Echo the input text", schema,
                        arguments -> ToolResult.text(arguments.getString("text")));

        ToolResult result = server.callTool("echo", Map.of("text", "hello"));
        System.out.println(result.content());
    }
}
