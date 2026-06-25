package io.github.sandking.fastmcp.safe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public final class SafeMcpTool {
    private final String framework;
    private final SafeMcpToolSpec spec;
    private final RawToolInvoker rawToolInvoker;
    private final SafeMcpPolicy policy;
    private final SafeAuditSink auditSink;

    public SafeMcpTool(String framework, SafeMcpToolSpec spec, RawToolInvoker rawToolInvoker) {
        this(framework, spec, rawToolInvoker, SafeMcpPolicies.allow(), SafeAuditSink.noOp());
    }

    public SafeMcpTool(String framework, SafeMcpToolSpec spec, RawToolInvoker rawToolInvoker,
            SafeMcpPolicy policy, SafeAuditSink auditSink) {
        this.framework = framework == null ? "" : framework;
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        this.rawToolInvoker = Objects.requireNonNull(rawToolInvoker, "rawToolInvoker must not be null");
        this.policy = policy == null ? SafeMcpPolicies.allow() : policy;
        this.auditSink = auditSink == null ? SafeAuditSink.noOp() : auditSink;
    }

    public CompletionStage<SafeToolResult> callAsync(Map<String, ?> input, SafeToolCallContext context) {
        SafeToolCallContext safeContext = context == null ? SafeToolCallContext.builder().build() : context;
        Map<String, ?> safeInput = input == null ? Map.of() : input;
        Map<String, Object> rawArguments = new LinkedHashMap<>();
        String policyDecision = "";
        try {
            copyModelArguments(safeInput, rawArguments);
            injectProtectedArguments(safeContext, rawArguments);
            SafeToolCallRequest request = new SafeToolCallRequest(spec.name(), spec.rawServerName(),
                    spec.rawToolName(), rawArguments, spec.readOnly());
            SafePolicyDecision decision = policy.evaluate(safeContext, request);
            policyDecision = decision == null ? "deny: missing policy decision"
                    : (decision.allowed() ? "allow: " : "deny: ") + decision.reason();
            if (decision == null || !decision.allowed()) {
                SafeMcpException exception = new SafeMcpException("POLICY_DENIED",
                        decision == null ? "Policy denied tool call" : decision.reason());
                recordAudit(safeContext, false, exception.code(), policyDecision);
                return failed(exception);
            }
            CompletionStage<RawToolResult> rawResult = rawToolInvoker.callAsync(spec.rawServerName(), spec.rawToolName(),
                    Collections.unmodifiableMap(new LinkedHashMap<>(rawArguments)));
            return rawResult.handle((result, exception) -> {
                if (exception != null) {
                    Throwable cause = unwrap(exception);
                    String code = cause instanceof SafeMcpException ? ((SafeMcpException) cause).code()
                            : "RAW_TOOL_FAILED";
                    recordAudit(safeContext, false, code, "allow");
                    throw new CompletionException(cause);
                }
                recordAudit(safeContext, true, null, "allow");
                return new SafeToolResult(spec.name(), result);
            });
        } catch (RuntimeException exception) {
            String code = exception instanceof SafeMcpException ? ((SafeMcpException) exception).code()
                    : "SAFE_TOOL_FAILED";
            recordAudit(safeContext, false, code, policyDecision);
            return failed(exception);
        }
    }

    private void copyModelArguments(Map<String, ?> input, Map<String, Object> rawArguments) {
        for (Map.Entry<String, ?> entry : input.entrySet()) {
            String virtualName = SafeMcpException.requireText(entry.getKey(), "argument name");
            String rawName = spec.argumentMappings().getOrDefault(virtualName, virtualName);
            if (spec.injectedArguments().containsKey(virtualName) || spec.injectedArguments().containsKey(rawName)) {
                throw new SafeMcpException("PROTECTED_ARGUMENT_SUPPLIED",
                        "Model supplied protected argument: " + virtualName);
            }
            rawArguments.put(rawName, entry.getValue());
        }
    }

    private void injectProtectedArguments(SafeToolCallContext context, Map<String, Object> rawArguments) {
        for (Map.Entry<String, SafeArgumentResolver> entry : spec.injectedArguments().entrySet()) {
            rawArguments.put(entry.getKey(), entry.getValue().resolve(context));
        }
    }

    private void recordAudit(SafeToolCallContext context, boolean success, String errorCode, String policyDecision) {
        auditSink.record(SafeAuditEvent.builder()
                .framework(framework)
                .virtualToolName(spec.name())
                .rawServerName(spec.rawServerName())
                .rawToolName(spec.rawToolName())
                .callerId(context.userId())
                .tenantId(context.tenantId())
                .injectedArgumentNames(spec.injectedArguments().keySet())
                .policyId(spec.policyId())
                .policyDecision(policyDecision)
                .success(success)
                .errorCode(errorCode)
                .build());
    }

    private static Throwable unwrap(Throwable exception) {
        if (exception instanceof CompletionException && exception.getCause() != null) {
            return exception.getCause();
        }
        return exception;
    }

    private static <T> CompletionStage<T> failed(Throwable exception) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(exception);
        return future;
    }
}
