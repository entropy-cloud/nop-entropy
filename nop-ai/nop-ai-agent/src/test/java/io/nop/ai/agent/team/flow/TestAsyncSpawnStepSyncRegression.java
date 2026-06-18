package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.model.AgentExecStatus;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 243 (L4-spawn-step-async) focused tests for sync zero-regression and
 * sync/async semantic equivalence on spawn-node graphs.
 *
 * <p>Verifies that the {@link TeamTaskFlowOrchestrator#execute(String)} sync
 * entry point (which delegates to {@code executeAsync(teamId).join()}) behaves
 * consistently on spawn-node graphs (linear / diamond / failure propagation),
 * and that {@code execute(...)} and {@code executeAsync(...).join()} are
 * semantically equivalent on the same spawn DAG.
 *
 * <p>Coverage map (maps to Phase 2 Exit Criteria):
 * <ul>
 *   <li>{@link #syncLinearSpawnGraphCompletes} — sync entry on a linear
 *       A→B→C spawn DAG: all COMPLETED.</li>
 *   <li>{@link #syncDiamondSpawnGraphCompletes} — sync entry on a diamond
 *       A→{B,C}→D spawn DAG: all COMPLETED.</li>
 *   <li>{@link #syncFailurePropagationLeavesClaimed} — sync entry on a spawn
 *       DAG with a failing spawn node: honest failed result, task CLAIMED.</li>
 *   <li>{@link #executeEqualsExecuteAsyncJoinSemanticEquivalent} — on the
 *       same spawn DAG, {@code execute(...)} and
 *       {@code executeAsync(...).join()} produce equivalent results.</li>
 * </ul>
 */
public class TestAsyncSpawnStepSyncRegression {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Counting functional spawner that wraps a delegate and records each
     * invocation's task id (wiring verification #23).
     */
    static final class CountingSpawner implements IMemberSpawner {
        final AtomicInteger invocations = new AtomicInteger(0);
        final List<String> spawnedTaskIds = Collections.synchronizedList(new java.util.ArrayList<>());
        final IMemberSpawner delegate;

        CountingSpawner(IMemberSpawner delegate) {
            this.delegate = delegate;
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            invocations.incrementAndGet();
            spawnedTaskIds.add(request.getTask().getTaskId());
            return delegate.spawnMember(request);
        }
    }

    /**
     * Functional spawner that returns a completed DISPATCHED result for every
     * task (no real engine needed).
     */
    static final class CompletedDispatchSpawner implements IMemberSpawner {
        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            String taskId = request.getTask().getTaskId();
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null),
                    "worker-agent-model", "spawned-" + taskId);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team createTeamWithoutBoundMember(InMemoryTeamManager mgr, String teamName) {
        TeamSpec spec = new TeamSpec(teamName, "test", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent-model", MemberRole.MEMBER)),
                0);
        return mgr.createTeam(spec);
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId,
                                      String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session").getTaskId();
    }

    // ========================================================================
    // 1. Sync entry on linear A→B→C spawn DAG: all COMPLETED.
    // ========================================================================

    @Test
    void syncLinearSpawnGraphCompletes() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        CountingSpawner spawner = new CountingSpawner(new CompletedDispatchSpawner());
        Team team = createTeamWithoutBoundMember(mgr, "sync-linear-team");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(b));

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.execute(teamId);

            assertTrue(result.isSuccess(),
                    "sync linear spawn DAG completes: " + result);
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus());
            assertEquals(3, spawner.invocations.get(),
                    "spawner consulted once per spawn node (wiring #23)");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 2. Sync entry on diamond A→{B,C}→D spawn DAG: all COMPLETED.
    // ========================================================================

    @Test
    void syncDiamondSpawnGraphCompletes() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        CountingSpawner spawner = new CountingSpawner(new CompletedDispatchSpawner());
        Team team = createTeamWithoutBoundMember(mgr, "sync-diamond-team");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.execute(teamId);

            assertTrue(result.isSuccess(),
                    "sync diamond spawn DAG completes: " + result);
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus());
            }
            assertEquals(4, spawner.invocations.get(),
                    "spawner consulted once per spawn node (wiring #23)");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 3. Sync entry on a spawn DAG with a failing spawn node: honest fail.
    // ========================================================================

    @Test
    void syncFailurePropagationLeavesClaimed() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithoutBoundMember(mgr, "sync-fail-team");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, NoOpMemberSpawner.noOp());
        try {
            TeamTaskFlowResult result = orchestrator.execute(teamId);

            assertFalse(result.isSuccess(),
                    "sync NoOp spawn must honestly fail (not silent success): " + result);
            assertEquals(a, result.getFailedTaskId());
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                    "failed task left CLAIMED (not abandoned)");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 4. execute(...) ≡ executeAsync(...).join() semantic equivalence on a
    //    spawn DAG.
    // ========================================================================

    @Test
    void executeEqualsExecuteAsyncJoinSemanticEquivalent() throws Exception {
        // Two identical setups (two stores/teams) — one driven via execute(),
        // one via executeAsync().join() — then compare the results.
        // Setup A: execute().
        InMemoryTeamManager mgrA = new InMemoryTeamManager();
        InMemoryTeamTaskStore storeA = new InMemoryTeamTaskStore();
        Team teamA = createTeamWithoutBoundMember(mgrA, "equiv-team-a");
        String tidA = teamA.getTeamId();
        String aA = createTask(storeA, tidA, "A", Collections.emptyList());
        String bA = createTask(storeA, tidA, "B", Collections.singletonList(aA));
        String cA = createTask(storeA, tidA, "C", Collections.singletonList(aA));
        String dA = createTask(storeA, tidA, "D", Arrays.asList(bA, cA));
        TeamTaskFlowOrchestrator orchA =
                new TeamTaskFlowOrchestrator(null, storeA, mgrA, null, new CompletedDispatchSpawner());

        // Setup B: executeAsync().join().
        InMemoryTeamManager mgrB = new InMemoryTeamManager();
        InMemoryTeamTaskStore storeB = new InMemoryTeamTaskStore();
        Team teamB = createTeamWithoutBoundMember(mgrB, "equiv-team-b");
        String tidB = teamB.getTeamId();
        String aB = createTask(storeB, tidB, "A", Collections.emptyList());
        String bB = createTask(storeB, tidB, "B", Collections.singletonList(aB));
        String cB = createTask(storeB, tidB, "C", Collections.singletonList(aB));
        String dB = createTask(storeB, tidB, "D", Arrays.asList(bB, cB));
        TeamTaskFlowOrchestrator orchB =
                new TeamTaskFlowOrchestrator(null, storeB, mgrB, null, new CompletedDispatchSpawner());

        try {
            TeamTaskFlowResult syncResult = orchA.execute(tidA);
            CompletableFuture<TeamTaskFlowResult> asyncFuture = orchB.executeAsync(tidB);
            TeamTaskFlowResult asyncResult = asyncFuture.get(20, TimeUnit.SECONDS);

            // Both succeed.
            assertTrue(syncResult.isSuccess(), "sync result success: " + syncResult);
            assertTrue(asyncResult.isSuccess(), "async result success: " + asyncResult);
            // Both complete all four tasks.
            assertEquals(4, syncResult.getCompletedTaskIds().size(), "sync completed 4");
            assertEquals(4, asyncResult.getCompletedTaskIds().size(), "async completed 4");
            // Final task states are all COMPLETED in both.
            for (String id : Arrays.asList(aA, bA, cA, dA)) {
                assertEquals(TeamTaskStatus.COMPLETED, storeA.getTask(id).orElseThrow().getStatus(),
                        "sync task " + id + " COMPLETED");
            }
            for (String id : Arrays.asList(aB, bB, cB, dB)) {
                assertEquals(TeamTaskStatus.COMPLETED, storeB.getTask(id).orElseThrow().getStatus(),
                        "async task " + id + " COMPLETED");
            }
        } finally {
            orchA.close();
            orchB.close();
        }
    }
}
