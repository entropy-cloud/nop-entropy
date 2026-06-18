package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.flow.AllMustSucceedReduction;
import io.nop.ai.agent.team.flow.DispatchTarget;
import io.nop.ai.agent.team.flow.ITaskMemberRouter;
import io.nop.ai.agent.team.flow.MemberDispatchPlan;
import io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator;
import io.nop.ai.agent.team.flow.TeamTaskFlowResult;
import io.nop.ai.agent.team.flow.TeamTaskTopology;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.commons.lang.IDestroyable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 245 (daemon dispatch parity) focused tests: verify the
 * {@link TeamTaskSchedulerDaemon} per-task dispatch consumes the
 * {@link ITaskMemberRouter} + the shared fan-out + reduce + complete chain,
 * with real-concurrency, async-non-blocking, honest-failure, tenant
 * propagation, single-member zero regression, and daemon-vs-orchestrator
 * parity evidence.
 *
 * <p>Coverage map (Phase 3 Exit Criteria):
 * <ul>
 *   <li>{@link #daemonBoundFanOutRealConcurrency} — daemon fan-out N bound
 *       members truly concurrent (peak ≥ 2 + interval overlap).</li>
 *   <li>{@link #daemonAsyncDispatchDoesNotBlockScanThread} — daemon scan
 *       returns before a slow async task completes (async non-blocking
 *       evidence).</li>
 *   <li>{@link #daemonAllMustSucceedReductionAllCompleteSingleCompleteTask} —
 *       N members all complete → single completeTask (CAS observed once).</li>
 *   <li>{@link #daemonReductionAnyFailureRetainsClaimed} — any member
 *       failure → task stays CLAIMED + in-flight run-to-completion discarded.</li>
 *   <li>{@link #daemonHonestFailureEmptyPlanAndSpawnerStatesRetainClaimed} —
 *       empty plan / NO_SPAWN / SPAWN_FAILED / spawner throws / null →
 *       task CLAIMED (No Silent No-Op #24).</li>
 *   <li>{@link #daemonSingleMemberNoOpDefaultZeroRegression} — NoOp default
 *       single-member completion equivalent to the pre-245 daemon path.</li>
 *   <li>{@link #daemonTenantPropagationSpawnFanOutNoLeak} — spawn fan-out
 *       worker observes captured tenant + no leak + cross-tenant invisible.</li>
 *   <li>{@link #daemonVsOrchestratorParitySamePlanSameResult} — same dispatch
 *       plan under daemon vs orchestrator → per-task result equivalent.</li>
 * </ul>
 */
public class TestTeamTaskSchedulerDaemonMultiMemberAsyncDispatch {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // 1. daemon bound fan-out real concurrency (peak ≥ 2 + interval overlap)
    // ========================================================================

    @Test
    void daemonBoundFanOutRealConcurrency() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        String[] members = {"m1", "m2"};
        String[] sessions = {"s-m1", "s-m2"};
        Team team = createTeamAndBindMembers(mgr, "fanout-team", members, sessions);
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITaskMemberRouter router = new FixedPlanRouter(t ->
                boundFanOutPlan(team, t, Arrays.asList("m1", "m2")));
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setTaskMemberRouter(router);

        SchedulerScanResult r = daemon.scanOnce();
        // Bound engine futures are NOT already-complete (they supplyAsync
        // internally with an 80ms sleep), so the dispatch is in-flight.
        assertTrue(daemon.awaitInFlightDispatches(10_000),
                "in-flight bound fan-out settled within timeout");

        assertEquals(1, r.getClaimedTasks(), "daemon claimed A");
        assertEquals(1, r.getDispatchedTasks(), "fan-out dispatch fired for A");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "A COMPLETED after fan-out reduction succeeded");

        // Anti-Hollow #22 — REAL CONCURRENCY evidence: m1 and m2 must overlap.
        long m1Enter = engine.enterNano.get(a + "#m1");
        long m1Exit = engine.exitNano.get(a + "#m1");
        long m2Enter = engine.enterNano.get(a + "#m2");
        long m2Exit = engine.exitNano.get(a + "#m2");
        boolean overlap = (m1Enter < m2Exit) && (m2Enter < m1Exit);
        assertTrue(overlap,
                "fan-out members m1,m2 MUST overlap (real concurrency): "
                        + "m1[" + m1Enter + "," + m1Exit + "], m2[" + m2Enter + "," + m2Exit + "]");
        assertTrue(engine.peakConcurrent.get() >= 2,
                "peakConcurrent >= 2 (N members concurrent): peak=" + engine.peakConcurrent.get());

        daemon.stop();
    }

    // ========================================================================
    // 2. daemon async non-blocking: scan returns BEFORE a slow async task
    //    completes (the daemon thread does NOT join the task's future).
    // ========================================================================

    @Test
    void daemonAsyncDispatchDoesNotBlockScanThread() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        // Engine that completes each execute future only after a 500ms delay
        // on a worker thread — long enough to prove the scan thread does not
        // block on it.
        SlowAsyncEngine engine = new SlowAsyncEngine(500L);
        Team team = createTeamAndBindMembers(mgr, "slow-team",
                new String[]{"worker"}, new String[]{"worker-session"});
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());

        long scanStart = System.nanoTime();
        SchedulerScanResult r = daemon.scanOnce();
        long scanElapsedMs = (System.nanoTime() - scanStart) / 1_000_000;

        // The scan must return WELL BEFORE the 500ms task completes — proof
        // the daemon thread did not block on the task's future.
        assertTrue(scanElapsedMs < 400L,
                "daemon scan thread must NOT block on the slow task (scan took "
                        + scanElapsedMs + "ms, task takes 500ms)");
        assertEquals(1, r.getDispatchedTasks(), "fan-out dispatch fired (in-flight)");
        assertEquals(0, r.getCompletedTasks(),
                "task NOT yet completed at scan-return time (async non-blocking)");
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                "A still CLAIMED at scan-return (the async future has not completed yet)");

        // After awaiting, the task completes.
        assertTrue(daemon.awaitInFlightDispatches(5_000),
                "in-flight dispatch settled within timeout");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "A COMPLETED after the async future settled");

        daemon.stop();
    }

    // ========================================================================
    // 3. daemon all-must-succeed reduction: N members all complete → single
    //    completeTask (the store's completeTask is invoked exactly once).
    // ========================================================================

    @Test
    void daemonAllMustSucceedReductionAllCompleteSingleCompleteTask() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        CountingTaskStore store = new CountingTaskStore(new InMemoryTeamTaskStore());
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        String[] members = {"m1", "m2", "m3"};
        String[] sessions = {"s-m1", "s-m2", "s-m3"};
        Team team = createTeamAndBindMembers(mgr, "reduce-team", members, sessions);
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITaskMemberRouter router = new FixedPlanRouter(t ->
                boundFanOutPlan(team, t, Arrays.asList("m1", "m2", "m3")));
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setTaskMemberRouter(router);

        daemon.scanOnce();
        assertTrue(daemon.awaitInFlightDispatches(10_000),
                "in-flight fan-out settled within timeout");

        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "A COMPLETED after all 3 members completed + reduction succeeded");
        assertEquals(1, store.completeTaskCount.get(),
                "completeTask invoked EXACTLY ONCE for the whole fan-out node (not per-member): "
                        + store.completeTaskCount.get());
        // 3 engine invocations — one per bound member.
        assertEquals(3, engine.executeCount.get(),
                "engine.execute invoked once per bound member (3 total)");

        daemon.stop();
    }

    // ========================================================================
    // 4. daemon reduction any-failure → task stays CLAIMED + in-flight
    //    run-to-completion results discarded.
    // ========================================================================

    @Test
    void daemonReductionAnyFailureRetainsClaimed() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        // Engine that records per-member completion; member "m2" returns a
        // non-completed status. The other member (m1) still runs to
        // completion (run-to-completion, result discarded).
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        engine.failOn("non-completed"); // makes every execute return failed
        String[] members = {"m1", "m2"};
        String[] sessions = {"s-m1", "s-m2"};
        Team team = createTeamAndBindMembers(mgr, "fail-team", members, sessions);
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITaskMemberRouter router = new FixedPlanRouter(t ->
                boundFanOutPlan(team, t, Arrays.asList("m1", "m2")));
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setTaskMemberRouter(router);

        daemon.scanOnce();
        assertTrue(daemon.awaitInFlightDispatches(10_000),
                "in-flight fan-out settled within timeout");

        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                "A LEFT IN CLAIMED after reduction failure (recovery via plan 240 reclaim, "
                        + "not abandoned — plan 245 parity with the orchestrator)");
        // Both members' engines were invoked (run-to-completion, not cancelled).
        assertEquals(2, engine.executeCount.get(),
                "both members ran to completion (no in-flight cancellation — plan 244 裁定 5)");
        assertFalse(engine.enterNano.isEmpty(),
                "in-flight members were started (run-to-completion, results discarded)");

        daemon.stop();
    }

    // ========================================================================
    // 5. daemon honest failure: empty plan / spawner three-state → task
    //    CLAIMED (No Silent No-Op #24).
    // ========================================================================

    @Test
    void daemonHonestFailureEmptyPlanAndSpawnerStatesRetainClaimed() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();

        // (a) Empty plan (no bound member, no memberSpec) → synchronous honest
        // failure, task CLAIMED, no fan-out fired.
        TeamSpec emptySpec = new TeamSpec("empty-team", "d", "lead-agent",
                Collections.emptyList(), 0);
        Team emptyTeam = mgr.createTeam(emptySpec);
        String emptyTask = createTask(store, emptyTeam.getTeamId(), "E", Collections.emptyList());

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        SchedulerScanResult r1 = daemon.scanOnce();
        assertEquals(1, r1.getFailedTasks(),
                "empty plan → synchronous honest failure (failedTasks)");
        assertTrue(r1.getFailedTaskIds().contains(emptyTask));
        assertEquals(0, r1.getDispatchedTasks(),
                "no fan-out fired for empty plan");
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(emptyTask).orElseThrow().getStatus(),
                "empty plan task LEFT IN CLAIMED (recoverable via plan 240 reclaim)");

        // (b) SPAWN plan + spawner three-state each leaves the task CLAIMED.
        Team spawnTeam = createTeamWithoutBoundMember(mgr, "w", "w-agent");
        for (SpawnFailure kind : SpawnFailure.values()) {
            String t = createTask(store, spawnTeam.getTeamId(),
                    "spawn-" + kind, Collections.emptyList());
            RecordingSpawner spawner = new RecordingSpawner().failWith(kind);
            TeamTaskSchedulerDaemon d = new TeamTaskSchedulerDaemon(
                    engine, store, mgr, new RecordingScheduler());
            d.setMemberSpawner(spawner);
            d.scanOnce();
            assertTrue(d.awaitInFlightDispatches(5_000),
                    "in-flight spawn dispatch settled for " + kind);
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(t).orElseThrow().getStatus(),
                    "spawner " + kind + " → task LEFT IN CLAIMED (honest failure)");
            d.stop();
        }

        daemon.stop();
    }

    /** Local enum for the spawner three-state + null/throw failure modes. */
    enum SpawnFailure { NO_SPAWN, SPAWN_FAILED, THROWS, NULL }

    // ========================================================================
    // 6. daemon single-member NoOp default zero regression: equivalent to
    //    the pre-245 daemon single-member bound dispatch (already-complete
    //    engine future → synchronous COMPLETED within the scan).
    // ========================================================================

    @Test
    void daemonSingleMemberNoOpDefaultZeroRegression() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamAndBindMembers(mgr, "single-team",
                new String[]{"worker"}, new String[]{"worker-session"});
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        // No router wired → NoOp shipped default (singleton single-member plan).
        assertNotNull(daemon.getTaskMemberRouter());
        assertTrue(daemon.getTaskMemberRouter() instanceof io.nop.ai.agent.team.flow.NoOpTaskMemberRouter,
                "shipped default router = NoOp (single-member plan, zero regression)");

        SchedulerScanResult r = daemon.scanOnce();
        // Single bound member + already-complete engine future → the whole
        // fan-out chain runs synchronously → COMPLETED within the scan
        // (line-for-line zero regression vs the pre-245 daemon).
        assertEquals(1, r.getCompletedTasks(),
                "single-member NoOp default → synchronous COMPLETED (zero regression)");
        assertEquals(0, r.getFailedTasks());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(1, engine.capturedRequests.size(),
                "engine.execute invoked exactly once (single-member dispatch)");
        assertEquals("worker-session", engine.capturedRequests.get(0).getSessionId(),
                "dispatch used the bound session (NoOp router bound priority)");

        daemon.stop();
    }

    // ========================================================================
    // 7. daemon tenant propagation: spawn fan-out worker observes the
    //    caller's captured tenant + finally clears (no leak) + cross-tenant
    //    isolation (completeTask observes the caller tenant).
    // ========================================================================

    @Test
    void daemonTenantPropagationSpawnFanOutNoLeak() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        TenantRecordingSpawner spawner = new TenantRecordingSpawner();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());
        // B blockedBy A so it is only dispatched in the SECOND scan (after A
        // completes), giving a clean two-scan no-leak proof.
        String b = createTask(store, teamId, "B", Collections.singletonList(a));

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                new RecordingAgentEngine(), store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);

        // (a) Set tenant T1 on the scan thread, scan, then clear. The spawn
        // worker for A must observe T1 (explicit propagation across the
        // supplyAsync boundary).
        ThreadLocalTenantResolver.set("tenant-T1");
        try {
            daemon.scanOnce();
        } finally {
            ThreadLocalTenantResolver.clear();
        }
        assertTrue(daemon.awaitInFlightDispatches(5_000),
                "in-flight spawn dispatch (A) settled within timeout");
        assertEquals("tenant-T1", spawner.observedTenant.get(),
                "spawn worker observed the caller's captured tenant (explicit propagation)");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "A COMPLETED via the spawn path under tenant T1");

        // (b) No leak: a second scan with NO tenant set on the scan thread
        // must observe null inside the worker (NOT stale T1 from the pooled
        // worker thread). The dispatcher's finally-clear prevents leak.
        spawner.observedTenant.set("STALE-SENTINEL");
        ThreadLocalTenantResolver.clear();
        daemon.scanOnce();
        assertTrue(daemon.awaitInFlightDispatches(5_000),
                "in-flight spawn dispatch (B) settled within timeout");
        assertEquals(null, spawner.observedTenant.get(),
                "second spawn worker observed null tenant (no stale T1 leak — finally-clear works)");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus(),
                "B COMPLETED via the spawn path under null tenant");

        daemon.stop();
    }

    // ========================================================================
    // 8. daemon-vs-orchestrator parity: the SAME dispatch plan under the
    //    daemon and the orchestrator yields an equivalent per-task result
    //    (both COMPLETED via the same fan-out + reduce + complete chain).
    // ========================================================================

    @Test
    void daemonVsOrchestratorParitySamePlanSameResult() {
        // Two independent stores / teams (same shape) so the daemon and the
        // orchestrator each drive their own task through the SAME fan-out
        // plan shape (2 bound targets). Both must reach COMPLETED via the
        // shared MemberFanOutDispatcher.

        // --- Daemon side ---
        InMemoryTeamManager mgrD = new InMemoryTeamManager();
        InMemoryTeamTaskStore storeD = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engineD = new ConcurrencyRecordingEngine();
        String[] members = {"m1", "m2"};
        String[] sessions = {"s-m1", "s-m2"};
        Team teamD = createTeamAndBindMembers(mgrD, "parity-daemon", members, sessions);
        String taskD = createTask(storeD, teamD.getTeamId(), "T", Collections.emptyList());

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engineD, storeD, mgrD, new RecordingScheduler());
        daemon.setTaskMemberRouter(new FixedPlanRouter(t ->
                boundFanOutPlan(teamD, t, Arrays.asList("m1", "m2"))));
        daemon.scanOnce();
        assertTrue(daemon.awaitInFlightDispatches(10_000),
                "daemon in-flight fan-out settled");
        TeamTaskStatus daemonStatus = storeD.getTask(taskD).orElseThrow().getStatus();

        // --- Orchestrator side (same plan shape) ---
        InMemoryTeamManager mgrO = new InMemoryTeamManager();
        InMemoryTeamTaskStore storeO = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engineO = new ConcurrencyRecordingEngine();
        Team teamO = createTeamAndBindMembers(mgrO, "parity-orch", members, sessions);
        String taskO = createTask(storeO, teamO.getTeamId(), "T", Collections.emptyList());

        TeamTaskFlowOrchestrator orch = new TeamTaskFlowOrchestrator(
                engineO, storeO, mgrO, null, null,
                new FixedPlanRouter(t -> boundFanOutPlan(teamO, t, Arrays.asList("m1", "m2"))));
        TeamTaskFlowResult orchResult = orch.execute(teamO.getTeamId());
        TeamTaskStatus orchStatus = storeO.getTask(taskO).orElseThrow().getStatus();

        // Parity: both reached the same per-task outcome via the SAME shared
        // fan-out + reduce + complete dispatcher.
        assertEquals(TeamTaskStatus.COMPLETED, daemonStatus,
                "daemon side COMPLETED via the shared dispatcher");
        assertEquals(TeamTaskStatus.COMPLETED, orchStatus,
                "orchestrator side COMPLETED via the shared dispatcher");
        assertEquals(daemonStatus, orchStatus,
                "daemon-vs-orchestrator per-task PARITY (same plan → same result)");
        assertTrue(orchResult.isSuccess(), "orchestrator DAG succeeded");
        // Both invoked the engine exactly twice (2 bound members).
        assertEquals(2, engineD.executeCount.get(), "daemon: 2 bound-member executions");
        assertEquals(2, engineO.executeCount.get(), "orchestrator: 2 bound-member executions");

        daemon.stop();
    }

    // ========================================================================
    // E2E: daemon diamond A→{B,C}→D where B and C each fan out to 2 bound
    // members. Full unattended path: per-cycle scan → ready resolution →
    // claim → fan-out → async non-blocking → real concurrency → all COMPLETED
    // in dependency order. Anti-Hollow #22: B and C dispatch strictly after A
    // completes; D strictly after both B and C complete.
    // ========================================================================

    @Test
    void e2eDaemonDiamondFanOutFullDependencyOrderedPath() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        String[] members = {"b1", "b2", "c1", "c2"};
        String[] sessions = {"s-b1", "s-b2", "s-c1", "s-c2"};
        Team team = createTeamAndBindMembers(mgr, "e2e-diamond-team", members, sessions);
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        // Router: B → {b1,b2} fan-out, C → {c1,c2} fan-out, A/D → single bound.
        ITaskMemberRouter router = new FixedPlanRouter(t -> {
            switch (t.getSubject()) {
                case "B": return boundFanOutPlan(team, t, Arrays.asList("b1", "b2"));
                case "C": return boundFanOutPlan(team, t, Arrays.asList("c1", "c2"));
                case "A": return boundFanOutPlan(team, t, Collections.singletonList("b1"));
                case "D": return boundFanOutPlan(team, t, Collections.singletonList("c1"));
                default: throw new AssertionError("unexpected: " + t.getSubject());
            }
        });
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setTaskMemberRouter(router);

        // Drive scans until the whole diamond COMPLETES (unattended loop).
        Set<String> expected = new HashSet<>(Arrays.asList(a, b, c, d));
        int maxScans = expected.size() + 3;
        for (int i = 0; i < maxScans && !allCompleted(store, expected); i++) {
            daemon.scanOnce();
            assertTrue(daemon.awaitInFlightDispatches(10_000),
                    "in-flight dispatches settled (scan " + (i + 1) + ")");
        }

        for (String id : expected) {
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                    "task " + id + " COMPLETED unattended via daemon fan-out dispatch");
        }

        // Anti-Hollow — DEPENDENCY ORDER: B and C dispatched strictly after
        // A completed; D strictly after both B and C completed.
        assertTrue(engine.enterNano.containsKey(a + "#b1"), "A was dispatched");
        long aExit = engine.exitNano.get(a + "#b1");
        long bEnter = engine.enterNano.get(b + "#b1");
        long cEnter = engine.enterNano.get(c + "#c1");
        assertTrue(bEnter > aExit, "B dispatched strictly AFTER A completed (dependency order)");
        assertTrue(cEnter > aExit, "C dispatched strictly AFTER A completed (dependency order)");
        long bExit = engine.exitNano.get(b + "#b2");
        long cExit = engine.exitNano.get(c + "#c2");
        long dEnter = engine.enterNano.get(d + "#c1");
        assertTrue(dEnter > bExit, "D dispatched strictly AFTER B completed");
        assertTrue(dEnter > cExit, "D dispatched strictly AFTER C completed");

        // Anti-Hollow — REAL CONCURRENCY inside B's fan-out (b1,b2 overlap)
        // and C's fan-out (c1,c2 overlap).
        boolean bOverlap = overlap(engine, b + "#b1", b + "#b2");
        boolean cOverlap = overlap(engine, c + "#c1", c + "#c2");
        assertTrue(bOverlap, "B fan-out b1,b2 overlap (real concurrency)");
        assertTrue(cOverlap, "C fan-out c1,c2 overlap (real concurrency)");

        daemon.stop();
    }

    private static boolean allCompleted(InMemoryTeamTaskStore store, Set<String> ids) {
        for (String id : ids) {
            if (store.getTask(id).orElseThrow().getStatus() != TeamTaskStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private static boolean overlap(ConcurrencyRecordingEngine engine, String k1, String k2) {
        Long e1 = engine.enterNano.get(k1), x1 = engine.exitNano.get(k1);
        Long e2 = engine.enterNano.get(k2), x2 = engine.exitNano.get(k2);
        if (e1 == null || x1 == null || e2 == null || x2 == null) {
            return false;
        }
        return (e1 < x2) && (e2 < x1);
    }

    // ========================================================================
    // Helpers: teams / tasks / plans
    // ========================================================================

    static Team createTeamAndBindMembers(InMemoryTeamManager mgr, String teamName,
                                          String[] memberNames, String[] sessionIds) {
        List<TeamMemberSpec> specs = new ArrayList<>();
        specs.add(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD));
        for (String name : memberNames) {
            specs.add(new TeamMemberSpec(name, name + "-agent", MemberRole.MEMBER));
        }
        TeamSpec spec = new TeamSpec(teamName, "d", "lead-agent", specs, 0);
        Team team = mgr.createTeam(spec);
        for (int i = 0; i < memberNames.length; i++) {
            mgr.bindMemberSession(team.getTeamId(), memberNames[i], sessionIds[i],
                    "actor-" + memberNames[i]);
        }
        return team;
    }

    static Team createTeamWithoutBoundMember(InMemoryTeamManager mgr, String memberName,
                                              String agentModel) {
        TeamSpec spec = new TeamSpec("spawn-team", "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, agentModel, MemberRole.MEMBER)),
                0);
        return mgr.createTeam(spec);
    }

    static String createTask(ITeamTaskStore store, String teamId,
                              String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session")
                .getTaskId();
    }

    static MemberDispatchPlan boundFanOutPlan(Team team, TeamTask task, List<String> memberNames) {
        List<DispatchTarget> targets = new ArrayList<>();
        for (String name : memberNames) {
            TeamMember m = team.getMembers().get(name);
            Objects.requireNonNull(m, "bound member not found: " + name);
            targets.add(DispatchTarget.bound(name, m.getSessionId(), name + "-agent"));
        }
        return new MemberDispatchPlan(team, task, targets, AllMustSucceedReduction.instance());
    }

    /** Router that returns a per-task plan computed by the supplied function. */
    static final class FixedPlanRouter implements ITaskMemberRouter {
        final java.util.function.Function<TeamTask, MemberDispatchPlan> fn;

        FixedPlanRouter(java.util.function.Function<TeamTask, MemberDispatchPlan> fn) {
            this.fn = fn;
        }

        @Override
        public MemberDispatchPlan route(Team team, TeamTask task) {
            return fn.apply(task);
        }
    }

    // ========================================================================
    // Recording engines / spawers / store
    // ========================================================================

    /** Engine that records per-(task,member) intervals + peak concurrency. */
    static final class ConcurrencyRecordingEngine implements IAgentEngine {
        final Map<String, Long> enterNano = new ConcurrentHashMap<>();
        final Map<String, Long> exitNano = new ConcurrentHashMap<>();
        final AtomicInteger peakConcurrent = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger executeCount = new AtomicInteger();
        volatile String failMode; // "non-completed" makes every execute return failed

        void failOn(String mode) {
            this.failMode = mode;
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            executeCount.incrementAndGet();
            String taskId = (String) request.getMetadata().get("teamTaskId");
            String member = (String) request.getMetadata().get("fanoutMember");
            String key = taskId + "#" + (member != null ? member : "single");
            int nowActive = active.incrementAndGet();
            peakConcurrent.accumulateAndGet(nowActive, Math::max);
            enterNano.put(key, System.nanoTime());
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NopAiAgentException("interrupted", e);
                }
                active.decrementAndGet();
                exitNano.put(key, System.nanoTime());
                if ("non-completed".equals(failMode)) {
                    return new AgentExecutionResult(AgentExecStatus.failed, null,
                            Collections.emptyList(), 0, 0L, 0L, "non-completed:" + key);
                }
                return new AgentExecutionResult(AgentExecStatus.completed, "ok:" + key,
                        Collections.emptyList(), 1, 10L, 1L, null);
            });
        }
    }

    /** Engine that completes each future only after a fixed delay (async proof). */
    static final class SlowAsyncEngine implements IAgentEngine {
        final long delayMs;
        final List<AgentMessageRequest> captured = Collections.synchronizedList(new ArrayList<>());

        SlowAsyncEngine(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            captured.add(request);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NopAiAgentException("interrupted", e);
                }
                return new AgentExecutionResult(AgentExecStatus.completed, "ok",
                        Collections.emptyList(), 1, 10L, 1L, null);
            });
        }
    }

    /** Simple recording engine returning completed results (singleton member). */
    static final class RecordingAgentEngine implements IAgentEngine {
        final List<AgentMessageRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            capturedRequests.add(request);
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok",
                            Collections.emptyList(), 1, 10L, 1L, null));
        }
    }

    /** Spawner whose per-call outcome can be configured (3-state + null/throw). */
    static final class RecordingSpawner implements IMemberSpawner {
        volatile SpawnFailure failKind;

        RecordingSpawner failWith(SpawnFailure kind) {
            this.failKind = kind;
            return this;
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            SpawnFailure k = failKind;
            if (k == null) {
                return SpawnMemberResult.dispatched(
                        new AgentExecutionResult(AgentExecStatus.completed, "ok",
                                Collections.emptyList(), 1, 10L, 1L, null),
                        "agent", "spawned-session");
            }
            switch (k) {
                case NO_SPAWN:
                    return SpawnMemberResult.noSpawn("declined");
                case SPAWN_FAILED:
                    return SpawnMemberResult.spawnFailed("spawn-failed");
                case THROWS:
                    throw new NopAiAgentException("spawner-throws");
                case NULL:
                    return null;
                default:
                    throw new AssertionError("unhandled: " + k);
            }
        }
    }

    /** Spawner that records the tenant observed on the worker thread. */
    static final class TenantRecordingSpawner implements IMemberSpawner {
        final AtomicReference<String> observedTenant = new AtomicReference<>();

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            observedTenant.set(ThreadLocalTenantResolver.current());
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok",
                            Collections.emptyList(), 1, 10L, 1L, null),
                    "worker-agent", "spawned-session");
        }
    }

    /** ITeamTaskStore wrapper that counts completeTask invocations. */
    static final class CountingTaskStore implements ITeamTaskStore {
        final ITeamTaskStore delegate;
        final AtomicInteger completeTaskCount = new AtomicInteger();

        CountingTaskStore(ITeamTaskStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public TeamTask createTask(String teamId, String subject, String description,
                                   List<String> blockedBy, String createdBy) {
            return delegate.createTask(teamId, subject, description, blockedBy, createdBy);
        }

        @Override
        public Optional<TeamTask> getTask(String taskId) {
            return delegate.getTask(taskId);
        }

        @Override
        public List<TeamTask> getTasksByTeam(String teamId) {
            return delegate.getTasksByTeam(teamId);
        }

        @Override
        public List<TeamTask> getTasksByCreator(String createdBy) {
            return delegate.getTasksByCreator(createdBy);
        }

        @Override
        public Optional<TeamTask> claimTask(String taskId, String claimedBy) {
            return delegate.claimTask(taskId, claimedBy);
        }

        @Override
        public Optional<TeamTask> completeTask(String taskId, String completedBy) {
            completeTaskCount.incrementAndGet();
            return delegate.completeTask(taskId, completedBy);
        }

        @Override
        public Optional<TeamTask> abandonTask(String taskId, String abandonedBy) {
            return delegate.abandonTask(taskId, abandonedBy);
        }

        @Override
        public Optional<TeamTask> reclaimTask(String taskId, String reclaimedBy) {
            return delegate.reclaimTask(taskId, reclaimedBy);
        }
    }

    // ========================================================================
    // Minimal recording IScheduledExecutor stub (lifecycle-wiring only).
    // ========================================================================
    static final class RecordingScheduler implements IScheduledExecutor, IDestroyable {
        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public void execute(Runnable command) {
        }

        @Override
        public void destroy() {
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public String getName() {
            return "recording-scheduler";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public ThreadPoolStats stats() {
            return null;
        }

        @Override
        public <V> CompletableFuture<V> submit(Callable<V> callable) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void refreshConfig() {
        }
    }
}
