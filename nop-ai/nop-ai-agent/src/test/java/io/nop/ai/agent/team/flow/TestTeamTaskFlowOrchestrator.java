package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for {@link TeamTaskFlowOrchestrator} (plan 233 Phase 2).
 *
 * <p>These tests drive the full path: orchestrator entry → nop-task
 * {@link io.nop.task.step.GraphTaskStep} real DAG scheduler → per-node
 * {@link MemberAgentTaskStep} delegate → mock member-agent engine →
 * {@link io.nop.ai.agent.team.ITeamTaskStore} claim/complete. They satisfy:
 * <ul>
 *   <li><b>Anti-Hollow #22</b>: the orchestrator really executes the graph
 *       through {@code ITask.execute(...).syncGetOutputs()} and every task
 *       is transitioned to COMPLETED in the live store.</li>
 *   <li><b>Wiring #23</b>: a recording mock engine asserts
 *       {@code IAgentEngine.execute} is actually invoked per node, and the
 *       store is mutated (CLAIMED → COMPLETED), not just type-present.</li>
 *   <li><b>Anti-Hollow dependency-order</b>: {@code completionOrder(A) <
 *       startOrder(B)} proves the nop-task scheduler enforced the blockedBy
 *       edge — B's delegate genuinely did not start before A finished.</li>
 *   <li><b>No Silent No-Op #24</b>: empty task set, unknown team, cyclic
 *       blockedBy, and node failure all surface honestly (exception or
 *       failed result with skipped successors).</li>
 * </ul>
 */
public class TestTeamTaskFlowOrchestrator {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Recording mock member-agent engine
    // ========================================================================

    /**
     * Minimal {@link IAgentEngine} that stands in for the bound member agent.
     * It records the start sequence of each invocation (keyed by the
     * {@code teamTaskId} metadata the delegate passes) and returns either a
     * completed result or, for a configured "failOn" task, a failed result.
     *
     * <p>This mirrors the {@code new IAgentEngine(){...}} pattern already used
     * in {@code TestCallAgentAsyncMailbox} for testing IAgentEngine consumers.
     */
    static final class RecordingAgentEngine implements IAgentEngine {
        final List<String> invocationOrder = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Integer> startSeqMap = new HashMap<>();
        int seq = 0;
        final String failOnTaskId;
        final boolean failByException;

        RecordingAgentEngine() {
            this(null, false);
        }

