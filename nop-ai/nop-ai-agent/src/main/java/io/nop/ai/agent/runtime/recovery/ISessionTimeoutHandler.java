package io.nop.ai.agent.runtime.recovery;

/**
 * Strategy hook invoked by {@code ScheduledRecoveryManager.scanOnce}
 * for each timed-out session it detects (plan 229 /
 * L4-8-P4-TimeoutAbort).
 *
 * <p>This interface extends nop-ai-agent session recovery from
 * "detect-and-log only" (plan 222 daemon + plan 226 orphan strategy) to
 * "decide-and-act per a pluggable strategy for sessions whose activity
 * timestamp exceeds the configured wall-clock timeout". A
 * <em>timed-out session</em> is one whose DB status is
 * {@code 'running'} or {@code 'pending'} and whose
 * {@code ai_agent_session.UPDATED_AT} is older than
 * {@code now - timeoutSeconds} (design 裁定 2). Unlike orphan detection
 * (which only targets sessions with <em>no</em> active lock), timeout
 * detection also catches sessions that still hold an active lock but
 * have stopped making progress (e.g. an LLM call blocked forever, a
 * tool stuck in an infinite loop, a logic deadlock).
 *
 * <p>The shipped default is {@link NoOpSessionTimeoutHandler}
 * (SKIPPED action — LOG.warn, zero behaviour regression with plan 226);
 * the functional implementation is
 * {@code DefaultSessionTimeoutHandler} (LOCAL_CANCELLED / FORCE_FAILED /
 * SKIPPED_REMOTE three-way classification — design 裁定 1).
 *
 * <p><b>Wiring</b>: the handler is a sub-component of the recovery
 * manager, not an engine-layer configuration point (mirrors the plan
 * 226 {@code IOrphanRecoveryHandler} layering). Integrators wire it via
 * {@code ScheduledRecoveryManager.setSessionTimeoutHandler}. The engine
 * does not hold a session-timeout-handler field.
 *
 * <p><b>Scan ordering</b> (design 裁定 3): the timeout-detection step
 * runs <em>after</em> stale-lock cleanup and <em>before</em> orphan
 * detection. This ensures a timed-out orphan session force-marked
 * {@code failed} (terminal) by the timeout handler is automatically
 * excluded by the subsequent orphan-detection SQL (which filters
 * {@code STATUS IN ('running','pending')}), avoiding double-handling
 * between the timeout handler and the orphan handler.
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent invocation. The handler
 * is called from the daemon's scan thread (single scan at a time per
 * manager instance), but integrators may also call {@code scanOnce}
 * directly from other threads.
 *
 * <h2>Non-silent contract (Minimum Rules #24)</h2>
 * A handler must never silently drop a timed-out session. Every call
 * returns a non-null {@link TimeoutOutcome}. A SKIPPED-action handler
 * logs the session (does not return a silent empty result); a failed
 * action records {@code succeeded=false} with a descriptive message.
 *
 * <p><b>Classification responsibility</b>: the three-way
 * local/orphan/remote classification (design 裁定 1) is the
 * implementation's responsibility — the daemon only selects timed-out
 * sessions and passes the sessionId. The implementation reads the lock
 * table to classify (e.g. {@code DefaultSessionTimeoutHandler} does a
 * raw JDBC {@code SELECT LOCK_OWNER, LOCK_EXPIRES_AT} on
 * {@code ai_agent_session_lock}).
 *
 * <p>See plan 229 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.3.
 */
public interface ISessionTimeoutHandler {

    /**
     * Handle a single timed-out session detected by the recovery scan.
     *
     * @param sessionId the timed-out session ID (DB status
     *                  {@code 'running'} or {@code 'pending'}, activity
     *                  timestamp older than {@code now - timeoutSeconds});
     *                  never null
     * @return a non-null {@link TimeoutOutcome} recording the action
     *         taken (action / succeeded / message); never null
     */
    TimeoutOutcome handleTimeout(String sessionId);
}
