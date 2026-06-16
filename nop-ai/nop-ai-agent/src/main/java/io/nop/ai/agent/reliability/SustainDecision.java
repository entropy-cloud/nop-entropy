package io.nop.ai.agent.reliability;

/**
 * Decision returned by {@link ISustainer#onStop(SustainContext)} at a
 * sustainable ReAct-loop exit point (design
 * {@code nop-ai-agent-reliability.md} §5.1a Sisyphean model / plan 212 / L3-8).
 *
 * <ul>
 *   <li>{@link #CONTINUE} — the sustainer forces the execution to continue:
 *       the agent wanted to stop, but the sustainer judges the task is not yet
 *       complete and extends the iteration budget so the reactLoop re-enters
 *       for another sustain round. This is the "never give up" (Sisyphean)
 *       elasticity philosophy. The engine re-runs the full reactLoop
 *       top-of-loop check chain (cancel / pause / force-stop / assessGoal)
 *       on every sustain round, so sustaining never bypasses governance.</li>
 *   <li>{@link #STOP} — the sustainer allows the execution to stop: the
 *       terminal-state change ({@code running} → {@code completed}) and the
 *       {@code EXECUTION_COMPLETED} / {@code POST_CALL} event publication
 *       proceed as normal. This is the shipped default behaviour
 *       ({@link NoOpSustainer} unconditionally returns STOP — zero-regression
 *       versus the pre-plan-212 zero-sustain engine).</li>
 * </ul>
 *
 * <p>This is deliberately a binary decision (not a richer "how many more
 * iterations" return). The iteration-budget extension step is an executor-level
 * adjudication (the engine extends by the original {@code maxIterations} per
 * sustain round — see {@code ReActAgentExecutor}). The {@code maxSustainCount}
 * hard ceiling that bounds the Sisyphean loop is a property of the functional
 * {@code SisypheanSustainer} implementation, enforced via
 * {@link SustainContext#getSustainCountSoFar()}.
 */
public enum SustainDecision {
    CONTINUE,
    STOP
}
