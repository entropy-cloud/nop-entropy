package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTask;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable per-task dispatch plan produced by {@link ITaskMemberRouter}
 * (plan 244 / L4-multi-member-per-task-routing).
 *
 * <p>A dispatch plan describes <b>which member targets</b> a single team-task
 * graph node fans out to and <b>how</b> their results reduce. It is the
 * bridge between the routing decision (build-time, non-executing) and the
 * node execution model (run-time): the orchestrator consumes the plan to
 * select the node step ({@code MemberAgentTaskStep} / {@code SpawnMemberAgentTaskStep}
 * / the multi-member fan-out variants) and the reduction strategy for the
 * node's N member futures.
 *
 * <h2>Single-member plan = zero-regression shape</h2>
 * <p>Under the NoOp / Single shipped default the plan always contains
 * exactly one target (the existing {@code resolveMember} /
 * {@code resolveSpawnTarget} single-target result wrapped as a singleton
 * list) and the shipped all-must-succeed reduction. A single-member plan
 * is consumed by the existing single-target node steps line-for-line —
 * zero behaviour regression for plans 233/238/241/243.
 *
 * <h2>Multi-member plan = fan-out</h2>
 * <p>A plan with N {@code >} 1 targets fans the node out to N member
 * agents that run concurrently. The shipped all-must-succeed reduction
 * requires all N members to reach {@code AgentExecStatus.completed} before
 * the task transitions CLAIMED → COMPLETED; any failure fast-fails the
 * node and leaves the task CLAIMED (No Silent No-Op #24). Other reduction
 * strategies (quorum / majority / first-wins) are opt-in extension points
 * and are Non-Goals of plan 244.
 *
 * <h2>Empty plan = honest failure</h2>
 * <p>A plan with zero targets is an honest failure: the orchestrator
 * throws (and leaves the task in its CREATED state, never silently skipping
 * the node — Minimum Rules #24). This is the routing equivalent of the
 * pre-244 {@code resolveMember} no-bound-member signal, but raised as an
 * explicit honest failure rather than a silent skip.
 *
 * <p>Immutable. Use {@link #of} for the common shapes.
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing).
 */
public final class MemberDispatchPlan {

    private final Team team;
    private final TeamTask task;
    private final List<DispatchTarget> targets;
    private final IReductionStrategy reductionStrategy;

    /**
     * Build an immutable dispatch plan.
     *
     * @param team              the live team snapshot (non-null)
     * @param task              the team task this plan dispatches (non-null)
     * @param targets           the dispatch targets (non-null, non-empty; an
     *                          empty list is an honest failure and the
     *                          orchestrator will throw on it rather than
     *                          build a hollow node)
     * @param reductionStrategy the reduction strategy for the N member
     *                          futures (non-null; the shipped default is
     *                          {@link AllMustSucceedReduction#instance()})
     */
    public MemberDispatchPlan(Team team, TeamTask task,
                              List<DispatchTarget> targets,
                              IReductionStrategy reductionStrategy) {
        this.team = Objects.requireNonNull(team, "team");
        this.task = Objects.requireNonNull(task, "task");
        this.targets = Collections.unmodifiableList(
                new java.util.ArrayList<>(Objects.requireNonNull(targets, "targets")));
        this.reductionStrategy = Objects.requireNonNull(reductionStrategy, "reductionStrategy");
    }

    /**
     * @return the live team snapshot the routing decision was made against.
     */
    public Team getTeam() {
        return team;
    }

    /**
     * @return the team task this plan dispatches.
     */
    public TeamTask getTask() {
        return task;
    }

    /**
     * @return an unmodifiable view of the dispatch targets. May be empty
     *         (the orchestrator treats an empty plan as an honest failure).
     */
    public List<DispatchTarget> getTargets() {
        return targets;
    }

    /**
     * @return the reduction strategy that combines the N member futures.
     */
    public IReductionStrategy getReductionStrategy() {
        return reductionStrategy;
    }

    /**
     * @return the number of dispatch targets (0 = honest-failure plan,
     *         1 = single-member plan, &gt;1 = fan-out plan).
     */
    public int size() {
        return targets.size();
    }

    /**
     * @return {@code true} when this plan has no targets (the orchestrator
     *         will throw an honest failure rather than build a hollow node).
     */
    public boolean isEmpty() {
        return targets.isEmpty();
    }

    @Override
    public String toString() {
        return "MemberDispatchPlan{taskId=" + task.getTaskId()
                + ", targets=" + targets
                + ", reduction=" + reductionStrategy + '}';
    }
}
