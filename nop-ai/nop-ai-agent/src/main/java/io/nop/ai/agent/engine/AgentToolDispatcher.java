package io.nop.ai.agent.engine;

import io.nop.ai.agent.compact.ToolResultTruncator;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.memory.IAiMemoryStore;
import io.nop.ai.agent.memory.IMemoryStoreProvider;
import io.nop.ai.agent.middleware.AttemptContext;
import io.nop.ai.agent.middleware.ExecutionPoint;
import io.nop.ai.agent.message.IAgentMessenger;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.ICheckpointManager;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.team.ITeamAclChecker;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tool fan-out execution for the ReAct loop (extracted from
 * {@link ReActAgentExecutor}, MA4.2-05). Builds the
 * {@link AgentToolExecuteContext}, fires the parallel {@code toolManager.callTool}
 * fan-out with per-tool wall-clock timeouts, joins with interruptible
 * {@code get()}, commits tool results with the TOOL_EXECUTION checkpoint +
 * persisted-session re-sync, and runs the hook points around result
 * processing. Also drains the actor steering queue at the round boundary.
 */
public class AgentToolDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(AgentToolDispatcher.class);

    private final IToolManager toolManager;
    private final IAgentEngine engine;
    private final IAgentMessenger messenger;
    private final ITeamManager teamManager;
    private final ITeamTaskStore teamTaskStore;
    private final ITeamAclChecker teamAclChecker;
    private final IMemoryStoreProvider memoryStoreProvider;
    private final long toolTimeoutMs;
    private final ISessionStore sessionStore;
    private final ICheckpointManager checkpointManager;
    private final AgentHookInvoker hookInvoker;
    private final AgentToolPlanResolver toolPlanResolver;
    private final AgentSecurityConsultation securityConsultation;

    public AgentToolDispatcher(IToolManager toolManager,
                               IAgentEngine engine,
                               IAgentMessenger messenger,
                               ITeamManager teamManager,
                               ITeamTaskStore teamTaskStore,
                               ITeamAclChecker teamAclChecker,
                               IMemoryStoreProvider memoryStoreProvider,
                               long toolTimeoutMs,
                               ISessionStore sessionStore,
                               ICheckpointManager checkpointManager,
                               AgentHookInvoker hookInvoker,
                               AgentToolPlanResolver toolPlanResolver,
                               AgentSecurityConsultation securityConsultation) {
        this.toolManager = toolManager;
        this.engine = engine;
        this.messenger = messenger;
        this.teamManager = teamManager;
        this.teamTaskStore = teamTaskStore;
        this.teamAclChecker = teamAclChecker;
        this.memoryStoreProvider = memoryStoreProvider;
        this.toolTimeoutMs = toolTimeoutMs;
        this.sessionStore = sessionStore;
        this.checkpointManager = checkpointManager;
        this.hookInvoker = hookInvoker;
        this.toolPlanResolver = toolPlanResolver;
        this.securityConsultation = securityConsultation;
    }

    // ---- moved verbatim from ReActAgentExecutor (MA4.2-05 split) ----
    static final class ToolCallOutput {
        final ChatToolCall chatToolCall;
        final AiToolCallResult result;

        ToolCallOutput(ChatToolCall chatToolCall, AiToolCallResult result) {
            this.chatToolCall = chatToolCall;
            this.result = result;
        }
    }

    /**
     * Build the AgentToolExecuteContext for one iteration's dispatch
     * (extracted from ReActAgentExecutor.execute, MA4.2-05): resolves the
     * per-session memory store, the effective (parent-clamped) tool / path
     * permission sets and the agent workDir, then wires the engine /
     * messenger / team facilities into the context.
     */
    public AgentToolExecuteContext prepareDispatchContext(AgentExecutionContext ctx,
                                                          AgentModel agentModel,
                                                          String sessionId,
                                                          String agentName) {
IAiMemoryStore memoryStore = memoryStoreProvider != null && sessionId != null
        ? memoryStoreProvider.getOrCreate(sessionId)
        : null;

AgentToolExecuteContext toolExecCtx = new AgentToolExecuteContext(
        toolPlanResolver.resolveWorkDir(agentModel),
        Collections.emptyMap(),
        0L,
        null,
        null,
        null,
        engine,
        messenger,
        sessionId,
        agentName,
        toolPlanResolver.computeEffectiveAllowedTools(agentModel, ctx),
        toolPlanResolver.computeEffectivePathRoots(agentModel, ctx),
        toolPlanResolver.computeEffectivePathRules(agentModel, ctx),
        memoryStore,
        teamManager,
        teamTaskStore,
        teamAclChecker);
// execution context so CallAgentExecutor can enforce
// MAX_DELEGATION_DEPTH and compute the child's depth.
toolExecCtx.setDelegationDepth(ctx.getDelegationDepth());

// (set-active-tags) can read/mutate session-scoped state.
if (sessionStore != null && sessionId != null) {
    toolExecCtx.setSession(sessionStore.get(sessionId));
}

// The workDir string used for action-fingerprint computation
// (design §6.3). Resolved once per iteration so all dispatch-loop
// consultations/recordings within this iteration share the same
// value.
String fingerprintWorkDir = securityConsultation.resolveWorkDirString(agentModel);
        return toolExecCtx;
    }

    /**
     * Execute the allowed tool calls of one iteration (extracted from
     * ReActAgentExecutor.execute, MA4.2-05): parallel fan-out with per-tool
     * wall-clock timeout, interruptible join, tool-result commit with
     * TOOL_EXECUTION checkpoint + persisted-session re-sync, and the
     * re-entrant hook points (BEFORE/AFTER_TOOL_RESULT_PROCESSED) with the
     * per-iteration re-entry counter.
     */
    public void executeAllowedCalls(AgentExecutionContext ctx,
                                    String agentName,
                                    String sessionId,
                                    List<ChatToolCall> allowedCalls,
                                    AgentToolExecuteContext toolExecCtx,
                                    long execStartTime,
                                    int[] checkpointSeq) {
        // AR-06 (plan 277): per-iteration re-entry counter, scoped to this
        // one call (one invocation per iteration).
        java.util.Map<AgentLifecyclePoint, Integer> reentryCounters = new java.util.HashMap<>();
if (!allowedCalls.isEmpty()) {
    // AR-03 (plan 277): re-enter requested by any hook in this
    // tool-batch's result processing. When set, after the for
    // loop finishes committing ALL tool results (so
    // tool_call_id pairing is complete), an iteration-level
    // re-enter marker message is injected before the next LLM
    // call. The flag replaces the old `break` that dropped
    // same-batch tool results and broke the tool_call_id
    // pairing invariant.
    boolean reenterRequested = false;
    List<CompletableFuture<ToolCallOutput>> futures = new ArrayList<>();
    // AR-15 (plan 280): wrap the fan-out build loop so a
    // synchronous throw mid-loop (e.g. the 2nd tool's callTool
    // validation throws) cannot leave the already-started tool
    // futures as orphans. The catch cancels every future that
    // has already been added to `futures` and rethrows — never
    // swallows.
    try {
        for (ChatToolCall chatToolCall : allowedCalls) {
            AiToolCall aiToolCall = new AiToolCall();
            aiToolCall.setToolName(chatToolCall.getName());
            aiToolCall.setInput(chatToolCall.getArgumentsText());

            // W3-1 (D4 方案 a): PRE_TOOL_ATTEMPT 执行级中间件——同步触发（在调用线程上，
            // 提交 future 之前）。安全检查在工具执行前确定性完成，不受线程池调度影响。
            // 工具无 retry 机制：AttemptContext 恒为 attempt=0、非 retry。
            // 未注册执行级中间件时零开销直通（executeExecutionMiddleware 返回 Pass）。
            AttemptContext preToolCtx = new AttemptContext(0, null);
            HookResult preToolResult = hookInvoker.executeExecutionMiddleware(
                    ExecutionPoint.PRE_TOOL_ATTEMPT, ctx, preToolCtx, agentName,
                    chatToolCall.getName(), chatToolCall.getId());
            if (preToolResult.isVeto()) {
                // D3: veto → 该单工具调用产错误 result（不提交 future，不影响同 batch 其他工具调用）。
                // Anti-Hollow：执行级中间件返回值被检查（非丢弃，与现有 PRE_ACTING:282 丢弃模式对比）。
                int resultId = LlmCallCoordinator.parseToolCallId(chatToolCall.getId());
                AiToolCallResult vetoResult = AiToolCallResult.errorResult(resultId,
                        "vetoed by PRE_TOOL_ATTEMPT execution middleware: "
                                + hookInvoker.vetoReason(preToolResult));
                futures.add(CompletableFuture.completedFuture(new ToolCallOutput(chatToolCall, vetoResult)));
                continue;
            }

            // with a wall-clock timeout so a permanently hung
            // tool cannot block the agent session, worker
            // thread, and takeover lock indefinitely. On
            // timeout the Future completes exceptionally with
            // a TimeoutException; the .exceptionally(...) below
            // converts it into an error ToolCallOutput so the
            // fanout's allOf wait never throws and the timed-
            // out tool is surfaced to the LLM as a normal tool
            // error response. A value <= 0 disables the timeout
            // (backward-compatible escape hatch).
            CompletableFuture<ToolCallOutput> toolFuture = toolManager.callTool(
                            chatToolCall.getName(), aiToolCall, toolExecCtx)
                    .thenApply(result -> new ToolCallOutput(chatToolCall, result));
            if (toolTimeoutMs > 0) {
                final ChatToolCall timedCall = chatToolCall;
                toolFuture = toolFuture.orTimeout(toolTimeoutMs, TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            String errMsg = cause instanceof java.util.concurrent.TimeoutException
                                    ? "tool timed out after " + toolTimeoutMs + "ms: tool=" + timedCall.getName()
                                    : "tool execution failed: tool=" + timedCall.getName()
                                            + ", error=" + cause.getMessage();
                            int resultId = LlmCallCoordinator.parseToolCallId(timedCall.getId());
                            return new ToolCallOutput(timedCall,
                                    AiToolCallResult.errorResult(resultId, errMsg));
                        });
            }
            futures.add(toolFuture);
        }
    } catch (RuntimeException | Error e) {
        // AR-15: cancel every already-started tool future so
        // they do not continue running as orphans, then rethrow.
        for (CompletableFuture<ToolCallOutput> f : futures) {
            f.cancel(true);
        }
        throw e;
    }

    @SuppressWarnings("unchecked")
    CompletableFuture<ToolCallOutput>[] futuresArray = futures.toArray(new CompletableFuture[0]);
    // 14-02 (plan 280): use interruptible get() instead of
    // join() so lease-lost / forced-cancel thread interrupts
    // can break the wait immediately (each tool already has its
    // own orTimeout; no batch-level timeout needed). Aligned
    // with callChatWithTimeout's interrupt semantics.
    CompletableFuture<Void> allFuture = CompletableFuture.allOf(futuresArray);
    try {
        allFuture.get();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        for (CompletableFuture<ToolCallOutput> f : futuresArray) {
            f.cancel(true);
        }
        throw new NopAiAgentException(
                "fan-out join interrupted (forced cancel or thread interrupt)", e);
    } catch (ExecutionException e) {
        // Preserve join()-style CompletionException wrapping so
        // exceptionally-completing tool futures (only possible
        // when toolTimeoutMs <= 0, no .exceptionally handler)
        // surface the same way as before this change.
        Throwable cause = e.getCause();
        if (cause instanceof CompletionException) {
            throw (CompletionException) cause;
        }
        throw new CompletionException(cause != null ? cause : e);
    }

    for (CompletableFuture<ToolCallOutput> f : futuresArray) {
        ToolCallOutput output = f.join();
        ChatToolCall chatToolCall = output.chatToolCall;
        AiToolCallResult toolResult = output.result;
        String toolName = chatToolCall.getName();

        // W3-1 (D4 方案 a): POST_TOOL_ATTEMPT 执行级中间件——工具完成后、提交结果前触发
        // （在调用线程上，join 之后、commit 之前）。中间件可检查工具结果并 veto。
        // Anti-Hollow：返回值被检查（非丢弃，与下面 PRE_ACTING 的丢弃模式对比）。
        // veto → 该单工具调用产错误 result（不 retry 工具，不影响同 batch 其他工具调用）。
        AttemptContext postToolCtx = new AttemptContext(0, null);
        HookResult postToolResult = hookInvoker.executeExecutionMiddleware(
                ExecutionPoint.POST_TOOL_ATTEMPT, ctx, postToolCtx, agentName, toolName, chatToolCall.getId());
        if (postToolResult.isVeto()) {
            int resultId = LlmCallCoordinator.parseToolCallId(chatToolCall.getId());
            toolResult = AiToolCallResult.errorResult(resultId,
                    "vetoed by POST_TOOL_ATTEMPT execution middleware: "
                            + hookInvoker.vetoReason(postToolResult));
        }

        hookInvoker.executeWithMiddleware(AgentLifecyclePoint.PRE_ACTING, ctx, agentName, toolName, chatToolCall.getId());

        String toolStatus;
        ChatToolResponseMessage toolResponse;
        if ("success".equals(toolResult.getStatus()) && toolResult.getError() == null) {
            String resultText = toolResult.getOutput() != null ? toolResult.getOutput().getBody() : "";
            resultText = resultText != null ? resultText : "";
            resultText = ToolResultTruncator.truncateIfAllowed(
                    resultText,
                    ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS,
                    toolName);
            toolResponse = ChatToolResponseMessage.fromToolCall(chatToolCall, resultText);
            toolStatus = "success";
        } else {
            String errorMsg = toolResult.getError() != null ? toolResult.getError().getBody() : "unknown error";
            toolResponse = ChatToolResponseMessage.error(
                    chatToolCall.getId(),
                    chatToolCall.getName(),
                    errorMsg != null ? errorMsg : "unknown error");
            toolStatus = "error";
        }

        HookResult beforeResult = hookInvoker.executeWithMiddleware(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED,
                ctx, agentName, toolName, chatToolCall.getId());
        if (beforeResult instanceof HookResult.ReenterResult) {
            int count = reentryCounters.getOrDefault(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, 0);
            if (count >= ReActAgentExecutor.DEFAULT_MAX_REENTRIES) {
                LOG.warn("Re-entry limit ({}) reached at BEFORE_TOOL_RESULT_PROCESSED, forcing PassResult",
                        ReActAgentExecutor.DEFAULT_MAX_REENTRIES);
            } else {
                reentryCounters.put(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, count + 1);
                String reenterMsg = ((HookResult.ReenterResult) beforeResult).getMessage();
                // AR-03 (plan 277): the synthetic re-enter
                // message uses the REAL tool_call_id (via
                // fromToolCall), so pairing for THIS tool is
                // maintained. Use `continue` (not `break`) so
                // the remaining tools in the batch still get
                // their results committed — `break` would
                // drop them and break the tool_call_id
                // pairing invariant for the assistant's
                // multi-tool call message.
                ctx.addMessage(ChatToolResponseMessage.fromToolCall(chatToolCall,
                        reenterMsg != null ? reenterMsg : "hook re-enter"));
                reenterRequested = true;
                continue;
            }
        }

        ctx.addMessage(toolResponse);

        // Layer 3-4 checkpoint recording (design §5.4a
        // "tool execution after" trigger point): after the tool
        // result is added to the context and before the next
        // LLM call, record a TOOL_EXECUTION checkpoint capturing
        // the tool-call payload and context-size snapshot. With
        // the shipped NoOpCheckpoint default this is a no-op;
        // with the ToolExecutionCheckpoint functional impl the
        // checkpoint is stored in-memory for save→retrieve
        // round-trip validation and crash/restart recovery.
        checkpointManager.saveCheckpoint(Checkpoint.of(
                sessionId,
                sessionId != null
                        ? sessionId + ":tool:" + chatToolCall.getId() + ":" + execStartTime + ":" + checkpointSeq[0]
                        : "anon:tool:" + chatToolCall.getId() + ":" + execStartTime + ":" + checkpointSeq[0],
                checkpointSeq[0],
                System.currentTimeMillis(),
                CheckpointType.TOOL_EXECUTION,
                toolName,
                chatToolCall.getId(),
                chatToolCall.getArgumentsText(),
                toolResponse.getContent(),
                ctx.getMessages().size(),
                ctx.getTokensUsed()));
        checkpointSeq[0]++;

        // the checkpoint is written, synchronize the session's
        // message list with the live ctx.getMessages() and
        // persist via sessionStore.save. This makes the
        // session file carry all messages produced up to the
        // last completed tool call, so a crash mid-execution
        // leaves a restorable state. With the
        // InMemorySessionStore default save is a no-op
        // (in-memory readers share the live reference), so
        // this is transparent to existing behaviour.
        if (sessionStore != null) {
            AgentSession persisted = sessionStore.get(sessionId);
            if (persisted != null) {
                persisted.replaceMessages(ctx.getMessages());
                sessionStore.save(persisted);
            }
        }

        hookInvoker.executeWithMiddleware(AgentLifecyclePoint.POST_ACTING, ctx, agentName, toolName, chatToolCall.getId());

        HookResult afterResult = hookInvoker.executeWithMiddleware(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED,
                ctx, agentName, toolName, chatToolCall.getId());
        if (afterResult instanceof HookResult.ReenterResult) {
            int count = reentryCounters.getOrDefault(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED, 0);
            if (count >= ReActAgentExecutor.DEFAULT_MAX_REENTRIES) {
                LOG.warn("Re-entry limit ({}) reached at AFTER_TOOL_RESULT_PROCESSED, forcing PassResult",
                        ReActAgentExecutor.DEFAULT_MAX_REENTRIES);
            } else {
                reentryCounters.put(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED, count + 1);
                // AR-03 (plan 277): the real tool result is
                // already committed to ctx (with proper
                // tool_call_id pairing). Do NOT `break` — that
                // would drop same-batch tool results. Just set
                // the flag; the for loop continues to process
                // remaining tools, then the iteration-level
                // re-enter marker is injected after the loop.
                reenterRequested = true;
            }
        }

        hookInvoker.publishEvent(AgentEventType.TOOL_CALL_COMPLETED, sessionId, agentName,
                Map.of("toolName", chatToolCall.getName(),
                        "status", toolStatus));
    }

    // AR-03 (plan 277): after ALL tool results in the batch
    // are committed (tool_call_id pairing intact), inject a
    // single iteration-level re-enter marker message if any
    // hook requested re-enter. This is the unified re-entry
    // trigger for both BEFORE_TOOL_RESULT_PROCESSED and
    // AFTER_TOOL_RESULT_PROCESSED — consistent behaviour
    // across both re-entrant hook points. The marker is a
    // user message (not a tool message) so it does not
    // interfere with tool_call_id pairing, and the LLM sees
    // the re-enter request in the next iteration's context.
    if (reenterRequested) {
        ctx.addMessage(new ChatUserMessage("[re-enter requested by lifecycle hook]"));
    }
}
    }

    /**
     * Drain the actor steering queue at the round boundary (extracted from
     * ReActAgentExecutor.execute, MA4.2-05): append any queued steering
     * messages to the context before the next LLM call.
     */
    public void drainSteering(AgentExecutionContext ctx, String sessionId) {
        List<ChatMessage> steeringMessages = ctx.drainSteering();
        if (!steeringMessages.isEmpty()) {
            for (ChatMessage steeringMsg : steeringMessages) {
                ctx.addMessage(steeringMsg);
            }
            LOG.info("Steering checkpoint: injected {} steering message(s) at round boundary "
                    + "(iteration={}). session={}",
                    steeringMessages.size(), ctx.getCurrentIteration(), sessionId);
        }
    }

}
