package io.nop.ai.agent.plan.runtime;

/**
 * The discrete set of plan/phase/task-level "no progress" signals consumed
 * by {@link StagnationDetector} (design §14.4.1 stagnation input signal set).
 *
 * <p>These are plan-level signals, deliberately distinct from the ReAct-level
 * {@code SessionGoalTracker} STUCK signal (which acts on a single agent
 * session's tool-call repetition and escalates-and-aborts the whole session).
 * Plan-level signals act on the plan/phase/task state machine instead.
 */
public enum StagnationSignalType {
    /** A phase gate's retry budget is exhausted
     * ({@code GateCheckResult.Outcome.RETRY_EXHAUSTED}). Structural
     * determination, not a count-based inference. */
    GATE_EXHAUSTED,

    /** A non-terminal task has failed to advance for
     * {@code staleTaskCycles} consecutive scheduling cycles (repeated
     * failures with no completion). */
    TASK_STALLED,

    /** A single task has accumulated at least {@code maxErrorsPerTask}
     * unresolved ({@code resolvedAt == null}) {@code AgentPlanError}
     * records. */
    REPEATED_ERRORS
}
