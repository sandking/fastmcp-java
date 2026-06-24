package io.github.sandking.fastmcp.spring;

import io.github.sandking.fastmcp.FastMcpServer;

@FunctionalInterface
public interface FastMcpToolCustomizer {
    void customize(FastMcpServer server);
}
