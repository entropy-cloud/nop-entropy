package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 244 router-default and tenant-isolation scenarios: the shipped NoOp
 * default router (single-member zero regression + empty-team honest
 * failure) and tenant propagation under spawn fan-out. Split from
 * {@code TestMultiMemberFanOut} (MA4.2-06); fixtures in
 * {@link AbstractMultiMemberFanOutTest}.
 */
public class TestMultiMemberFanOutRouting extends AbstractMultiMemberFanOutTest {

    // ========================================================================
    // Single-member zero regression: NoOp shipped default produces a
    // singleton plan = existing MemberAgentTaskStep / SpawnMemberAgentTaskStep
    // path. Run the same DAG with NoOp router (default) and verify
    // semantic equivalence to the pre-244 single-member behaviour.
    // ========================================================================

    @Test
    void noOpRouterSingleMemberZeroRegression() {
        // NoOp router is the shipped default; constructing an orchestrator
        // without an explicit router must use it.
        assertEquals(NoOpTaskMemberRouter.noOp().getClass(),
                new TeamTaskFlowOrchestrator(null, new InMemoryTeamTaskStore(),
                        new InMemoryTeamManager()).getTaskMemberRouter().getClass(),
                "shipped default router is NoOpTaskMemberRouter");

        // NoOp + single bound member → singleton BOUND plan via the existing
        // MemberAgentTaskStep (zero regression).
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConfigurableFanOutEngine engine = new ConfigurableFanOutEngine();
        Team team = createTeamAndBindMembers(mgr, "noop-team",
                new String[]{"worker"}, new String[]{"worker-session"});
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        // No explicit router → shipped NoOp default.
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        TeamTaskFlowResult result = orchestrator.executeAsync(teamId).join();

        assertTrue(result.isSuccess(), "NoOp router single bound member: " + result);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        // Engine invoked exactly once (singleton plan, not fan-out).
        assertEquals(1, engine.executeCount.get(),
                "singleton plan invokes engine exactly once (zero regression to pre-244)");
    }

    @Test
    void noOpRouterEmptyTeamEmptyPlanHonestFailure() {
        // NoOp router on a team with no memberSpecs at all and no bound
        // members produces an empty plan → orchestrator honest-fails at
        // build time (same semantics shape as pre-244 NoOpMemberSpawner
        // NO_SPAWN throw, detected earlier at the router layer).
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConfigurableFanOutEngine engine = new ConfigurableFanOutEngine();
        // Team with NO memberSpecs (not even a MEMBER-role one). The lead
        // is only referenced by name in TeamSpec, not added as a memberSpec
        // — so NoOp router's bound priority finds nothing and spawn
        // fallback finds nothing.
        TeamSpec spec = new TeamSpec("empty-team", "test", "lead-agent",
                Collections.emptyList(), 0);
        Team team = mgr.createTeam(spec);
        String a = createTask(store, team.getTeamId(), "A", Collections.emptyList());

        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> orchestrator.executeAsync(team.getTeamId()),
                "NoOp router with no dispatchable member honest-fails");
        assertEquals(TeamTaskStatus.CREATED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(0, engine.executeCount.get());
    }

    // ========================================================================
    // Tenant isolation under fan-out: bound + spawn fan-out propagate
    // the caller's tenant to every member worker; no leak across runs.
    // ========================================================================

