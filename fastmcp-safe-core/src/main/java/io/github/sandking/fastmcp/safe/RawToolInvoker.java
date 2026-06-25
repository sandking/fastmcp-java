package io.github.sandking.fastmcp.safe;

import java.util.Map;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface RawToolInvoker {
    CompletionStage<RawToolResult> callAsync(String serverName, String rawToolName, Map<String, Object> rawArguments);
}
