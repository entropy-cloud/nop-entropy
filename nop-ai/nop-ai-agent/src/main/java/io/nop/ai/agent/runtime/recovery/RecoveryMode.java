package io.nop.ai.agent.runtime.recovery;

/**
 * Recovery strategy for an orphaned session detected by the
 * {@link IRecoveryManager} daemon (plan 226 / L4-8-P4-RecoveryStrategy).
 *
 * <p>An <em>orphaned session</em> is one whose DB status is
 * {@code 'running'} or {@code 'pending'} but has <em>no</em> active
 * (non-expired) takeover lock — i.e. the owning process crashed or
 * restarted without cleanly transitioning the session. The daemon
 * detects these via {@code ScheduledRecoveryManager.scanOnce}; an
 * {@link IOrphanRecoveryHandler} then decides what to do with each one
 * based on the configured {@code RecoveryMode}.
 *
 * <p><b>First-version modes (this plan)</b>:
 * <ul>
 *   <li>{@link #RESUME} — auto-resume: delegate to
 *       {@code IAgentEngine.restoreSession} (fire-and-forget). The
 *       session resumes its ReAct loop from its last checkpoint.
 *       Safety against double-execution is provided by the cross-process
 *       takeover lock (the restore path internally calls
 *       {@code tryAcquire}; if another instance already owns the lock,
 *       the handler records a SKIPPED outcome — non-silent).</li>
 *   <li>{@link #ABORT} — mark failed: a raw JDBC
 *       {@code UPDATE ai_agent_session SET STATUS='failed' WHERE
 *       SESSION_ID=? AND STATUS IN ('running','pending')} (conditional
 *       WHERE prevents aborting a session that already transitioned).</li>
 *   <li>{@link #SKIP} — observe only: LOG.warn the orphan session ID
 *       and take no recovery action. This is the shipped default
 *       behaviour (via {@link NoOpOrphanRecoveryHandler}), preserving
 *       zero behaviour regression with plan 222.</li>
 * </ul>
 *
 * <p><b>Deferred successor: RETRY</b> — clearing the session state and
 * re-executing the original request from scratch. Requires
 * {@code ISessionStore} contract changes (a reset method) plus a
 * product-strategy decision on tool-call side-effect idempotency when
 * retrying. Classified as an explicit successor plan.
 *
 * <p>See plan 226 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.1 / §6.3.
 */
public enum RecoveryMode {

    /**
     * Auto-resume the orphaned session by delegating to
     * {@code IAgentEngine.restoreSession} (fire-and-forget — the daemon
     * scan loop does not block on the potentially long-running restore).
     * The takeover lock guarantees no double-execution: the restore path
     * internally calls {@code tryAcquire}, and if the lock is already
     * held by another instance the handler records a non-silent SKIPPED
     * outcome.
     */
    RESUME,

    /**
     * Mark the orphaned session as {@code failed} via a conditional raw
     * JDBC UPDATE ({@code WHERE STATUS IN ('running','pending')}). The
     * conditional WHERE prevents aborting a session that has already
     * transitioned (e.g. another instance already resumed it).
     */
    ABORT,

    /**
     * Observe-only: LOG.warn the orphan session ID and take no recovery
     * action. This is the shipped default behaviour (via
     * {@link NoOpOrphanRecoveryHandler}), preserving zero behaviour
     * regression with plan 222.
     */
    SKIP
}
