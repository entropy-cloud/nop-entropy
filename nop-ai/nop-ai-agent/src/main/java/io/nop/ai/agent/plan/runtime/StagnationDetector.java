package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects plan/phase/task-level stagnation from a {@link PlanExecutionState}
 * and emits structured {@link StagnationEvent}s (design §14.4.1 signal set).
 *
 * <p>This is a pure query: it reads the runtime execution state produced by
 * the {@link PlanExecutor} host (real status transitions + real
 * {@code AgentPlanError} records + gate-exhaustion markers) and produces the
 * three plan-level stagnation signals:
 * <ul>
 *   <li>{@link StagnationSignalType#GATE_EXHAUSTED} — for each phase the host
 *       marked gate-exhausted.</li>
 *   <li>{@link StagnationSignalType#TASK_STALLED} — each non-terminal task
 *       whose consecutive-failure count has reached {@code staleTaskCycles}.</li>
 *   <li>{@link StagnationSignalType#REPEATED_ERRORS} — each task whose
 *       unresolved runtime error count has reached {@code maxErrorsPerTask}.</li>
 * </ul>
 *
 * <p>ReAct-level {@code SessionGoalTracker} STUCK is intentionally NOT part
 * of this signal set — it acts on a single agent session and is a different
 * semantic layer (design §14.4.1).
 */
public class StagnationDetector {

    private final int staleTaskCycles;
    private final int maxErrorsPerTask;

    /**
     * @param staleTaskCycles   consecutive failures required to flag a task
     *                          as stalled (must be &gt; 0)
     * @param maxErrorsPerTask  unresolved errors required to flag a task for
     *                          repeated errors (must be &gt; 0)
     */
    public StagnationDetector(int staleTaskCycles, int maxErrorsPerTask) {
        if (staleTaskCycles <= 0) {
            throw new IllegalArgumentException("staleTaskCycles must be > 0");
        }
        if (maxErrorsPerTask <= 0) {
            throw new IllegalArgumentException("maxErrorsPerTask must be > 0");
        }
        this.staleTaskCycles = staleTaskCycles;
        this.maxErrorsPerTask = maxErrorsPerTask;
    }

    public int getStaleTaskCycles() {
        return staleTaskCycles;
    }

    public int getMaxErrorsPerTask() {
        return maxErrorsPerTask;
    }

    /**
     * Scan the execution state and return all currently-active stagnation
     * events (one per active signal source). Empty list means "no
     * stagnation".
     *
     * <p>Task-level events ({@link StagnationSignalType#TASK_STALLED},
     * {@link StagnationSignalType#REPEATED_ERRORS}) carry their owning phase
     * name on {@link StagnationEvent#getTargetPhase()} so downstream
     * {@link PlanReplanner} decisions can match rollback-eligible phases
     * without re-resolving task→phase ownership.
     */
    public List<StagnationEvent> detect(PlanExecutionState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }

        List<StagnationEvent> events = new ArrayList<>();

        for (String phase : state.getGateExhaustedPhases()) {
            int attempt = state.getGateExhaustedAttempt(phase);
            events.add(new StagnationEvent(
                    StagnationSignalType.GATE_EXHAUSTED, phase, null, attempt,
                    "gate retry budget exhausted for phase " + phase + " at attempt " + attempt));
        }

        for (AgentPlanTaskModel task : collectAllTasks(state)) {
            String taskNo = task.getTaskNo();
            if (taskNo == null) continue;
            AgentExecStatus status = state.getTaskStatus(taskNo);

            if (!PlanScheduler.isTerminal(status)) {
                int failures = state.getConsecutiveFailures(taskNo);
                if (failures >= staleTaskCycles) {
                    String owner = state.phaseOwningTask(taskNo);
                    events.add(new StagnationEvent(
                            StagnationSignalType.TASK_STALLED, owner, taskNo, failures,
                            "task " + taskNo + " stalled: " + failures + " consecutive failures"));
                }
            }

            int unresolved = state.countUnresolvedErrors(taskNo);
            if (unresolved >= maxErrorsPerTask) {
                String owner = state.phaseOwningTask(taskNo);
                events.add(new StagnationEvent(
                        StagnationSignalType.REPEATED_ERRORS, owner, taskNo, unresolved,
                        "task " + taskNo + " accumulated " + unresolved + " unresolved errors"));
            }
        }

        return Collections.unmodifiableList(events);
    }

    private List<AgentPlanTaskModel> collectAllTasks(PlanExecutionState state) {
        List<AgentPlanTaskModel> all = new ArrayList<>();
        if (state.getPlan().getPhases() != null) {
            for (AgentPlanPhase phase : state.getPlan().getPhases()) {
                collectRecursive(phase.getTasks(), all);
            }
        }
        return all;
    }

    private void collectRecursive(List<AgentPlanTaskModel> tasks, List<AgentPlanTaskModel> out) {
        if (tasks == null) return;
        for (AgentPlanTaskModel task : tasks) {
            out.add(task);
            collectRecursive(task.getSubTasks(), out);
        }
    }
}
