package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageKind;
import io.nop.ai.agent.message.AgentMessageTopics;
import io.nop.ai.agent.message.CallAgentRequestPayload;
import io.nop.ai.agent.message.CallAgentResponsePayload;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.message.NoOpAgentMessenger;
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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Functional call-agent tool executor. Resolves a target agent by name,
 * creates/forks/continues a sub-session, executes the sub-agent, and returns
 * the sub-agent's response as an {@link AiAgentCallResult}.
 *
 * <p><b>Two execution pathways</b> (plan 224 / L4-8-call-agent-async):
 * <ul>
 *     <li><b>Async mailbox pathway</b> (default when a functional messenger is
 *         wired): the sub-agent execution request is routed as a REQUEST
 *         envelope to {@link AgentMessageTopics#callAgentTopic()} via
 *         {@link IAgentMessenger#request}. The engine-registered handler
 *         executes the sub-agent and returns a RESPONSE. This decouples the
 *         caller from the callee engine instance and makes the call path
 *         observable. The caller still resolves all three session modes
 *         (continue/fork/create-new) locally before building the REQUEST
 *         payload; fork mode calls {@code engine.forkSession()} first to
 *         obtain the child session id.</li>
 *     <li><b>Fork+exec pathway</b> (fallback): the shipped default. When the
 *         messenger is {@link NoOpAgentMessenger} (no messenger configured),
 *         the executor directly calls {@link IAgentEngine#execute} with
 *         {@code .orTimeout} protection — zero behaviour change from the
 *         pre-plan-224 baseline.</li>
 * </ul>
 *
 * <p>This executor lives in {@code nop-ai-agent} (not {@code nop-ai-toolkit})
 * because it needs access to {@link IAgentEngine}, which lives in
 * {@code nop-ai-agent}. The hollow mock in {@code nop-ai-toolkit} is removed;
 * this executor is the sole {@code call-agent} provider.
 *
 * <p><b>Fail-fast</b>: returns descriptive error results (never throws
 * uncaught) when the engine is missing, agentId is empty, the target agent
 * model cannot be loaded, or the sub-agent execution fails.
 *
 * <p><b>Defense-in-depth path-injection guard (finding [13-16])</b>: a
 * non-{@code "self"} {@code agentId} sourced from LLM-supplied tool args is
 * validated against the strict allow-list {@code ^[A-Za-z0-9_-]+$} via
 * {@link io.nop.ai.agent.engine.AgentNames#isValidIdentifier} <b>before</b>
 * the engine is invoked. A traversal-shaped agentId (e.g.
 * {@code "../../../etc/passwd"}) returns a descriptive LLM-facing error
 * result rather than reaching the unvalidated VFS path concatenation in
 * {@code DefaultAgentEngine.loadAgentModel}. The {@code "self"} branch is
 * left unchanged (it resolves to the parent's already-validated agentName).
 */
public class CallAgentExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "call-agent";

    /**
     * Plan 278 (AR-05): metadata key under which the delegation depth is
     * propagated from a parent agent to a sub-agent via
     * {@link AgentMessageRequest#getMetadata()}. The depth is an Integer
     * (1 for first-level sub-agent, 2 for second-level, etc.). Absent =
     * top-level agent (depth 0). Using a dedicated metadata key (rather than
     * riding inside {@link ParentPermissionConstraint}) ensures the depth is
     * ALWAYS propagated, even when no permission constraint is present
     * (e.g. a top-level agent with no declared tools/path-rules).
     */
    public static final String DELEGATION_DEPTH_METADATA_KEY = "__nopAiAgent.delegationDepth";

    /**
     * Plan 278 (AR-05): conservative default maximum delegation depth for
     * call-agent chains. With the {@code >=} check, this allows up to 3
     * levels of LLM-initiated delegation (depth 1, 2, 3) — sufficient for
     * typical lead → member → helper patterns while preventing unbounded
     * recursion / stack overflow on self-referencing or mutually-recursive
     * agents. Team-flow delegation (SpawnMemberAgentTaskStep /
     * MemberAgentTaskStep) does NOT go through this executor — it has its
     * own DAG cycle detection, so this limit does not affect team-flow
     * chains. Configurable via {@link #setMaxDelegationDepth(int)}.
     */
    public static final int DEFAULT_MAX_DELEGATION_DEPTH = 4;

    private static final Logger LOG = LoggerFactory.getLogger(CallAgentExecutor.class);

    private static final long DEFAULT_TIMEOUT_MS = 60_000L;

    private int maxDelegationDepth = DEFAULT_MAX_DELEGATION_DEPTH;

    /**
     * Plan 278 (AR-05): set the maximum delegation depth for call-agent
     * chains. Must be {@code >= 1}. When a sub-agent call would result in
     * a depth {@code >= maxDelegationDepth}, the call is rejected with a
     * structured error result (not a stack overflow).
     *
     * @param maxDelegationDepth the maximum depth; must be {@code >= 1}
     */
    public void setMaxDelegationDepth(int maxDelegationDepth) {
        if (maxDelegationDepth < 1) {
            throw new IllegalArgumentException("maxDelegationDepth must be >= 1, got: " + maxDelegationDepth);
        }
        this.maxDelegationDepth = maxDelegationDepth;
    }

    public int getMaxDelegationDepth() {
        return maxDelegationDepth;
    }

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

        // Plan 278 (AR-05): delegation depth guard. Read the parent's depth
        // and compute the child's depth. If the child's depth would meet or
        // exceed the configured maximum, reject immediately with a structured
        // error result — never a StackOverflowError. This prevents self-
        // referencing agents (agentId="self") and A↔B mutual recursion from
        // overflowing the call stack. Each level still gets its own session/
        // actor/lock (no orphan leak) because the rejected call never reaches
        // engine.execute.
        int parentDepth = agentCtx.getDelegationDepth();
        int childDepth = parentDepth + 1;
        if (childDepth >= maxDelegationDepth) {
            LOG.warn("call-agent rejected: delegation depth {} would reach or exceed max {} "
                            + "(parentDepth={}, targetAgent={})",
                    childDepth, maxDelegationDepth, parentDepth, agentCtx.getAgentName());
            return fail(call.getId(),
                    "call-agent rejected: delegation depth limit reached (depth=" + childDepth
                            + ", max=" + maxDelegationDepth + "). "
                            + "Circular or excessively deep agent delegation is not permitted.");
        }

        // Capture the parent agent's effective (clamped) allowed tool set and
        // build a permission constraint to propagate to the sub-agent (design
        // §4.4). When allowedTools is null (backward-compatible caller that did
        // not opt into the constraint field), no constraint is propagated and
        // the sub-agent executes with the engine's default permission pipeline.
        ParentPermissionConstraint parentConstraint = buildParentConstraint(agentCtx);

        // Plan 278 (AR-05): always build the propagation metadata carrying the
        // child's delegation depth, even when no permission constraint is
        // present. This ensures the depth auto-propagates through both the
        // sync (engine.execute) and async (messenger) pathways — the metadata
        // map is carried verbatim by CallAgentRequestPayload.
        Map<String, Object> childMetadata = buildPropagationMetadata(parentConstraint, childDepth);

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
            // Defense-in-depth path-injection guard (finding [13-16]): an
            // LLM-supplied agentId flows into the VFS path concatenation in
            // loadAgentModel. Reject any agentId outside [A-Za-z0-9_-] with a
            // clean LLM-facing error result BEFORE the engine is invoked (no
            // throw — the documented contract is fail-with-error-result).
            if (!io.nop.ai.agent.engine.AgentNames.isValidIdentifier(agentId)) {
                return fail(call.getId(),
                        "call-agent failed: agentId contains invalid characters; only [A-Za-z0-9_-] are allowed "
                                + "(agent-name path-injection guard): agentId=" + agentId);
            }
            targetAgentId = agentId;
        }

        if (sessionId != null && !sessionId.isEmpty()) {
            LOG.debug("call-agent continuing existing sub-session: targetAgentId={}, sessionId={}, childDepth={}",
                    targetAgentId, sessionId, childDepth);
            return dispatch(call, agentCtx, engine, targetAgentId, input, sessionId, timeoutMs,
                    parentConstraint, childMetadata);
        }

        if ("self".equals(agentId) && inheritContext) {
            String parentSessionId = agentCtx.getSessionId();
            if (parentSessionId == null || parentSessionId.isEmpty()) {
                return fail(call.getId(),
                        "call-agent with inheritContext=true requires a non-null sessionId in the execution context");
            }
            LOG.debug("call-agent forking from parent session: parentSessionId={}, targetAgentId={}, childDepth={}",
                    parentSessionId, targetAgentId, childDepth);
            AgentMessageRequest forkRequest = new AgentMessageRequest(
                    targetAgentId, "", parentSessionId, childMetadata);
            return engine.forkSession(forkRequest, true)
                    .thenCompose(childSessionId ->
                            dispatch(call, agentCtx, engine, targetAgentId, input, childSessionId,
                                    timeoutMs, parentConstraint, childMetadata));
        }

        LOG.debug("call-agent creating new sub-session: targetAgentId={}, childDepth={}",
                targetAgentId, childDepth);
        return dispatch(call, agentCtx, engine, targetAgentId, input, null, timeoutMs,
                parentConstraint, childMetadata);
    }

    /**
     * Route the resolved sub-agent execution to the async mailbox pathway (when
     * a functional messenger is wired) or the fork+exec fallback. The decision
     * is based on {@code instanceof NoOpAgentMessenger} (Design Decisions §2),
     * consistent with {@code SendMessageExecutor}.
     *
     * <p>{@code resolvedSessionId} is the caller-resolved sub-session id:
     * non-null for continue/fork modes, null for create-new. Both pathways
     * receive the same value so the observable result (sub-session id in the
     * tool result) is identical regardless of which pathway executes.
     */
    private CompletionStage<AiToolCallResult> dispatch(
            AiToolCall call, AgentToolExecuteContext agentCtx,
            IAgentEngine engine, String targetAgentId,
            String input, String resolvedSessionId, long timeoutMs,
            ParentPermissionConstraint parentConstraint,
            Map<String, Object> childMetadata) {
        IAgentMessenger messenger = agentCtx.getMessenger();
        if (messenger != null && !(messenger instanceof NoOpAgentMessenger)) {
            return executeViaMessenger(call, agentCtx, messenger, targetAgentId, input,
                    resolvedSessionId, timeoutMs, childMetadata);
        }
        return executeSubAgent(engine, call, targetAgentId, input, resolvedSessionId, timeoutMs, childMetadata);
    }

    /**
     * Async mailbox pathway (plan 224): build a REQUEST envelope carrying a
     * {@link CallAgentRequestPayload}, send it via
     * {@link IAgentMessenger#request} to the call-agent topic, and convert the
     * RESPONSE payload to an {@link AiToolCallResult}. The engine-registered
     * handler executes the sub-agent on the receiving side.
     *
     * <p>The {@code messenger.request} future is already timeout-guarded inside
     * {@code LocalAgentMessenger} (via {@code .orTimeout(timeoutMs)}). On
     * timeout or request failure the future completes exceptionally; this
     * method converts that into a fail-fast error result rather than
     * propagating the exception.
     */
    private CompletionStage<AiToolCallResult> executeViaMessenger(
            AiToolCall call, AgentToolExecuteContext agentCtx,
            IAgentMessenger messenger, String targetAgentId,
            String input, String resolvedSessionId, long timeoutMs,
            Map<String, Object> childMetadata) {

        String senderId = agentCtx.getSessionId();
        if (senderId == null || senderId.isEmpty()) {
            senderId = "unknown-sender";
        }
        String correlationId = UUID.randomUUID().toString();
        String targetTopic = AgentMessageTopics.callAgentTopic();

        CallAgentRequestPayload payload = new CallAgentRequestPayload(
                targetAgentId,
                input != null ? input : "",
                resolvedSessionId,
                childMetadata,
                timeoutMs);

        AgentMessageEnvelope envelope = new AgentMessageEnvelope(
                senderId, targetTopic, correlationId, AgentMessageKind.REQUEST, payload);

        LOG.debug("call-agent async pathway: targetAgentId={}, resolvedSessionId={}, "
                        + "targetTopic={}, correlationId={}, timeoutMs={}",
                targetAgentId, resolvedSessionId, targetTopic, correlationId, timeoutMs);

        Duration timeout = Duration.ofMillis(timeoutMs);
        return messenger.request(envelope, timeout)
                .handle((response, ex) -> {
                    if (ex != null) {
                        return AiToolCallResult.errorResult(call.getId(),
                                "call-agent async request failed or timed out: agentId=" + targetAgentId
                                        + ", error=" + rootCauseMessage(ex));
                    }
                    if (!(response instanceof CallAgentResponsePayload)) {
                        return AiToolCallResult.errorResult(call.getId(),
                                "call-agent async received unexpected response type: "
                                        + (response == null ? "null" : response.getClass().getName()));
                    }
                    return responseToToolCallResult(call, (CallAgentResponsePayload) response, resolvedSessionId);
                });
    }

    /**
     * Convert a {@link CallAgentResponsePayload} (RESPONSE from the handler)
     * into an {@link AiAgentCallResult}, mirroring {@link #toToolCallResult}
     * for the fork+exec pathway so the observable result shape is identical
     * across both pathways.
     */
    private AiToolCallResult responseToToolCallResult(
            AiToolCall call, CallAgentResponsePayload resp, String providedSessionId) {
        AiAgentCallResult agentResult = new AiAgentCallResult();
        agentResult.setId(call.getId());

        String resultSessionId = resp.getSessionId() != null ? resp.getSessionId() : providedSessionId;
        agentResult.setSessionId(resultSessionId);

        if (!"success".equals(resp.getStatus())) {
            agentResult.setStatus("failure");
            AiToolError error = new AiToolError();
            String errorMsg = resp.getError() != null ? resp.getError()
                    : "sub-agent async execution failed: status=" + resp.getStatus();
            error.setBody(errorMsg);
            agentResult.setError(error);
            return agentResult;
        }

        agentResult.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(resp.getFinalMessage());
        agentResult.setOutput(output);
        return agentResult;
    }

    private static String rootCauseMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
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
        java.util.List<io.nop.ai.agent.model.PathRuleModel> allowedPathRules = agentCtx.getAllowedPathRules();
        if (allowedTools == null && allowedPathRoots == null && allowedPathRules == null) {
            return null;
        }
        return new ParentPermissionConstraint(
                allowedTools != null ? allowedTools : java.util.Set.of(),
                allowedPathRoots,
                allowedPathRules,
                agentCtx.getAgentName(), agentCtx.getSessionId());
    }

    /**
     * Plan 278 (AR-05): build the metadata map that propagates from the
     * parent to the sub-agent via {@link AgentMessageRequest#getMetadata()}.
     * Always returns a non-null map carrying the delegation depth under
     * {@link #DELEGATION_DEPTH_METADATA_KEY}. When a non-null
     * {@code ParentPermissionConstraint} is provided, it is also included
     * under its well-known key.
     *
     * <p>This replaces the former {@code buildConstraintMetadata} which
     * returned {@code null} when no constraint was present — losing the
     * delegation depth. The depth MUST always be propagated so the
     * recursion guard works even for agents without declared tools/path-rules.
     */
    private static Map<String, Object> buildPropagationMetadata(
            ParentPermissionConstraint constraint, int childDepth) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(DELEGATION_DEPTH_METADATA_KEY, childDepth);
        if (constraint != null) {
            metadata.put(ParentPermissionConstraint.METADATA_KEY, constraint);
        }
        return metadata;
    }

    private CompletionStage<AiToolCallResult> executeSubAgent(
            IAgentEngine engine, AiToolCall call, String targetAgentId,
            String input, String subSessionId, long timeoutMs,
            Map<String, Object> childMetadata) {

        String message = input != null ? input : "";
        // Plan 271 (finding 14-01): resolve the child session id upfront so that
        // on timeout we can call engine.cancelSession(childSessionId) to cancel
        // the sub-agent execution (the .orTimeout below only cancels the Future,
        // not the underlying engine.execute). When subSessionId is null/empty
        // (create-new mode), generate a UUID — engine.execute would generate one
        // anyway via resolveSessionId, so this is behavior-preserving.
        String childSessionId = (subSessionId != null && !subSessionId.isEmpty())
                ? subSessionId : UUID.randomUUID().toString();
        AgentMessageRequest execRequest = new AgentMessageRequest(
                targetAgentId, message, childSessionId, childMetadata);

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
                .<AiToolCallResult>thenApply(result -> toToolCallResult(call, result, childSessionId))
                .exceptionally(e -> {
                    // Plan 271 (finding 14-01): on timeout, cancel the sub-agent
                    // so its execution does not continue as a zombie consuming
                    // LLM API quota and database resources. .orTimeout completes
                    // the Future exceptionally with a TimeoutException (wrapped in
                    // CompletionException); without this cancel the underlying
                    // engine.execute Future keeps running. cancelSession failures
                    // are logged but never mask the original timeout error.
                    Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                    if (cause instanceof java.util.concurrent.TimeoutException) {
                        try {
                            engine.cancelSession(childSessionId,
                                    "call-agent timeout after " + timeoutMs + "ms", true);
                        } catch (RuntimeException cancelEx) {
                            LOG.warn("call-agent: failed to cancel sub-agent after timeout: "
                                            + "agentId={}, childSessionId={}",
                                    targetAgentId, childSessionId, cancelEx);
                        }
                    }
                    return AiToolCallResult.errorResult(call.getId(),
                            "call-agent sub-agent execution failed or timed out: agentId=" + targetAgentId
                                    + ", error=" + cause.getMessage());
                });
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
