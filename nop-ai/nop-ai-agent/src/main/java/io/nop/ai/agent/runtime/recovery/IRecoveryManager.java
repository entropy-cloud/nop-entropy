package io.nop.ai.agent.runtime.recovery;

/**
 * RecoveryManager daemon — extends nop-ai-agent session recovery from a
 * one-shot startup scan ({@code restorePendingSessions}) to a continuously
 * running periodic sweep. This interface is the opt-in extension point;
 * the shipped default is {@link NoOpRecoveryManager} (zero behaviour
 * regression), and the functional implementation is
 * {@code ScheduledRecoveryManager} (plan 222 / L4-8-P4-RecoveryDaemon).
 *
 * <p><b>Scope of this version (plan 222)</b>: each {@link #scanOnce}
 * performs exactly two DB operations against the existing
 * {@code ai_agent_session} / {@code ai_agent_session_lock} tables:
 * <ol>
 *   <li><b>Stale lock cleanup</b>: DELETE rows from
 *       {@code ai_agent_session_lock} whose {@code LOCK_EXPIRES_AT <= now}.
 *       Idempotent — DELETE of absent rows is a no-op, so concurrent
 *       scans from multiple instances are safe. Releasing stale locks
 *       makes the corresponding sessions eligible for restoration.</li>
 *   <li><b>Orphan session detection</b>: SELECT sessions with
 *       {@code STATUS IN ('running','pending')} that have no active
 *       (non-expired) takeover lock, and LOG.warn each orphan session ID.
 *       This version does not auto-recover orphans (resume/retry/abort
 *       is an explicit successor — see Non-Goals).</li>
 * </ol>
 *
 * <h2>Lifecycle semantics (start / stop)</h2>
 * {@link #start} registers a fixed-delay periodic task on the configured
 * {@code IScheduledExecutor} (default 60s); {@link #stop} cancels it.
 * Both are <b>idempotent</b> — repeated start (or stop) calls have no
 * additional effect beyond the first.
 *
 * <p><b>Deployment-layer lifecycle (design 裁定)</b>: the engine does
 * <em>not</em> call {@code start()}/{@code stop()} on the
 * RecoveryManager. {@code IAgentEngine}'s design contract states that
 * lifecycle management is a deployment-layer decision, not an
 * engine-layer contract. Integrators wire the RecoveryManager via
 * {@code DefaultAgentEngine.setRecoveryManager} and then call
 * {@code start()} (e.g. after app startup) and {@code stop()} (e.g.
 * before app shutdown) from the deployment layer.
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent calls to {@link #scanOnce}
 * and the lifecycle methods. {@code ScheduledRecoveryManager} achieves
 * this via a {@code volatile} {@code ScheduledFuture} handle guarded by
 * {@code synchronized} start/stop.
 *
 * <p>See plan 222 and design {@code nop-ai-agent-actor-runtime-vision.md}
 * §6.3 / §10 Phase 4.
 */
public interface IRecoveryManager {

    /**
     * Start the periodic scan (idempotent). After the first successful
     * call, a fixed-delay task is registered on the configured
     * {@code IScheduledExecutor}; subsequent calls before {@link #stop}
     * are no-ops.
     */
    void start();

    /**
     * Stop the periodic scan (idempotent). Cancels the registered task
     * (best-effort, {@code mayInterruptIfRunning=false}). Subsequent
     * calls when no task is registered are no-ops.
     */
    void stop();

    /**
     * Run a single scan synchronously and return the result. Exposed as
     * a public method so tests can trigger a scan without relying on
     * scheduler timing, and so integrators can trigger an on-demand scan.
     * This is also the unit of work scheduled by {@link #start}.
     *
     * <p>Under the shipped {@link NoOpRecoveryManager} default this
     * returns an all-zero {@link RecoveryScanResult} — an explicit "no
     * recovery scanning" semantic, not a silent no-op.
     *
     * @return a non-null snapshot of this scan's outcome
     */
    RecoveryScanResult scanOnce();
}