    /**
     * Spawner that records the tenant observed at spawn time per worker.
     * Used to assert each fan-out spawn worker sees the caller's tenant.
     */
    static final class TenantRecordingSpawner implements IMemberSpawner {
        final java.util.Map<String, String> tenantAtSpawn = new ConcurrentHashMap<>();

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            String member = request.getTarget() != null ? request.getTarget().getMemberName() : "?";
            tenantAtSpawn.put(request.getTask().getTaskId() + "#" + member,
                    ThreadLocalTenantResolver.current());
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok",
                            Collections.emptyList(), 1, 10L, 1L, null),
                    request.getTarget() != null ? request.getTarget().getAgentModel() : "x",
                    "spawned-" + request.getTask().getTaskId());
        }
    }

    /**
     * Store wrapper that records the tenant observed at completeTask time.
     */
    static final class TenantRecordingStore implements ITeamTaskStore {
        private final InMemoryTeamTaskStore delegate;
        final java.util.Map<String, String> tenantAtComplete = new ConcurrentHashMap<>();
        final AtomicInteger completeCount = new AtomicInteger();

        TenantRecordingStore(InMemoryTeamTaskStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public TeamTask createTask(String t, String s, String d, List<String> b, String c) {
            return delegate.createTask(t, s, d, b, c);
        }

        @Override
        public Optional<TeamTask> getTask(String taskId) {
            return delegate.getTask(taskId);
        }

        @Override
        public List<TeamTask> getTasksByTeam(String tid) {
            return delegate.getTasksByTeam(tid);
        }

        @Override
        public List<TeamTask> getTasksByCreator(String c) {
            return delegate.getTasksByCreator(c);
        }

        @Override
        public Optional<TeamTask> claimTask(String t, String b) {
            return delegate.claimTask(t, b);
        }

        @Override
        public Optional<TeamTask> completeTask(String t, String b, Long claimEpoch) {
            tenantAtComplete.put(t, ThreadLocalTenantResolver.current());
            completeCount.incrementAndGet();
            return delegate.completeTask(t, b, claimEpoch);
        }

        @Override
        public Optional<TeamTask> abandonTask(String t, String b, Long claimEpoch) {
            return delegate.abandonTask(t, b, claimEpoch);
        }

        @Override
        public Optional<TeamTask> reclaimTask(String t, String b) {
            return delegate.reclaimTask(t, b);
        }
    }

    @Test
    void spawnFanOutPropagatesTenantToAllWorkers() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TenantRecordingStore store = new TenantRecordingStore(new InMemoryTeamTaskStore());
        TenantRecordingSpawner spawner = new TenantRecordingSpawner();
        Team team = createTeamWithMembers(mgr, "tenant-fanout-team", "m1", "m2", "m3");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> spawnFanOutPlan(team, t, Arrays.asList("m1", "m2", "m3")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner, router);

        ThreadLocalTenantResolver.set("tenant-fanout");
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);
            assertTrue(result.isSuccess(), "fan-out completes: " + result);

            // Anti-Hollow — every spawn worker observed the caller's tenant.
            for (String m : Arrays.asList("m1", "m2", "m3")) {
                assertEquals("tenant-fanout", spawner.tenantAtSpawn.get(a + "#" + m),
                        "spawn worker for " + m + " observed the caller's tenant");
            }
            // completeTask observed the caller's tenant once.
            assertEquals("tenant-fanout", store.tenantAtComplete.get(a),
                    "completeTask observed the caller's tenant");
            assertEquals(1, store.completeCount.get(),
                    "completeTask invoked exactly once for the 3-member fan-out");
        } finally {
            ThreadLocalTenantResolver.clear();
            orchestrator.close();
        }
    }

    @Test
    void spawnFanOutNoTenantLeakAcrossRuns() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TenantRecordingStore store = new TenantRecordingStore(new InMemoryTeamTaskStore());

        // Single-thread pool: both runs reuse the SAME worker thread, so a
        // missing finally-clear would leak T1 into run 2.
        ExecutorService singleThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fanout-pool-single");
            t.setDaemon(true);
            return t;
        });
        try {
            // Run 1: tenant T1, 2-member spawn fan-out.
            Team team1 = createTeamWithMembers(mgr, "leak-team-1", "m1", "m2");
            String a1 = createTask(store, team1.getTeamId(), "A1", Collections.emptyList());
            TenantRecordingSpawner spawner1 = new TenantRecordingSpawner();
            ITaskMemberRouter router1 = new FixedPlanRouter(
                    t -> spawnFanOutPlan(team1, t, Arrays.asList("m1", "m2")));
            TeamTaskFlowOrchestrator orch1 =
                    new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner1, router1);
            orch1.setSpawnStepExecutor(singleThread);
            try {
                ThreadLocalTenantResolver.set("T1");
                try {
                    TeamTaskFlowResult r1 = orch1.executeAsync(team1.getTeamId()).get(30, TimeUnit.SECONDS);
                    assertTrue(r1.isSuccess(), "run 1 completes: " + r1);
                    assertEquals("T1", spawner1.tenantAtSpawn.get(a1 + "#m1"));
                    assertEquals("T1", spawner1.tenantAtSpawn.get(a1 + "#m2"));
                } finally {
                    ThreadLocalTenantResolver.clear();
                }
            } finally {
                orch1.close();
            }

            // Run 2: tenant T2, same pool (reused thread).
            Team team2 = createTeamWithMembers(mgr, "leak-team-2", "m1", "m2");
            String a2 = createTask(store, team2.getTeamId(), "A2", Collections.emptyList());
            TenantRecordingSpawner spawner2 = new TenantRecordingSpawner();
            ITaskMemberRouter router2 = new FixedPlanRouter(
                    t -> spawnFanOutPlan(team2, t, Arrays.asList("m1", "m2")));
            TeamTaskFlowOrchestrator orch2 =
                    new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner2, router2);
            orch2.setSpawnStepExecutor(singleThread);
            try {
                ThreadLocalTenantResolver.set("T2");
                try {
                    TeamTaskFlowResult r2 = orch2.executeAsync(team2.getTeamId()).get(30, TimeUnit.SECONDS);
                    assertTrue(r2.isSuccess(), "run 2 completes: " + r2);
                    // Anti-Hollow — workers observed T2 (not stale T1, not null).
                    assertEquals("T2", spawner2.tenantAtSpawn.get(a2 + "#m1"),
                            "run 2 worker observed T2 (no leak from T1)");
                    assertEquals("T2", spawner2.tenantAtSpawn.get(a2 + "#m2"),
                            "run 2 worker observed T2 (no leak from T1)");
                } finally {
                    ThreadLocalTenantResolver.clear();
                }
            } finally {
                orch2.close();
            }
        } finally {
            singleThread.shutdownNow();
        }
    }
}
