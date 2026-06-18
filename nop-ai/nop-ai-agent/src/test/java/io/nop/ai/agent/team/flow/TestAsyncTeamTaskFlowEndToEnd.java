package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.DefaultMemberSpawner;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpMemberSpawner;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 241 (L4-async-cross-process-orchestration, async half) end-to-end
 * tests for the complete async team-task orchestration path.
 *
 * <p>These tests drive the full pipeline from the {@code executeAsync(teamId)}
 * entry through the nop-task graph scheduler, the async member step, the
 * spawner (for spawn-on-demand correctness), the parallel branch
 * concurrency, the dependency ordering, and the final
 * {@link TeamTaskFlowResult}. They satisfy the Phase 3 end-to-end exit
 * criteria:
 * <ul>
 *   <li><b>Anti-Hollow #22</b>: full pipeline observable end to end — the
 *       diamond DAG really runs with B/C concurrency and D-after-both
 *       dependency order, observed via execution-record snapshots.</li>
 *   <li><b>Anti-Hollow #23</b>: the async member step really drives the
 *       task state machine CLAIMED→COMPLETED through the live
 *       {@link io.nop.ai.agent.team.ITeamTaskStore}; the spawner (when
 *       invoked) really executes the spawned agent.</li>
 *   <li><b>No Silent No-Op #24</b>: every failure mode surfaces honestly
 *       (success=false result, never silent success, never silent skip).</li>
 *   <li><b>Spawn-on-demand correctness</b>: a graph with spawn nodes still
 *       completes correctly through {@code executeAsync} (design 裁定 3a —
 *       spawn nodes stay synchronous; correctness unchanged, only
 *       concurrency limited).</li>
 *   <li><b>Semantic equivalence</b>: {@code execute} and
 *       {@code executeAsync().join()} produce equivalent results on the
 *       same DAG.</li>
 * </ul>
 */
public class TestAsyncTeamTaskFlowEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Recording engine with concurrency + interval tracking, plus optional
     * fail-on-task. Used for the success / failure / spawn-on-demand E2E
     * scenarios.
     */
    static final class E2EAgentEngine implements IAgentEngine {
        final Map<String, Long> enterNanoTime = new ConcurrentHashMap<>();
        final Map<String, Long> exitNanoTime = new ConcurrentHashMap<>();
        final Map<String, Integer> startOrder = new ConcurrentHashMap<>();
        final Map<String, Integer> completionOrder = new ConcurrentHashMap<>();
        final AtomicInteger startSeq = new AtomicInteger();
        final AtomicInteger completionSeq = new AtomicInteger();
        final AtomicInteger peakConcurrent = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final List<AgentMessageRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());
        final String failOnTaskId;

        E2EAgentEngine() {
            this(null);
        }

        E2EAgentEngine(String failOnTaskId) {
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
            int nowActive = active.incrementAndGet();
            peakConcurrent.accumulateAndGet(nowActive, Math::max);
            enterNanoTime.put(taskId, System.nanoTime());
            startOrder.put(taskId, startSeq.incrementAndGet());

            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NopAiAgentException("interrupted", e);
                }
                active.decrementAndGet();
                exitNanoTime.put(taskId, System.nanoTime());
                completionOrder.put(taskId, completionSeq.incrementAndGet());

                if (failOnTaskId != null && failOnTaskId.equals(taskId)) {
                    return new AgentExecutionResult(AgentExecStatus.failed, null,
                            Collections.emptyList(), 0, 0L, 0L, "e2e-failed:" + taskId);
                }
                return new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                        Collections.emptyList(), 1, 10L, 1L, null);
            });
        }
    }

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                  String memberName, String sessionId) {
        TeamSpec spec = new TeamSpec("e2e-bound-team", "test", "lead-agent",
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
        TeamSpec spec = new TeamSpec("e2e-spawn-team", "test", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberAgentModel, MemberRole.MEMBER)),
                0);
        return mgr.createTeam(spec);
        // No bindMemberSession: every node hits the spawn path.
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId,
                                     String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session").getTaskId();
    }

    // ========================================================================
    // 1. End-to-end success: A→{B,C}→D diamond with bound members — all
    //    COMPLETED, B/C truly concurrent, D after both.
    // ========================================================================

    @Test
    void endToEndDiamondAsyncAllCompletedRealConcurrency() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        E2EAgentEngine engine = new E2EAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        CompletableFuture<TeamTaskFlowResult> future = orchestrator.executeAsync(teamId);

        // Calling thread is not blocked — the future is initially incomplete.
        assertFalse(future.isDone(),
                "executeAsync must not block the caller on the entire DAG");

        TeamTaskFlowResult result = future.get(15, TimeUnit.SECONDS);

        // Full pipeline success.
        assertTrue(result.isSuccess(),
                "end-to-end diamond should complete successfully: " + result);
        assertEquals(4, result.getCompletedTaskIds().size());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(d).orElseThrow().getStatus());

        // Anti-Hollow #22 — REAL CONCURRENCY between B and C.
        assertTrue(engine.peakConcurrent.get() >= 2,
                "B and C truly ran concurrently (peakConcurrent="
                        + engine.peakConcurrent.get() + ")");
        long bEnter = engine.enterNanoTime.get(b);
        long bExit = engine.exitNanoTime.get(b);
        long cEnter = engine.enterNanoTime.get(c);
        long cExit = engine.exitNanoTime.get(c);
        assertTrue(bEnter < cExit && cEnter < bExit,
                "B and C execution intervals overlap (real concurrency)");

        // Anti-Hollow #23 — DEPENDENCY ORDER: D after both B and C.
        int completionB = result.getCompletionOrder().get(b);
        int completionC = result.getCompletionOrder().get(c);
        int startD = result.getStartOrder().get(d);
        assertTrue(startD > completionB && startD > completionC,
                "D starts strictly after B and C complete");
    }

    // ========================================================================
    // 2. End-to-end honest failure: B fails → D skipped + B left CLAIMED.
    //    GraphTaskStep short-circuits and cancels successors.
    // ========================================================================

    @Test
    void endToEndBFailurePropagatesDSkippedBLeavesClaimed() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        E2EAgentEngine engine = new E2EAgentEngine(b); // B fails (non-completed status)
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(15, TimeUnit.SECONDS);

        // No Silent No-Op #24 — honest failure.
        assertFalse(result.isSuccess(),
                "B failure must honestly fail the DAG: " + result);
        assertEquals(b, result.getFailedTaskId());

        // A completed before B failed.
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());

        // B was claimed but failed — left CLAIMED (not abandoned).
        assertNotEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus(),
                "B did not complete");
        assertNotEquals(TeamTaskStatus.ABANDONED, store.getTask(b).orElseThrow().getStatus(),
                "B NOT abandoned — orchestrator's failure model leaves it CLAIMED");
        // B was actually claimed by the orchestrator session.
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(b).orElseThrow().getStatus());

        // D was skipped (GraphTaskStep short-circuits on B failure → D never starts).
        assertTrue(result.getSkippedTaskIds().contains(d),
                "D skipped: " + result.getSkippedTaskIds());

        // C — depending on scheduler timing, C may or may not have started
        // (B and C are independent branches). At minimum, C is not COMPLETED
        // via D's path (D doesn't run).
        // We don't assert C's final state because it's timing-dependent.
    }

    // ========================================================================
    // 3. Spawn-on-demand correctness: a graph with spawn nodes still
    //    completes correctly through executeAsync (design 裁定 3a).
    //    Spawn nodes stay synchronous; the graph finishes correctly.
    // ========================================================================

    @Test
    void endToEndSpawnOnDemandGraphCompletesViaExecuteAsync() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        E2EAgentEngine engine = new E2EAgentEngine();
        // Team with declared member but NO bound session — every node spawns.
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));

        IMemberSpawner spawner = new DefaultMemberSpawner(engine);
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);
        TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(15, TimeUnit.SECONDS);

        // Correctness unchanged: spawn nodes stay synchronous (裁定 3a), the
        // graph still completes correctly via executeAsync.
        assertTrue(result.isSuccess(),
                "spawn-on-demand graph completes via executeAsync: " + result);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(2, engine.capturedRequests.size(),
                "DefaultMemberSpawner invoked the engine for both spawn nodes");
    }

    @Test
    void endToEndSpawnOnDemandNoOpSpawnerHonestFailure() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        E2EAgentEngine engine = new E2EAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        // NoOp spawner — honestly declines. The graph fails honestly.
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, NoOpMemberSpawner.noOp());
        TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(15, TimeUnit.SECONDS);

        assertFalse(result.isSuccess(),
                "NoOp spawner + unbound-member graph must honestly fail (not silent success): " + result);
        assertEquals(a, result.getFailedTaskId());
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                "failed spawn task left CLAIMED (not abandoned)");
        assertEquals(0, engine.capturedRequests.size(),
                "engine NOT invoked — NoOp spawner declined before any execution");
    }

    // ========================================================================
    // 4. Sync vs async semantic equivalence (design 裁定 2).
    // ========================================================================

    @Test
    void executeEqualsExecuteAsyncJoinSemanticEquivalent() {
        // Two fresh identical DAGs, one run via execute, one via executeAsync().join()
        InMemoryTeamManager mgr1 = new InMemoryTeamManager();
        InMemoryTeamTaskStore store1 = new InMemoryTeamTaskStore();
        Team team1 = createTeamWithBoundMember(mgr1, "worker", "worker-session");
        String teamId1 = team1.getTeamId();
        String a1 = createTask(store1, teamId1, "A", Collections.emptyList());
        String b1 = createTask(store1, teamId1, "B", Collections.singletonList(a1));

        TeamTaskFlowOrchestrator asyncOrchestrator =
                new TeamTaskFlowOrchestrator(new E2EAgentEngine(), store1, mgr1);
        TeamTaskFlowResult asyncResult = asyncOrchestrator.executeAsync(teamId1).join();

        InMemoryTeamManager mgr2 = new InMemoryTeamManager();
        InMemoryTeamTaskStore store2 = new InMemoryTeamTaskStore();
        Team team2 = createTeamWithBoundMember(mgr2, "worker", "worker-session");
        String teamId2 = team2.getTeamId();
        String a2 = createTask(store2, teamId2, "A", Collections.emptyList());
        String b2 = createTask(store2, teamId2, "B", Collections.singletonList(a2));

        TeamTaskFlowOrchestrator syncOrchestrator =
                new TeamTaskFlowOrchestrator(new E2EAgentEngine(), store2, mgr2);
        TeamTaskFlowResult syncResult = syncOrchestrator.execute(teamId2);

        // Both runs produce the same shape: success, both tasks COMPLETED.
        assertTrue(asyncResult.isSuccess(),
                "executeAsync().join() succeeds: " + asyncResult);
        assertTrue(syncResult.isSuccess(),
                "execute() succeeds: " + syncResult);
        assertEquals(2, asyncResult.getCompletedTaskIds().size());
        assertEquals(2, syncResult.getCompletedTaskIds().size());
        assertEquals(TeamTaskStatus.COMPLETED, store1.getTask(a1).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store2.getTask(a2).orElseThrow().getStatus());
    }
}
