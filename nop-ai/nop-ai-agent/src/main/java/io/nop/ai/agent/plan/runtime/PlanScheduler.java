package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
import io.nop.ai.agent.plan.model.TriggerRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes the set of <b>ready</b> tasks for an {@link AgentPlan} based on
 * each task's {@link TriggerRule} and the global DAG topology (design §14.2).
 *
 * <p>A task is "ready" when its trigger rule is satisfied by the current
 * states of its dependencies AND the task itself is not yet in a terminal
 * state.
 *
 * <p><b>Trigger rule semantics</b>:
 * <ul>
 *   <li>{@link TriggerRule#all_success} — all dependencies are
 *       {@link AgentExecStatus#completed}. (Default.)</li>
 *   <li>{@link TriggerRule#one_success} — at least one dependency is
 *       {@link AgentExecStatus#completed}.</li>
 *   <li>{@link TriggerRule#none_failed_min_one_success} — at least one
 *       dependency is {@link AgentExecStatus#completed} and none are
 *       {@link AgentExecStatus#failed}.</li>
 *   <li>{@link TriggerRule#all_done} — all dependencies are in a terminal
 *       state (completed, failed, cancelled, etc.).</li>
 * </ul>
 *
 * <p>This scheduler is a stateless query: it reads task statuses (either from
 * the plan model, or from a runtime status provider overlay — see
 * {@link #getReadyTasks(AgentPlan, Function)}) and computes the ready set
 * without mutating anything.
 */
public class PlanScheduler {

    /**
     * Compute the list of tasks that are ready to execute, reading statuses
     * from the plan model itself.
     *
     * @param plan the agent plan (non-null; should have passed
     *             {@link AgentPlanValidator} validation so the DAG is
     *             cycle-free and all dependsOn references are valid)
     * @return an unmodifiable list of ready tasks (never null; empty if none)
     */
    public List<AgentPlanTaskModel> getReadyTasks(AgentPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        Map<String, AgentExecStatus> declaredStatus = new HashMap<>();
        for (AgentPlanTaskModel t : new PlanDagBuilder().collectAllTasks(plan)) {
            declaredStatus.put(t.getTaskNo(), t.getStatus());
        }
        return getReadyTasks(plan, declaredStatus::get);
    }

    /**
     * Compute the list of tasks that are ready to execute, reading runtime
     * statuses from a provider overlay. Used by the {@link PlanExecutor} host
     * which tracks task statuses in a mutable execution state rather than in
     * the (frozen) plan model.
     *
     * @param plan           the agent plan (non-null)
     * @param statusProvider maps a {@code taskNo} to its runtime
     *                       {@link AgentExecStatus}; may return {@code null}
     *                       (treated as {@link AgentExecStatus#pending})
     * @return an unmodifiable list of ready tasks (never null; empty if none)
     */
    public List<AgentPlanTaskModel> getReadyTasks(AgentPlan plan,
                                                  Function<String, AgentExecStatus> statusProvider) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (statusProvider == null) {
            throw new IllegalArgumentException("statusProvider must not be null");
        }

        List<AgentPlanTaskModel> allTasks = new PlanDagBuilder().collectAllTasks(plan);
        if (allTasks.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, AgentPlanTaskModel> taskByNo = allTasks.stream()
                .collect(Collectors.toMap(AgentPlanTaskModel::getTaskNo, Function.identity(), (a, b) -> a));

        List<AgentPlanTaskModel> ready = new ArrayList<>();

        for (AgentPlanTaskModel task : allTasks) {
            if (isTerminal(statusOf(task.getTaskNo(), statusProvider))) {
                continue;
            }

            if (isTriggerSatisfied(task, taskByNo, statusProvider)) {
                ready.add(task);
            }
        }

        return Collections.unmodifiableList(ready);
    }

    private static AgentExecStatus statusOf(String taskNo, Function<String, AgentExecStatus> statusProvider) {
        AgentExecStatus s = statusProvider.apply(taskNo);
        return s == null ? AgentExecStatus.pending : s;
    }

    private boolean isTriggerSatisfied(AgentPlanTaskModel task,
                                       Map<String, AgentPlanTaskModel> taskByNo,
                                       Function<String, AgentExecStatus> statusProvider) {
        Set<String> deps = task.getDependsOn();

        if (deps == null || deps.isEmpty()) {
            return true;
        }

        TriggerRule trigger = task.getTriggerRule();
        if (trigger == null) {
            trigger = TriggerRule.all_success;
        }

        boolean anyCompleted = false;
        boolean anyFailed = false;
        boolean allTerminal = true;

        for (String depNo : deps) {
            AgentPlanTaskModel dep = taskByNo.get(depNo);
            if (dep == null) continue;

            AgentExecStatus depStatus = statusOf(dep.getTaskNo(), statusProvider);
            if (depStatus == AgentExecStatus.completed) {
                anyCompleted = true;
            }
            if (depStatus == AgentExecStatus.failed) {
                anyFailed = true;
            }
            if (!isTerminal(depStatus)) {
                allTerminal = false;
            }
        }

        switch (trigger) {
            case all_success:
                return deps.stream()
                        .allMatch(depNo -> taskByNo.get(depNo) != null
                                && statusOf(depNo, statusProvider) == AgentExecStatus.completed);
            case one_success:
                return anyCompleted;
            case none_failed_min_one_success:
                return anyCompleted && !anyFailed;
            case all_done:
                return allTerminal;
            default:
                throw new IllegalArgumentException("Unknown TriggerRule: " + trigger);
        }
    }

    /**
     * Check whether a status is terminal (the task will not transition
     * further). Non-terminal statuses: {@code pending}, {@code running}.
     */
    static boolean isTerminal(AgentExecStatus status) {
        if (status == null) return false;
        switch (status) {
            case pending:
            case running:
                return false;
            default:
                return true;
        }
    }
}
