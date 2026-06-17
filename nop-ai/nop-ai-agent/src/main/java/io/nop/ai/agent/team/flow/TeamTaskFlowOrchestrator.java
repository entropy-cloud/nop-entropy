package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.impl.TaskImpl;
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.step.GraphTaskStep;
import io.nop.task.step.GraphTaskStep.GraphStepNode;
import io.nop.task.step.TaskStepExecution;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dependency-ordered synchronous orchestrator that runs a team's task DAG
 * through the <b>real nop-task runtime</b> (design 裁定 3).
 *
 * <p>For a given team, the orchestrator:
 * <ol>
 *   <li>Loads the team's tasks from {@link ITeamTaskStore} (the single source
 *       of truth for team-task state — design 裁定 1).</li>
 *   <li>Builds a nop-task {@link GraphTaskStepModel} via
 *       {@link TeamTaskGraphBuilder} — this performs <b>real</b> cycle
 *       detection through nop-task's {@code GraphStepAnalyzer} (design
 *       裁定 2). Cyclic {@code blockedBy} is rejected before any execution.</li>
 *   <li>Constructs a runtime {@link GraphTaskStep} — nop-task's DAG scheduler
 *       — where each team task is one graph node whose {@code waitSteps}
 *       encode its {@code blockedBy} predecessors. Each node's step is a
 *       {@link MemberAgentTaskStep} that delegates to a bound member agent
 *       (design 裁定 4: consume already-bound members, never spawn).</li>
 *   <li>Wraps the graph as the main step of a nop-task {@link ITask}, creates
 *       a runtime via {@link ITaskFlowManager#newTaskRuntime}, and executes it
 *       synchronously ({@link io.nop.task.TaskStepReturn#syncGetOutputs()}).
 *       The nop-task scheduler guarantees a node runs only after all its
 *       {@code waitSteps} predecessors have completed successfully.</li>
 *   <li>Each successful node transitions its task CLAIMED → COMPLETED via
 *       {@link ITeamTaskStore#completeTask}; a failed node short-circuits the
 *       graph and cancels its successors (dependency-ordered failure
 *       propagation).</li>
 * </ol>
 *
 * <h2>Synthetic sink</h2>
 * A nop-task graph completes (resolves its future) when an <em>exit</em> node
 * finishes. A team-task set with multiple independent chains has multiple
 * natural sinks; without care the graph would terminate on the first sink and
 * cancel the rest. To guarantee <b>every</b> team task runs regardless of the
 * DAG shape, the orchestrator marks all real task nodes non-exit and appends
 * one synthetic {@link FlowSinkStep} that waits on every task and is the sole
 * exit. This cannot introduce a cycle (the sink has no successors).
 *
 * <h2>No Silent No-Op (Minimum Rules #24)</h2>
 * Empty task set, unknown team, cyclic {@code blockedBy}, and a team with no
 * bound member for a task all fail fast with {@link NopAiAgentException}.
 *
 * <p>This orchestrator is read-only with respect to {@link IAgentEngine},
 * {@link ITeamTaskStore}, and {@link ITeamManager}: it consumes their existing
 * contracts and does not modify them.
 *
 * <p>See plan 233 (L4-nop-task-dag-integration) Phase 2.
 */
public class TeamTaskFlowOrchestrator {

    private final IAgentEngine agentEngine;
    private final ITeamTaskStore taskStore;
    private final ITeamManager teamManager;
    private final ITaskFlowManager taskFlowManager;
    private final TeamTaskGraphBuilder graphBuilder;

    public TeamTaskFlowOrchestrator(IAgentEngine agentEngine, ITeamTaskStore taskStore,
                                    ITeamManager teamManager) {
        this(agentEngine, taskStore, teamManager, null);
    }

    public TeamTaskFlowOrchestrator(IAgentEngine agentEngine, ITeamTaskStore taskStore,
                                    ITeamManager teamManager, ITaskFlowManager taskFlowManager) {
        this.agentEngine = agentEngine;
        this.taskStore = taskStore;
        this.teamManager = teamManager;
        this.taskFlowManager = taskFlowManager != null ? taskFlowManager : new TaskFlowManagerImpl();
        this.graphBuilder = new TeamTaskGraphBuilder();
    }

    /**
     * Execute the team's task DAG synchronously through the nop-task runtime.
     *
     * <p>Structural problems (empty task set, unknown team, cyclic
     * {@code blockedBy}, unresolvable member) throw {@link NopAiAgentException}.
     * A failed node returns a {@link TeamTaskFlowResult} with
     * {@link TeamTaskFlowResult#isSuccess()} {@code false} and the failed /
     * skipped task ids populated — failure is reported honestly, never silent.
     *
     * @param teamId the owning team's identity
     * @return the run outcome (success or failure), never {@code null}
     * @throws NopAiAgentException for structural fast-failures
     */
    public TeamTaskFlowResult execute(String teamId) {
        if (teamId == null) {
            throw new NopAiAgentException("nop.ai.team.flow.null-team-id: teamId must not be null");
        }

        List<TeamTask> tasks = taskStore.getTasksByTeam(teamId);
        if (tasks == null || tasks.isEmpty()) {
            throw new NopAiAgentException(
                    "nop.ai.team.flow.no-tasks: team has no tasks to orchestrate: teamId=" + teamId);
        }

        Team team = teamManager.getTeam(teamId)
                .orElseThrow(() -> new NopAiAgentException(
                        "nop.ai.team.flow.team-not-found: unknown team teamId=" + teamId));

        // Real nop-task cycle detection (裁定 2): buildGraph runs the graph
        // through GraphStepAnalyzer and throws on a cyclic blockedBy set.
        graphBuilder.buildGraph(tasks);

        Set<String> allTaskIds = tasks.stream()
                .map(TeamTask::getTaskId)
                .collect(Collectors.toCollection(HashSet::new));

        ExecutionRecorder recorder = new ExecutionRecorder();

        // Build the runtime nop-task graph: one node per team task.
        List<GraphStepNode> nodes = new ArrayList<>(tasks.size() + 1);
        for (TeamTask task : tasks) {
            Set<String> waitSteps = task.getBlockedBy().stream()
                    .filter(allTaskIds::contains)
                    .collect(Collectors.toCollection(HashSet::new));
            boolean enter = waitSteps.isEmpty();

            ResolvedMember member = resolveMember(team, task);
            MemberAgentTaskStep delegate = new MemberAgentTaskStep(
                    task, member.sessionId, member.agentName, agentEngine, taskStore, recorder);
            delegate.setStepType("team-task:" + task.getTaskId());

            ITaskStepExecution execution = wrapExecution(delegate, task.getTaskId());
            // Real task nodes are never the graph exit (see "Synthetic sink" above).
            nodes.add(new GraphStepNode(new HashSet<>(waitSteps), null, execution, enter, false));
        }

        // Synthetic sole exit: waits on every task, runs last, terminates the graph.
        FlowSinkStep sink = new FlowSinkStep();
        sink.setStepType("team-flow-sink");
        nodes.add(new GraphStepNode(new HashSet<>(allTaskIds), null, wrapExecution(sink, "__sink__"),
                false, true));

        GraphTaskStep graph = new GraphTaskStep();
        graph.setStepType("team-task-dag");
        graph.setNodes(nodes);

        ITask task = new TaskImpl("team-flow:" + teamId, 0, graph, false,
                null, null, Collections.emptyList(), Collections.emptyList());

        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        try {
            task.execute(taskRt).syncGetOutputs();
            return recorder.buildResult(true, tasks);
        } catch (Exception e) {
            // A node delegate threw (member-agent failure / claim/complete CAS loss).
            // nop-task's GraphTaskStep short-circuited the graph and cancelled
            // successor nodes. Report honestly which task failed and which were
            // skipped — never silently succeed.
            return recorder.buildResult(false, tasks);
        }
    }

    private ITaskStepExecution wrapExecution(AbstractTaskStep delegate, String stepName) {
        return new TaskStepExecution(null, stepName,
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                null, null, delegate, null, null, false, null, false);
    }

    /**
     * Resolve the bound member session to delegate a task to (裁定 4):
     * <ol>
     *   <li>If the task is already claimed, use the recorded claimedBy session.</li>
     *   <li>Otherwise prefer the first bound MEMBER-role member; fall back to
     *       the first bound member of any role.</li>
     *   <li>If no member is bound, fail fast (No Silent No-Op).</li>
     * </ol>
     * The orchestrator never spawns a new member.
     */
    private ResolvedMember resolveMember(Team team, TeamTask task) {
        Map<String, TeamMember> members = team.getMembers();

        if (task.getClaimedBy() != null) {
            for (TeamMember m : members.values()) {
                if (task.getClaimedBy().equals(m.getSessionId())) {
                    return new ResolvedMember(m.getMemberName(), m.getSessionId(),
                            agentModelOf(team, m.getMemberName()));
                }
            }
            return new ResolvedMember("claimed", task.getClaimedBy(), null);
        }

        TeamMember fallback = null;
        for (TeamMember m : members.values()) {
            if (!m.isBound()) {
                continue;
            }
            if (m.getRole() == MemberRole.MEMBER) {
                return new ResolvedMember(m.getMemberName(), m.getSessionId(),
                        agentModelOf(team, m.getMemberName()));
            }
            if (fallback == null) {
                fallback = m;
            }
        }
        if (fallback != null) {
            return new ResolvedMember(fallback.getMemberName(), fallback.getSessionId(),
                    agentModelOf(team, fallback.getMemberName()));
        }
        throw new NopAiAgentException(
                "nop.ai.team.flow.no-bound-member: team has no bound member to delegate taskId="
                        + task.getTaskId() + ", teamId=" + team.getTeamId()
                        + " (bind a member session before orchestrating, or use auto-spawn successor)");
    }

    private String agentModelOf(Team team, String memberName) {
        if (team.getSpec() == null) {
            return null;
        }
        for (TeamMemberSpec spec : team.getSpec().getMemberSpecs()) {
            if (spec.getMemberName().equals(memberName)) {
                return spec.getAgentModel();
            }
        }
        return null;
    }

    private static final class ResolvedMember {
        final String memberName;
        final String sessionId;
        final String agentName;

        ResolvedMember(String memberName, String sessionId, String agentName) {
            this.memberName = memberName;
            this.sessionId = sessionId;
            this.agentName = agentName;
        }
    }

    /**
     * Synthetic sole-exit node of the team-task graph. It performs no work;
     * its only role is to be the single nop-task graph exit that waits on
     * every real task, so the graph future resolves exactly once — after all
     * real tasks have completed — regardless of how many natural sinks the
     * DAG has. This is legitimate graph topology (a real terminal marker),
     * not a silent no-op in the Minimum-Rules-#24 sense.
     */
    private static final class FlowSinkStep extends AbstractTaskStep {
        @Nonnull
        @Override
        public TaskStepReturn execute(ITaskStepRuntime stepRt) {
            return TaskStepReturn.RETURN_RESULT("team-flow-complete");
        }
    }
}
