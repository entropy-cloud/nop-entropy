package io.nop.ai.agent.runtime.recovery;

/**
 * Action taken by an {@link ISessionTimeoutHandler} for a single
 * timed-out session detected by {@code ScheduledRecoveryManager.scanOnce}
 * (plan 229 / L4-8-P4-TimeoutAbort).
 *
 * <p>A <em>timed-out session</em> is one whose DB status is
 * {@code 'running'} or {@code 'pending'} and whose activity timestamp
 * ({@code ai_agent_session.UPDATED_AT} — design 裁定 2) is older than
 * the configured {@code timeoutSeconds} wall-clock threshold. The daemon
 * detects these via the timeout-detection step inside {@code scanOnce}
 * (run after stale-lock cleanup and before orphan detection — design
 * 裁定 3); an {@link ISessionTimeoutHandler} then decides what to do
 * with each one based on the three-way lock-ownership classification
 * (design 裁定 1):
 *
 * <ul>
 *   <li><b>Local ownership</b> (this instance holds an active takeover
 *       lock) → {@link #LOCAL_CANCELLED}: delegate to
 *       {@code IAgentEngine.cancelSession(sessionId, "timeout", true)}
 *       (forced graceful + thread interrupt). Terminal status
 *       {@code cancelled} / {@code forced_stopped}.</li>
 *   <li><b>No active lock</b> (orphaned — stale lock already cleaned by
 *       step 1, or never held) → {@link #FORCE_FAILED}: raw JDBC
 *       conditional {@code UPDATE ai_agent_session SET STATUS='failed'
 *       WHERE SESSION_ID=? AND STATUS IN ('running','pending')}.
 *       Terminal status {@code failed}.</li>
 *   <li><b>Remote ownership</b> (another instance holds an active
 *       takeover lock) → {@link #SKIPPED_REMOTE}: LOG.warn the remote
 *       owner and do not intervene. The remote instance's own daemon is
 *       responsible for handling its timeouts; intervening would cause
 *       cross-instance DB-status contention.</li>
 *   <li><b>NoOp shipped default</b> ({@link NoOpSessionTimeoutHandler})
 *       → {@link #SKIPPED}: LOG.warn the timed-out session ID and take
 *       no action. Preserves zero behaviour regression with plan 226
 *       shipped default.</li>
 * </ul>
 *
 * <p>See plan 229 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.3 (RecoveryManager
 * workflow step 3 — timeout handling).
 */
public enum TimeoutAction {

    /**
     * The timed-out session was actively owned by this engine instance
     * (active takeover lock with {@code LOCK_OWNER == instanceId}).
     * Delegated to
     * {@code IAgentEngine.cancelSession(sessionId, "timeout", true)}
     * (forced=true encapsulates graceful + thread interrupt — plan 197).
     * The session transitions to {@code cancelled} or
     * {@code forced_stopped} via the existing cancel path.
     *
     * <p>{@code succeeded=true} when {@code cancelSession} was invoked
     * without a synchronous exception; {@code succeeded=false} when
     * {@code cancelSession} threw (session not found / already terminal
     * / engine error) — the exception is captured in the outcome
     * message (non-silent, Minimum Rules #24).
     */
    LOCAL_CANCELLED,

    /**
     * The timed-out session had no active takeover lock (orphaned —
     * stale lock already cleaned in scan step 1, or never held). The
     * handler performed a raw JDBC conditional
     * {@code UPDATE ai_agent_session SET STATUS='failed' WHERE
     * SESSION_ID=? AND STATUS IN ('running','pending')} (conditional
     * WHERE prevents marking a session that already transitioned —
     * design 裁定 1, mirroring plan 226 ABORT).
     *
     * <p>{@code succeeded=true} when affected rows=1 (status transition
     * applied); {@code succeeded=false} when affected rows=0 (session
     * already transitioned to terminal between detection and UPDATE).
     */
    FORCE_FAILED,

    /**
     * The timed-out session is actively owned by a <em>remote</em>
     * instance (active takeover lock with
     * {@code LOCK_OWNER != instanceId}). The handler LOG.warn's the
     * remote owner and takes no DB / engine action — intervening would
     * cause cross-instance status contention (the remote instance may
     * overwrite {@code failed} back to {@code running}). The remote
     * instance's own daemon is expected to handle its timeouts, or its
     * lease will expire and the session will become an orphan in a
     * later scan.
     *
     * <p>Always {@code succeeded=true} (the deliberate skip is itself a
     * successful non-intervention decision, observed and logged).
     */
    SKIPPED_REMOTE,

    /**
     * NoOp shipped default ({@link NoOpSessionTimeoutHandler}). The
     * handler LOG.warn's the timed-out session ID and takes no action,
     * preserving zero behaviour regression with plan 226 shipped
     * default (timeout detection is observe-only out of the box).
     *
     * <p>Always {@code succeeded=true} (the deliberate skip is an
     * observation-only success).
     */
    SKIPPED
}
