package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of {@link PlanExecutor#execute}, capturing the observable
 * outcome of driving a plan through the host state machine (design §14.5).
 *
 * <p>Exposes the final plan status, the stagnation events observed, the
 * replan decisions enacted (with their payloads — target phase/task,
 * triggering signal, reason), and basic counters — so end-to-end tests can
 * assert that stagnation flowed from real state transitions through the
 * detector to an idempotent {@link ReplanDecisionResult} (Anti-Hollow
 * verification).
 */
public final class PlanExecutionResult {

    private final AgentExecStatus finalStatus;
    private final List<StagnationEvent> eventsObserved;
    private final List<ReplanDecisionResult> decisionsEnacted;
    private final int tasksCompleted;
    private final int errorsRecorded;
    private final String lastPhase;

    public PlanExecutionResult(AgentExecStatus finalStatus,
                               List<StagnationEvent> eventsObserved,
                               List<ReplanDecisionResult> decisionsEnacted,
                               int tasksCompleted, int errorsRecorded, String lastPhase) {
        this.finalStatus = finalStatus;
        this.eventsObserved = eventsObserved == null
                ? Collections.emptyList() : Collections.unmodifiableList(eventsObserved);
        this.decisionsEnacted = decisionsEnacted == null
                ? Collections.emptyList() : Collections.unmodifiableList(decisionsEnacted);
        this.tasksCompleted = tasksCompleted;
        this.errorsRecorded = errorsRecorded;
        this.lastPhase = lastPhase;
    }

    public AgentExecStatus getFinalStatus() {
        return finalStatus;
    }

    public List<StagnationEvent> getEventsObserved() {
        return eventsObserved;
    }

    public List<ReplanDecisionResult> getDecisionsEnacted() {
        return decisionsEnacted;
    }

    public int getTasksCompleted() {
        return tasksCompleted;
    }

    public int getErrorsRecorded() {
        return errorsRecorded;
    }

    public String getLastPhase() {
        return lastPhase;
    }

    public boolean isEscalated() {
        return finalStatus == AgentExecStatus.escalated;
    }

    public boolean isCompleted() {
        return finalStatus == AgentExecStatus.completed;
    }
}
