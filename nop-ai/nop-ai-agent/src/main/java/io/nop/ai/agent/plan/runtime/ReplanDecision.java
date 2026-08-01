package io.nop.ai.agent.plan.runtime;

/**
 * The set of replan decisions produced by {@link PlanReplanner} from
 * stagnation events (design §14.4.2 decision contract).
 *
 * <p>A decision is the output of a pure function over an input stagnation
 * state (plus the configured {@link ReplanPolicy}). {@link #CONTINUE},
 * {@link #ESCALATE}, {@link #ROLLBACK_PHASE}, and {@link #SPLIT_TASK} all
 * have real runtime enactment; {@link #ABORT} is a reserved slot whose
 * enactment is out-of-scope and fails fast with
 * {@link UnsupportedOperationException} rather than being silently skipped
 * (Minimum Rules #24).
 */
public enum ReplanDecision {
    /** No stagnation signal observed — the plan state machine should keep
     * advancing. Wired in W1-4. */
    CONTINUE,

    /** Stagnation reached a threshold (gate exhausted / task stalled /
     * repeated errors) — the plan/phase status should be set to
     * {@link io.nop.ai.agent.model.AgentExecStatus#escalated}. Wired in
     * W1-4. Terminal: execution stops. */
    ESCALATE,

    /** Roll back to a preceding phase (reset task statuses). Recoverable
     * (non-terminal): execution re-enters the target phase. Enacted when the
     * source phase is rollback-eligible per {@link ReplanPolicy}. */
    ROLLBACK_PHASE,

    /** Split a stalled task into runtime sub-task nodes (dynamic DAG node
     * insert). Recoverable (non-terminal). Enacted when the task is
     * split-eligible per {@link ReplanPolicy}. */
    SPLIT_TASK,

    /** Abort the plan entirely. Contract slot reserved; enactment is
     * out-of-scope and fails fast with {@link UnsupportedOperationException}. */
    ABORT
}
