package io.nop.ai.agent.plan.runtime;

/**
 * The set of replan decisions produced by {@link PlanReplanner} from
 * stagnation events (design §14.4.2 decision contract).
 *
 * <p>A decision is the output of a pure function over an input stagnation
 * state. The first cut (W1-4) wires only {@link #CONTINUE} and
 * {@link #ESCALATE}; {@link #ROLLBACK_PHASE} and {@link #SPLIT_TASK} are
 * defined contracts whose runtime enactment is deferred to a successor —
 * requesting their enactment fails fast with
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
     * W1-4. */
    ESCALATE,

    /** Roll back to a preceding phase (reset task statuses). Contract
     * defined; runtime enactment deferred to a successor — enactment
     * throws {@link UnsupportedOperationException}. */
    ROLLBACK_PHASE,

    /** Split / merge tasks (dynamic DAG node insert/remove). Contract
     * defined; runtime enactment deferred to a successor — enactment
     * throws {@link UnsupportedOperationException}. */
    SPLIT_TASK,

    /** Abort the plan entirely. Contract slot reserved; not produced by
     * the first-cut detector. */
    ABORT
}
