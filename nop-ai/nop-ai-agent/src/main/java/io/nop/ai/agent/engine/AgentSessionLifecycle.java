package io.nop.ai.agent.engine;

import io.nop.ai.agent.NopAiAgentException;
import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.runtime.AgentActor;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.api.core.exceptions.NopException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Session-lifecycle orchestration for the engine (extracted from
 * {@link DefaultAgentEngine}, MA4.2-05): resume/restore execution
 * (takeover-lock acquisition, session recovery, budgeted-memory injection,
 * execution dispatch), the shared base execution-context builder, cancel
 * event publication and the {@link CancelHandle} holder.
 */
public class AgentSessionLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(AgentSessionLifecycle.class);
    private final DefaultAgentEngineConfig config;
    private final ISessionStore sessionStore;
    private final IAgentEventPublisher eventPublisher;
    private final String instanceId;
    private final java.util.concurrent.ConcurrentHashMap<String, CancelHandle> runningExecutions;
    private final java.util.function.Supplier<ExecutorService> agentExecutorSupplier;
    private final AgentExecutorResolver executorResolver;
    private final AgentSessionSupport sessionSupport;
    private final AgentTeamBinder teamBinder;
    private final SessionLockRenewal lockRenewal;
    private final AgentStartupWarnings startupWarnings;
    private final DefaultAgentEngine engine;
    /**
     * Plan 2519 (裁定①): live injection-target registry, shared with the
     * engine (wired by {@link DefaultAgentEngine} right after construction).
     * The three recovery paths (resume/wake/restore) register/unregister
     * symmetrically with {@code doExecute} so the injection window is the
     * execution window — the previous zero-registration left injections during
     * recovery silently lost (C1-008).
     */
    private volatile java.util.concurrent.ConcurrentHashMap<String, AgentExecutionContext> injectionTargets;
    /**
     * Plan 2519 (裁定②): per-session dedup marker for the
     * {@code nop.ai.agent.terminal-cleanup-miss} WARN — concurrent terminal
     * paths reaching the same session report the first failure only.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> terminalCleanupMissReported =
            new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * 裁定② 终态语义适配: retained terminal statuses of evicted sessions
     * (enum only). Bounds = number of terminal sessions since process start —
     * O(1) tiny entries; unbounded growth is accepted at the same order as
     * audit counters (CL-IND-01 retention family owns durable retention).
     */
    private final java.util.concurrent.ConcurrentHashMap<String, AgentExecStatus> terminalStatuses =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Plan 2519 (裁定①): wire the shared injection-target registry (engine-owned map). */
    public void setInjectionTargetRegistry(
            java.util.concurrent.ConcurrentHashMap<String, AgentExecutionContext> injectionTargets) {
        this.injectionTargets = injectionTargets;
    }

    /**
     * Plan 2519 (裁定①): register this execution as the live injection target
     * for the session (execution window open). No-op when the registry was
     * never wired (defensive — should not happen in engine assembly).
     */
    void registerInjectionTarget(String sessionId, AgentExecutionContext ctx) {
        java.util.concurrent.ConcurrentHashMap<String, AgentExecutionContext> registry = injectionTargets;
        if (registry != null) {
            registry.put(sessionId, ctx);
        }
    }

    /**
     * Plan 2519 (裁定①): symmetric unregister (execution window closed /
     * terminal removal step 3). Must run AFTER the terminal marker is set
     * (in-lock drain) so post-marker injectInput is rejected instead of
     * silently lost.
     */
    void unregisterInjectionTarget(String sessionId) {
        java.util.concurrent.ConcurrentHashMap<String, AgentExecutionContext> registry = injectionTargets;
        if (registry != null) {
            registry.remove(sessionId);
        }
    }

    /**
     * Plan 2519 (裁定①终态摘除序 step 1, in-lock): set the session's terminal
     * marker and drain residual injections (staged pendingInjections + the
     * live ctx steering queue) so they are persisted with the terminal save —
     * structurally no injection can be both unconsumed and unrejected
     * (C3-014/CL-061 lost-write window closure).
     */
    public void drainTerminalInjections(String sessionId, AgentSession session,
                                        AgentExecutionContext ctx) {
        synchronized (session) {
            session.markTerminal();
        }
        java.util.List<AgentSession.PendingInjection> staged = session.drainPendingInjections();
        java.util.List<ChatMessage> steeringResidual = ctx.drainSteering();
        for (AgentSession.PendingInjection p : staged) {
            try {
                ChatMessage msg = io.nop.core.lang.json.JsonTool.parseBeanFromText(
                        p.getMessageJson(), ChatMessage.class);
                ctx.addMessage(msg);
            } catch (Exception e) {
                LOG.warn("nop.ai.agent.terminal-drain-parse-failed: dropped unparseable staged "
                        + "injection during terminal drain: sessionId={}, semantics={}",
                        sessionId, p.getSemantics(), e);
            }
        }
        for (ChatMessage msg : steeringResidual) {
            ctx.addMessage(msg);
        }
        if (!staged.isEmpty() || !steeringResidual.isEmpty()) {
            LOG.info("nop.ai.agent.terminal-drain: persisted {} staged + {} steering residual "
                            + "injection(s) with the terminal save: sessionId={}",
                    staged.size(), steeringResidual.size(), sessionId);
        }
    }

    /**
     * Plan 2519 (裁定②): sessionStore.remove terminal wiring (F4 terminal
     * cleanup family; the three store implementations already provide remove).
     * The persisted layer keeps the terminal-marked row (AgentExecStatus
     * status retention for DB/FileBacked) — only the in-memory cache is
     * evicted (终态语义矩阵, design §3.2). Cleanup is remove-idempotent; a
     * cleanup failure is reported at WARN once per session
     * ({@code nop.ai.agent.terminal-cleanup-miss}, 双入去重) and never blocks
     * the terminal path.
     */
    public void removeTerminalSessionFromStore(String sessionId, AgentExecStatus terminalStatus) {
        // Plan 2519 裁定② deviation (registered): `completed` is execution-terminal
        // but NOT session-lifecycle-terminal — the multi-turn continuation contract
        // re-executes a completed session in place (store-resident message history;
        // TestDefaultAgentEngineMultiTurn and 15+ sibling pins). Only the true
        // session-lifecycle terminal statuses (failed/cancelled/forced_stopped/
        // escalated/truncated) evict the store entry; the checkpoint-side cleanup
        // (pre-existing at all terminal statuses) is unaffected. The F4 unbounded-
        // growth face for completed sessions is carried by the registered deviation
        // note and the CL-IND-01 retention-policy successor.
        if (terminalStatus == AgentExecStatus.completed) {
            return;
        }
        // F4 disk purge: the persisted journal/snapshot of a lifecycle-terminal
        // session is deleted (completed keeps its journal — see deviation above).
        if (config != null && config.getCheckpointManager() != null) {
            config.getCheckpointManager().purgeTerminal(sessionId);
        }
        // 裁定② 终态语义适配（调用点盘点 C 类）：the terminal status survives
        // the eviction in a tiny side map (enum only — message history IS
        // released) so the query surface (getSessionStatus) keeps reporting
        // the terminal outcome after cleanup (TestDefaultAgentEngineCancel
        // pin stays green, zero-rollback).
        terminalStatuses.put(sessionId, terminalStatus);
        try {
            sessionStore.remove(sessionId);
        } catch (RuntimeException e) {
            if (terminalCleanupMissReported.putIfAbsent(sessionId, Boolean.TRUE) == null) {
                LOG.warn("nop.ai.agent.terminal-cleanup-miss: failed to remove terminal session "
                        + "from the session store (subsequent failures for this session are "
                        + "deduplicated): sessionId={}", sessionId, e);
            }
        }
    }

    /**
     * 裁定② 终态语义适配: the retained terminal status of an evicted session,
     * or {@code null} when the session has no terminal record.
     */
    public AgentExecStatus getEvictedTerminalStatus(String sessionId) {
        return terminalStatuses.get(sessionId);
    }

    public AgentSessionLifecycle(DefaultAgentEngineConfig config,
                                 ISessionStore sessionStore,
                                 IAgentEventPublisher eventPublisher,
                                 String instanceId,
                                 java.util.concurrent.ConcurrentHashMap<String, CancelHandle> runningExecutions,
                                 java.util.function.Supplier<ExecutorService> agentExecutorSupplier,
                                 AgentExecutorResolver executorResolver,
                                 AgentSessionSupport sessionSupport,
                                 AgentTeamBinder teamBinder,
                                 SessionLockRenewal lockRenewal,
                                 AgentStartupWarnings startupWarnings,
                                 DefaultAgentEngine engine) {
        this.config = config;
        this.sessionStore = sessionStore;
        this.eventPublisher = eventPublisher;
        this.instanceId = instanceId;
        this.runningExecutions = runningExecutions;
        this.agentExecutorSupplier = agentExecutorSupplier;
        this.executorResolver = executorResolver;
        this.sessionSupport = sessionSupport;
        this.teamBinder = teamBinder;
        this.lockRenewal = lockRenewal;
        this.startupWarnings = startupWarnings;
        this.engine = engine;
    }

    // ---- moved verbatim from DefaultAgentEngine (MA4.2-05 split) ----
    public void releaseLockQuietly(String sessionId, String ownerId) {
        try {
            config.getSessionTakeoverLock().release(sessionId, ownerId);
        } catch (RuntimeException e) {
            LOG.warn("DefaultAgentEngine: failed to release takeover lock for sessionId={}, "
                    + "ownerId={} (the lease will auto-expire via TTL): {}", sessionId, ownerId,
                    e.toString());
        }
    }
    public void publishCancelRequested(String sessionId, String agentName, String reason, boolean forced) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(AgentEventPayloads.REASON, reason != null ? reason : "");
        payload.put(AgentEventPayloads.FORCED, forced);
        eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_CANCEL_REQUESTED,
                sessionId, agentName, payload));
    }
    public void publishCancelled(String sessionId, String agentName, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(AgentEventPayloads.REASON, reason != null ? reason : "");
        eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_CANCELLED,
                sessionId, agentName, payload));
    }
    /**
     * Plan 197 (AUDIT-14-01): {@code thread} is {@code volatile} and
     * lazily bound. The handle is pre-registered in the synchronous phase
     * (before {@code supplyAsync}) to close the cancel-lost-window — at
     * that point the ForkJoinPool execution thread is not yet known, so
     * {@code thread} is initialized to {@code null}. The lambda updates it
     * to {@code Thread.currentThread()} at entry. {@code cancelSession}
     * null-checks before {@code interrupt()} so a forced cancel during the
     * enqueue window does not interrupt the calling thread.
     *
     * <p>Plan 273 (carry-over 14-06): {@code renewHandle} is {@code volatile}
     * and lazily bound in the synchronous phase right after the takeover lock
     * is acquired. It is read by the supplyAsync lambda's cleanup finally
     * (which runs on the worker thread) — storing it on the handle (rather
     * than a local) lets the lambda capture the effectively-final handle and
     * still observe the renewal task set in the synchronous phase. The
     * happens-before edge from supplyAsync-submission → task-execution
     * guarantees the worker sees the assigned value.
     */
    /**
     * 3.1-MA-J (plan 2555 裁定 J / C1-010): static null-ctx sentinel marker
     * occupying {@code runningExecutions} during the cancelSession
     * unregistered branch. The sentinel's {@code context} is {@code null} —
     * real handles always carry a live context (the field stays {@code
     * final}); any code path that must dereference {@code handle.context}
     * is guarded by an identity check against this marker first.
     */
    static final CancelHandle CANCEL_SENTINEL = new CancelHandle(null, null);

    /**
     * 3.1-MA-J (plan 2555 裁定 J / C1-010): sentinel-loser consumption for
     * the resume/wake/restore registration sites (and registerExecutionSlot).
     * When a just-built execution's {@code putIfAbsent} loses to the
     * sentinel, the loser consumes it via the TWO-ARG conditional remove
     * (never the one-arg remove — that would delete a real handle after the
     * sentinel winner finished and re-open the assembly window) and marks
     * the just-built context cancel-requested so the execution
     * self-terminates through the existing registered-cancel flow (its own
     * lease is released by that execution's normal terminal finally).
     *
     * @return true when {@code existing} was the sentinel (caller must still
     *         fail-fast the duplicate submission exactly as before)
     */
    boolean consumeCancelSentinel(String sessionId, CancelHandle existing, AgentExecutionContext ctx) {
        if (existing != CANCEL_SENTINEL) {
            return false;
        }
        if (runningExecutions.remove(sessionId, CANCEL_SENTINEL)) {
            if (ctx != null) {
                ctx.setCancelRequested(true);
            }
            LOG.info("nop.ai.agent.cancel-during-assembly: consumed cancel sentinel for "
                    + "sessionId={} — the losing execution was marked cancel-requested and "
                    + "will self-terminate via the registered-cancel flow", sessionId);
        }
        return true;
    }

    static final class CancelHandle {
        final AgentExecutionContext context;
        volatile Thread thread;
        volatile Future<?> renewHandle;
        /**
         * Plan 2519 (裁定⑩ / 3.1-C7-012): consecutive takeover-lock renewal
         * failures, attributed per handle (per running session — each
         * execution owns exactly one handle). Reset to zero on every
         * successful renewal; reaching the configured bound escalates to the
         * lease-lost abort path instead of warning forever (the pre-fix
         * infinite-WARN tolerance was the C7-012 defect). Per-handle
         * attribution isolates concurrent sessions.
         */
        final java.util.concurrent.atomic.AtomicInteger renewalFailureCount =
                new java.util.concurrent.atomic.AtomicInteger();

        CancelHandle(AgentExecutionContext context, Thread thread) {
            this.context = context;
            this.thread = thread;
        }
    }
    public AgentExecutionContext buildBaseExecutionContext(AgentModel agentModel, AgentSession session) {
        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, session.getSessionId());
        // Plan 2289 (WI8): bridge the injection event publisher (nullable —
        // direct ctx construction stays silent) so enqueues are observable.
        ctx.setInjectionEventPublisher(eventPublisher, session.getAgentName());
        // Plan 2289 (WI8): merge offline-injected entries (injectInput while
        // the session was not executing) into this execution's queue, then
        // clear the staging + persist (single-writer: caller's thread before
        // the execution slot is registered).
        if (!session.getPendingInjections().isEmpty()) {
            // Plan 2519 (裁定①): atomic snapshot+clear under the session
            // monitor — the previous iterate-then-clear was two separate
            // critical sections (a concurrent injectInput could land between
            // them and be dropped or double-counted).
            for (AgentSession.PendingInjection staged : session.drainPendingInjections()) {
                // messageJson is a JSON string (not a map) — parse to ChatMessage
                ChatMessage msg = io.nop.core.lang.json.JsonTool.parseBeanFromText(
                        staged.getMessageJson(), ChatMessage.class);
                InjectionSemantics semantics = InjectionSemantics.STEER;
                try {
                    semantics = InjectionSemantics.valueOf(staged.getSemantics());
                } catch (IllegalArgumentException e) {
                    semantics = InjectionSemantics.STEER;
                }
                ctx.enqueueSteering(msg, semantics);
            }
            sessionStore.save(session);
        }

        String systemPrompt = null;
        if (agentModel.getPrompt() != null) {
            systemPrompt = agentModel.getPrompt().getSource();
        }

        // Memory is a session-level persistent context (like the system prompt),
        // so it is injected here — the single point shared by doExecute (new
        // turn) and resumeSession (recovery continuation). Only non-empty
        // budgeted memory is injected (backward compatible). A null provider
        // (test-only opt-out) or budget <= 0 (explicit opt-out) skips injection
        // without throwing.
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ctx.addMessage(new ChatSystemMessage(systemPrompt));
        }

        String memorySection = buildBudgetedMemorySection(session.getSessionId());
        if (memorySection != null) {
            // Memory content is added as a separate system message, not
            // concatenated into the original system prompt. This creates a
            // natural privilege boundary: the LLM architecture gives the
            // first (original) system message higher priority over subsequent
            // system messages, preventing cross-turn prompt injection
            // amplification via user-derived memory content.
            ctx.addMessage(new ChatSystemMessage(memorySection));
        }

        if (session.getMessageCount() > 0) {
            ctx.getMessages().addAll(session.getMessages());
        }

        return ctx;
    }
    public String buildBudgetedMemorySection(String sessionId) {
        if (config.getMemoryStoreProvider() == null || config.getMemoryInjectionBudgetTokens() <= 0) {
            return null;
        }
        java.util.Map<String, Object> context = new HashMap<>();
        context.put("sessionId", sessionId);
        context.put("source", "system-prompt-auto-injection");

        java.util.List<io.nop.ai.agent.memory.AiMemoryItem> items;
        try {
            io.nop.ai.agent.memory.IAiMemoryStore store = config.getMemoryStoreProvider().getOrCreate(sessionId);
            items = store.readBudgeted(config.getMemoryInjectionBudgetTokens(), context);
        } catch (RuntimeException e) {
            LOG.warn("Budgeted memory injection skipped for sessionId={}", sessionId, e);
            return null;
        }

        if (items == null || items.isEmpty()) {
            return null;
        }

        return formatMemorySection(items);
    }
    public static String formatMemorySection(java.util.List<io.nop.ai.agent.memory.AiMemoryItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Working Memory\n");
        for (io.nop.ai.agent.memory.AiMemoryItem item : items) {
            sb.append("- ");
            String key = item.getKey();
            String type = item.getType();
            boolean hasMeta = false;
            if (type != null && !type.isEmpty()) {
                sb.append('[').append(type).append("] ");
                hasMeta = true;
            }
            if (key != null && !key.isEmpty()) {
                sb.append('[').append(key).append("] ");
                hasMeta = true;
            }
            sb.append(item.getContent() != null ? item.getContent() : "");
            sb.append('\n');
        }
        // Trim the trailing newline so the section is clean.
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '\n') {
            sb.setLength(len - 1);
        }
        return sb.toString();
    }
    public CompletableFuture<AgentExecutionResult> resumeSession(String sessionId, String approver, String reason) {
        AgentSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "resumeSession failed: session not found: sessionId=" + sessionId);
        }
        if (session.getStatus() != AgentExecStatus.paused) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "resumeSession failed: session is not paused (status=" + session.getStatus()
                            + "), only paused sessions can be resumed: sessionId=" + sessionId);
        }

        String agentName = session.getAgentName();

        // resumeSession has no request/Principal source, so the tenant context
        // must be re-established from the persisted session. Without this the
        // ledger's reset/clear SQL would run with tenant=null and DELETE every
        // tenant's denial rows for this sessionId (cross-tenant data
        // destruction). Capture the session's tenantId and scope the
        // count/reset to it, restoring the caller's context afterward so the
        // synchronous phase never leaks tenant state. The tenant-scoped reset
        // block itself only runs AFTER the takeover lock is acquired (below) —
        // a failed tryAcquire must leave the shared live session untouched.
        String sessionTenantId = session.getTenantId();
        String previousTenant = ThreadLocalTenantResolver.current();

        // Re-execute the session as a transparent continuation: rebuild the
        // context from the agent model + the existing conversation history (NO
        // new user message is appended — resume continues where the paused
        // execution left off, letting the LLM re-plan from the last denied
        // tool-call error response rather than starting a new turn).
        AgentModel agentModel = sessionSupport.loadAgentModel(agentName);
        teamBinder.precheckTeamDeclarations(agentModel);
        AgentExecutionContext ctx = buildBaseExecutionContext(agentModel, session);

        // Resolve the executor with the engine's own checkers (no parent
        // constraint applies on resume — resume is a top-level recovery action,
        // not a sub-agent call). Per-agent path rules are still honoured so the
        // resumed execution is consistent with a normal top-level execution.
        IToolAccessChecker effectiveToolAccessChecker = config.getToolAccessChecker();
        IPathAccessChecker effectivePathAccessChecker = executorResolver.resolvePerAgentPathChecker(agentModel);
        sessionSupport.ensureSessionMailbox(sessionId);
        IAgentExecutor executor = executorResolver.resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);

        // phase with putIfAbsent + fail-fast (see doExecute for full rationale).
        //
        // full rationale — tryAcquire before putIfAbsent, release on every
        // cleanup path).
        //
        CancelHandle handle = new CancelHandle(ctx, null);
        boolean slotRegistered = false;
        try {
            if (!config.getSessionTakeoverLock().tryAcquire(sessionId, instanceId, config.getLockLeaseMs())) {
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "resumeSession failed: session is locked by another instance: sessionId="
                                + sessionId);
            }
            CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
            if (existing != null) {
                // 3.1-MA-J (plan 2555 裁定 J): losing to the cancel sentinel
                // consumes it and marks the just-built ctx cancel-requested
                // (execution self-terminates); the duplicate submission
                // still fails fast with the unchanged error.
                consumeCancelSentinel(sessionId, existing, ctx);
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "resumeSession failed: session already executing: sessionId=" + sessionId);
            }
            slotRegistered = true;
            handle.renewHandle = lockRenewal.startLockRenewal(handle, sessionId, instanceId);

            // The takeover lock is acquired (including putIfAbsent success):
            // only NOW mutate the shared live session. A failed tryAcquire /
            // putIfAbsent leaves zero in-memory mutations — the cached session
            // stays paused with its denial evidence intact, and no misleading
            // SESSION_RESUMED event is published (round-4 audit: the reset +
            // status change used to run before the lock acquisition, bricking
            // the cached session as running with the pause evidence cleared).
            ThreadLocalTenantResolver.set(sessionTenantId);
            try {
                // Capture the pre-reset denial count for the audit event before clearing.
                int preResetDenialCount = config.getDenialLedger().getDenialCount(sessionId);

                // Clear the pause by resetting the ledger (design §6.2 sticky
                // recovery). With the tenant context now set, the ledger's reset
                // SQL includes the tenant WHERE — only this tenant's denials are
                // cleared.
                config.getDenialLedger().reset(sessionId);

                // Clear the denied-fingerprint set. Without this, a resumed
                // session's next identical tool call is treated as a blind
                // retry and blocked by the guard before Layer 1 — driving the
                // session straight back to pause within 3 iterations, making
                // the recovery path useless. Placed inside the tenant-scoped
                // try, right after the ledger reset, so future tenant-aware
                // guard implementations inherit the correct tenant context.
                config.getPostDenialGuard().reset(sessionId);

                // Transition the session back to running before re-execution.
                session.setStatus(AgentExecStatus.running);

                Map<String, Object> resumePayload = new HashMap<>();
                resumePayload.put(AgentEventPayloads.APPROVER, approver != null ? approver : "");
                resumePayload.put(AgentEventPayloads.REASON, reason != null ? reason : "");
                resumePayload.put(AgentEventPayloads.PRE_RESET_DENIAL_COUNT, preResetDenialCount);
                eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_RESUMED,
                        sessionId, agentName, resumePayload));
            } finally {
                ThreadLocalTenantResolver.set(previousTenant);
            }
        } catch (RuntimeException e) {
            // M6-P1 (round-2 audit): see DefaultAgentEngine.registerExecutionSlot
            // — release the lease ONLY when THIS call registered the execution
            // slot (putIfAbsent succeeded). Same-owner tryAcquire is an
            // idempotent renewal, so a losing duplicate submit also "acquires";
            // releasing here would delete the winning execution's lease row.
            // This also covers a failure of the tenant-scoped reset block above
            // (slotRegistered is true then, so the lease is released).
            if (slotRegistered) {
                releaseLockQuietly(sessionId, instanceId);
            }
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                // on the worker thread. resumeSession has no request/Principal
                // source, so the tenant context is re-established from the
                // persisted session (captured above as sessionTenantId) — NOT
                // forced to null, which would make any tenant-scoped DB
                // operation on this thread see all tenants' data.
                ThreadLocalTenantResolver.set(sessionTenantId);
                try {
                handle.thread = Thread.currentThread();
                // Plan 2519 (裁定①): symmetric registration with doExecute —
                // the recovery execution window is injection-reachable.
                registerInjectionTarget(sessionId, ctx);

                AgentExecutionResult result;
                boolean executionCompleted = false;
                try {
                    // this inner try (see doExecute) so a failure in either
                    // triggers the symmetric cleanup in the finally below.
                    //
                    // Actor (see doExecute).
                    if (config.getActorRuntime().isEnabled()) {
                        AgentActor actor = config.getActorRuntime().createActor(sessionId, agentName);
                        actor.setSteeringQueue(ctx.getSteeringQueue());
                    }

                    // Runs after createActor so the actorId is available.
                    teamBinder.autoBindTeam(agentModel, sessionId, agentName);

                    result = executor.execute(ctx).toCompletableFuture().join();
                    executionCompleted = true;
                } finally {
                    // Plan 2519 (裁定①终态摘除序 step 1, in-lock BEFORE registry
                    // remove): terminal marker + drain (see doExecute).
                    boolean terminalExit = isSessionLifecycleTerminal(
                            ctx.isLeaseLost() ? AgentExecStatus.failed : ctx.getStatus());
                    if (terminalExit) {
                        drainTerminalInjections(sessionId, session, ctx);
                    }
                    runningExecutions.remove(sessionId, handle);
                    unregisterInjectionTarget(sessionId);
                    // lease was lost (see doExecute).
                    session.setStatus(ctx.isLeaseLost() ? AgentExecStatus.failed : ctx.getStatus());
                    // (mirrors doExecute / restoreSession finally cleanup).
                    config.getWriteIntentRegistry().releaseSession(sessionId);
                    if (config.getActorRuntime().isEnabled()) {
                        config.getActorRuntime().getActorBySession(sessionId)
                                .ifPresent(a -> config.getActorRuntime().destroyActor(a.getActorId()));
                    }
                    try {
                        if (executionCompleted) {
                            // Success path: persist the terminal state BEFORE
                            // releasing the lease and BEFORE removing the
                            // terminal checkpoint — no "unlocked but stale
                            // persisted state" window (a crash or save failure
                            // here cannot leave another instance free to
                            // resume a stale session and re-execute the same
                            // user turn). Idempotent full-sync — no duplicate
                            // appends.
                            session.replaceMessages(ctx.getMessages());
                            session.addTokensUsed(ctx.getTokensUsed());
                            session.addIterations(ctx.getCurrentIteration());
                            session.touch();
                            sessionStore.save(session);
                            // terminal sessions so it does not grow unbounded.
                            // NOT called for paused — paused is non-terminal
                            // and must retain checkpoints for restoreSession
                            // recovery. Plan 2519 (裁定②): the terminal cleanup
                            // segment also removes the session from the store.
                            if (isTerminalStatus(session.getStatus())) {
                                config.getCheckpointManager().remove(sessionId);
                                removeTerminalSessionFromStore(sessionId, session.getStatus());
                            }
                        } else if (isTerminalStatus(session.getStatus())) {
                            // Failure path (executor/autoBindTeam threw):
                            // preserve the original cleanup order — checkpoint
                            // removal without terminal persistence.
                            config.getCheckpointManager().remove(sessionId);
                            removeTerminalSessionFromStore(sessionId, session.getStatus());
                        }
                    } finally {
                        // 路径 3 — inner finally). Fault-tolerant release
                        // regardless of save outcome — a failed save must not
                        // leave a permanently-held lease (auto-expires via TTL
                        // otherwise).
                        releaseLockQuietly(sessionId, instanceId);
                        // (mirrors releaseLockQuietly path 3).
                        SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
                    }
                }

                return result;
                } finally {
                    ThreadLocalTenantResolver.clear();
                }
        }, agentExecutorSupplier.get());
        } catch (RuntimeException e) {
            runningExecutions.remove(sessionId, handle);
            unregisterInjectionTarget(sessionId);
            releaseLockQuietly(sessionId, instanceId);
            // (mirrors releaseLockQuietly path 2).
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }
    }
    public CompletableFuture<AgentExecutionResult> wakeSession(String sessionId) {
        AgentSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "wakeSession failed: session not found: sessionId=" + sessionId);
        }
        if (session.getStatus() != AgentExecStatus.waiting) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "wakeSession failed: session is not waiting (status=" + session.getStatus()
                            + "), only waiting sessions can be woken: sessionId=" + sessionId);
        }

        String agentName = session.getAgentName();

        // wakeSession has no request/Principal source, so the tenant context is
        // re-established from the persisted session (symmetric with
        // resumeSession): the synchronous phase and the worker lambda both run
        // tenant-scoped DB operations (denial ledger / session store) and must
        // not silently run with tenant=null (cross-tenant visibility).
        String sessionTenantId = session.getTenantId();
        String previousTenant = ThreadLocalTenantResolver.current();

        // Re-execute the session as a transparent continuation: rebuild the
        // context from the agent model + the existing conversation history.
        AgentModel agentModel = sessionSupport.loadAgentModel(agentName);
        teamBinder.precheckTeamDeclarations(agentModel);
        AgentExecutionContext ctx = buildBaseExecutionContext(agentModel, session);

        IToolAccessChecker effectiveToolAccessChecker = config.getToolAccessChecker();
        IPathAccessChecker effectivePathAccessChecker = executorResolver.resolvePerAgentPathChecker(agentModel);
        sessionSupport.ensureSessionMailbox(sessionId);
        IAgentExecutor executor = executorResolver.resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);

        CancelHandle handle = new CancelHandle(ctx, null);
        boolean slotRegistered = false;
        try {
            if (!config.getSessionTakeoverLock().tryAcquire(sessionId, instanceId, config.getLockLeaseMs())) {
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "wakeSession failed: session is locked by another instance: sessionId="
                                + sessionId);
            }
            CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
            if (existing != null) {
                // 3.1-MA-J (plan 2555 裁定 J): losing to the cancel sentinel
                // consumes it and marks the just-built ctx cancel-requested
                // (execution self-terminates); the duplicate submission
                // still fails fast with the unchanged error.
                consumeCancelSentinel(sessionId, existing, ctx);
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "wakeSession failed: session already executing: sessionId=" + sessionId);
            }
            slotRegistered = true;
            handle.renewHandle = lockRenewal.startLockRenewal(handle, sessionId, instanceId);

            // The takeover lock is acquired (including putIfAbsent success):
            // only NOW mutate the shared live session / coordinator state. A
            // failed tryAcquire / putIfAbsent leaves zero in-memory mutations —
            // the cached session stays waiting, the wake condition stays
            // unsatisfied, and no misleading SESSION_WOKE event is published
            // (round-4 audit: deliverWake + setStatus(running) used to run
            // before the lock acquisition, bricking retries as "not waiting").
            ThreadLocalTenantResolver.set(sessionTenantId);
            try {
                // Mark the condition satisfied via the coordinator (design §13.1
                // Decision C/H): deliverWake sets the satisfied flag so the next
                // checkWait at the registration point returns PROCEED (skip
                // suspend), preventing re-suspend on replay re-entry.
                config.getWaitCoordinator().deliverWake(sessionId, null);

                // Transition the session back to running before re-execution.
                session.setStatus(AgentExecStatus.running);

                Map<String, Object> wakePayload = new HashMap<>();
                wakePayload.put(AgentEventPayloads.REASON, "condition satisfied");
                eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_WOKE,
                        sessionId, agentName, wakePayload));
            } finally {
                ThreadLocalTenantResolver.set(previousTenant);
            }
        } catch (RuntimeException e) {
            // M6-P1 (round-2 audit): see DefaultAgentEngine.registerExecutionSlot
            // — release the lease ONLY when THIS call registered the execution
            // slot (putIfAbsent succeeded). Same-owner tryAcquire is an
            // idempotent renewal, so a losing duplicate submit also "acquires";
            // releasing here would delete the winning execution's lease row.
            // This also covers a failure of the tenant-scoped wake block above
            // (slotRegistered is true then, so the lease is released).
            if (slotRegistered) {
                releaseLockQuietly(sessionId, instanceId);
            }
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                // on the worker thread. wakeSession has no request/Principal
                // source, so the tenant context is re-established from the
                // persisted session (symmetric with resumeSession) — NOT forced
                // to null, which would make any tenant-scoped DB operation on
                // this thread see all tenants' data.
                ThreadLocalTenantResolver.set(sessionTenantId);
                try {
                handle.thread = Thread.currentThread();
                // Plan 2519 (裁定①): symmetric registration with doExecute.
                registerInjectionTarget(sessionId, ctx);
                AgentExecutionResult result;
                boolean executionCompleted = false;
                try {
                    if (config.getActorRuntime().isEnabled()) {
                        AgentActor actor = config.getActorRuntime().createActor(sessionId, agentName);
                        actor.setSteeringQueue(ctx.getSteeringQueue());
                    }
                    teamBinder.autoBindTeam(agentModel, sessionId, agentName);

                    result = executor.execute(ctx).toCompletableFuture().join();
                    executionCompleted = true;
                } finally {
                    // Plan 2519 (裁定①终态摘除序 step 1, in-lock BEFORE registry
                    // remove): terminal marker + drain (see doExecute).
                    boolean terminalExit = isSessionLifecycleTerminal(
                            ctx.isLeaseLost() ? AgentExecStatus.failed : ctx.getStatus());
                    if (terminalExit) {
                        drainTerminalInjections(sessionId, session, ctx);
                    }
                    runningExecutions.remove(sessionId, handle);
                    unregisterInjectionTarget(sessionId);
                    session.setStatus(ctx.isLeaseLost() ? AgentExecStatus.failed : ctx.getStatus());
                    config.getWriteIntentRegistry().releaseSession(sessionId);
                    if (config.getActorRuntime().isEnabled()) {
                        config.getActorRuntime().getActorBySession(sessionId)
                                .ifPresent(a -> config.getActorRuntime().destroyActor(a.getActorId()));
                    }
                    try {
                        if (executionCompleted) {
                            // Success path: persist the terminal state BEFORE
                            // releasing the lease and BEFORE removing the
                            // terminal checkpoint — no "unlocked but stale
                            // persisted state" window. Idempotent full-sync —
                            // no duplicate appends.
                            session.replaceMessages(ctx.getMessages());
                            session.addTokensUsed(ctx.getTokensUsed());
                            session.addIterations(ctx.getCurrentIteration());
                            session.touch();
                            sessionStore.save(session);
                            // terminal sessions so it does not grow unbounded.
                            // Plan 2519 (裁定②): sessionStore.remove wiring.
                            if (isTerminalStatus(session.getStatus())) {
                                config.getCheckpointManager().remove(sessionId);
                                removeTerminalSessionFromStore(sessionId, session.getStatus());
                            }
                        } else if (isTerminalStatus(session.getStatus())) {
                            // Failure path: preserve the original cleanup order
                            // (checkpoint removal without terminal persistence).
                            config.getCheckpointManager().remove(sessionId);
                            removeTerminalSessionFromStore(sessionId, session.getStatus());
                        }
                    } finally {
                        // Fault-tolerant release regardless of save outcome — a
                        // failed save must not leave a permanently-held lease.
                        releaseLockQuietly(sessionId, instanceId);
                        SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
                    }
                }

                return result;
                } finally {
                    ThreadLocalTenantResolver.clear();
                }
            }, agentExecutorSupplier.get());
        } catch (RuntimeException e) {
            runningExecutions.remove(sessionId, handle);
            unregisterInjectionTarget(sessionId);
            releaseLockQuietly(sessionId, instanceId);
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }
    }
    public CompletableFuture<AgentExecutionResult> restoreSession(String sessionId, String approver, String reason) {
        AgentSession session = validateRestorableSession(sessionId);

        String agentName = session.getAgentName();
        AgentExecStatus currentStatus = session.getStatus();

        // 3.1-C7-015（plan 2556 裁定 J）: verifyLatestCheckpoint is read-only and
        // may stay pre-lock.
        CheckpointVerification verification = verifyLatestCheckpoint(sessionId, session);

        ExecutionWiring wiring = buildExecutionWiring(agentName, session, sessionId);

        CancelHandle handle = new CancelHandle(wiring.ctx(), null);

        // 3.1-C7-015（plan 2556 裁定 J）: the RUNNING transition + SESSION_RESTORED
        // event publish moved AFTER acquireRestoreExecutionSlot succeeds —
        // mirroring resumeSession (:441) lock-gate discipline: a failed slot
        // acquisition (lock held by another instance / duplicate execution)
        // must leave the shared live session untouched (no running flip, no
        // misleading SESSION_RESTORED event). If the transition itself throws
        // after the slot was acquired, the symmetric cleanup below releases
        // the slot (same shape as the resumeSession catch block).
        boolean slotAcquired = false;
        try {
            acquireRestoreExecutionSlot(sessionId, handle);
            slotAcquired = true;
            transitionToRunningAndPublishRestored(session, sessionId, agentName, approver, reason,
                    currentStatus, verification);
        } catch (RuntimeException e) {
            if (slotAcquired) {
                // acquireRestoreExecutionSlot succeeded but the transition
                // threw — release the execution slot symmetrically (see the
                // resumeSession / M6-P1 lease-release discipline).
                runningExecutions.remove(sessionId, handle);
                releaseLockQuietly(sessionId, instanceId);
                SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            }
            throw e;
        }

        return dispatchRestoreExecution(sessionId, agentName, session, handle, wiring);
    }

    /**
     * AI-12b (plan 355): extracted from restoreSession — entry validation:
     * non-empty sessionId, persistent-state load and terminal-status check.
     * Returns the loaded session (never null; throws on every failure path).
     */
    private AgentSession validateRestorableSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "restoreSession failed: sessionId must not be null or empty");
        }
        // putIfAbsent below is the atomic dedup guard; the old containsKey was
        // a TOCTOU race (could pass, then another thread registers before
        // putIfAbsent). All three entry points now share the same fail-fast
        // behavior via putIfAbsent.

        // Load from persistent store (FileBackedSessionStore.get returns the
        // persisted session on cache-miss; InMemorySessionStore.get returns
        // null for unknown sessions, which is the correct "no persistent
        // state" signal).
        AgentSession session = sessionStore.get(sessionId);
        if (session == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "restoreSession failed: no persistent state found for session "
                            + "(was the session ever persisted, or is the session store in-memory only?): sessionId="
                            + sessionId);
        }

        AgentExecStatus currentStatus = session.getStatus();
        if (isTerminalStatus(currentStatus)) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "restoreSession failed: session is in a terminal state (status="
                            + currentStatus + "), only non-terminal sessions can be restored: sessionId="
                            + sessionId);
        }
        return session;
    }

    /**
     * AI-12b (plan 355): extracted from restoreSession — checkpoint journal
     * consumption.
     */
    private CheckpointVerification verifyLatestCheckpoint(String sessionId, AgentSession session) {
        // Checkpoint journal consumption (plan 182 investment realized on
        // the restore path): the latest checkpoint provides resume-point
        // metadata + a consistency check (checkpoint.messageCount ≤ persisted
        // session.messageCount, since the checkpoint was written after a tool
        // execution that produced messages now present in the session file).
        // Best-effort: a warning is logged on inconsistency but recovery is
        // not blocked — the persisted session is the source of truth, the
        // checkpoint is a verification supplement, not a message source.
        Checkpoint latestCheckpoint = config.getCheckpointManager().getLatestCheckpoint(sessionId);
        String latestCheckpointWatermark = null;
        boolean divergenceDetected = false;
        String rejectedCheckpointWatermark = null;
        if (latestCheckpoint != null) {
            latestCheckpointWatermark = latestCheckpoint.getWatermark();
            int checkpointMsgCount = latestCheckpoint.getMessageCount();
            int sessionMsgCount = session.getMessageCount();
            if (checkpointMsgCount > sessionMsgCount) {
                LOG.warn("restoreSession checkpoint consistency warning: checkpoint messageCount {} "
                                + "exceeds persisted session messageCount {} — persisted history may be incomplete. "
                                + "Continuing with best-effort recovery (session is source of truth). session={}",
                        checkpointMsgCount, sessionMsgCount, sessionId);
            }

            // Design §13.2: idempotency_key divergence detection. A non-null
            // key (TOOL_EXECUTION checkpoint) enables a stronger check than
            // messageCount: recompute the key from the persisted session's
            // tool-call message at the same callId and compare. A mismatch
            // means the tool-call input has diverged (state corruption), so
            // the checkpoint is rejected as a resume-point verification and
            // restore degrades to session replay (persisted session remains
            // the source of truth — recovery is NOT blocked). A null key
            // (legacy data / non-TOOL_EXECUTION) falls back to the
            // best-effort messageCount check above (zero regression).
            String storedKey = latestCheckpoint.getIdempotencyKey();
            if (storedKey != null) {
                String liveKey = recomputeToolExecutionKey(session, latestCheckpoint.getCallId());
                if (liveKey == null || !liveKey.equals(storedKey)) {
                    divergenceDetected = true;
                    rejectedCheckpointWatermark = latestCheckpointWatermark;
                    // Plan 2287 (WI6): a phase-tagged checkpoint with a missing
                    // live key is ambiguous — a wakeup-derived call not yet
                    // persisted vs a truncated history are indistinguishable
                    // from the snapshot alone. Conservative discipline keeps
                    // the divergence flag; the message reclassifies the cause
                    // instead of asserting state corruption.
                    ToolExecutionPhase checkpointPhase =
                            ToolExecutionPhase.fromWatermark(latestCheckpoint.getWatermark());
                    if (checkpointPhase != null && liveKey == null) {
                        LOG.warn("restoreSession checkpoint divergence (reclassified): phase-tagged "
                                        + "checkpoint ({}) has no matching persisted call message — "
                                        + "inconclusive cause (wakeup-derived call not yet persisted, OR "
                                        + "truncated history); flagging divergence conservatively. "
                                        + "Rejecting checkpoint as resume-point verification, recovery "
                                        + "not blocked. session={}",
                                latestCheckpoint.getWatermark(), sessionId);
                    } else {
                        LOG.warn("restoreSession checkpoint divergence detected: idempotency_key mismatch "
                                        + "(stored={}, live={}) for callId={} watermark={} — tool-call input has "
                                        + "diverged (state corruption). Rejecting checkpoint as resume-point "
                                        + "verification and degrading to session replay (session is source of "
                                        + "truth, recovery not blocked). session={}",
                                storedKey, liveKey, latestCheckpoint.getCallId(),
                                latestCheckpointWatermark, sessionId);
                    }
                }
            }
        }

        logOpenToolCallDiff(sessionId, session);
        return new CheckpointVerification(latestCheckpointWatermark, divergenceDetected,
                rejectedCheckpointWatermark);
    }

    /**
     * Plan 2284 (WI3 invariant 3): restore-time open tool_call diff — open
     * set = snapshot messages × TOOL_EXECUTION checkpoint callIds. Three-state
     * semantics (plan 2284): (a) message-level open with no checkpoint =
     * never-committed orphan, WARN (the recoverable-orphan surface that
     * successor WI10 synthesis will consume); (b) message-level closed (any
     * result, including security-deny error results) = terminal, silent;
     * (c) message-level open with a checkpoint = committed-but-unsynced
     * crash window, DEBUG only — the messageCount warning above already owns
     * that state. Best-effort: never blocks recovery. With the NoOp
     * checkpoint default the checkpoint list is empty; state (c) is empty and
     * every message-level open call alarms — but a NoOp manager also means
     * no TOOL_EXECUTION checkpoints exist at all, so a message-level open
     * call genuinely is a never-committed call. Session messages are the
     * source of truth; recovery is not blocked either way.
     */
    private void logOpenToolCallDiff(String sessionId, AgentSession session) {
        List<ChatMessage> snapshotMessages = session.getMessages();
        if (snapshotMessages == null || snapshotMessages.isEmpty()) {
            return;
        }
        java.util.Set<String> openCallIds = OpenToolCallResolver.findOpenCallIds(snapshotMessages);
        if (openCallIds.isEmpty()) {
            return;
        }
        List<Checkpoint> checkpoints = config.getCheckpointManager().getCheckpoints(sessionId);
        java.util.Set<String> neverCommitted = OpenToolCallResolver.filterNeverCommitted(openCallIds, checkpoints);
        java.util.Set<String> committedButUnsynced = OpenToolCallResolver.filterCommittedButUnsynced(openCallIds, checkpoints);
        if (!neverCommitted.isEmpty()) {
            // Plan 2287 (WI6): phase-graded orphan alarm — EXECUTING (sandbox
            // observably started; side effects possible) ranks above
            // DISPATCHED (intent only). Conservative discipline: both mean
            // "result undeterminable — explicit failure, never a blind
            // rerun"; the disposition (synthetic closure) is autonomous WI10.
            Map<String, List<String>> graded = gradeOrphanPhases(neverCommitted, checkpoints);
            List<String> executingOrphans = graded.getOrDefault("EXECUTING", java.util.Collections.emptyList());
            List<String> dispatchedOrphans = graded.getOrDefault("DISPATCHED", java.util.Collections.emptyList());
            if (!executingOrphans.isEmpty()) {
                LOG.warn("restoreSession open tool_call diff: {} tool call(s) reached EXECUTING "
                                + "phase with no terminal checkpoint (side effects possible, result "
                                + "undeterminable — explicit failure, never blind rerun; callIds={}). "
                                + "Continuing with best-effort recovery (session is source of truth). session={}",
                        executingOrphans.size(), executingOrphans, sessionId);
            }
            if (!dispatchedOrphans.isEmpty()) {
                LOG.warn("restoreSession open tool_call diff: {} tool call(s) reached DISPATCHED "
                                + "phase with no terminal checkpoint (dispatch intent, result "
                                + "undeterminable — explicit failure, never blind rerun; callIds={}). "
                                + "Continuing with best-effort recovery (session is source of truth). session={}",
                        dispatchedOrphans.size(), dispatchedOrphans, sessionId);
            }
        }
        if (!committedButUnsynced.isEmpty()) {
            LOG.debug("restoreSession open tool_call diff: {} tool call(s) committed (TOOL_EXECUTION "
                    + "checkpoint present) but missing from the persisted snapshot (commit→session-sync "
                    + "crash window, owned by the messageCount consistency check; callIds={}). session={}",
                    committedButUnsynced.size(), committedButUnsynced, sessionId);
        }
    }

    /**
     * Plan 2287 (WI6): phase-grades never-committed orphan callIds by their
     * furthest recorded execution phase. Package-visible for focused
     * verification of the restore alarm split (EXECUTING vs DISPATCHED rows).
     */
    static Map<String, List<String>> gradeOrphanPhases(java.util.Set<String> neverCommitted,
                                                       List<Checkpoint> checkpoints) {
        List<String> executing = new java.util.ArrayList<>();
        List<String> dispatched = new java.util.ArrayList<>();
        for (String callId : neverCommitted) {
            ToolExecutionPhase phase = OpenToolCallResolver.resolveExecutionPhase(callId, checkpoints);
            if (phase == ToolExecutionPhase.EXECUTING) {
                executing.add(callId);
            } else {
                dispatched.add(callId);
            }
        }
        Map<String, List<String>> graded = new java.util.LinkedHashMap<>();
        graded.put("EXECUTING", executing);
        graded.put("DISPATCHED", dispatched);
        return graded;
    }

    /**
     * AI-12b (plan 355): extracted from restoreSession — status transition
     * back to running + SESSION_RESTORED audit event publication (carrying
     * approver, reason, latestCheckpointWatermark and the Design §13.2
     * Decision B divergence markers).
     */
    private void transitionToRunningAndPublishRestored(AgentSession session, String sessionId,
                                                       String agentName, String approver, String reason,
                                                       AgentExecStatus currentStatus,
                                                       CheckpointVerification verification) {
        // Transition the session back to running before re-execution. A
        // session that was running when the process crashed has status=running
        // in the persisted file; a pending session has status=pending. Both
        // are non-terminal and valid restore candidates.
        session.setStatus(AgentExecStatus.running);

        // Publish the SESSION_RESTORED audit event carrying approver, reason,
        // and latestCheckpointWatermark for audit trail.
        Map<String, Object> restorePayload = new HashMap<>();
        restorePayload.put(AgentEventPayloads.APPROVER, approver != null ? approver : "");
        restorePayload.put(AgentEventPayloads.REASON, reason != null ? reason : "");
        restorePayload.put(AgentEventPayloads.LATEST_CHECKPOINT_WATERMARK,
                verification.latestCheckpointWatermark() != null ? verification.latestCheckpointWatermark() : "");
        restorePayload.put(AgentEventPayloads.PRE_RESTORE_STATUS, currentStatus != null ? currentStatus.name() : "");
        // Design §13.2 Decision B: the divergence flag + rejected watermark
        // make checkpoint rejection observably distinct from the best-effort
        // messageCount warning (divergenceDetected=false / empty here means
        // "checkpoint accepted or best-effort fallback").
        restorePayload.put(AgentEventPayloads.DIVERGENCE_DETECTED, verification.divergenceDetected());
        restorePayload.put(AgentEventPayloads.REJECTED_CHECKPOINT_WATERMARK,
                verification.rejectedCheckpointWatermark() != null ? verification.rejectedCheckpointWatermark() : "");
        eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_RESTORED,
                sessionId, agentName, restorePayload));
    }

    /**
     * AI-12b (plan 355): extracted from restoreSession — rebuild the
     * execution context from the agent model + the persisted conversation
     * history and resolve the executor (no parent constraint applies on
     * restore — top-level recovery action).
     */
    private ExecutionWiring buildExecutionWiring(String agentName, AgentSession session, String sessionId) {
        // Rebuild the execution context from the agent model + the persisted
        // conversation history (NO new user message — restore continues where
        // the crashed execution left off, letting the LLM re-plan from the
        // last completed tool result rather than starting a new turn).
        AgentModel agentModel = sessionSupport.loadAgentModel(agentName);
        teamBinder.precheckTeamDeclarations(agentModel);
        AgentExecutionContext ctx = buildBaseExecutionContext(agentModel, session);

        IToolAccessChecker effectiveToolAccessChecker = config.getToolAccessChecker();
        IPathAccessChecker effectivePathAccessChecker = executorResolver.resolvePerAgentPathChecker(agentModel);
        sessionSupport.ensureSessionMailbox(sessionId);
        IAgentExecutor executor = executorResolver.resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);
        return new ExecutionWiring(agentModel, ctx, executor);
    }

    /**
     * AI-12b (plan 355): extracted from restoreSession — the synchronous
     * registration phase: takeover-lock tryAcquire + putIfAbsent fail-fast +
     * lock-renewal start, with symmetric cleanup on failure.
     */
    private void acquireRestoreExecutionSlot(String sessionId, CancelHandle handle) {
        // phase with putIfAbsent + fail-fast (see doExecute for full rationale).
        //
        // full rationale — tryAcquire before putIfAbsent, release on every
        // cleanup path).
        //
        boolean slotRegistered = false;
        try {
            if (!config.getSessionTakeoverLock().tryAcquire(sessionId, instanceId, config.getLockLeaseMs())) {
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "restoreSession failed: session is locked by another instance: sessionId="
                                + sessionId);
            }
            CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
            if (existing != null) {
                // 3.1-MA-J (plan 2555 裁定 J): sentinel-consume on the
                // just-built ctx (see consumeCancelSentinel) — the duplicate
                // submission still fails fast with the unchanged error.
                consumeCancelSentinel(sessionId, existing, handle.context);
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "restoreSession failed: session already executing: sessionId=" + sessionId);
            }
            slotRegistered = true;
            handle.renewHandle = lockRenewal.startLockRenewal(handle, sessionId, instanceId);
        } catch (RuntimeException e) {
            // M6-P1 (round-2 audit): see DefaultAgentEngine.registerExecutionSlot
            // — release the lease ONLY when THIS call registered the execution
            // slot (putIfAbsent succeeded). Same-owner tryAcquire is an
            // idempotent renewal, so a losing duplicate submit also "acquires";
            // releasing here would delete the winning execution's lease row.
            if (slotRegistered) {
                releaseLockQuietly(sessionId, instanceId);
            }
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }
    }

    /**
     * AI-12b (plan 355): extracted from restoreSession — the async dispatch:
     * supplyAsync on the agent executor with the worker-thread tenant
     * context, executor invocation, symmetric finally cleanup, and the
     * post-execution session persistence sync. The outer catch mirrors the
     * synchronous-phase cleanup for a rejected/failed submission.
     */
    private CompletableFuture<AgentExecutionResult> dispatchRestoreExecution(String sessionId,
                                                                             String agentName,
                                                                             AgentSession session,
                                                                             CancelHandle handle,
                                                                             ExecutionWiring wiring) {
        AgentModel agentModel = wiring.agentModel();
        AgentExecutionContext ctx = wiring.ctx();
        IAgentExecutor executor = wiring.executor();
        try {
            return CompletableFuture.supplyAsync(() -> {
                // restoreSession has no Principal source in the foundational
                // slice (no request parameter), so the tenant context is null
                // = all data visible (recovery-path semantics).
                ThreadLocalTenantResolver.set(null);
                try {
                handle.thread = Thread.currentThread();
                // Plan 2519 (裁定①): symmetric registration with doExecute.
                registerInjectionTarget(sessionId, ctx);

                AgentExecutionResult result;
                boolean executionCompleted = false;
                try {
                    // this inner try (see doExecute) so a failure in either
                    // triggers the symmetric cleanup in the finally below.
                    //
                    // Actor (see doExecute).
                    if (config.getActorRuntime().isEnabled()) {
                        AgentActor actor = config.getActorRuntime().createActor(sessionId, agentName);
                        actor.setSteeringQueue(ctx.getSteeringQueue());
                    }

                    // Runs after createActor so the actorId is available.
                    teamBinder.autoBindTeam(agentModel, sessionId, agentName);

                    result = executor.execute(ctx).toCompletableFuture().join();
                    executionCompleted = true;
                } finally {
                    // Plan 2519 (裁定①终态摘除序 step 1, in-lock BEFORE registry
                    // remove): terminal marker + drain (see doExecute).
                    boolean terminalExit = isSessionLifecycleTerminal(
                            ctx.isLeaseLost() ? AgentExecStatus.failed : ctx.getStatus());
                    if (terminalExit) {
                        drainTerminalInjections(sessionId, session, ctx);
                    }
                    runningExecutions.remove(sessionId, handle);
                    unregisterInjectionTarget(sessionId);
                    // lease was lost (see doExecute).
                    session.setStatus(ctx.isLeaseLost() ? AgentExecStatus.failed : ctx.getStatus());
                    // (mirrors doExecute / resumeSession finally cleanup).
                    config.getWriteIntentRegistry().releaseSession(sessionId);
                    if (config.getActorRuntime().isEnabled()) {
                        config.getActorRuntime().getActorBySession(sessionId)
                                .ifPresent(a -> config.getActorRuntime().destroyActor(a.getActorId()));
                    }
                    try {
                        if (executionCompleted) {
                            // Success path: persist the terminal state BEFORE
                            // releasing the lease and BEFORE removing the
                            // terminal checkpoint — no "unlocked but stale
                            // persisted state" window (sync with the
                            // intra-execution persistence path; idempotent
                            // full-sync — no duplicate appends).
                            session.replaceMessages(ctx.getMessages());
                            session.addTokensUsed(ctx.getTokensUsed());
                            session.addIterations(ctx.getCurrentIteration());
                            session.touch();
                            sessionStore.save(session);
                            // terminal sessions so it does not grow unbounded.
                            // Plan 2519 (裁定②): sessionStore.remove wiring.
                            if (isTerminalStatus(session.getStatus())) {
                                config.getCheckpointManager().remove(sessionId);
                                removeTerminalSessionFromStore(sessionId, session.getStatus());
                            }
                        } else if (isTerminalStatus(session.getStatus())) {
                            // Failure path: preserve the original cleanup order
                            // (checkpoint removal without terminal persistence).
                            config.getCheckpointManager().remove(sessionId);
                            removeTerminalSessionFromStore(sessionId, session.getStatus());
                        }
                    } finally {
                        // 路径 3 — inner finally). Fault-tolerant release
                        // regardless of save outcome — a failed save must not
                        // leave a permanently-held lease.
                        releaseLockQuietly(sessionId, instanceId);
                        // (mirrors releaseLockQuietly path 3).
                        SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
                    }
                }

                return result;
                } finally {
                    ThreadLocalTenantResolver.clear();
                }
        }, agentExecutorSupplier.get());
        } catch (RuntimeException e) {
            runningExecutions.remove(sessionId, handle);
            unregisterInjectionTarget(sessionId);
            releaseLockQuietly(sessionId, instanceId);
            // (mirrors releaseLockQuietly path 2).
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }
    }

    /**
     * AI-12b (plan 355): the checkpoint-verification results consumed by the
     * SESSION_RESTORED audit event (design §13.2 Decision B).
     */
    private record CheckpointVerification(String latestCheckpointWatermark,
                                          boolean divergenceDetected,
                                          String rejectedCheckpointWatermark) {
    }

    /**
     * AI-12b (plan 355): the resolved agent model + execution context +
     * executor for a restore re-execution.
     */
    private record ExecutionWiring(AgentModel agentModel, AgentExecutionContext ctx, IAgentExecutor executor) {
    }

    public static boolean isTerminalStatus(AgentExecStatus status) {
        return status == AgentExecStatus.completed
                || status == AgentExecStatus.failed
                || status == AgentExecStatus.cancelled
                || status == AgentExecStatus.forced_stopped
                || status == AgentExecStatus.escalated
                || status == AgentExecStatus.truncated;

    }

    /**
     * Plan 2519 裁定①② deviation (registered, see
     * {@code removeTerminalSessionFromStore}): `completed` is execution-terminal
     * but NOT session-lifecycle-terminal — a completed multi-turn session is
     * re-executed in place, so its pending-injection staging channel must stay
     * open and its persisted journal must survive. The terminal removal
     * sequence (marker + drain + store eviction + journal purge) runs only for
     * the true session-lifecycle terminal statuses.
     */
    public static boolean isSessionLifecycleTerminal(AgentExecStatus status) {
        return isTerminalStatus(status) && status != AgentExecStatus.completed;
    }

    public SessionRestoreSummary restorePendingSessions(String approver, String reason) {
        java.util.Collection<AgentSession> discovered;
        try {
            discovered = sessionStore.listAllSessions();
        } catch (io.nop.api.core.exceptions.NopException e) {
            // Fail-fast: store does not support discovery. Surface as
            // NopAiAgentException so the operator learns the deployment is
            // misconfigured rather than seeing a silent empty summary.
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "restorePendingSessions failed: the session store does not support "
                            + "discovery (listAllSessions threw NopException: " + e.getErrorCode() + "). "
                            + "Auto-restore requires a discovery-capable store such as "
                            + "FileBackedSessionStore. Underlying error: " + e.getMessage());
        }
        if (discovered == null || discovered.isEmpty()) {
            return new SessionRestoreSummary(
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList());
        }

        java.util.List<SessionRestoreSummary.Entry> restored = new java.util.ArrayList<>();
        java.util.List<SessionRestoreSummary.SkipEntry> skipped = new java.util.ArrayList<>();
        java.util.List<SessionRestoreSummary.Entry> failed = new java.util.ArrayList<>();

        // Iterate over a snapshot to avoid concurrent-modification surprises
        // if restoreSession mutates the store's cache.
        java.util.List<AgentSession> snapshot = new java.util.ArrayList<>(discovered);
        for (AgentSession session : snapshot) {
            String sessionId = session.getSessionId();
            AgentExecStatus status = session.getStatus();

            if (status == AgentExecStatus.running || status == AgentExecStatus.pending) {
                // another instance. isHeld returns true iff an active (non-
                // expired) lease exists for this sessionId regardless of
                // owner — a true return means another JVM instance is
                // handling this session, so add to skipped (not failed).
                if (config.getSessionTakeoverLock().isHeld(sessionId)) {
                    skipped.add(new SessionRestoreSummary.SkipEntry(
                            sessionId, status, "locked by another instance"));
                    continue;
                }
                // Restore candidate. Delegate to the single-session primitive
                // (Wiring Verification: this calls restoreSession rather than
                // duplicating the restore protocol). Sequential restore with
                // per-session failure isolation.
                try {
                    AgentExecutionResult result = engine.restoreSession(sessionId, approver, reason)
                            .toCompletableFuture().join();
                    restored.add(new SessionRestoreSummary.Entry(
                            sessionId,
                            result.getStatus() != null ? result.getStatus().name() : "unknown"));
                } catch (Throwable t) {
                    // A single session restore failure must not abort the
                    // batch. Record the failure and continue.
                    LOG.warn("DefaultAgentEngine.restorePendingSessions: failed to restore session {}",
                            sessionId, t);
                    failed.add(new SessionRestoreSummary.Entry(sessionId, t.toString()));
                }
            } else if (status == AgentExecStatus.paused) {
                // Governance: sticky-pause requires an explicit human
                // resumeSession (plan 180). Auto-restore would bypass the
                // human-intervention contract.
                skipped.add(new SessionRestoreSummary.SkipEntry(
                        sessionId, status,
                        "paused: sticky-pause requires an explicit resumeSession (plan 180)"));
            } else if (status == AgentExecStatus.waiting) {
                // WAIT_FOR sticky-wait (design §13.1 Decision E): a waiting
                // session is suspended on a condition and must be woken by an
                // explicit wakeSession — auto-restore would bypass the wait
                // condition.
                skipped.add(new SessionRestoreSummary.SkipEntry(
                        sessionId, status,
                        "waiting: sticky-wait requires an explicit wakeSession (design §13.1)"));
            } else if (isTerminalStatus(status)) {
                skipped.add(new SessionRestoreSummary.SkipEntry(
                        sessionId, status,
                        "terminal: session already reached a final outcome"));
            } else {
                skipped.add(new SessionRestoreSummary.SkipEntry(
                        sessionId, status,
                        "non-restorable status: " + status));
            }
        }

        LOG.info("restorePendingSessions completed: restored={}, skipped={}, failed={} (approver={}, reason={})",
                restored.size(), skipped.size(), failed.size(), approver, reason);

        return new SessionRestoreSummary(restored, skipped, failed);
    }

    /**
     * Recompute the {@code TOOL_EXECUTION} idempotency key for the tool call
     * identified by {@code callId} from the persisted session's message
     * history (design §13.2 Decision A). The session is the source of truth
     * for message history; this re-derivation is what lets restore detect
     * whether the tool-call input has diverged from what the checkpoint
     * recorded.
     *
     * <p>Iterates the session messages, finds the {@link ChatAssistantMessage}
     * whose {@link ChatToolCall#getId()} equals {@code callId}, and recomputes
     * {@code hash(toolName + callId + argumentsText)} via
     * {@link Checkpoint#computeIdempotencyKey}.
     *
     * @param session the persisted session whose message history to scan
     * @param callId  the tool-call id to locate; may be null (returns null)
     * @return the recomputed 32-hex-char key, or {@code null} if the callId is
     *         not found in the session's message history (the matching
     *         tool-call message is missing/truncated — treated as divergence
     *         by the caller)
     */
    private String recomputeToolExecutionKey(AgentSession session, String callId) {
        if (callId == null || session == null) {
            return null;
        }
        List<ChatMessage> messages = session.getMessages();
        if (messages == null) {
            return null;
        }
        for (ChatMessage msg : messages) {
            // Plan 329：单一拆分模型——工具调用以独立 ChatToolCallMessage 承载。
            if (msg instanceof io.nop.ai.api.chat.messages.ChatToolCallMessage) {
                io.nop.ai.api.chat.messages.ChatToolCallMessage tcm =
                        (io.nop.ai.api.chat.messages.ChatToolCallMessage) msg;
                if (callId.equals(tcm.getCallId())) {
                    return Checkpoint.computeIdempotencyKey(CheckpointType.TOOL_EXECUTION,
                            tcm.getName(), tcm.getCallId(), tcm.getArgumentsText());
                }
            }
        }
        return null;
    }

    /**
     * Plan 278 (AR-09): lifecycle termination entry point. Shuts down the
     * engine's self-created thread pools ({@code lockRenewExecutor} and
     * {@code agentExecutor}). Externally injected pools (set via
     * {@code setLockRenewExecutor} / {@code setAgentExecutor}) are NOT
     * closed — the caller owns their lifecycle.
     *
     * <p><b>Idempotent</b>: a second close is a no-op (LOG.debug). Does NOT
     * cancel in-flight executions (the caller's responsibility — use
     * {@code cancelSession} or wait for executions to complete before close).
     *
     * <p><b>InterruptedException handling</b>: restores the interrupt flag,
     * logs at WARN, and does NOT rethrow (per plan 278 contract — close
     * should be best-effort and never block the caller with a checked
     * exception from pool shutdown).
     */
}
