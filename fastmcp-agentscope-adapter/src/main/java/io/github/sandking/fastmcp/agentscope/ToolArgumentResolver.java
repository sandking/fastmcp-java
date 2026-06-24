package io.github.sandking.fastmcp.agentscope;

import io.agentscope.core.tool.ToolCallParam;

@FunctionalInterface
public interface ToolArgumentResolver {
    Object resolve(ToolCallParam param);
}
