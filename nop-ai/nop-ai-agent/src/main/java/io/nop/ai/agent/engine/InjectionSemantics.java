package io.nop.ai.agent.engine;

/**
 * Plan 2289 (WI8): injection semantics trichotomy.
 *
 * <ul>
 *   <li>{@link #STEER} — interrupt-and-merge: wakes the wait phase (WI5
 *       steering wakeup turn) and is consumed there; falls back to the round
 *       boundary when the heartbeat mode is off.</li>
 *   <li>{@link #FOLLOWUP} — idle append: consumed ONLY at the round boundary
 *       (never wakes the wait phase — a tool wait is not "idle").</li>
 *   <li>{@link #INJECT} — background injection: consumed ONLY at the round
 *       boundary, silently appended to history (never enters a wakeup request
 *       snapshot, never triggers a wakeup).</li>
 * </ul>
 */
public enum InjectionSemantics {
    FOLLOWUP,
    STEER,
    INJECT
}
