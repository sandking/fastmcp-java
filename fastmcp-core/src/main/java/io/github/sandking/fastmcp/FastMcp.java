package io.github.sandking.fastmcp;

/**
 * Entry point for creating FastMCP-style Java servers.
 */
public final class FastMcp {
    private FastMcp() {
    }

    public static FastMcpServer server(String name) {
        return new FastMcpServer(name);
    }
}
