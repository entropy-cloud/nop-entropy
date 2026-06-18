package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.DefaultMemberSpawner;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 241 (L4-async-cross-process-orchestration, async half) zero-regression
 * test for the {@link TeamTaskFlowOrchestrator#execute(String)} sync entry
 * point.
 *
 * <p>After plan 241 the sync entry delegates to {@code executeAsync(teamId).join()}.
 * This test verifies that the observable behaviour remains consistent with the
 * pre-241 plan 233/238 expectations across the major DAG shapes and failure
 * modes — i.e. the sync entry still works for a bound-member linear chain,
 * diamond DAG, spawn-on-demand with a functional spawner, and node failure
 * propagation.
 */
public class TestExecuteSyncZeroRegression {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    static final class RecordingAgentEngine implements IAgentEngine {
        final List<AgentMessageRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());
        final String failOnTaskId;

        RecordingAgentEngine() {
            this(null);
        }

        RecordingAgentEngine(String failOnTaskId) {
            this.failOnTaskId = failOnTaskId;
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            capturedRequests.add(request);
            String taskId = (String) request.getMetadata().get("teamTaskId");
            if (failOnTaskId != null && failOnTaskId.equals(taskId)) {
                return CompletableFuture.completedFuture(
                        new AgentExecutionResult(AgentExecStatus.failed, null,
                                Collections.emptyList(), 0, 0L, 0L, "sync-zero-reg:" + taskId));
            }
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null));
        }
    }

    static final class CountingSpawner implements IMemberSpawner {
        final AtomicInteger invocations = new AtomicInteger();
        final IMemberSpawner delegate;

        CountingSpawner(IMemberSpawner delegate) {
            this.delegate = delegate;
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            invocations.incrementAndGet();
            return delegate.spawnMember(request);
        }
    }

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                  String memberName, String sessionId) {
        TeamSpec spec = new TeamSpec("sync-team", "test", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberName + "-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), memberName, sessionId, "actor-" + memberName);
        return team;
    }

    private static Team createTeamWithoutBoundMember(InMemoryTeamManager mgr,
                                                     String memberName,
                                                     String memberAgentModel) {
        TeamSpec spec = new TeamSpec("sync-unbound-team", "test", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberAgentModel, MemberRole.MEMBER)),
                0);
        return mgr.createTeam(spec);
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId,
                                     String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session").getTaskId();
    }

    @Test
    void linearChainSyncExecution() {
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

        assertTrue(result.isSuccess(),
                "sync entry still works for a linear chain: " + result);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus());
        assertEquals(3, engine.capturedRequests.size());
    }

    @Test
    void diamondDagSyncExecution() {
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

        assertTrue(result.isSuccess(), "sync entry still works for a diamond DAG: " + result);
        assertEquals(4, result.getCompletedTaskIds().size());
        // Dependency order: D after B and C.
        assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(b));
        assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(c));
    }

    @Test
    void syncSpawnOnDemandFunctionalSpawner() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        CountingSpawner spawner = new CountingSpawner(new DefaultMemberSpawner(engine));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertTrue(result.isSuccess(),
                "sync entry works for spawn-on-demand: " + result);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(1, spawner.invocations.get(),
                "spawner consulted at run time for the unbound node");
    }

    @Test
    void syncNodeFailurePropagates() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(b));

        RecordingAgentEngine engine = new RecordingAgentEngine(b);
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertFalse(result.isSuccess(),
                "sync entry still propagates node failures honestly: " + result);
        assertEquals(b, result.getFailedTaskId());
        assertTrue(result.getSkippedTaskIds().contains(c),
                "C skipped after B failed: " + result.getSkippedTaskIds());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "predecessor A still completes");
    }
}
