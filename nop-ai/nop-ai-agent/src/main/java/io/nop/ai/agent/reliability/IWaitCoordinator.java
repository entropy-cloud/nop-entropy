package io.nop.ai.agent.reliability;

/**
 * Coordinates the WAIT_FOR long-wait primitive (design §13.1): condition
 * registration, suspend/wake decisions, and condition re-evaluation
 * (anti-re-suspend mechanism, Decision H).
 *
 * <p>The coordinator is consulted at two points:
 * <ul>
 *   <li><b>ReAct loop registration point</b> (iteration top, after
 *       denial-ledger pause check): {@link #checkWait} returns the
 *       {@link WaitDecision} — NONE (proceed normally), SUSPEND (produce
 *       WAIT_FOR checkpoint + suspend), or PROCEED (condition already
 *       satisfied, skip suspend).</li>
 *   <li><b>Wake re-entry</b> ({@code AgentSessionLifecycle.wakeSession}):
 *       {@link #deliverWake} marks the condition as satisfied so the next
 *       {@link #checkWait} returns PROCEED (preventing re-suspend on replay
 *       re-entry).</li>
 * </ul>
 *
 * <p>The default {@link NoOpWaitCoordinator} returns {@link WaitDecision#none}
 * unconditionally, making WAIT_FOR opt-in (zero regression when not wired).
 */
public interface IWaitCoordinator {

    /**
     * Register a wait request for the given session. Called externally
     * (e.g. by a tool, hook, or API) to request the session to suspend at
     * the next iteration boundary.
     *
     * @param sessionId the session to wait; never null
     * @param condition the wait condition; never null
     */
    void requestWait(String sessionId, WaitCondition condition);

    /**
     * Consulted at the ReAct loop registration point (design §13.1 Decision
     * B/H). Returns the decision for this session:
     * <ul>
     *   <li>NONE — no wait request; proceed normally.</li>
     *   <li>SUSPEND — wait requested, condition not yet satisfied; suspend.</li>
     *   <li>PROCEED — wait requested but condition already satisfied;
     *       consume the wait and proceed (anti-re-suspend).</li>
     * </ul>
     *
     * @param sessionId the session to check; never null
     * @return the decision; never null
     */
    WaitDecision checkWait(String sessionId);

    /**
     * Deliver a wake signal for the given session (design §13.1 Decision C).
     * Marks the condition as satisfied so the next {@link #checkWait} returns
     * PROCEED. Called by {@code wakeSession} before re-entering execution.
     *
     * @param sessionId the session to wake; never null
     * @param payload   an optional wake payload (e.g. user input); may be null
     */
    void deliverWake(String sessionId, Object payload);

    /**
     * Check whether the given session has an active (unsatisfied) wait
     * request. Used by restore/wake decisions.
     *
     * @param sessionId the session to check; never null
     * @return {@code true} if the session is waiting (has an unsatisfied condition)
     */
    boolean isWaiting(String sessionId);
}
