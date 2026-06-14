package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ParentPermissionConstraint;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiAgentCallResult;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolError;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.api.core.json.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Functional call-agent tool executor (fork+exec model). Resolves a target
 * agent by name, creates/forks/continues a sub-session, executes the sub-agent
 * via {@link IAgentEngine#execute(AgentMessageRequest)}, and returns the
 * sub-agent's response as an {@link AiAgentCallResult}.
 *
 * <p>This executor lives in {@code nop-ai-agent} (not {@code nop-ai-toolkit})
 * because it needs access to {@link IAgentEngine}, which lives in
 * {@code nop-ai-agent}. The hollow mock in {@code nop-ai-toolkit} is removed;
 * this executor is the sole {@code call-agent} provider.
 *
 * <p><b>Fail-fast</b>: returns descriptive error results (never throws
 * uncaught) when the engine is missing, agentId is empty, the target agent
 * model cannot be loaded, or the sub-agent execution fails.
 */
public class CallAgentExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "call-agent";

    private static final Logger LOG = LoggerFactory.getLogger(CallAgentExecutor.class);

    private static final long DEFAULT_TIMEOUT_MS = 60_000L;

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            return doExecuteAsync(call, context);
        } catch (Exception e) {
            LOG.error("call-agent failed unexpectedly", e);
            return CompletableFuture.completedFuture(AiToolCallResult.errorResult(call.getId(), e));
        }
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<AiToolCallResult> doExecuteAsync(AiToolCall call, IToolExecuteContext context) {
        if (!(context instanceof AgentToolExecuteContext)) {
            return fail(call.getId(),
                    "call-agent requires AgentToolExecuteContext (engine not available). "
                            + "The tool must be invoked within an agent execution context.");
        }
        AgentToolExecuteContext agentCtx = (AgentToolExecuteContext) context;

        IAgentEngine engine = agentCtx.getEngine();
        if (engine == null) {
            return fail(call.getId(),
                    "call-agent failed: no engine reference available in the tool execution context. "
                            + "Ensure the ReAct executor is wired with an engine via the Builder.");
        }

        // Capture the parent agent's effective (clamped) allowed tool set and
        // build a permission constraint to propagate to the sub-agent (design
        // §4.4). When allowedTools is null (backward-compatible caller that did
        // not opt into the constraint field), no constraint is propagated and
        // the sub-agent executes with the engine's default permission pipeline.
        ParentPermissionConstraint parentConstraint = buildParentConstraint(agentCtx);

        Map<String, Object> args = resolveArguments(call);

        String agentId = getStringArg(args, call, "agentId");
        if (agentId == null || agentId.isEmpty()) {
            return fail(call.getId(), "call-agent failed: agentId is required");
        }

        String sessionId = getStringArg(args, call, "sessionId");
        boolean inheritContext = getBooleanArg(args, call, "inheritContext", false);
        String input = getStringArg(args, call, "input");
        long timeoutMs = resolveTimeoutMs(call);

        String targetAgentId;
        if ("self".equals(agentId)) {
            targetAgentId = agentCtx.getAgentName();
            if (targetAgentId == null || targetAgentId.isEmpty()) {
                return fail(call.getId(),
                        "call-agent with agentId='self' requires a non-null agentName in the execution context");
            }
        } else {
            targetAgentId = agentId;
        }

        if (sessionId != null && !sessionId.isEmpty()) {
            LOG.debug("call-agent continuing existing sub-session: targetAgentId={}, sessionId={}",
                    targetAgentId, sessionId);
            return executeSubAgent(engine, call, targetAgentId, input, sessionId, timeoutMs, parentConstraint);
        }

        if ("self".equals(agentId) && inheritContext) {
            String parentSessionId = agentCtx.getSessionId();
            if (parentSessionId == null || parentSessionId.isEmpty()) {
                return fail(call.getId(),
                        "call-agent with inheritContext=true requires a non-null sessionId in the execution context");
            }
            LOG.debug("call-agent forking from parent session: parentSessionId={}, targetAgentId={}",
                    parentSessionId, targetAgentId);
            AgentMessageRequest forkRequest = new AgentMessageRequest(
                    targetAgentId, "", parentSessionId, buildConstraintMetadata(parentConstraint));
            return engine.forkSession(forkRequest, true)
                    .thenCompose(childSessionId ->
                            executeSubAgent(engine, call, targetAgentId, input, childSessionId, timeoutMs, parentConstraint));
        }

        LOG.debug("call-agent creating new sub-session: targetAgentId={}", targetAgentId);
        return executeSubAgent(engine, call, targetAgentId, input, null, timeoutMs, parentConstraint);
    }

    /**
     * Build the parent permission constraint from the parent agent's effective
     * (clamped) allowed tool set and effective (clamped) allowed path roots
     * carried in the execution context. Returns {@code null} when no constraint
     * information is available (backward-compatible caller), in which case the
     * sub-agent executes without a parent constraint.
     *
     * <p>The tool set and path roots propagate together through a single
     * {@link ParentPermissionConstraint} object under the well-known metadata
     * key, reusing the plan-169 metadata infrastructure (design §4.4:
     * 工具权限 = 父权限 ∩ 子配置, 文件权限 = 父权限 ∩ 子配置).
     */
    private ParentPermissionConstraint buildParentConstraint(AgentToolExecuteContext agentCtx) {
        java.util.Set<String> allowedTools = agentCtx.getAllowedTools();
        java.util.Set<String> allowedPathRoots = agentCtx.getAllowedPathRoots();
        if (allowedTools == null && allowedPathRoots == null) {
            return null;
        }
        return new ParentPermissionConstraint(
                allowedTools != null ? allowedTools : java.util.Set.of(),
                allowedPathRoots,
                agentCtx.getAgentName(), agentCtx.getSessionId());
    }

    /**
     * Build a metadata map carrying the parent permission constraint under the
     * well-known metadata key, or {@code null} when there is no constraint to
     * propagate.
     */
    private static Map<String, Object> buildConstraintMetadata(ParentPermissionConstraint constraint) {
        if (constraint == null) {
            return null;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ParentPermissionConstraint.METADATA_KEY, constraint);
        return metadata;
    }

    private CompletionStage<AiToolCallResult> executeSubAgent(
            IAgentEngine engine, AiToolCall call, String targetAgentId,
            String input, String subSessionId, long timeoutMs,
            ParentPermissionConstraint parentConstraint) {

        String message = input != null ? input : "";
        AgentMessageRequest execRequest = new AgentMessageRequest(
                targetAgentId, message, subSessionId, buildConstraintMetadata(parentConstraint));

        CompletableFuture<AgentExecutionResult> future;
        try {
            future = engine.execute(execRequest);
        } catch (Exception e) {
            LOG.warn("call-agent failed to start sub-agent execution: agentId={}", targetAgentId, e);
            return fail(call.getId(),
                    "call-agent failed to start sub-agent execution: agentId=" + targetAgentId
                            + ", error=" + e);
        }

        CompletableFuture<AgentExecutionResult> withTimeout = future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        return withTimeout
                .<AiToolCallResult>thenApply(result -> toToolCallResult(call, result, subSessionId))
                .exceptionally(e -> AiToolCallResult.errorResult(call.getId(),
                        "call-agent sub-agent execution failed or timed out: agentId=" + targetAgentId
                                + ", error=" + e.getMessage()));
    }

    private AiAgentCallResult toToolCallResult(AiToolCall call, AgentExecutionResult result, String providedSessionId) {
        AiAgentCallResult agentResult = new AiAgentCallResult();
        agentResult.setId(call.getId());

        String resultSessionId = result.getSessionId() != null ? result.getSessionId() : providedSessionId;
        agentResult.setSessionId(resultSessionId);

        if (result.getStatus() != AgentExecStatus.completed) {
            agentResult.setStatus("failure");
            AiToolError error = new AiToolError();
            String errorMsg = result.getError() != null
                    ? result.getError()
                    : "sub-agent did not complete successfully: status=" + result.getStatus();
            error.setBody(errorMsg);
            agentResult.setError(error);
            return agentResult;
        }

        String responseText = extractFinalMessage(result);
        agentResult.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(responseText);
        agentResult.setOutput(output);
        return agentResult;
    }

    private String extractFinalMessage(AgentExecutionResult result) {
        List<ChatMessage> messages = result.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof ChatAssistantMessage) {
                String content = msg.getContent();
                return content != null ? content : "";
            }
        }
        return "";
    }

    // ---- Argument resolution helpers ----

    /**
     * Resolve arguments from the tool call. When invoked from the ReAct loop,
     * the LLM provides JSON arguments in {@code call.getInput()} and the node
     * is null. When invoked from the XML DSL, the node carries attributes and
     * child elements.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveArguments(AiToolCall call) {
        Map<String, Object> args = new HashMap<>();

        if (call.getInput() != null && !call.getInput().isEmpty()) {
            try {
                Object parsed = JSON.parse(call.getInput());
                if (parsed instanceof Map) {
                    args.putAll((Map<String, Object>) parsed);
                }
            } catch (Exception e) {
                LOG.debug("call-agent: could not parse input as JSON, treating as plain text input: input={}",
                        call.getInput(), e);
                args.put("input", call.getInput());
            }
        }

        return args;
    }

    private String getStringArg(Map<String, Object> args, AiToolCall call, String key) {
        Object val = args.get(key);
        if (val != null) {
            return val.toString();
        }
        return call.attrText(key);
    }

    private boolean getBooleanArg(Map<String, Object> args, AiToolCall call, String key, boolean defaultValue) {
        Object val = args.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val instanceof String) {
            return Boolean.parseBoolean((String) val);
        }
        Boolean nodeVal = call.attrBoolean(key);
        return nodeVal != null ? nodeVal : defaultValue;
    }

    private long resolveTimeoutMs(AiToolCall call) {
        Integer explicit = call.getTimeoutMs();
        if (explicit != null && explicit > 0) {
            return explicit;
        }
        Integer attrTimeout = call.attrInt("timeoutMs");
        if (attrTimeout != null && attrTimeout > 0) {
            return attrTimeout;
        }
        return DEFAULT_TIMEOUT_MS;
    }

    private static CompletableFuture<AiToolCallResult> fail(int callId, String message) {
        return CompletableFuture.completedFuture(AiToolCallResult.errorResult(callId, message));
    }
}
