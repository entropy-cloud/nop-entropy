package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 243 (L4-spawn-step-async) focused tests for tenant-context
 * propagation across the supplyAsync worker boundary in the async spawn
 * step.
 *
 * <p><b>Adjudication test (plan 243 design 裁定 2)</b>: standard
 * {@link ThreadLocal} does not cross the {@code supplyAsync} boundary, so
 * the orchestrator captures the caller's tenant once and injects it into
 * each {@link SpawnMemberAgentTaskStep} (explicit-propagation mechanism),
 * which re-applies it inside the supplyAsync worker. This is verified for
 * BOTH topologies:
 * <ul>
 *   <li>{@link #enterSpawnNodeWorkerObservesCallerTenant} — a single ENTER
 *       spawn node: the worker observes the caller's tenant.</li>
 *   <li>{@link #nonEnterSpawnNodesWorkerObserveCallerTenant} — the CRITICAL
 *       case: diamond A→{B,C}→D where B and C are NON-ENTER spawn nodes
 *       (their {@code execute()} runs on a predecessor-completion thread).
 *       The workers for B and C must STILL observe the caller's tenant
 *       (non-null), proving explicit-propagation is robust where thread-based
 *       capture would fail.</li>
 *   <li>{@link #completeTaskObservesCallerTenant} — the store's
 *       {@code completeTask} (which DB stores filter by tenant on) observes
 *       the caller's tenant on the worker thread.</li>
 *   <li>{@link #noTenantLeakAcrossSequentialRuns} — after a run with tenant
 *       T1, a subsequent run on the same (single-thread) pool with tenant T2
 *       observes T2 (not stale T1, not null) — proves the finally-clear does
 *       not leak tenant context to pooled worker threads.</li>
 *   <li>{@link #multiTenantIsolationCompleteTask} — two teams in two tenants
 *       each completeTask under their own tenant (cross-tenant not visible).</li>
 * </ul>
 */
public class TestAsyncSpawnStepTenantPropagation {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Spawner that records the {@link ThreadLocalTenantResolver#current()}
     * value observed on the worker thread during each {@code spawnMember}
     * call. Used to assert the caller's tenant propagated across the
     * supplyAsync boundary. Returns a completed DISPATCHED result so the
     * node completes normally.
     */
    static final class TenantRecordingSpawner implements IMemberSpawner {
        final Map<String, String> tenantObservedAtSpawn = new ConcurrentHashMap<>();

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            String taskId = request.getTask().getTaskId();
            tenantObservedAtSpawn.put(taskId, ThreadLocalTenantResolver.current());
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null),
                    "worker-agent-model", "spawned-" + taskId);
        }
    }

    /**
     * Store wrapper that records the {@link ThreadLocalTenantResolver#current()}
     * value observed on the worker thread during each {@code completeTask}
     * call (DB stores filter by this tenant).
     */
    static final class TenantRecordingStore implements ITeamTaskStore {
        private final InMemoryTeamTaskStore delegate;
        final Map<String, String> tenantObservedAtComplete = new ConcurrentHashMap<>();

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
        public Optional<TeamTask> completeTask(String t, String b) {
            tenantObservedAtComplete.put(t, ThreadLocalTenantResolver.current());
            return delegate.completeTask(t, b);
        }

        @Override
        public Optional<TeamTask> abandonTask(String t, String b) {
            return delegate.abandonTask(t, b);
        }

        @Override
        public Optional<TeamTask> reclaimTask(String t, String b) {
            return delegate.reclaimTask(t, b);
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

    private static String createTask(ITeamTaskStore store, String teamId,
                                      String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session").getTaskId();
    }

    /**
     * Run the orchestrator inside a caller-tenant scope, restoring the
     * thread-local afterwards so tests do not leak tenant into each other.
     */
    private static TeamTaskFlowResult runWithTenant(String tenantId, TeamTaskFlowOrchestrator orch,
                                                     String teamId) throws Exception {
        ThreadLocalTenantResolver.set(tenantId);
        try {
            return orch.executeAsync(teamId).get(20, TimeUnit.SECONDS);
        } finally {
            ThreadLocalTenantResolver.clear();
        }
    }

    // ========================================================================
    // 1. ENTER spawn node: worker observes the caller's tenant.
    // ========================================================================

    @Test
    void enterSpawnNodeWorkerObservesCallerTenant() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TenantRecordingSpawner spawner = new TenantRecordingSpawner();
        Team team = createTeamWithoutBoundMember(mgr, "enter-tenant-team");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = runWithTenant("tenant-enter", orchestrator, teamId);

            assertTrue(result.isSuccess(), "enter spawn node completes: " + result);
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
            // The worker thread observed the caller's tenant (not null).
            assertEquals("tenant-enter", spawner.tenantObservedAtSpawn.get(a),
                    "enter spawn node worker MUST observe the caller's tenant "
                            + "(explicit-propagation across supplyAsync boundary)");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 2. CRITICAL: NON-ENTER spawn nodes (diamond A→{B,C}→D, B and C are
    //    non-enter) still observe the caller's tenant. This is the case where
    //    thread-based capture would fail (B/C execute() runs on a predecessor-
    //    completion thread whose tenant was cleared); explicit-propagation is
    //    robust to it.
    // ========================================================================

    @Test
    void nonEnterSpawnNodesWorkerObserveCallerTenant() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TenantRecordingSpawner spawner = new TenantRecordingSpawner();
        Team team = createTeamWithoutBoundMember(mgr, "nonenter-tenant-team");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = runWithTenant("tenant-nonenter", orchestrator, teamId);

            assertTrue(result.isSuccess(), "diamond spawn DAG completes: " + result);
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus());
            }

            // CRITICAL assertion: the NON-ENTER spawn nodes (B and C, whose
            // execute() runs on a predecessor-completion thread) still
            // observed the caller's tenant (non-null, equals caller value).
            // This proves explicit-propagation is robust where thread-based
            // capture would have read null.
            for (String id : Arrays.asList(a, b, c, d)) {
                assertNotNull(spawner.tenantObservedAtSpawn.get(id),
                        "spawn node " + id + " worker observed NULL tenant — explicit-propagation failed");
                assertEquals("tenant-nonenter", spawner.tenantObservedAtSpawn.get(id),
                        "spawn node " + id + " worker MUST observe the caller's tenant");
            }
            // B and C specifically are the non-enter nodes — assert them
            // explicitly for clarity.
            assertEquals("tenant-nonenter", spawner.tenantObservedAtSpawn.get(b),
                    "non-enter spawn node B worker observed the caller's tenant");
            assertEquals("tenant-nonenter", spawner.tenantObservedAtSpawn.get(c),
                    "non-enter spawn node C worker observed the caller's tenant");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 3. completeTask observes the caller's tenant on the worker thread.
    // ========================================================================

    @Test
    void completeTaskObservesCallerTenant() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TenantRecordingStore store = new TenantRecordingStore(new InMemoryTeamTaskStore());
        TenantRecordingSpawner spawner = new TenantRecordingSpawner();
        Team team = createTeamWithoutBoundMember(mgr, "complete-tenant-team");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = runWithTenant("tenant-complete", orchestrator, teamId);

            assertTrue(result.isSuccess(), "spawn node completes: " + result);
            // completeTask (which DB stores filter by tenant) observed the
            // caller's tenant on the worker thread.
            assertEquals("tenant-complete", store.tenantObservedAtComplete.get(a),
                    "completeTask MUST observe the caller's tenant on the worker thread "
                            + "(a DB store would filter by this tenant)");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 4. No tenant leak across sequential runs on the same pool. A single-
    //    thread injected pool forces both runs to reuse the SAME worker
    //    thread, so a missing finally-clear would leak T1 into run 2.
    // ========================================================================

    @Test
    void noTenantLeakAcrossSequentialRuns() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();

        // Single-thread pool: both runs reuse the SAME worker thread.
        ExecutorService singleThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "spawn-pool-single");
            t.setDaemon(true);
            return t;
        });
        try {
            // Run 1: tenant T1.
            Team team1 = createTeamWithoutBoundMember(mgr, "leak-team-1");
            String tid1 = team1.getTeamId();
            String a1 = createTask(store, tid1, "A1", Collections.emptyList());
            TenantRecordingSpawner spawner1 = new TenantRecordingSpawner();
            TeamTaskFlowOrchestrator orch1 =
                    new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner1);
            orch1.setSpawnStepExecutor(singleThread);
            try {
                TeamTaskFlowResult r1 = runWithTenant("T1", orch1, tid1);
                assertTrue(r1.isSuccess(), "run 1 completes: " + r1);
                assertEquals("T1", spawner1.tenantObservedAtSpawn.get(a1),
                        "run 1 worker observed T1");
            } finally {
                orch1.close();  // does NOT shut down the injected pool
            }

            // Run 2: tenant T2, on the SAME orchestrator pool (reused thread).
            Team team2 = createTeamWithoutBoundMember(mgr, "leak-team-2");
            String tid2 = team2.getTeamId();
            String a2 = createTask(store, tid2, "A2", Collections.emptyList());
            TenantRecordingSpawner spawner2 = new TenantRecordingSpawner();
            TeamTaskFlowOrchestrator orch2 =
                    new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner2);
            orch2.setSpawnStepExecutor(singleThread);
            try {
                TeamTaskFlowResult r2 = runWithTenant("T2", orch2, tid2);
                assertTrue(r2.isSuccess(), "run 2 completes: " + r2);
                assertEquals("T2", spawner2.tenantObservedAtSpawn.get(a2),
                        "run 2 worker MUST observe T2 (not stale T1, not null) — "
                                + "the finally-clear prevented tenant leak to the pooled thread");
            } finally {
                orch2.close();
            }
        } finally {
            singleThread.shutdownNow();
        }
    }

    // ========================================================================
    // 5. Multi-tenant isolation: two teams in two tenants each completeTask
    //    under their own tenant (cross-tenant not visible). Runs sequentially
    //    on distinct orchestrators with the same caller thread setting
    //    different tenants.
    // ========================================================================

    @Test
    void multiTenantIsolationCompleteTask() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TenantRecordingStore store = new TenantRecordingStore(new InMemoryTeamTaskStore());

        // Tenant A.
        Team teamA = createTeamWithoutBoundMember(mgr, "iso-team-a");
        String tidA = teamA.getTeamId();
        String taskA = createTask(store, tidA, "A", Collections.emptyList());
        TenantRecordingSpawner spawnerA = new TenantRecordingSpawner();
        TeamTaskFlowOrchestrator orchA =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawnerA);
        try {
            TeamTaskFlowResult rA = runWithTenant("tenant-A", orchA, tidA);
            assertTrue(rA.isSuccess(), "tenant-A run completes: " + rA);
        } finally {
            orchA.close();
        }

        // Tenant B.
        Team teamB = createTeamWithoutBoundMember(mgr, "iso-team-b");
        String tidB = teamB.getTeamId();
        String taskB = createTask(store, tidB, "B", Collections.emptyList());
        TenantRecordingSpawner spawnerB = new TenantRecordingSpawner();
        TeamTaskFlowOrchestrator orchB =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawnerB);
        try {
            TeamTaskFlowResult rB = runWithTenant("tenant-B", orchB, tidB);
            assertTrue(rB.isSuccess(), "tenant-B run completes: " + rB);
        } finally {
            orchB.close();
        }

        // Each tenant's completeTask observed ITS OWN tenant.
        assertEquals("tenant-A", store.tenantObservedAtComplete.get(taskA),
                "tenant-A task completed under tenant-A");
        assertEquals("tenant-B", store.tenantObservedAtComplete.get(taskB),
                "tenant-B task completed under tenant-B");
        assertNotEquals(store.tenantObservedAtComplete.get(taskA),
                store.tenantObservedAtComplete.get(taskB),
                "cross-tenant isolation: the two tasks completed under different tenants");
    }
}
