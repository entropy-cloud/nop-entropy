package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanError;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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

    /**
     * Per-task typed-failure counters (design §13.3 W2-3 three-level failure
     * escalation). Each task maps to a {@code FailureType → count} enum-map.
     * An entry is created lazily on the first typed failure and reset on task
     * success / ROLLBACK phase reset (analogous to
     * {@link #taskConsecutiveFailures}).
     */
    private final Map<String, EnumMap<FailureType, Integer>> taskTypedFailures = new LinkedHashMap<>();

    /**
     * Runtime task-node overlay (design §14.4.3 SPLIT_TASK): sub-task nodes
     * inserted by SPLIT live here, keyed by taskNo, with their owning phase.
     * The frozen template's task list is never mutated; these nodes are
     * visible to {@link PlanScheduler} (structural source) and to the
     * executor's phase filter only through the overlay API.
     */
    private final Map<String, AgentPlanTaskModel> runtimeTasks = new LinkedHashMap<>();
    private final Map<String, String> runtimeTaskPhase = new LinkedHashMap<>();
    private final Set<String> splitParents = new LinkedHashSet<>();

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

    // -------------------- Typed-failure counters (W2-3, design §13.3) --------------------

    /**
     * Increment the per-task cumulative counter for the given failure type
     * (design §13.3 W2-3). The host calls this for every typed failure
     * returned by {@link TaskRunner}; the {@link FailureEscalationPolicy}
     * then decides whether to escalate.
     *
     * @param taskNo the task number (non-null)
     * @param type   the failure type (non-null)
     */
    public void recordTypedFailure(String taskNo, FailureType type) {
        if (taskNo == null) {
            throw new IllegalArgumentException("taskNo must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        taskTypedFailures
                .computeIfAbsent(taskNo, k -> new EnumMap<>(FailureType.class))
                .merge(type, 1, Integer::sum);
    }

    /**
     * The per-task cumulative count for the given failure type (0 if none).
     */
    public int getTypedFailureCount(String taskNo, FailureType type) {
        EnumMap<FailureType, Integer> counts = taskTypedFailures.get(taskNo);
        if (counts == null) {
            return 0;
        }
        return counts.getOrDefault(type, 0);
    }

    /**
     * Reset all typed-failure counters for a task to zero. Called on task
     * success (analogous to {@link #resetConsecutiveFailures}) and during
     * ROLLBACK phase reset (analogous to
     * {@link PlanReplanner#resetStagnationForPhase}).
     */
    public void resetTypedFailures(String taskNo) {
        if (taskNo != null) {
            taskTypedFailures.remove(taskNo);
        }
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

    /**
     * Mark all unresolved runtime errors of the given task as resolved
     * (design §14.4.3 — ROLLBACK enactment is the first business writer of
     * {@code AgentPlanError.resolvedAt}). Returns the count of errors resolved.
     */
    public int resolveErrorsForTask(String taskNo) {
        LocalDateTime now = LocalDateTime.now();
        int n = 0;
        for (AgentPlanError e : errors) {
            if (taskNo.equals(e.getRelatedTaskNo()) && e.getResolvedAt() == null) {
                e.setResolvedAt(now);
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

    /**
     * Clear the gate-exhaustion marker for a phase (design §14.4.3 ROLLBACK
     * enactment). Without this, a ROLLBACK would leave the marker in place and
     * {@link StagnationDetector} would immediately re-emit
     * {@link StagnationSignalType#GATE_EXHAUSTED} on the next detect cycle,
     * re-triggering ROLLBACK and deadlocking (until the cycle-safety bound
     * fires). Returns true if a marker was actually cleared.
     */
    public boolean clearGateExhausted(String phaseName) {
        gateExhaustedAttempt.remove(phaseName);
        return gateExhaustedPhases.remove(phaseName);
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

    // -------------------- Runtime task-node overlay (SPLIT_TASK, design §14.4.3) --------------------

    /**
     * Register a runtime sub-task node under an owning phase. Used by SPLIT
     * enactment. The node is appended to the mutable overlay (the frozen
     * template is never mutated) and initialized to
     * {@link AgentExecStatus#pending} with zeroed counters.
     */
    public void registerRuntimeTask(String phaseName, AgentPlanTaskModel task) {
        if (phaseName == null) {
            throw new IllegalArgumentException("phaseName must not be null");
        }
        if (task == null || task.getTaskNo() == null) {
            throw new IllegalArgumentException("task with non-empty taskNo must not be null");
        }
        String taskNo = task.getTaskNo();
        if (taskStatus.containsKey(taskNo) && !runtimeTasks.containsKey(taskNo)) {
            throw new IllegalStateException(
                    "runtime taskNo '" + taskNo + "' collides with a frozen/registered task");
        }
        runtimeTasks.put(taskNo, task);
        runtimeTaskPhase.put(taskNo, phaseName);
        if (!taskStatus.containsKey(taskNo)) {
            taskStatus.put(taskNo, task.getStatus() == null ? AgentExecStatus.pending : task.getStatus());
            taskConsecutiveFailures.put(taskNo, 0);
            taskAttempts.put(taskNo, 0);
        }
    }

    /** Whether the given task is a runtime overlay node (inserted by SPLIT). */
    public boolean isRuntimeTask(String taskNo) {
        return taskNo != null && runtimeTasks.containsKey(taskNo);
    }

    /** Whether the given (frozen-template) task was marked as a SPLIT parent. */
    public boolean isSplitParent(String taskNo) {
        return taskNo != null && splitParents.contains(taskNo);
    }

    /** Mark a frozen-template task as a SPLIT parent (placeholder). */
    public void markSplitParent(String taskNo) {
        if (taskNo != null) {
            splitParents.add(taskNo);
        }
    }

    /** The runtime overlay task nodes (for the scheduler's structural source). */
    public java.util.Collection<AgentPlanTaskModel> runtimeTaskOverlay() {
        return Collections.unmodifiableCollection(runtimeTasks.values());
    }

    /** Count of overlay task nodes currently in {@link AgentExecStatus#completed}. */
    public int countCompletedRuntimeTasks() {
        int n = 0;
        for (String taskNo : runtimeTasks.keySet()) {
            if (taskStatus.get(taskNo) == AgentExecStatus.completed) {
                n++;
            }
        }
        return n;
    }

    /** Whether a phase with the given name exists in the (frozen) plan. */
    public boolean hasPhase(String phaseName) {
        if (phaseName == null || plan.getPhases() == null) {
            return false;
        }
        for (AgentPlanPhase phase : plan.getPhases()) {
            if (phaseName.equals(phase.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The ordered list of phase names declared in the (frozen) plan template.
     * Used by the executor for phase-index resolution and rollback re-entry.
     */
    public List<String> getPhaseNames() {
        List<String> names = new ArrayList<>();
        if (plan.getPhases() != null) {
            for (AgentPlanPhase phase : plan.getPhases()) {
                if (phase.getName() != null) {
                    names.add(phase.getName());
                }
            }
        }
        return names;
    }

    /**
     * The set of declared task numbers owned by a phase (recursive over
     * subTasks), read from the frozen template, <b>plus</b> any runtime
     * overlay nodes registered under that phase (SPLIT sub-tasks). Used by
     * ROLLBACK/SPLIT enactment to scope task-status resets to a single phase
     * and by the executor's phase filter to admit runtime sub-tasks.
     */
    public Set<String> phaseTaskNos(String phaseName) {
        Set<String> nos = new LinkedHashSet<>();
        if (plan.getPhases() != null && phaseName != null) {
            for (AgentPlanPhase phase : plan.getPhases()) {
                if (phaseName.equals(phase.getName())) {
                    collectTaskNosRecursive(phase.getTasks(), nos);
                    break;
                }
            }
        }
        if (phaseName != null) {
            for (Map.Entry<String, String> e : runtimeTaskPhase.entrySet()) {
                if (phaseName.equals(e.getValue())) {
                    nos.add(e.getKey());
                }
            }
        }
        return nos;
    }

    /** The phase name that owns the given task (frozen or runtime overlay), or {@code null} if not found. */
    public String phaseOwningTask(String taskNo) {
        if (taskNo == null) {
            return null;
        }
        String runtimePhase = runtimeTaskPhase.get(taskNo);
        if (runtimePhase != null) {
            return runtimePhase;
        }
        if (plan.getPhases() == null) {
            return null;
        }
        for (AgentPlanPhase phase : plan.getPhases()) {
            Set<String> nos = new LinkedHashSet<>();
            collectTaskNosRecursive(phase.getTasks(), nos);
            if (nos.contains(taskNo)) {
                return phase.getName();
            }
        }
        return null;
    }

    private void collectTaskNosRecursive(List<AgentPlanTaskModel> tasks, Set<String> out) {
        if (tasks == null) return;
        for (AgentPlanTaskModel task : tasks) {
            if (task.getTaskNo() != null) {
                out.add(task.getTaskNo());
            }
            collectTaskNosRecursive(task.getSubTasks(), out);
        }
    }
}
