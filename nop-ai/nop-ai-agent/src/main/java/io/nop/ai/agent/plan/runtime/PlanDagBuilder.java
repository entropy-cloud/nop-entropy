package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
import io.nop.task.builder.GraphStepAnalyzer;
import io.nop.task.model.GraphTaskStepModel;
import io.nop.task.model.SimpleTaskStepModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bridges an {@link AgentPlan}'s task {@code dependsOn} structure into a
 * nop-task {@link GraphTaskStepModel} and runs nop-task's real
 * {@link GraphStepAnalyzer} for cycle detection (design §14.3).
 *
 * <p><b>DAG scope (design §14.3 ruling: global flattization)</b>: the DAG is
 * <b>global</b> — all tasks across all phases <b>and their recursive
 * subTasks</b> are flattened into a single graph. Cross-phase and
 * cross-subTask dependencies are valid edges. {@code taskNo} must be globally
 * unique across the entire plan.
 *
 * <p><b>Validation</b> (fail-fast, no silent acceptance):
 * <ul>
 *   <li>Duplicate {@code taskNo} (globally) → throw.</li>
 *   <li>Dangling {@code dependsOn} (references a non-existent {@code taskNo})
 *       → throw.</li>
 *   <li>Cyclic {@code dependsOn} (A→B→A or self-loop) → throw (the underlying
 *       nop-task {@code GraphStepAnalyzer} detects the loop and reports
 *       {@code loopEdges}).</li>
 *   <li>Empty plan (no tasks) → valid, returns an empty graph (honest empty
 *       result, not an error).</li>
 * </ul>
 *
 * <p>This component follows the same pattern as
 * {@code TeamTaskGraphBuilder} (plan 233): it performs <b>real</b> nop-task
 * graph construction and analysis — it does not re-implement DAG logic
 * locally.
 */
public class PlanDagBuilder {

    /**
     * Build a validated nop-task {@link GraphTaskStepModel} from an
     * {@link AgentPlan}'s globally-flattened task collection.
     *
     * <p>The returned model has been through nop-task's
     * {@link GraphStepAnalyzer}, so it is guaranteed cycle-free (or this
     * method throws).
     *
     * @param plan the agent plan (non-null)
     * @return a validated, cycle-free graph model (empty if the plan has no
     *         tasks)
     * @throws NopAiAgentException if duplicate taskNo, dangling dependsOn,
     *         or a cyclic dependency is detected
     */
    public GraphTaskStepModel buildDag(AgentPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        List<AgentPlanTaskModel> allTasks = collectAllTasks(plan);

        if (allTasks.isEmpty()) {
            GraphTaskStepModel empty = new GraphTaskStepModel();
            empty.setName("agent-plan-dag");
            empty.setEnterSteps(new LinkedHashSet<>());
            empty.setExitSteps(new LinkedHashSet<>());
            return empty;
        }

        Map<String, AgentPlanTaskModel> taskByNo = new LinkedHashMap<>();
        for (AgentPlanTaskModel task : allTasks) {
            String taskNo = task.getTaskNo();
            if (taskByNo.containsKey(taskNo)) {
                throw new NopAiAgentException(
                        "nop.ai.agent.plan.duplicate-task-no: taskNo '" + taskNo
                                + "' appears more than once in the plan (taskNo must be globally unique)");
            }
            taskByNo.put(taskNo, task);
        }

        Set<String> allTaskNos = taskByNo.keySet();

        GraphTaskStepModel graph = new GraphTaskStepModel();
        graph.setName("agent-plan-dag");

        Set<String> dependedUpon = new HashSet<>();

        for (AgentPlanTaskModel task : allTasks) {
            String taskNo = task.getTaskNo();

            SimpleTaskStepModel step = new SimpleTaskStepModel();
            step.setName(taskNo);
            step.setBean(taskNo);

            Set<String> deps = task.getDependsOn();
            if (deps != null && !deps.isEmpty()) {
                Set<String> waitSteps = new LinkedHashSet<>();
                for (String depNo : deps) {
                    if (!allTaskNos.contains(depNo)) {
                        throw new NopAiAgentException(
                                "nop.ai.agent.plan.dangling-dependency: task '" + taskNo
                                        + "' depends on non-existent taskNo '" + depNo + "'");
                    }
                    waitSteps.add(depNo);
                    dependedUpon.add(depNo);
                }
                step.setWaitSteps(waitSteps);
            }
            graph.addStep(step);
        }

        Set<String> enterSteps = new LinkedHashSet<>();
        Set<String> exitSteps = new LinkedHashSet<>();

        for (AgentPlanTaskModel task : allTasks) {
            String taskNo = task.getTaskNo();
            Set<String> deps = task.getDependsOn();
            boolean hasDeps = deps != null && !deps.isEmpty();
            if (!hasDeps) {
                enterSteps.add(taskNo);
            }
            if (!dependedUpon.contains(taskNo)) {
                exitSteps.add(taskNo);
            }
        }

        if (enterSteps.isEmpty()) {
            throw new NopAiAgentException(
                    "nop.ai.agent.plan.no-enter-steps: the plan's dependency graph has no source node "
                            + "(every task depends on another) — this implies a cycle");
        }
        if (exitSteps.isEmpty()) {
            throw new NopAiAgentException(
                    "nop.ai.agent.plan.no-exit-steps: the plan's dependency graph has no sink node "
                            + "(every task is depended upon by another) — this implies a cycle");
        }

        graph.setEnterSteps(enterSteps);
        graph.setExitSteps(exitSteps);

        try {
            new GraphStepAnalyzer().analyze(graph);
        } catch (NopAiAgentException e) {
            throw e;
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "nop.ai.agent.plan.cycle-detected: the plan's dependsOn structure contains a cycle", e);
        }

        return graph;
    }

    /**
     * Recursively collect all tasks from all phases, including nested
     * subTasks. This is the global flattization required by design §14.3.
     *
     * @param plan the agent plan
     * @return a flat list of all tasks (including subTasks at any depth)
     */
    public List<AgentPlanTaskModel> collectAllTasks(AgentPlan plan) {
        List<AgentPlanTaskModel> all = new ArrayList<>();
        if (plan.getPhases() != null) {
            for (AgentPlanPhase phase : plan.getPhases()) {
                collectTasksRecursive(phase.getTasks(), all);
            }
        }
        return all;
    }

    private void collectTasksRecursive(List<AgentPlanTaskModel> tasks,
                                       List<AgentPlanTaskModel> out) {
        if (tasks == null) return;
        for (AgentPlanTaskModel task : tasks) {
            out.add(task);
            collectTasksRecursive(task.getSubTasks(), out);
        }
    }
}