        RecordingAgentEngine(String failOnTaskId, boolean failByException) {
            this.failOnTaskId = failOnTaskId;
            this.failByException = failByException;
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            String taskId = (String) request.getMetadata().get("teamTaskId");
            invocationOrder.add(taskId);
            startSeqMap.put(taskId, ++seq);

            if (failOnTaskId != null && failOnTaskId.equals(taskId)) {
                if (failByException) {
                    CompletableFuture<AgentExecutionResult> f = new CompletableFuture<>();
                    f.completeExceptionally(new RuntimeException("member-agent-boom:" + taskId));
                    return f;
                }
                return CompletableFuture.completedFuture(
                        new AgentExecutionResult(AgentExecStatus.failed, null,
                                Collections.emptyList(), 0, 0L, 0L, "member-agent-failed:" + taskId));
            }
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null));
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                   String memberName, String sessionId) {
        TeamSpec spec = new TeamSpec("flow-team", "test", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberName + "-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), memberName, sessionId, "actor-" + memberName);
        return team;
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId,
                                     String subject, List<String> blockedBy) {
        TeamTask t = store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session");
        return t.getTaskId();
    }

    // ========================================================================
    // Linear A -> B -> C: dependency-ordered execution + COMPLETED + Anti-Hollow
    // ========================================================================

    @Test
    void linearChainExecutesInDependencyOrderAndCompletesAll() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();

        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(b));

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertTrue(result.isSuccess(), "linear chain should complete successfully: " + result);
        assertEquals(Arrays.asList(a, b, c).stream().sorted().collect(java.util.stream.Collectors.toList()),
                result.getCompletedTaskIds().stream().sorted().collect(java.util.stream.Collectors.toList()),
                "all three tasks completed");
        assertNull(result.getFailedTaskId(), "no failure");
        assertTrue(result.getSkippedTaskIds().isEmpty(), "nothing skipped");

        // Store reflects COMPLETED for all three (live mutation, not type-only).
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus());

        // Anti-Hollow: dependency order truly enforced by nop-task scheduler.
        int completionA = result.getCompletionOrder().get(a);
        int startB = result.getStartOrder().get(b);
        int completionB = result.getCompletionOrder().get(b);
        int startC = result.getStartOrder().get(c);
        assertTrue(startB > completionA,
                "B must start AFTER A completes: startB=" + startB + ", completionA=" + completionA);
        assertTrue(startC > completionB,
                "C must start AFTER B completes: startC=" + startC + ", completionB=" + completionB);

        // Wiring #23: the member-agent engine was actually invoked once per task.
        assertEquals(3, engine.invocationOrder.size(),
                "engine.execute invoked exactly once per task");
    }

    // ========================================================================
    // Diamond A -> {B, C} -> D: parallel-ready branches, D after both
    // ========================================================================

    @Test
    void diamondDependencyBothBranchesAfterAAndDAfterBoth() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();

        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertTrue(result.isSuccess(), "diamond should complete: " + result);
        assertEquals(4, result.getCompletedTaskIds().size(), "all four completed");

        int completionA = result.getCompletionOrder().get(a);
        int startB = result.getStartOrder().get(b);
        int startC = result.getStartOrder().get(c);
        int completionB = result.getCompletionOrder().get(b);
        int completionC = result.getCompletionOrder().get(c);
        int startD = result.getStartOrder().get(d);

        // B and C both run only after A completes.
        assertTrue(startB > completionA, "B after A");
        assertTrue(startC > completionA, "C after A");
        // D runs only after BOTH B and C complete.
        assertTrue(startD > completionB, "D after B");
        assertTrue(startD > completionC, "D after C");

        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(d).orElseThrow().getStatus());
    }

    // ========================================================================
    // Failure propagation: B fails -> C (successor) never runs, reported
    // ========================================================================

    @Test
    void nodeFailurePropagatesAndSuccessorsAreSkipped() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(b));

        // Member agent fails (non-completed status) on task B.
        RecordingAgentEngine engine = new RecordingAgentEngine(b, false);

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertFalse(result.isSuccess(), "run with a failed node is not success");
        assertEquals(b, result.getFailedTaskId(), "the failing task is reported");
        // A ran and completed before B failed.
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "predecessor A completed before B failed");
        // B did not complete (left CLAIMED).
        assertNotEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus(),
                "failed task B is not COMPLETED");
        // C was never started (dependency-ordered failure propagation).
        assertTrue(result.getSkippedTaskIds().contains(c),
                "successor C skipped: " + result.getSkippedTaskIds());
        assertEquals(TeamTaskStatus.CREATED, store.getTask(c).orElseThrow().getStatus(),
                "C never claimed (still CREATED)");
        // B was started (claimed) by the engine.
        assertTrue(engine.invocationOrder.contains(b), "B's delegate was invoked");
        assertFalse(engine.invocationOrder.contains(c), "C's delegate never invoked");
    }

    @Test
    void nodeFailureByExceptionAlsoPropagates() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));

        RecordingAgentEngine engine = new RecordingAgentEngine(a, true);

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertFalse(result.isSuccess());
        assertEquals(a, result.getFailedTaskId());
        assertTrue(result.getSkippedTaskIds().contains(b), "B skipped after A threw");
    }

    // ========================================================================
    // No Silent No-Op (#24): structural fast-failures
    // ========================================================================

    @Test
    void emptyTaskSetFailsFast() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(new RecordingAgentEngine(), store, mgr);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> orchestrator.execute(team.getTeamId()));
        assertTrue(ex.getMessage().contains("no-tasks"));
    }

    @Test
    void unknownTeamFailsFast() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        // A store that reports a task for an unknown team id, so the
        // orchestrator gets past the empty-tasks check and hits the
        // team-not-found guard.
        io.nop.ai.agent.team.ITeamTaskStore store = storeReturning(
                "nonexistent-team-id",
                new TeamTask("orphan", "nonexistent-team-id", "A", "d",
                        Collections.emptyList(), TeamTaskStatus.CREATED, "lead", null, 1L));

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(new RecordingAgentEngine(), store, mgr);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> orchestrator.execute("nonexistent-team-id"));
        assertTrue(ex.getMessage().contains("team-not-found"));
    }

    /**
     * Minimal {@link io.nop.ai.agent.team.ITeamTaskStore} that returns the
     * given tasks for one specific team id (and empty otherwise). Used to
     * drive specific orchestrator guard paths without a full store.
     */
    private static io.nop.ai.agent.team.ITeamTaskStore storeReturning(String teamId, TeamTask... tasks) {
        List<TeamTask> list = Arrays.asList(tasks);
        return new io.nop.ai.agent.team.ITeamTaskStore() {
            @Override
            public TeamTask createTask(String t, String s, String d, List<String> b, String c) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<TeamTask> getTask(String taskId) {
                return java.util.Optional.empty();
            }

            @Override
            public List<TeamTask> getTasksByTeam(String tid) {
                return teamId.equals(tid) ? list : Collections.emptyList();
            }

            @Override
            public List<TeamTask> getTasksByCreator(String c) {
                return Collections.emptyList();
            }

            @Override
            public java.util.Optional<TeamTask> claimTask(String t, String b) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<TeamTask> completeTask(String t, String b) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<TeamTask> abandonTask(String t, String b) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void cyclicBlockedByFailsFastBeforeAnyExecution() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        // A minimal ITeamTaskStore whose getTasksByTeam returns a real 2-node
        // cycle (A blockedBy B, B blockedBy A). The orchestrator must run the
        // nop-task GraphStepAnalyzer cycle check and reject it before any node
        // executes — closing the "cyclic blockedBy silently stored" gap.
        io.nop.ai.agent.team.ITeamTaskStore cycleStore = new io.nop.ai.agent.team.ITeamTaskStore() {
            @Override
            public TeamTask createTask(String t, String s, String d, List<String> b, String c) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<TeamTask> getTask(String taskId) {
                return java.util.Optional.empty();
            }

            @Override
            public List<TeamTask> getTasksByTeam(String tid) {
                TeamTask ca = new TeamTask("CA", tid, "A", "d",
                        Collections.singletonList("CB"), TeamTaskStatus.CREATED, "lead", null, 1L);
                TeamTask cb = new TeamTask("CB", tid, "B", "d",
                        Collections.singletonList("CA"), TeamTaskStatus.CREATED, "lead", null, 1L);
                return Arrays.asList(ca, cb);
            }

            @Override
            public List<TeamTask> getTasksByCreator(String c) {
                return Collections.emptyList();
            }

            @Override
            public java.util.Optional<TeamTask> claimTask(String t, String b) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<TeamTask> completeTask(String t, String b) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<TeamTask> abandonTask(String t, String b) {
                throw new UnsupportedOperationException();
            }
        };

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(new RecordingAgentEngine(), cycleStore, mgr);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> orchestrator.execute(teamId));
        assertTrue(ex.getMessage().contains("cycle"),
                "cyclic blockedBy must be rejected before execution: " + ex.getMessage());
    }

    @Test
    void noBoundMemberFailsFast() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        // Create a team with an UNBOUND member.
        TeamSpec spec = new TeamSpec("no-bind-team", "d", "lead-agent",
                Collections.singletonList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD)),
                0);
        Team team = mgr.createTeam(spec);
        createTask(store, team.getTeamId(), "A", Collections.emptyList());

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(new RecordingAgentEngine(), store, mgr);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> orchestrator.execute(team.getTeamId()));
        assertTrue(ex.getMessage().contains("no-bound-member"),
                "unbound team must fail fast, not silently skip: " + ex.getMessage());
    }
}
