package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;

import java.util.Collections;
import java.util.List;

/**
 * Produces idempotent {@link ReplanDecision}s from stagnation events and
 * enacts them on the runtime execution state (design §14.4.2 / §14.4.4).
 *
 * <p><b>Decision (pure function)</b>: {@link #decide(StagnationEvent)} maps a
 * stagnation event to a decision. The first cut wires a single deterministic
 * mapping — every observed plan-level stagnation signal
 * ({@link StagnationSignalType#GATE_EXHAUSTED},
 * {@link StagnationSignalType#TASK_STALLED},
 * {@link StagnationSignalType#REPEATED_ERRORS}) escalates. Because the
 * decision is a pure function of the event, and the event's
 * {@link StagnationEvent#idempotencyKey()} excludes wall-clock fields, the
 * same observable stagnation state always yields the same decision
 * (design §14.4.4 idempotency).
 *
 * <p><b>Enactment</b>: {@link #apply(ReplanDecision, PlanExecutionState)}
 * mutates the execution state. {@code ESCALATE} sets the plan and current
 * phase to {@link AgentExecStatus#escalated}; {@code CONTINUE} is a no-op;
 * {@code ROLLBACK_PHASE} / {@code SPLIT_TASK} / {@code ABORT} are defined
 * contracts whose runtime enactment is deferred to a successor — requesting
 * enactment throws {@link UnsupportedOperationException} rather than silently
 * skipping (Minimum Rules #24).
 */
public class PlanReplanner {

    /**
     * Decide the replan action for a single stagnation event. Pure function.
     */
    public ReplanDecision decide(StagnationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        switch (event.getSignalType()) {
            case GATE_EXHAUSTED:
            case TASK_STALLED:
            case REPEATED_ERRORS:
                return ReplanDecision.ESCALATE;
            default:
                throw new IllegalArgumentException("Unknown signal type: " + event.getSignalType());
        }
    }

    /**
     * Decide the overall replan action for a batch of events. An empty batch
     * means no stagnation → {@link ReplanDecision#CONTINUE}; any non-empty
     * batch escalates (the most severe applicable decision).
     */
    public ReplanDecision decide(List<StagnationEvent> events) {
        if (events == null || events.isEmpty()) {
            return ReplanDecision.CONTINUE;
        }
        for (StagnationEvent event : events) {
            if (decide(event) == ReplanDecision.ESCALATE) {
                return ReplanDecision.ESCALATE;
            }
        }
        return ReplanDecision.CONTINUE;
    }

    /**
     * Enact a decision on the runtime execution state.
     *
     * @throws UnsupportedOperationException if the decision is
     *         {@link ReplanDecision#ROLLBACK_PHASE},
     *         {@link ReplanDecision#SPLIT_TASK}, or
     *         {@link ReplanDecision#ABORT} — these contracts are defined but
     *         their runtime enactment is deferred to a successor
     */
    public void apply(ReplanDecision decision, PlanExecutionState state) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        switch (decision) {
            case CONTINUE:
                break;
            case ESCALATE:
                state.setPlanStatus(AgentExecStatus.escalated);
                String phase = state.getCurrentPhase();
                if (phase != null) {
                    state.setPhaseStatus(phase, AgentExecStatus.escalated);
                }
                break;
            case ROLLBACK_PHASE:
                throw new UnsupportedOperationException(
                        "not yet implemented: ROLLBACK_PHASE enactment is deferred to a successor plan (design §14.4.3)");
            case SPLIT_TASK:
                throw new UnsupportedOperationException(
                        "not yet implemented: SPLIT_TASK enactment is deferred to a successor plan (design §14.4.3)");
            case ABORT:
                throw new UnsupportedOperationException(
                        "not yet implemented: ABORT enactment is deferred to a successor plan (design §14.4.2)");
            default:
                throw new IllegalArgumentException("Unknown decision: " + decision);
        }
    }

    /**
     * Convenience: decide the overall action for a batch of events and enact
     * it. Returns the enacted decision.
     */
    public ReplanDecision decideAndApply(List<StagnationEvent> events, PlanExecutionState state) {
        ReplanDecision decision = decide(events);
        apply(decision, state);
        return decision;
    }
}
