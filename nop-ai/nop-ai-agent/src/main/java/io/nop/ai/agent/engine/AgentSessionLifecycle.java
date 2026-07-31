package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.runtime.AgentActor;
import io.nop.ai.agent.security.IPathAccessChecker;
import io.nop.ai.agent.security.IToolAccessChecker;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Session-lifecycle orchestration for the engine (extracted from
 * {@link DefaultAgentEngine}, MA4.2-05): resume/restore execution
 * (takeover-lock acquisition, session recovery, budgeted-memory injection,
 * execution dispatch), the shared base execution-context builder, cancel
 * event publication and the {@link CancelHandle} holder.
 */
public class AgentSessionLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);
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
        payload.put("reason", reason != null ? reason : "");
        payload.put("forced", forced);
        eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_CANCEL_REQUESTED,
                sessionId, agentName, payload));
    }
    public void publishCancelled(String sessionId, String agentName, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason != null ? reason : "");
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
    static final class CancelHandle {
        final AgentExecutionContext context;
        volatile Thread thread;
        volatile Future<?> renewHandle;

        CancelHandle(AgentExecutionContext context, Thread thread) {
            this.context = context;
            this.thread = thread;
        }
    }
    public AgentExecutionContext buildBaseExecutionContext(AgentModel agentModel, AgentSession session) {
        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, session.getSessionId());

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
            throw new NopAiAgentException(
                    "resumeSession failed: session not found: sessionId=" + sessionId);
        }
        if (session.getStatus() != AgentExecStatus.paused) {
            throw new NopAiAgentException(
                    "resumeSession failed: session is not paused (status=" + session.getStatus()
                            + "), only paused sessions can be resumed: sessionId=" + sessionId);
        }

        String agentName = session.getAgentName();

        // source, so the tenant context must be re-established from the
        // persisted session. Without this the ledger's reset/clear SQL would
        // run with tenant=null and DELETE every tenant's denial rows for this
        // sessionId (cross-tenant data destruction). Capture the session's
        // tenantId and scope the count/reset to it, restoring the caller's
        // context afterward so the synchronous phase never leaks tenant state.
        String sessionTenantId = session.getTenantId();
        String previousTenant = ThreadLocalTenantResolver.current();
        ThreadLocalTenantResolver.set(sessionTenantId);
        try {
            // Capture the pre-reset denial count for the audit event before clearing.
            int preResetDenialCount = config.getDenialLedger().getDenialCount(sessionId);

            // Clear the pause by resetting the ledger (design §6.2 sticky
            // recovery). With the tenant context now set, the ledger's reset
            // SQL includes the tenant WHERE — only this tenant's denials are
            // cleared.
            config.getDenialLedger().reset(sessionId);

            // denied-fingerprint set. Without this, a resumed session's next
            // identical tool call is treated as a blind retry and blocked by
            // the guard before Layer 1 — driving the session straight back to
            // pause within 3 iterations, making the recovery path useless.
            // Placed inside the tenant-scoped try, right after the ledger
            // reset, so future tenant-aware guard implementations inherit the
            // correct tenant context.
            config.getPostDenialGuard().reset(sessionId);

            // Transition the session back to running before re-execution.
            session.setStatus(AgentExecStatus.running);

            Map<String, Object> resumePayload = new HashMap<>();
            resumePayload.put("approver", approver != null ? approver : "");
            resumePayload.put("reason", reason != null ? reason : "");
            resumePayload.put("preResetDenialCount", preResetDenialCount);
            eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_RESUMED,
                    sessionId, agentName, resumePayload));
        } finally {
            ThreadLocalTenantResolver.set(previousTenant);
        }

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
        try {
            if (!config.getSessionTakeoverLock().tryAcquire(sessionId, instanceId, config.getLockLeaseMs())) {
                throw new NopAiAgentException(
                        "resumeSession failed: session is locked by another instance: sessionId="
                                + sessionId);
            }
            CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
            if (existing != null) {
                throw new NopAiAgentException(
                        "resumeSession failed: session already executing: sessionId=" + sessionId);
            }
            handle.renewHandle = lockRenewal.startLockRenewal(handle, sessionId, instanceId);
        } catch (RuntimeException e) {
            releaseLockQuietly(sessionId, instanceId);
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

                AgentExecutionResult result;
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
                } finally {
                    runningExecutions.remove(sessionId, handle);
                    // lease was lost (see doExecute).
                    session.setStatus(ctx.isLeaseLost() ? AgentExecStatus.failed : ctx.getStatus());
                    // (mirrors doExecute / restoreSession finally cleanup).
                    config.getWriteIntentRegistry().releaseSession(sessionId);
                    // terminal sessions so it does not grow unbounded.
                    // NOT called for paused — paused is non-terminal and must
                    // retain checkpoints for restoreSession recovery.
                    if (isTerminalStatus(session.getStatus())) {
                        config.getCheckpointManager().remove(sessionId);
                    }
                    if (config.getActorRuntime().isEnabled()) {
                        config.getActorRuntime().getActorBySession(sessionId)
                                .ifPresent(a -> config.getActorRuntime().destroyActor(a.getActorId()));
                    }
                    // 路径 3 — inner finally).
                    releaseLockQuietly(sessionId, instanceId);
                    // (mirrors releaseLockQuietly path 3).
                    SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
                }

            // sync with the intra-execution persistence path (see doExecute
            // for the full rationale). Idempotent full-sync — no duplicate
            // appends.
            session.replaceMessages(ctx.getMessages());

            session.addTokensUsed(ctx.getTokensUsed());
            session.addIterations(ctx.getCurrentIteration());
            session.touch();
            sessionStore.save(session);

            return result;
                } finally {
                    ThreadLocalTenantResolver.clear();
                }
        }, agentExecutorSupplier.get());
        } catch (RuntimeException e) {
            runningExecutions.remove(sessionId, handle);
            releaseLockQuietly(sessionId, instanceId);
            // (mirrors releaseLockQuietly path 2).
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }
    }
    public CompletableFuture<AgentExecutionResult> restoreSession(String sessionId, String approver, String reason) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NopAiAgentException(
                    "restoreSession failed: sessionId must not be null or empty");
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
            throw new NopAiAgentException(
                    "restoreSession failed: no persistent state found for session "
                            + "(was the session ever persisted, or is the session store in-memory only?): sessionId="
                            + sessionId);
        }

        AgentExecStatus currentStatus = session.getStatus();
        if (isTerminalStatus(currentStatus)) {
            throw new NopAiAgentException(
                    "restoreSession failed: session is in a terminal state (status="
                            + currentStatus + "), only non-terminal sessions can be restored: sessionId="
                            + sessionId);
        }

        String agentName = session.getAgentName();

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
        }

        // Transition the session back to running before re-execution. A
        // session that was running when the process crashed has status=running
        // in the persisted file; a pending session has status=pending. Both
        // are non-terminal and valid restore candidates.
        session.setStatus(AgentExecStatus.running);

        // Publish the SESSION_RESTORED audit event carrying approver, reason,
        // and latestCheckpointWatermark for audit trail.
        Map<String, Object> restorePayload = new HashMap<>();
        restorePayload.put("approver", approver != null ? approver : "");
        restorePayload.put("reason", reason != null ? reason : "");
        restorePayload.put("latestCheckpointWatermark",
                latestCheckpointWatermark != null ? latestCheckpointWatermark : "");
        restorePayload.put("preRestoreStatus", currentStatus != null ? currentStatus.name() : "");
        eventPublisher.publish(AgentEvent.create(AgentEventType.SESSION_RESTORED,
                sessionId, agentName, restorePayload));

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

        // phase with putIfAbsent + fail-fast (see doExecute for full rationale).
        //
        // full rationale — tryAcquire before putIfAbsent, release on every
        // cleanup path).
        //
        CancelHandle handle = new CancelHandle(ctx, null);
        try {
            if (!config.getSessionTakeoverLock().tryAcquire(sessionId, instanceId, config.getLockLeaseMs())) {
                throw new NopAiAgentException(
                        "restoreSession failed: session is locked by another instance: sessionId="
                                + sessionId);
            }
            CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
            if (existing != null) {
                throw new NopAiAgentException(
                        "restoreSession failed: session already executing: sessionId=" + sessionId);
            }
            handle.renewHandle = lockRenewal.startLockRenewal(handle, sessionId, instanceId);
        } catch (RuntimeException e) {
            releaseLockQuietly(sessionId, instanceId);
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                // restoreSession has no Principal source in the foundational
                // slice (no request parameter), so the tenant context is null
                // = all data visible (recovery-path semantics).
                ThreadLocalTenantResolver.set(null);
                try {
                handle.thread = Thread.currentThread();

                AgentExecutionResult result;
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
                } finally {
                    runningExecutions.remove(sessionId, handle);
                    // lease was lost (see doExecute).
                    session.setStatus(ctx.isLeaseLost() ? AgentExecStatus.failed : ctx.getStatus());
                    // (mirrors doExecute / resumeSession finally cleanup).
                    config.getWriteIntentRegistry().releaseSession(sessionId);
                    // terminal sessions so it does not grow unbounded.
                    // NOT called for paused — paused is non-terminal and must
                    // retain checkpoints for restoreSession recovery.
                    if (isTerminalStatus(session.getStatus())) {
                        config.getCheckpointManager().remove(sessionId);
                    }
                    if (config.getActorRuntime().isEnabled()) {
                        config.getActorRuntime().getActorBySession(sessionId)
                                .ifPresent(a -> config.getActorRuntime().destroyActor(a.getActorId()));
                    }
                    // 路径 3 — inner finally).
                    releaseLockQuietly(sessionId, instanceId);
                    // (mirrors releaseLockQuietly path 3).
                    SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
                }

            // sync with the intra-execution persistence path.
            session.replaceMessages(ctx.getMessages());

            session.addTokensUsed(ctx.getTokensUsed());
            session.addIterations(ctx.getCurrentIteration());
            session.touch();
            sessionStore.save(session);

            return result;
                } finally {
                    ThreadLocalTenantResolver.clear();
                }
        }, agentExecutorSupplier.get());
        } catch (RuntimeException e) {
            runningExecutions.remove(sessionId, handle);
            releaseLockQuietly(sessionId, instanceId);
            // (mirrors releaseLockQuietly path 2).
            SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
            throw e;
        }
    }
    public static boolean isTerminalStatus(AgentExecStatus status) {
        return status == AgentExecStatus.completed
                || status == AgentExecStatus.failed
                || status == AgentExecStatus.cancelled
                || status == AgentExecStatus.forced_stopped
                || status == AgentExecStatus.escalated
                || status == AgentExecStatus.truncated;
    }

    public SessionRestoreSummary restorePendingSessions(String approver, String reason) {
        java.util.Collection<AgentSession> discovered;
        try {
            discovered = sessionStore.listAllSessions();
        } catch (io.nop.api.core.exceptions.NopException e) {
            // Fail-fast: store does not support discovery. Surface as
            // NopAiAgentException so the operator learns the deployment is
            // misconfigured rather than seeing a silent empty summary.
            throw new NopAiAgentException(
                    "restorePendingSessions failed: the session store does not support "
                            + "discovery (listAllSessions threw NopException: " + e.getErrorCode() + "). "
                            + "Auto-restore requires a discovery-capable store such as "
                            + "FileBackedSessionStore. Underlying error: " + e.getMessage(), e);
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
