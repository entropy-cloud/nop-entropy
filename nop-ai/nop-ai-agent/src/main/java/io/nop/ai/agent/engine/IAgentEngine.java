package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;

import java.util.concurrent.CompletableFuture;

public interface IAgentEngine extends AutoCloseable {

    AgentMessageAck sendMessage(AgentMessageRequest request);

    CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request);

    /**
     * Fork the session identified by {@code request.getSessionId()} into an
     * independent child session.
     * <p>
     * The parent session id is taken from {@link AgentMessageRequest#getSessionId()};
     * if it is {@code null} or empty the call fails fast with a
     * {@link NopAiAgentException}. The parent session must exist in the
     * backing {@link io.nop.ai.agent.session.ISessionStore}; otherwise the
     * call fails fast.
     * <p>
     * {@code request.getAgentName()} (if non-null) overrides the child
     * session's agent name; {@code request.getMetadata()} is merged into the
     * child session's metadata.
     * <p>
     * When {@code inheritContext} is {@code true}, the child receives an
     * independent snapshot of the parent's message history, planId reference,
     * and metadata. When {@code false}, the child starts empty.
     * <p>
     * A {@link AgentEventType#SESSION_FORKED} event is published on fork.
     *
     * @param request        the fork request (carries parent sessionId, agentName, metadata)
     * @param inheritContext whether to inherit the parent's message history, planId, and metadata
     * @return a completed future holding the new child session id
     */
    default CompletableFuture<String> forkSession(AgentMessageRequest request, boolean inheritContext) {
        throw new UnsupportedOperationException("forkSession requires Phase 2 ISessionStore");
    }

    default AgentExecStatus getSessionStatus(String sessionId) {
        throw new UnsupportedOperationException("getSessionStatus requires Phase 2");
    }

    default CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
        throw new UnsupportedOperationException("cancelSession requires Phase 2");
    }

    /**
     * Resume a denial-ledger-paused session (design §6.2
     * {@code pauseBehavior = sticky}): this is the human-intervention recovery
     * entry point that clears the pause and re-executes the session.
     * <p>
     * Only a session with status {@link AgentExecStatus#paused} can be resumed.
     * The implementation calls {@link io.nop.ai.agent.security.IDenialLedger#reset}
     * to clear the pause (removing the per-session denial count), publishes a
     * {@link AgentEventType#SESSION_RESUMED} event carrying the
     * {@code approver}, {@code reason}, and {@code preResetDenialCount} for
     * audit, then re-executes the session as a transparent continuation of its
     * existing conversation history (no new user message is appended).
     * <p>
     * Calling this on a non-paused session, a session that does not exist, or
     * an engine with a ledger that never pauses (e.g. {@code NoOpDenialLedger}
     * opt-in) fails fast with a {@link NopAiAgentException} rather than silently
     * no-op'ing. This enforces the sticky contract: only an explicit resume
     * clears a pause; auto-recovery is forbidden.
     *
     * @param sessionId the paused session to resume; must exist and be paused
     * @param approver  the identity of the human operator performing the
     *                  recovery (recorded for audit; never used for permission
     *                  checks in this contract)
     * @param reason    a free-text reason for the recovery (recorded for audit)
     * @return a future that completes with the result of the re-execution
     */
    default CompletableFuture<AgentExecutionResult> resumeSession(String sessionId, String approver, String reason) {
        throw new UnsupportedOperationException("resumeSession requires a registered denial ledger and a paused session");
    }

    /**
     * Restore a session after a process crash/restart (plan 183 crash/restart
     * durable session restore protocol, design §1.1 / §5.4a). This is the
     * <b>crash-restart recovery</b> entry point — distinct from
     * {@link #resumeSession} (which is the <b>sticky-pause</b> recovery entry
     * point, plan 180). The two are mutually exclusive:
     * <ul>
     *   <li>{@code restoreSession}: the session is <b>not</b> in the active
     *       execution map ({@code runningExecutions}) — it was lost when the
     *       process restarted. The implementation loads the session from a
     *       persistent {@link io.nop.ai.agent.session.FileBackedSessionStore},
     *       rebuilds the {@link AgentExecutionContext}, verifies consistency
     *       against the latest {@link io.nop.ai.agent.reliability.Checkpoint}
     *       (checkpoint journal consumption), and resumes ReAct execution.</li>
     *   <li>{@code resumeSession}: the session <b>is</b> in the active memory
     *       and its status is {@link AgentExecStatus#paused} (sticky-pause by
     *       Layer 3 denial-ledger governance). The implementation clears the
     *       pause via {@code IDenialLedger.reset} and resumes.</li>
     * </ul>
     * <p>
     * Calling this on a session that is still in active memory (not a
     * crash-restart scenario), a session that has no persistent state, or a
     * session that is in a terminal state (completed/failed/cancelled/
     * forced_stopped/escalated) fails fast with a {@link NopAiAgentException}
     * rather than silently no-op'ing. This enforces the crash-restart
     * contract: restore is only valid for a session that was running when
     * the process crashed and is now absent from the active execution map.
     *
     * @param sessionId the crashed session to restore; must have persistent
     *                  state and must not be in the active execution map
     * @param approver  the identity of the operator performing the recovery
     *                  (recorded for audit; never used for permission checks)
     * @param reason    a free-text reason for the recovery (recorded for audit)
     * @return a future that completes with the result of the resumed execution
     */
    default CompletableFuture<AgentExecutionResult> restoreSession(String sessionId, String approver, String reason) {
        throw new UnsupportedOperationException(
                "restoreSession requires a FileBackedSessionStore-backed engine");
    }

    /**
     * Auto restore-on-startup batch orchestrator (plan 184, design §1.1
     * recovery model). This is the <b>"unattended automation"</b> entry point
     * that turns plan 183's single-session {@link #restoreSession} primitive
     * into a full scan-and-restore workflow: after a process crash/restart,
     * the caller invokes this method on a freshly-constructed engine pointing
     * at the same persistent session/checkpoint root directories, and every
     * unfinished session is automatically discovered and resumed — without
     * any caller having to know a sessionId ahead of time.
     *
     * <p><b>Protocol</b> (each step realized on top of existing primitives):
     * <ol>
     *   <li><b>Discover</b>: call {@code sessionStore.listAllSessions()} to
     *       enumerate every persisted session (including those not yet in the
     *       in-memory cache). This is the new discovery contract introduced
     *       by plan 184.</li>
     *   <li><b>Filter</b>: select restore candidates by status. Only
     *       {@link AgentExecStatus#running} (crashed mid-execution) and
     *       {@link AgentExecStatus#pending} (never started) are candidates.
     *       {@link AgentExecStatus#paused} is <b>skipped</b> — sticky-pause is
     *       a Layer 3 governance state that requires an explicit human
     *       {@link #resumeSession} (plan 180); auto-restoring a paused
     *       session would silently bypass the human-intervention contract.
     *       Terminal statuses (completed/failed/cancelled/forced_stopped/
     *       escalated) are skipped because they already reached a final
     *       outcome.</li>
     *   <li><b>Sequential restore</b>: for each candidate, call
     *       {@link #restoreSession(String, String, String)} and wait for it
     *       to complete (sequential — not parallel — to avoid concurrent LLM
     *       calls that could trip provider rate limits). Per-session failure
     *       isolation: if one restore throws, the failure is recorded and the
     *       next candidate is still processed; the batch never aborts on a
     *       single session failure.</li>
     *   <li><b>Summary</b>: return a {@link SessionRestoreSummary} with the
     *       restored / skipped / failed buckets and per-session reasons.</li>
     * </ol>
     *
     * <p><b>Fail-fast</b>: if the session store does not support discovery
     * (its {@code listAllSessions()} throws {@link UnsupportedOperationException}
     * or is not overridden), this method throws
     * {@link NopAiAgentException} rather than silently returning an empty
     * summary — "store cannot discover" is a deployment misconfiguration that
     * must surface to the operator. An <em>empty</em> store (no persisted
     * sessions, e.g. a fresh root or an in-memory store whose cache is empty
     * after a restart) is a legitimate state and returns an empty summary,
     * not an exception.
     *
     * <p><b>Opt-in, not lifecycle-coupled</b>: this method is an explicit
     * opt-in. The engine does <em>not</em> call it from a constructor or any
     * lifecycle callback — <em>when</em> to run auto-restore (at startup? on
     * a timer?) is a deployment-layer decision, not an engine-layer contract.
     * The caller invokes it after construction to implement "restore on
     * startup".
     *
     * <p><b>Blocking</b>: this method blocks until every candidate restore has
     * completed (each {@code restoreSession} future is joined). For a large
     * backlog the caller may wish to invoke it from a dedicated thread; the
     * engine makes no threading guarantee beyond "sequential per session".
     *
     * @param approver the identity of the operator (or automated system)
     *                 performing the batch recovery; recorded for audit on
     *                 every per-session {@code SESSION_RESTORED} event
     * @param reason   a free-text reason for the batch recovery (recorded for audit)
     * @return a summary of the restore outcome (restored / skipped / failed);
     *         never null, possibly empty if no sessions were discovered
     */
    default SessionRestoreSummary restorePendingSessions(String approver, String reason) {
        throw new UnsupportedOperationException(
                "restorePendingSessions requires a DefaultAgentEngine with a discovery-capable session store");
    }

    /**
     * Plan 278 (AR-09): lifecycle termination entry point. The default
     * implementation is a no-op so existing {@link IAgentEngine}
     * implementations (including ~32 in-tree test stubs) continue to compile
     * and behave identically without source changes.
     *
     * <p>{@link DefaultAgentEngine} overrides this to shut down its
     * self-created thread pools ({@code lockRenewExecutor} /
     * {@code agentExecutor}). Externally injected pools are NOT closed (the
     * caller owns their lifecycle). The override is idempotent (a second
     * close is a no-op). In-flight executions are NOT cancelled (that is
     * the caller's responsibility, e.g. via {@code cancelSession} or
     * {@code restorePendingSessions} before close).
     *
     * <p>Extending {@link AutoCloseable} makes {@link IAgentEngine}
     * try-with-resources compatible and provides a legal
     * {@code @Override} target for the default method.
     */
    @Override
    default void close() throws Exception {
    }
}
