package io.nop.ai.agent.engine;

import io.nop.ai.agent.engine.NopAiAgentException;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.api.core.time.CoreMetrics;
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
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
    /**
     * Plan 2285 (WI4) toolTimeoutPolicy (design §三.5 layer 2): key = exact
     * tool name, tool tag, or "*" global cap; value = per-tool mechanical
     * timeout cap in millis. Empty/null = plain toolTimeoutMs for every tool
     * (shipped default, zero behaviour change).
     */
    private final Map<String, Long> toolTimeoutPolicy;

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
        this(toolManager, engine, messenger, teamManager, teamTaskStore, teamAclChecker,
                memoryStoreProvider, toolTimeoutMs, sessionStore, checkpointManager, hookInvoker,
                toolPlanResolver, securityConsultation, null);
    }

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
                               AgentSecurityConsultation securityConsultation,
                               Map<String, Long> toolTimeoutPolicy) {
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
        this.toolTimeoutPolicy = toolTimeoutPolicy;
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
     * Plan 2285 (WI4): one tool wait-phase session. Encapsulates the pending
     * futures with their callId addressing and deadlines so the ReAct executor
     * can drive the wait loop (heartbeat slices, WI5 steering wakeups) that
     * used to be a blocking {@code allOf().get()} inside
     * {@link #executeAllowedCalls}. The session owns the re-entry counters and
     * checkpoint-sequence continuity; heartbeat/steering wakeup turns are
     * executor-side concerns and never touch this object's state.
     *
     * <p>Thread-safety: all methods are called on the ReAct executor thread
     * (the same thread that runs build → wait → commit), matching the
     * existing single-writer discipline on ctx.messages.
     */
    public class ToolPhaseSession {

        private final AgentExecutionContext ctx;
        private final String agentName;
        private final String sessionId;
        private final long execStartTime;
        private final int[] checkpointSeq;
        private final Map<AgentLifecyclePoint, Integer> reentryCounters = new java.util.HashMap<>();
        private final List<CompletableFuture<ToolCallOutput>> futures = new ArrayList<>();
        private final Map<String, CompletableFuture<ToolCallOutput>> futuresByCallId = new java.util.LinkedHashMap<>();
        private final Map<String, ChatToolCall> callsByCallId = new java.util.LinkedHashMap<>();
        private final Map<String, Long> deadlinesByCallId = new java.util.LinkedHashMap<>();
        private final Map<String, Long> startsByCallId = new java.util.LinkedHashMap<>();
        private final Set<String> cancelledCallIds = new java.util.LinkedHashSet<>();

        ToolPhaseSession(AgentExecutionContext ctx, String agentName, String sessionId,
                         long execStartTime, int[] checkpointSeq) {
            this.ctx = ctx;
            this.agentName = agentName;
            this.sessionId = sessionId;
            this.execStartTime = execStartTime;
            this.checkpointSeq = checkpointSeq;
        }

        /**
         * Waits up to {@code sliceMs} for ALL pending (non-cancelled) futures
         * to complete. Returns true when everything has completed (phase can
         * commit), false when the slice expired without completion — the
         * heartbeat wakeup point for the executor loop. Interruption keeps the
         * existing discipline: cancel every pending future and throw
         * {@code NopAiAgentException} (awaitToolFutures precedent).
         */
        public boolean awaitCompletionSlice(long sliceMs) {
            List<CompletableFuture<ToolCallOutput>> pending = pendingFutures();
            if (pending.isEmpty()) {
                return true;
            }
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                    pending.toArray(new CompletableFuture[0]));
            try {
                allOf.get(sliceMs, TimeUnit.MILLISECONDS);
                return true;
            } catch (TimeoutException e) {
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                for (CompletableFuture<ToolCallOutput> f : pending) {
                    f.cancel(true);
                }
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL)
                        .param(ARG_DETAIL, "tool wait phase interrupted");
            } catch (ExecutionException e) {
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e)
                        .param(ARG_DETAIL, "tool wait phase failed: " + e.getMessage());
            }
        }

        /**
         * The callIds still in flight (never completed and not cancelled) —
         * the heartbeat payload's running list and the placeholder renderer's
         * input surface.
         */
        public List<String> pendingCallIds() {
            List<String> pending = new ArrayList<>();
            for (Map.Entry<String, CompletableFuture<ToolCallOutput>> entry : futuresByCallId.entrySet()) {
                if (!entry.getValue().isDone() && !cancelledCallIds.contains(entry.getKey())) {
                    pending.add(entry.getKey());
                }
            }
            return pending;
        }

        /**
         * Millis elapsed since this call started (heartbeat payload evidence).
         */
        public long elapsedMs(String callId) {
            Long start = startsByCallId.get(callId);
            return start == null ? 0L : CoreMetrics.currentTimeMillis() - start;
        }

        /**
         * Millis until the earliest pending deadline (the next slice must not
         * oversleep past a cap); Long.MAX_VALUE when nothing is pending.
         */
        public long earliestDeadlineRemainingMs() {
            long now = CoreMetrics.currentTimeMillis();
            long min = Long.MAX_VALUE;
            for (Map.Entry<String, CompletableFuture<ToolCallOutput>> entry : futuresByCallId.entrySet()) {
                if (entry.getValue().isDone() || cancelledCallIds.contains(entry.getKey())) {
                    continue;
                }
                Long deadline = deadlinesByCallId.get(entry.getKey());
                if (deadline != null) {
                    min = Math.min(min, Math.max(0L, deadline - now));
                }
            }
            return min;
        }

        /**
         * "Abandon" adjudication (plan 2285): cancels the future, records the
         * cancellation so {@link #commitResults()} skips it, and (when a
         * progress tracker is wired) cancels the tool process itself. Returns
         * false for unknown or already-completed callIds — the caller then
         * knows no synthetic cancel result should be emitted for it.
         */
        public boolean cancelCall(String callId) {
            CompletableFuture<ToolCallOutput> future = futuresByCallId.get(callId);
            if (future == null || future.isDone() || cancelledCallIds.contains(callId)) {
                return false;
            }
            future.cancel(true);
            cancelledCallIds.add(callId);
            return true;
        }

        /**
         * Plan 2285 (WI4) "abandon" adjudication backend: cancels the future,
         * registers the cancellation (commit skips it — no TOOL_EXECUTION
         * checkpoint, aligned with the security-deny precedent), and inserts
         * the caller-built synthetic cancel result IN-PLACE (WI3 invariant 2).
         * Returns false when the callId is unknown/completed/already
         * cancelled (the caller then skips the synthetic result).
         */
        public boolean abandon(String callId, ChatToolResponseMessage syntheticCancelResult) {
            if (!cancelCall(callId)) {
                return false;
            }
            if (!insertToolResultInList(ctx.getMessages(), syntheticCancelResult)) {
                // the open tool_call must exist for a pending call — a miss is
                // a wiring bug; tail-append keeps the result visible (fail-visible)
                ctx.addMessage(syntheticCancelResult);
            }
            return true;
        }

        /**
         * Commits every completed, non-cancelled future's result (WI3 in-place
         * insertion) and emits nothing for cancelled callIds — their synthetic
         * cancel results were already inserted by the abandon adjudication.
         * Returns the reenter flag (AR-03) for the executor's round tail.
         */
        public boolean commitResults() {
            List<CompletableFuture<ToolCallOutput>> ordered = new ArrayList<>(futures);
            ordered.removeIf(CompletableFuture::isCancelled);
            boolean reenterRequested = commitToolResults(ctx, agentName, sessionId, execStartTime,
                    checkpointSeq, reentryCounters, ordered.toArray(new CompletableFuture[0]));
            // AR-03 (plan 277): after ALL batch results are committed, inject
            // the single iteration-level re-enter marker when any hook
            // requested re-enter (moved from executeAllowedCalls so both the
            // facade path and the WI4 executor-driven wait loop share it).
            if (reenterRequested) {
                ctx.addMessage(new ChatUserMessage("[re-enter requested by lifecycle hook]"));
            }
            return reenterRequested;
        }

        ChatToolCall callByCallId(String callId) {
            return callsByCallId.get(callId);
        }

        private List<CompletableFuture<ToolCallOutput>> pendingFutures() {
            List<CompletableFuture<ToolCallOutput>> pending = new ArrayList<>();
            for (CompletableFuture<ToolCallOutput> f : futures) {
                if (!f.isDone()) {
                    pending.add(f);
                }
            }
            return pending;
        }
    }

    /**
     * Plan 2285 (WI4): builds and arms one tool wait-phase session — the
     * future fan-out (buildToolFutures) plus callId/deadline addressing.
     * The executor drives {@link ToolPhaseSession#awaitCompletionSlice} in its
     * wait loop (heartbeat slices; WI5 adds the steering wakeup source) and
     * finishes with {@link ToolPhaseSession#commitResults}.
     */
    public ToolPhaseSession startToolPhase(AgentExecutionContext ctx,
                                           String agentName,
                                           String sessionId,
                                           List<ChatToolCall> allowedCalls,
                                           AgentToolExecuteContext toolExecCtx,
                                           long execStartTime,
                                           int[] checkpointSeq) {
        ToolPhaseSession session = new ToolPhaseSession(ctx, agentName, sessionId,
                execStartTime, checkpointSeq);
        try {
            for (ChatToolCall chatToolCall : allowedCalls) {
                long startMs = CoreMetrics.currentTimeMillis();
                long capMs = resolveToolTimeoutMs(chatToolCall.getName());
                List<CompletableFuture<ToolCallOutput>> built = buildToolFutures(ctx, agentName,
                        List.of(chatToolCall), toolExecCtx);
                if (built.size() != 1) {
                    // buildToolFutures emits exactly one future per call; a
                    // different shape is a wiring bug — fail loud, no silent skip
                    throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL)
                            .param(ARG_DETAIL, "buildToolFutures returned " + built.size()
                                    + " futures for a single call: tool=" + chatToolCall.getName());
                }
                CompletableFuture<ToolCallOutput> future = built.get(0);
                session.futures.add(future);
                session.futuresByCallId.put(chatToolCall.getId(), future);
                session.callsByCallId.put(chatToolCall.getId(), chatToolCall);
                // cap <= 0 = mechanical timeout disabled → no deadline bound
                // (MAX_VALUE); a zero deadline would collapse the wait slice
                // to a busy heartbeat loop
                session.deadlinesByCallId.put(chatToolCall.getId(),
                        capMs > 0 ? startMs + capMs : Long.MAX_VALUE);
                session.startsByCallId.put(chatToolCall.getId(), startMs);
            }
        } catch (RuntimeException | Error e) {
            // AR-15: a synchronous throw mid-loop must not leave the
            // already-started futures as orphans — cancel them, then rethrow.
            for (CompletableFuture<ToolCallOutput> f : session.futures) {
                f.cancel(true);
            }
            throw e;
        }
        return session;
    }

    /**
     * Plan 2285 (WI4) parallel-work adjudication: adds security-chain-cleared
     * derived calls (wakeup-turn tool_calls) into a running wait-phase
     * session — new futures join the pending set so the executor's wait loop
     * covers them. AR-15 mid-loop throw discipline applies.
     */
    public void addCallsToPhase(ToolPhaseSession session,
                                AgentExecutionContext ctx,
                                String agentName,
                                List<ChatToolCall> allowedCalls,
                                AgentToolExecuteContext toolExecCtx) {
        try {
            for (ChatToolCall chatToolCall : allowedCalls) {
                long startMs = CoreMetrics.currentTimeMillis();
                long capMs = resolveToolTimeoutMs(chatToolCall.getName());
                List<CompletableFuture<ToolCallOutput>> built = buildToolFutures(ctx, agentName,
                        List.of(chatToolCall), toolExecCtx);
                if (built.size() != 1) {
                    throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL)
                            .param(ARG_DETAIL, "buildToolFutures returned " + built.size()
                                    + " futures for a single call: tool=" + chatToolCall.getName());
                }
                CompletableFuture<ToolCallOutput> future = built.get(0);
                session.futures.add(future);
                session.futuresByCallId.put(chatToolCall.getId(), future);
                session.callsByCallId.put(chatToolCall.getId(), chatToolCall);
                session.deadlinesByCallId.put(chatToolCall.getId(),
                        capMs > 0 ? startMs + capMs : Long.MAX_VALUE);
                session.startsByCallId.put(chatToolCall.getId(), startMs);
            }
        } catch (RuntimeException | Error e) {
            for (CompletableFuture<ToolCallOutput> f : session.futures) {
                if (!f.isDone()) {
                    f.cancel(true);
                }
            }
            throw e;
        }
    }

    /**
     * Per-tool mechanical-timeout cap resolution (plan 2285 / design §三.5
     * layer 2): explicit policy override by exact tool name, then by any tool
     * tag, then the wildcard global cap, then the plain toolTimeoutMs default.
     * Tag lookup goes through the tool manager's tool model; unknown tools
     * resolve to the default.
     */
    long resolveToolTimeoutMs(String toolName) {
        if (toolTimeoutPolicy != null && !toolTimeoutPolicy.isEmpty()) {
            Long byName = toolTimeoutPolicy.get(toolName);
            if (byName != null && byName > 0) {
                return byName;
            }
            // precedence per design §三.5: exact name → any tool tag →
            // wildcard global cap → plain default (the global cap is the
            // LAST override, not the first — a tag override must not be
            // shadowed by "*")
            if (toolManager != null) {
                io.nop.ai.toolkit.model.AiToolModel model = toolManager.loadTool(toolName);
                if (model != null && model.getTags() != null) {
                    for (String tag : model.getTags()) {
                        Long byTag = toolTimeoutPolicy.get(tag);
                        if (byTag != null && byTag > 0) {
                            return byTag;
                        }
                    }
                }
            }
            Long global = toolTimeoutPolicy.get("*");
            if (global != null && global > 0) {
                return global;
            }
        }
        return toolTimeoutMs;
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
     *
     * <p>AI-12b (plan 355): the body is split into the phase methods
     * {@link #buildToolFutures}, the wait-phase session and
     * {@link #commitToolResults} — a pure extract-method refactor with zero
     * behaviour change.
     */
    public void executeAllowedCalls(AgentExecutionContext ctx,
                                    String agentName,
                                    String sessionId,
                                    List<ChatToolCall> allowedCalls,
                                    AgentToolExecuteContext toolExecCtx,
                                    long execStartTime,
                                    int[] checkpointSeq) {
        if (allowedCalls.isEmpty()) {
            return;
        }
        // Plan 2285 (WI4): the legacy one-shot path is now a facade over the
        // wait-phase session — same build/security/commit machinery, wait
        // driven by deadline slices (no heartbeat: the executor-level loop is
        // what re-arms slices with heartBeatIntervalMs). AR-15 mid-loop throw
        // discipline is preserved inside startToolPhase.
        ToolPhaseSession session = startToolPhase(ctx, agentName, sessionId,
                allowedCalls, toolExecCtx, execStartTime, checkpointSeq);
        while (!session.awaitCompletionSlice(session.earliestDeadlineRemainingMs())) {
            // mechanical cap expiries complete futures exceptionally
            // (.exceptionally error results), shrinking the pending set —
            // loop until nothing is pending
        }
        session.commitResults();
    }

    /**
     * AI-12b (plan 355): extracted from executeAllowedCalls — the fan-out
     * build loop: per-call PRE_TOOL_ATTEMPT execution middleware (veto →
     * error result, no future), toolManager.callTool invocation wrapped with
     * the per-tool wall-clock timeout. The AR-15 catch cancels every
     * already-started future and rethrows — never swallows.
     */
    private List<CompletableFuture<ToolCallOutput>> buildToolFutures(AgentExecutionContext ctx,
                                                                     String agentName,
                                                                     List<ChatToolCall> allowedCalls,
                                                                     AgentToolExecuteContext toolExecCtx) {
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
                // Plan 2285 (WI4): propagate the provider callId so sandbox
                // progress/cancel can address the running execution
                aiToolCall.setCallId(chatToolCall.getId());

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
                long effectiveTimeoutMs = resolveToolTimeoutMs(chatToolCall.getName());
                if (effectiveTimeoutMs > 0) {
                    final ChatToolCall timedCall = chatToolCall;
                    final long timeoutForLog = effectiveTimeoutMs;
                    toolFuture = toolFuture.orTimeout(effectiveTimeoutMs, TimeUnit.MILLISECONDS)
                            .exceptionally(ex -> {
                                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                String errMsg = cause instanceof java.util.concurrent.TimeoutException
                                        ? "tool timed out after " + timeoutForLog + "ms: tool=" + timedCall.getName()
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
        return futures;
    }

    /**
     * AI-12b (plan 355): extracted from executeAllowedCalls — the per-tool
     * result commit loop: POST_TOOL_ATTEMPT middleware, success/error
     * response construction (with spill), the re-entrant
     * BEFORE/AFTER_TOOL_RESULT_PROCESSED hook points with the per-iteration
     * re-entry counter, TOOL_EXECUTION checkpoint + persisted-session
     * re-sync, POST_ACTING hook, and the TOOL_CALL_COMPLETED event. Returns
     * whether any hook requested a re-enter for this batch.
     */
    private boolean commitToolResults(AgentExecutionContext ctx,
                                      String agentName,
                                      String sessionId,
                                      long execStartTime,
                                      int[] checkpointSeq,
                                      Map<AgentLifecyclePoint, Integer> reentryCounters,
                                      CompletableFuture<ToolCallOutput>[] futuresArray) {
        boolean reenterRequested = false;
        // WI3 invariant 2 (design nop-ai-agent-async-tool-execution.md §三.2):
        // results are committed IN-PLACE — each result is anchored by its OWN
        // callId (plan 2285), landing at the end of its contiguous
        // tool_call/tool_output segment, before any message a wait-phase
        // wakeup appended after the tool calls (WI4 heartbeat / WI5 steering).
        // With no wait-phase messages (the only shape before WI4/WI5 land)
        // the insertion index is the list tail — byte-identical ordering to
        // the previous tail-append behaviour.
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
                resultText = spillIfOversized(sessionId, resultText, toolName);
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
                    addToolResultInPlace(ctx, ChatToolResponseMessage.fromToolCall(chatToolCall,
                            reenterMsg != null ? reenterMsg : "hook re-enter"));
                    reenterRequested = true;
                    continue;
                }
            }

            addToolResultInPlace(ctx, toolResponse);

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
                    CoreMetrics.currentTimeMillis(),
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
        return reenterRequested;
    }

    /**
     * WI3 invariant 2: insert a tool result IN-PLACE rather than appending at
     * the tail. The insertion index is the end of the contiguous
     * tool_call/tool_output segment that starts at the tool_call message
     * carrying THIS result's own callId (plan 2285: anchoring by the result's
     * own callId — not the batch's first callId — keeps tool_result
     * immediately after its own tool_use even in multi-segment histories
     * produced by wait-phase heartbeats and wakeup-spawned parallel calls).
     * With no wait-phase messages the index is the list tail, which
     * reproduces the previous tail-append ordering exactly (zero regression).
     * <p>
     * Locator miss (no matching tool_call found — a caller wiring bug) falls
     * back to tail append with a WARN so the pairing problem is visible
     * instead of silently dropping the result.
     */
    private void addToolResultInPlace(AgentExecutionContext ctx,
                                      ChatToolResponseMessage result) {
        if (!insertToolResultInList(ctx.getMessages(), result)) {
            LOG.warn("tool_call for result not found in context; falling back to tail append "
                    + "(callId={}). This indicates a commit/dispatch wiring bug.", result.getCallId());
            ctx.addMessage(result);
        }
    }

    /**
     * List-level twin of {@link #addToolResultInPlace} for request copies:
     * inserts {@code result} at the end of the contiguous
     * tool_call/tool_output segment that starts at the tool_call message with
     * the same callId. Type-based ({@code instanceof}), never role strings.
     * Consumed by the wakeup-request builder (WI4) so placeholder results in
     * a request copy satisfy the provider adjacency contract in multi-heartbeat
     * histories. Does nothing (returns false) when the callId has no
     * tool_call message in the list.
     */
    public static boolean insertToolResultInList(List<ChatMessage> messages, ChatToolResponseMessage result) {
        String callId = result.getCallId();
        int start = -1;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message instanceof ChatToolCallMessage
                    && callId != null && callId.equals(((ChatToolCallMessage) message).getCallId())) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return false;
        }
        int end = start;
        for (int i = start; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message instanceof ChatToolCallMessage || message instanceof ChatToolResponseMessage) {
                end = i;
            } else {
                break;
            }
        }
        messages.add(end + 1, result);
        return true;
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

    /**
     * Spill-to-store path for oversized successful tool results (design §3.3).
     * <p>
     * When the result exceeds the inline threshold AND the tool is
     * spillable (not in {@link ToolResultTruncator#NON_TRUNCATABLE_TOOLS}),
     * the full text is PUT into the session's spill store and the inline
     * content becomes a bounded preview + {@code [SPILL_REF id=...]} marker.
     * <p>
     * Degradation semantics (design §3.3 adjudication):
     * <ul>
     *   <li><b>put failure</b> (memory/capacity) → fall back to plain
     *       truncation and LOG.warn (tool name + reason + bytes) — never
     *       silently drop.</li>
     *   <li><b>null-session</b> (session not registered in the store, e.g.
     *       direct-builder test wiring) → silent truncation, no warn — the
     *       unassembled case is not a failure.</li>
     *   <li><b>NON_TRUNCATABLE_TOOLS</b> (ask-oracle/ask-human) → exempt,
     *       full text stays inline.</li>
     * </ul>
     *
     * @param sessionId the current session id, used to resolve the session's
     *                  spill store; may be {@code null} (unassembled wiring)
     * @param resultText the successful tool result text to inspect
     * @param toolName  the tool that produced the result (exemption check)
     * @return the text to keep inline: the original when below the threshold
     *         or exempt, the preview + {@code [SPILL_REF id=...]} marker when
     *         spilled, or a plain truncation on any degraded path
     */
    private String spillIfOversized(final String sessionId, final String resultText,
                                    final String toolName) {
        if (resultText.length() <= ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS) {
            return resultText;
        }
        if (toolName != null
                && ToolResultTruncator.NON_TRUNCATABLE_TOOLS.contains(toolName)) {
            return resultText;
        }
        if (sessionStore != null && sessionId != null) {
            AgentSession session = sessionStore.get(sessionId);
            if (session != null) {
                try {
                    String spillId = session.getOrCreateSpillStore().put(resultText);
                    String preview = ToolResultTruncator.truncate(resultText,
                            ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS);
                    LOG.debug("Tool result spilled: tool={}, bytes={}, spillId={}, session={}",
                            toolName, resultText.length(), spillId, sessionId);
                    return preview + "\n\n[SPILL_REF id=" + spillId
                            + "] Full result stored; use read-spill to retrieve.";
                } catch (Exception e) {
                    LOG.warn("Spill failed for tool {} ({} bytes, session={}), "
                                    + "degrading to truncation: {}",
                            toolName, resultText.length(), sessionId, e.toString());
                }
            }
            // null-session: unassembled wiring, not a failure — silent truncation.
        }
        return ToolResultTruncator.truncateIfAllowed(resultText,
                ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS, toolName);
    }

}
