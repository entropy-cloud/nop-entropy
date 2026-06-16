package io.nop.ai.agent.reliability;

/**
 * Pass-through {@link IGoalTracker} used as the shipped default when no
 * functional tracker is registered (design
 * {@code nop-ai-agent-reliability.md} §5.3 / plan 211 Phase 1 / L3-3).
 *
 * <p>{@link #assessGoal} unconditionally returns
 * {@link GoalAssessment#PROGRESSING}, so the ReAct loop never aborts on a
 * goal-tracker assessment and the iteration budget is governed solely by
 * {@code maxIterations} — exactly the pre-plan-211 behaviour. This preserves
 * the engine's behaviour verbatim, so wiring the tracker is a zero-regression
 * change.
 *
 * <p>This is an <b>explicit pass-through default</b>, not a silent skip of
 * required behaviour. The correct shipped behaviour of a pass-through goal
 * tracker is to never interfere with the loop, because no functional
 * stuck-detection strategy is configured out of the box. A functional tracker
 * ({@code SessionGoalTracker}) is registered explicitly via
 * {@code DefaultAgentEngine.setGoalTracker}. This mirrors the
 * {@code AlwaysClosed} / {@code NoRetryPolicy} / {@code NoOpCheckpoint} /
 * {@code NoOpBudgetProvider} sibling pass-through pattern.
 *
 * <p>{@link #recordIteration} is an <b>explicit no-op</b>: it is not an empty
 * method body used as a placeholder for required behaviour (Minimum Rules
 * #24). The correct shipped behaviour of a pass-through tracker is to discard
 * the recorded iteration, because the tracker maintains no per-session state
 * by design.
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public final class NoOpGoalTracker implements IGoalTracker {

    private static final NoOpGoalTracker INSTANCE = new NoOpGoalTracker();

    private NoOpGoalTracker() {
    }

    /**
     * @return the singleton pass-through {@link IGoalTracker} instance
     */
    public static IGoalTracker noOp() {
        return INSTANCE;
    }

    @Override
    public void recordIteration(String sessionId, IterationSnapshot snapshot) {
        // Explicit no-op: the pass-through default maintains no per-session
        // state, so a recorded iteration is discarded by design. This is not
        // an empty placeholder for required behaviour — the correct behaviour
        // of a pass-through goal tracker is to ignore iteration reports.
        // A functional tracker (SessionGoalTracker) appends the iteration's
        // tool-call signatures to its sliding window.
    }

    @Override
    public GoalAssessment assessGoal(String sessionId) {
        return GoalAssessment.PROGRESSING;
    }
}
