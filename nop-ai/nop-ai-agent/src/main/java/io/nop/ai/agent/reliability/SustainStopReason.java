package io.nop.ai.agent.reliability;

/**
 * The reason the ReAct loop reached a sustainable exit decision point
 * (design {@code nop-ai-agent-reliability.md} §5.1a / plan 212 / L3-8).
 *
 * <p>Carried in {@link SustainContext#getStopReason()} so an
 * {@link ISustainer} can key its {@link SustainDecision} on why the loop is
 * stopping. <b>Plan 212 first version</b> only consults the sustainer on a
 * single exit point and therefore only ever produces one value:
 *
 * <ul>
 *   <li>{@link #MAX_ITERATIONS} — the reactLoop's
 *       {@code while (currentIteration < maxIterations)} condition became
 *       false, so the loop exited naturally. This is the most objective
 *       "the task was truncated, not completed" signal: the agent exhausted
 *       its iteration budget without the completion judge declaring
 *       completion, without escalating, and without a force-stop / cancel /
 *       pause. At this exit point {@code ctx.getStatus()} is still
 *       {@code running}, so the engine knows it is a budget truncation rather
 *       than a voluntary / governance terminal state.</li>
 * </ul>
 *
 * <p>Other exit points (completion-judge {@code isComplete}, {@code isEscalate},
 * {@code shouldForceStop}, cancel, denial-ledger pause) are <b>not</b>
 * sustainable in plan 212's first version and the sustainer is <b>not</b>
 * consulted on them (they go straight to the terminal-state change +
 * event publication). Successor plans may add more values here when they
 * extend sustaining to those exit points (see plan 212 Non-Goals:
 * sustain completion-judge-complete / escalate / forced-stop).
 */
public enum SustainStopReason {
    /**
     * The reactLoop exited because the iteration budget was exhausted
     * ({@code currentIteration >= maxIterations}) while the status was still
     * {@code running}. This is the only sustainable exit point in plan 212's
     * first version.
     */
    MAX_ITERATIONS
}
