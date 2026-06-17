package io.nop.ai.agent.runtime.recovery;

/**
 * Strategy hook invoked by {@code ScheduledRecoveryManager.scanOnce}
 * for each orphaned session it detects (plan 226 /
 * L4-8-P4-RecoveryStrategy).
 *
 * <p>This interface extends nop-ai-agent session recovery from
 * "detect-and-log only" (plan 222 daemon) to "decide-and-act per a
 * pluggable strategy". The shipped default is
 * {@link NoOpOrphanRecoveryHandler} (SKIP mode — LOG.warn, zero
 * behaviour regression with plan 222); the functional implementation is
 * {@code DefaultOrphanRecoveryHandler} (RESUME / ABORT / SKIP).
 *
 * <p><b>Wiring</b>: the handler is a sub-component of the recovery
 * manager, not an engine-layer configuration point. Integrators wire it
 * via {@code ScheduledRecoveryManager.setOrphanRecoveryHandler}. The
 * engine does not hold an orphan-recovery-handler field (the handler is
 * the recovery manager's internal strategy, consistent with the layering
 * where {@code setRecoveryManager} is the engine-layer point and the
 * handler is a manager-internal detail).
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent invocation. The handler is
 * called from the daemon's scan thread (single scan at a time per
 * manager instance), but integrators may also call {@code scanOnce}
 * directly from other threads.
 *
 * <h2>Non-silent contract (Minimum Rules #24)</h2>
 * A handler must never silently drop an orphan session. Every call
 * returns a non-null {@link RecoveryOutcome}. A SKIP-mode handler logs
 * the orphan (does not return a silent empty result); a failed action
 * records {@code succeeded=false} with a descriptive message.
 *
 * <p>See plan 226 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.3.
 */
public interface IOrphanRecoveryHandler {

    /**
     * Handle a single orphaned session detected by the recovery scan.
     *
     * @param sessionId the orphaned session ID (DB status
     *                  {@code 'running'} or {@code 'pending'}, no active
     *                  takeover lock); never null
     * @return a non-null {@link RecoveryOutcome} recording the action
     *         taken (mode / succeeded / message); never null
     */
    RecoveryOutcome handleOrphan(String sessionId);
}
