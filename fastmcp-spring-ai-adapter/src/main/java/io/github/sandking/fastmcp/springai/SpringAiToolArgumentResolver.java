package io.github.sandking.fastmcp.springai;

import org.springframework.ai.chat.model.ToolContext;

@FunctionalInterface
public interface SpringAiToolArgumentResolver {
    Object resolve(ToolContext context);
}
