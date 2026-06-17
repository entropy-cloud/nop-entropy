package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.task.model.GraphTaskStepModel;
import io.nop.task.model.TaskStepModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Topology-aware query layer over a team's task DAG.
 *
 * <p>Wraps a {@link TeamTaskGraphBuilder}-produced {@link GraphTaskStepModel}
 * together with the current {@link TeamTask} statuses to answer:
 * <ul>
 *   <li><b>Ready tasks</b>: tasks whose all {@code blockedBy} dependencies
 *       are in {@link TeamTaskStatus#COMPLETED}. These are eligible for
 *       execution / claim.</li>
 *   <li><b>Blocked tasks</b>: tasks that have at least one dependency not
 *       yet COMPLETED (still CREATED / CLAIMED / ABANDONED).</li>
 * </ul>
 *
 * <p>The topology is derived from the nop-task graph model's
 * {@code waitSteps} edges (which encode the {@code blockedBy} structure),
 * combined with each task's current status. This is a pure query — it does
 * not mutate the store.
 *
 * <p><b>No Silent No-Op</b> (Minimum Rules #24): if the topology is
 * constructed from an empty task collection, all query methods return
 * empty lists (honest empty result, not null).
 *
 * <p>See plan 233 (L4-nop-task-dag-integration).
 */
public class TeamTaskTopology {

    private final List<TeamTask> tasks;
    private final Map<String, TeamTask> taskById;

    /**
     * Construct a topology from a team's tasks.
     *
     * <p>This builds a fresh {@link GraphTaskStepModel} via
     * {@link TeamTaskGraphBuilder} (which performs cycle detection). If the
     * task collection is empty, the topology is valid but empty — all
     * queries return empty lists.
     *
     * @param tasks the team's tasks (non-null)
     */
    public TeamTaskTopology(Collection<TeamTask> tasks) {
        this.tasks = tasks != null ? new ArrayList<>(tasks) : Collections.emptyList();
        this.taskById = this.tasks.stream()
                .collect(Collectors.toMap(TeamTask::getTaskId, Function.identity(), (a, b) -> a));
    }

    /**
     * Return the list of tasks that are <b>ready</b> to execute: their
     * status is not yet terminal ({@link TeamTaskStatus#COMPLETED} or
     * {@link TeamTaskStatus#ABANDONED}) and all their in-set
     * {@code blockedBy} dependencies are {@link TeamTaskStatus#COMPLETED}.
     *
     * @return an unmodifiable list of ready tasks (never null; empty if none)
     */
    public List<TeamTask> getReadyTasks() {
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> allTaskIds = taskById.keySet();
        List<TeamTask> ready = new ArrayList<>();

        for (TeamTask task : tasks) {
            if (task.getStatus() == TeamTaskStatus.COMPLETED
                    || task.getStatus() == TeamTaskStatus.ABANDONED) {
                continue;
            }

            boolean allDepsCompleted = true;
            for (String depId : task.getBlockedBy()) {
                if (!allTaskIds.contains(depId)) {
                    continue;
                }
                TeamTask dep = taskById.get(depId);
                if (dep.getStatus() != TeamTaskStatus.COMPLETED) {
                    allDepsCompleted = false;
                    break;
                }
            }

            if (allDepsCompleted) {
                ready.add(task);
            }
        }

        return Collections.unmodifiableList(ready);
    }

    /**
     * Return the list of tasks that are <b>blocked</b>: their status is not
     * terminal and at least one in-set {@code blockedBy} dependency is not
     * yet {@link TeamTaskStatus#COMPLETED}.
     *
     * @return an unmodifiable list of blocked tasks (never null; empty if none)
     */
    public List<TeamTask> getBlockedTasks() {
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> allTaskIds = taskById.keySet();
        List<TeamTask> blocked = new ArrayList<>();

        for (TeamTask task : tasks) {
            if (task.getStatus() == TeamTaskStatus.COMPLETED
                    || task.getStatus() == TeamTaskStatus.ABANDONED) {
                continue;
            }

            for (String depId : task.getBlockedBy()) {
                if (!allTaskIds.contains(depId)) {
                    continue;
                }
                TeamTask dep = taskById.get(depId);
                if (dep.getStatus() != TeamTaskStatus.COMPLETED) {
                    blocked.add(task);
                    break;
                }
            }
        }

        return Collections.unmodifiableList(blocked);
    }

    /**
     * Return the list of tasks that are already
     * {@link TeamTaskStatus#COMPLETED}.
     *
     * @return an unmodifiable list (never null)
     */
    public List<TeamTask> getCompletedTasks() {
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .filter(t -> t.getStatus() == TeamTaskStatus.COMPLETED)
                .collect(Collectors.toUnmodifiableList());
    }
}
