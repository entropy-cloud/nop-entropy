package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.TeamTask;
import io.nop.task.builder.GraphStepAnalyzer;
import io.nop.task.model.GraphTaskStepModel;
import io.nop.task.model.SimpleTaskStepModel;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bridges a team's {@link TeamTask} collection into a nop-task
 * {@link GraphTaskStepModel} — the in-memory DAG graph model used by the
 * nop-task engine for topological scheduling, cycle detection, and
 * dependency-ordered execution.
 *
 * <p><b>Mapping (design 裁定 2)</b>:
 * <ul>
 *   <li>Each team task → one graph node (step name = {@code taskId}).</li>
 *   <li>{@link TeamTask#getBlockedBy()} → the node's {@code waitSteps}
 *       (predecessors that must complete successfully before this node runs).
 *       Only dependencies referencing tasks <em>within the same team's task
 *       set</em> are mapped; dangling references to unknown task IDs are
 *       ignored for graph-edge purposes (they cannot create a real edge to
 *       a non-existent node).</li>
 *   <li>{@code enterSteps} = tasks with no in-set blockedBy (the DAG
 *       sources).</li>
 *   <li>{@code exitSteps} = tasks that no other task depends on (the DAG
 *       sinks).</li>
 * </ul>
 *
 * <p><b>Cycle detection (design 裁定 2 / Minimum Rules #24)</b>: after
 * constructing the graph model, this builder invokes nop-task's real
 * {@link GraphStepAnalyzer#analyze} which builds a topological {@code Dag}
 * and checks {@code containsLoop()}. Cyclic {@code blockedBy} (e.g.
 * A→B→A or a self-loop A→A) causes a {@link NopAiAgentException} to be
 * thrown — <b>fast failure, not silent acceptance</b>. This closes the gap
 * where the team task store currently stores cyclic dependencies verbatim
 * without validation.
 *
 * <p>This component is the single integration point between nop-ai-agent's
 * team task model and nop-task's DAG model. It performs <b>real</b>
 * nop-task graph construction and analysis — it does not re-implement DAG
 * logic locally.
 *
 * <p>See plan 233 (L4-nop-task-dag-integration).
 */
public class TeamTaskGraphBuilder {

    /**
     * Build a validated nop-task {@link GraphTaskStepModel} from a team's
     * task collection.
     *
     * <p>The returned model has been through nop-task's
     * {@link GraphStepAnalyzer}, so it is guaranteed cycle-free (or this
     * method throws). The model's {@code enterSteps} / {@code exitSteps}
     * are derived from the {@code blockedBy} structure.
     *
     * @param tasks the team's tasks (non-null; may be empty)
     * @return a validated, cycle-free graph model
     * @throws NopAiAgentException if the task collection is empty (no graph
     *         can be constructed — honest fast-failure per Minimum Rules #24),
     *         or if the {@code blockedBy} structure contains a cycle (the
     *         underlying nop-task {@link GraphStepAnalyzer} throws
     *         {@code ERR_TASK_GRAPH_STEP_CONTAINS_LOOP} which is wrapped)
     */
    public GraphTaskStepModel buildGraph(Collection<TeamTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new NopAiAgentException(
                    "nop.ai.team.flow.empty-task-set: cannot build a DAG graph from an empty task collection");
        }

        Map<String, TeamTask> taskById = tasks.stream()
                .collect(Collectors.toMap(TeamTask::getTaskId, Function.identity(), (a, b) -> a));

        Set<String> allTaskIds = taskById.keySet();

        GraphTaskStepModel graph = new GraphTaskStepModel();
        graph.setName("team-task-dag");

        Set<String> dependedUpon = new HashSet<>();

        for (TeamTask task : tasks) {
            SimpleTaskStepModel step = new SimpleTaskStepModel();
            step.setName(task.getTaskId());
            step.setBean(task.getTaskId());

            Set<String> waitSteps = new LinkedHashSet<>();
            for (String depId : task.getBlockedBy()) {
                if (allTaskIds.contains(depId)) {
                    waitSteps.add(depId);
                    dependedUpon.add(depId);
                }
            }
            if (!waitSteps.isEmpty()) {
                step.setWaitSteps(waitSteps);
            }
            graph.addStep(step);
        }

        Set<String> enterSteps = new LinkedHashSet<>();
        Set<String> exitSteps = new LinkedHashSet<>();

        for (TeamTask task : tasks) {
            String taskId = task.getTaskId();
            boolean hasInSetDeps = task.getBlockedBy().stream().anyMatch(allTaskIds::contains);
            if (!hasInSetDeps) {
                enterSteps.add(taskId);
            }
            if (!dependedUpon.contains(taskId)) {
                exitSteps.add(taskId);
            }
        }

        graph.setEnterSteps(enterSteps);
        graph.setExitSteps(exitSteps);

        try {
            new GraphStepAnalyzer().analyze(graph);
        } catch (Exception e) {
            throw new NopAiAgentException(
                    "nop.ai.team.flow.cycle-detected: the team task blockedBy structure contains a cycle", e);
        }

        return graph;
    }

    /**
     * Convenience: build a graph model from a task list and return it,
     * or return {@code null} if the list is empty.
     *
     * <p>This is useful for callers that treat "no tasks" as a legitimate
     * empty-graph state (e.g. topology queries on a team with no tasks yet).
     * Callers that need fast-failure on empty should use
     * {@link #buildGraph(Collection)} instead.
     *
     * @param tasks the team's tasks (non-null)
     * @return a validated graph model, or {@code null} if {@code tasks} is empty
     */
    public GraphTaskStepModel buildGraphOrEmpty(Collection<TeamTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        return buildGraph(tasks);
    }
}
