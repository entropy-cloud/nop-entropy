package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanError;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Mutable runtime execution state that overlays a (frozen, read-only)
 * {@link AgentPlan} template (design §14.4.3 freeze ruling).
 *
 * <p>{@code ResourceComponentManager} freezes loaded plans with
 * {@code freeze(true)} (cascade), so the loaded template cannot be mutated.
 * This execution state is the host's mutable runtime representation: it
 * tracks the runtime status of every task and phase, the per-task
 * consecutive-failure and attempt counters, the runtime
 * {@link AgentPlanError} records (the stagnation input source), and the set
 * of phases whose gate has been exhausted.
 *
 * <p>The frozen template is consulted only for read-only declared structure
 * (gate definitions, DAG topology, trigger rules, declared initial statuses).
 * All runtime mutations happen here. {@link #statusProvider()} exposes the
 * runtime task-status view so {@link PlanScheduler#getReadyTasks(AgentPlan,
 * Function)} computes readiness from the live execution state rather than the
 * frozen declaration.
 */
public class PlanExecutionState {

    private final AgentPlan plan;
    private final Map<String, AgentExecStatus> taskStatus = new LinkedHashMap<>();
    private final Map<String, Integer> taskConsecutiveFailures = new LinkedHashMap<>();
    private final Map<String, Integer> taskAttempts = new LinkedHashMap<>();
    private final Map<String, AgentExecStatus> phaseStatus = new LinkedHashMap<>();
    private final List<AgentPlanError> errors = new ArrayList<>();
    private final Set<String> gateExhaustedPhases = new LinkedHashSet<>();
    private final Map<String, Integer> gateExhaustedAttempt = new LinkedHashMap<>();
    private final Map<String, Integer> phaseGateAttempts = new LinkedHashMap<>();

    private String currentPhase;
    private AgentExecStatus planStatus;

    public PlanExecutionState(AgentPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        this.plan = plan;
        initFromTemplate();
    }

    private void initFromTemplate() {
        if (plan.getPhases() != null) {
            for (AgentPlanPhase phase : plan.getPhases()) {
                String name = phase.getName();
                if (name != null) {
                    AgentExecStatus declared = phase.getStatus();
                    phaseStatus.put(name, declared == null ? AgentExecStatus.pending : declared);
                    phaseGateAttempts.put(name, 0);
                }
                if (phase.getTasks() != null) {
                    initTasks(phase.getTasks());
                }
            }
        }
        this.currentPhase = plan.getCurrentPhase();
        if (this.currentPhase == null && plan.getPhases() != null && !plan.getPhases().isEmpty()) {
            this.currentPhase = plan.getPhases().get(0).getName();
        }
        this.planStatus = plan.getStatus() == null ? AgentExecStatus.pending : plan.getStatus();
    }

    private void initTasks(List<AgentPlanTaskModel> tasks) {
        for (AgentPlanTaskModel task : tasks) {
            String no = task.getTaskNo();
            if (no == null) continue;
            AgentExecStatus declared = task.getStatus();
            taskStatus.put(no, declared == null ? AgentExecStatus.pending : declared);
            taskConsecutiveFailures.put(no, 0);
            taskAttempts.put(no, 0);
            if (task.getSubTasks() != null) {
                initTasks(task.getSubTasks());
            }
        }
    }

    public AgentPlan getPlan() {
        return plan;
    }

    public AgentExecStatus getPlanStatus() {
        return planStatus;
    }

    public void setPlanStatus(AgentExecStatus status) {
        this.planStatus = status;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String phaseName) {
        this.currentPhase = phaseName;
    }

    public AgentExecStatus getTaskStatus(String taskNo) {
        return taskStatus.getOrDefault(taskNo, AgentExecStatus.pending);
    }

    public void setTaskStatus(String taskNo, AgentExecStatus status) {
        taskStatus.put(taskNo, status);
    }

    public int getTaskAttempts(String taskNo) {
        return taskAttempts.getOrDefault(taskNo, 0);
    }

    public void incrementTaskAttempts(String taskNo) {
        taskAttempts.merge(taskNo, 1, Integer::sum);
    }

    public int getConsecutiveFailures(String taskNo) {
        return taskConsecutiveFailures.getOrDefault(taskNo, 0);
    }

    public void incrementConsecutiveFailures(String taskNo) {
        taskConsecutiveFailures.merge(taskNo, 1, Integer::sum);
    }

    public void resetConsecutiveFailures(String taskNo) {
        taskConsecutiveFailures.put(taskNo, 0);
    }

    public AgentExecStatus getPhaseStatus(String phaseName) {
        return phaseStatus.getOrDefault(phaseName, AgentExecStatus.pending);
    }

    public void setPhaseStatus(String phaseName, AgentExecStatus status) {
        phaseStatus.put(phaseName, status);
    }

    public int getPhaseGateAttempts(String phaseName) {
        return phaseGateAttempts.getOrDefault(phaseName, 0);
    }

    public void setPhaseGateAttempts(String phaseName, int attempt) {
        phaseGateAttempts.put(phaseName, attempt);
    }

    /**
     * Record a runtime {@link AgentPlanError} for the given task. This is the
     * input source for the {@link StagnationSignalType#REPEATED_ERRORS}
     * stagnation signal.
     */
    public void recordError(String taskNo, int attemptNumber, String errorText) {
        AgentPlanError error = new AgentPlanError();
        error.setId("err-" + taskNo + "-" + attemptNumber);
        error.setRelatedTaskNo(taskNo);
        error.setAttemptNumber(attemptNumber);
        error.setBlocking(Boolean.FALSE);
        error.setErrorText(errorText);
        errors.add(error);
    }

    public List<AgentPlanError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /** Count unresolved ({@code resolvedAt == null}) errors for a task. */
    public int countUnresolvedErrors(String taskNo) {
        int n = 0;
        for (AgentPlanError e : errors) {
            if (taskNo.equals(e.getRelatedTaskNo()) && e.getResolvedAt() == null) {
                n++;
            }
        }
        return n;
    }

    public void markGateExhausted(String phaseName, int attempt) {
        gateExhaustedPhases.add(phaseName);
        gateExhaustedAttempt.put(phaseName, attempt);
    }

    public boolean isGateExhausted(String phaseName) {
        return gateExhaustedPhases.contains(phaseName);
    }

    public int getGateExhaustedAttempt(String phaseName) {
        return gateExhaustedAttempt.getOrDefault(phaseName, 0);
    }

    public Set<String> getGateExhaustedPhases() {
        return Collections.unmodifiableSet(gateExhaustedPhases);
    }

    /**
     * A status-provider view of the runtime task statuses, suitable for
     * {@link PlanScheduler#getReadyTasks(AgentPlan, Function)}.
     */
    public Function<String, AgentExecStatus> statusProvider() {
        return taskNo -> {
            AgentExecStatus s = taskStatus.get(taskNo);
            return s == null ? AgentExecStatus.pending : s;
        };
    }
}
