package io.nop.ai.agent.reliability;

/**
 * Functional {@link ISustainer} implementing the "never give up" (Sisyphean)
 * elasticity philosophy (design {@code nop-ai-agent-reliability.md} §5.1a /
 * plan 212 Phase 2 / L3-8) — the structural opposite of the fail-fast
 * {@code ThresholdBreaker} (design §5.1 / §11a — mutual exclusivity is a
 * deployment-layer documentation constraint, not a runtime guard).
 *
 * <p>At a sustainable ReAct-loop exit point
 * ({@link SustainStopReason#MAX_ITERATIONS}), the Sisyphean sustainer forces
 * the execution to continue (returns {@link SustainDecision#CONTINUE}) as long
 * as the per-execution sustain count has not reached the configured
 * {@code maxSustainCount} hard ceiling. This implements <b>at-least-once</b>
 * semantics: an execution truncated by the iteration budget is granted at
 * least {@code maxSustainCount} additional sustain rounds (each extending the
 * budget by one sustain-round step — the original {@code maxIterations}) to
 * ensure the task completes. Once the ceiling is reached, the sustainer allows
 * the stop (returns {@link SustainDecision#STOP}) — fail-safe, not an infinite
 * loop.
 *
 * <p><b>Stateless by design</b>: this implementation holds only a
 * {@code final int maxSustainCount} and carries no per-session or
 * per-execution mutable state. The per-execution sustain counter is maintained
 * by the executor (incremented each time a CONTINUE is honoured) and passed in
 * via {@link SustainContext#getSustainCountSoFar()}. This makes the Sisyphean
 * sustainer inherently thread-safe and isolates concurrent
 * {@code execute()} calls without synchronisation primitives or a per-session
 * map — session A's sustain counter and session B's sustain counter are
 * independent locals in their respective execute() frames.
 *
 * <p><b>Decision logic</b> (plan 212 Phase 2 adjudication):
 * <ul>
 *   <li>{@code stopReason == MAX_ITERATIONS} and
 *       {@code sustainCountSoFar < maxSustainCount} →
 *       {@link SustainDecision#CONTINUE} (force another sustain round).</li>
 *   <li>{@code stopReason == MAX_ITERATIONS} and
 *       {@code sustainCountSoFar >= maxSustainCount} →
 *       {@link SustainDecision#STOP} (ceiling reached, fail-safe stop).</li>
 *   <li>{@code stopReason != MAX_ITERATIONS} →
 *       {@link SustainDecision#STOP} (non-sustainable exit point in this
 *       version — respect the original behaviour). Plan 212's first version
 *       only ever receives MAX_ITERATIONS from the engine, but the defensive
 *       check keeps the sustainer correct if a successor passes another stop
 *       reason.</li>
 * </ul>
 *
 * <p><b>Default ceiling</b>: {@link #DEFAULT_MAX_SUSTAIN_COUNT} = 3. A
 * Sisyphean-sustained execution gets at least 3 additional sustain rounds
 * (4× the original iteration budget in total) before the ceiling stops it.
 * The ceiling is a constructor parameter ({@link #SisypheanSustainer(int)})
 * so deployments can tune it to their unattended-execution SLA without
 * XDSL configuration (Non-Goal). Dynamic ceiling calibration based on
 * historical completion rates is an independent successor (Non-Goal).
 *
 * <p>This mirrors the sibling functional reliability implementations
 * ({@code ThresholdBreaker} for L3-1, {@code StandardRetryPolicy} for L3-2,
 * {@code SessionGoalTracker} for L3-3) as the opt-in functional impl behind
 * the shipped pass-through default ({@link NoOpSustainer}).
 */
public final class SisypheanSustainer implements ISustainer {

    /**
     * Default hard ceiling on the number of sustain rounds granted per
     * execution. A Sisyphean-sustained execution gets at least 3 additional
     * sustain rounds (4× the original iteration budget in total) before the
     * ceiling stops it.
     */
    public static final int DEFAULT_MAX_SUSTAIN_COUNT = 3;

    private final int maxSustainCount;

    /**
     * Create a Sisyphean sustainer with the default ceiling
     * ({@link #DEFAULT_MAX_SUSTAIN_COUNT}).
     */
    public SisypheanSustainer() {
        this(DEFAULT_MAX_SUSTAIN_COUNT);
    }

    /**
     * Create a Sisyphean sustainer with a custom ceiling.
     *
     * @param maxSustainCount the maximum number of sustain rounds granted per
     *                        execution before the sustainer allows the stop
     *                        (fail-safe). Must be {@code >= 0}. A value of
     *                        {@code 0} means the sustainer never forces a
     *                        continuation (equivalent to {@link NoOpSustainer}
     *                        for the MAX_ITERATIONS exit point).
     */
    public SisypheanSustainer(int maxSustainCount) {
        if (maxSustainCount < 0) {
            throw new IllegalArgumentException(
                    "SisypheanSustainer maxSustainCount must not be negative: " + maxSustainCount);
        }
        this.maxSustainCount = maxSustainCount;
    }

    /**
     * @return the configured hard ceiling on sustain rounds per execution
     */
    public int getMaxSustainCount() {
        return maxSustainCount;
    }

    @Override
    public SustainDecision onStop(SustainContext context) {
        if (context == null) {
            throw new IllegalArgumentException("SustainContext must not be null");
        }
        // Non-sustainable exit point (defensive — plan 212 first version only
        // ever receives MAX_ITERATIONS from the engine). Respect the original
        // behaviour: allow the stop.
        if (context.getStopReason() != SustainStopReason.MAX_ITERATIONS) {
            return SustainDecision.STOP;
        }
        // MAX_ITERATIONS exit: force a continuation as long as the per-execution
        // sustain count has not reached the hard ceiling. The executor maintains
        // the sustainCountSoFar counter and passes it here so this stateless
        // sustainer can enforce the ceiling without per-session mutable state.
        if (context.getSustainCountSoFar() < maxSustainCount) {
            return SustainDecision.CONTINUE;
        }
        // Ceiling reached: fail-safe stop (not an infinite loop). This is an
        // explicit decision to allow the stop, not a silent signal swallow
        // (Minimum Rules #24) — the agent exhausted maxSustainCount additional
        // sustain rounds and still did not complete.
        return SustainDecision.STOP;
    }
}
