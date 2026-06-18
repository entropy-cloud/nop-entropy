package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.DefaultMemberSpawner;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 237 Phase 2 end-to-end unattended auto-spawn tests for
 * {@link TeamTaskSchedulerDaemon} + {@link DefaultMemberSpawner}.
 *
 * <p>These tests drive the full unattended auto-spawn path: daemon start →
 * periodic scan (simulated via manual ticks on a recording scheduler) →
 * ready resolution → CAS claim → no bound member → spawner.spawnMember →
 * {@link IAgentEngine#execute} → completeTask → task COMPLETED, with
 * <b>no manual {@code bindMemberSession} call at all</b>. This is the
 * "unattended auto-spawn" closure proof (Anti-Hollow #22): the team
 * declares its member spec but never binds a session, yet the daemon
 * materialises a fresh member-agent execution per task via the spawner.
 *
 * <p>Coverage map (maps to Phase 2 Exit Criteria):
 * <ul>
 *   <li>{@link #linearUnattendedAutoSpawnDagCompletesNoManualBind} — 端到端
 *       线性 A→B→C 无人值守 auto-spawn 自动完成 + 完成顺序 A→B→C（Anti-Hollow
 *       执行序证据）+ **无任何手动 bindMemberSession**</li>
 *   <li>{@link #diamondUnattendedAutoSpawnDagCompletesNoManualBind} — 端到端
 *       菱形 A→{B,C}→D 无人值守 auto-spawn 自动完成 + 依赖序</li>
 *   <li>{@link #noOpSpawnerZeroRegressionAbandonsNoSpawn} — 零回归对比测试：
 *       同样场景 + NoOp spawner = 任务 abandon（既有行为不变）</li>
 *   <li>{@link #boundMemberPrioritySpawnerNotConsultedE2e} — bound-priority
 *       端到端：有 bound member + functional spawner = 不 spawn（用 bound
 *       session），断言 spawner 未被调用</li>
 *   <li>{@link #spawnUsesAgentModelFromTeamMemberSpecNotHardcoded} — Anti-Hollow：
 *       spawn 使用的 agentModel 来自 {@link TeamMemberSpec}（非硬编码）</li>
 *   <li>{@link #daemonWiredIntoDefaultAgentEngineSpawnerInjectedViaSetter} — 接线
 *       验证：{@code DefaultAgentEngine} + functional daemon + spawner 经
 *       daemon setter 注入（consumer-side wiring, decision 5）</li>
 * </ul>
 */
public class TestTeamTaskSchedulerDaemonMemberSpawnEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Recording mock member-agent engine — captures spawned execution order +
    //   the agentName / sessionId used (Anti-Hollow evidence).
    // ========================================================================

    static final class RecordingAgentEngine implements IAgentEngine {
        final List<AgentMessageRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Integer> startSeq = new ConcurrentHashMap<>();
        final Map<String, Integer> executeCompletedSeq = new ConcurrentHashMap<>();
        final AtomicInteger seq = new AtomicInteger(0);
        final AtomicInteger completedSeq = new AtomicInteger(0);
        /** For task B (key), the set of task ids whose execute had completed before B started. */
        final Map<String, Set<String>> completedBeforeStart = new ConcurrentHashMap<>();

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            String taskId = (String) request.getMetadata().get("teamTaskId");
            int order = seq.incrementAndGet();
            capturedRequests.add(request);
            startSeq.put(taskId, order);
            completedBeforeStart.put(taskId, new HashSet<>(executeCompletedSeq.keySet()));

            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null))
                    .whenComplete((r, err) -> executeCompletedSeq.put(taskId, completedSeq.incrementAndGet()));
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Create a team with a declarative member spec but NO bound session —
     * the auto-spawn case. The daemon will not find a bound member and will
     * consult the spawner.
     */
    private static Team createTeamWithoutBoundMember(InMemoryTeamManager mgr,
                                                     String memberName,
                                                     String memberAgentModel) {
        TeamSpec spec = new TeamSpec("e2e-spawn-team", "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberAgentModel, MemberRole.MEMBER)),
                0);
        return mgr.createTeam(spec);
        // Deliberately NOT calling mgr.bindMemberSession(...).
    }

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                  String memberName, String sessionId,
                                                  String memberAgentModel) {
        TeamSpec spec = new TeamSpec("e2e-bound-team", "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberAgentModel, MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), memberName, sessionId, "actor-" + memberName);
        return team;
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId,
                                     String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session")
                .getTaskId();
    }

    /**
     * Recording spawner that wraps {@link DefaultMemberSpawner} but counts
     * invocations (so the bound-priority test can assert "spawner not
     * consulted when bound member exists").
     */
    static final class CountingSpawner implements IMemberSpawner {
        final AtomicInteger invocations = new AtomicInteger(0);
        final DefaultMemberSpawner delegate;

        CountingSpawner(IAgentEngine engine) {
            this.delegate = new DefaultMemberSpawner(engine);
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            invocations.incrementAndGet();
            return delegate.spawnMember(request);
        }
    }

    private static int runScansUntilAllComplete(TeamTaskSchedulerDaemon daemon,
                                                 ITeamTaskStore store,
                                                 Set<String> expectedIds) {
        int maxScans = expectedIds.size() + 2;
        for (int i = 0; i < maxScans; i++) {
            daemon.scanOnce();
            boolean allComplete = true;
            for (String id : expectedIds) {
                TeamTask t = store.getTask(id).orElseThrow();
                if (t.getStatus() != TeamTaskStatus.COMPLETED) {
                    allComplete = false;
                    break;
                }
            }
            if (allComplete) {
                return i + 1;
            }
        }
        return maxScans;
    }

    // ========================================================================
    // 1. Linear A → B → C unattended auto-spawn: full path, dependency-ordered,
    //    NO manual bindMemberSession call (Anti-Hollow #22).
    // ========================================================================

    @Test
    void linearUnattendedAutoSpawnDagCompletesNoManualBind() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(b));

        CountingSpawner spawner = new CountingSpawner(engine);
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);
        daemon.start();

        int scans = runScansUntilAllComplete(daemon, store,
                new HashSet<>(Arrays.asList(a, b, c)));

        // Whole DAG COMPLETED — unattended, NO manual bindMemberSession.
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus());

        // Anti-Hollow: dependency-order evidence across scans. Each task was
        // dispatched in its own scan via a fresh spawn (no manual binding).
        assertEquals(3, engine.capturedRequests.size(),
                "exactly 3 spawned executions (one per task)");
        assertEquals(Arrays.asList(a, b, c), extractTaskIds(engine.capturedRequests),
                "spawn dispatch order is A → B → C (dependency-ordered across scans), scans=" + scans);

        // Stronger Anti-Hollow: B dispatched strictly AFTER A's spawn completed.
        assertTrue(engine.completedBeforeStart.get(b).contains(a),
                "B spawned strictly AFTER A's execution completed (Anti-Hollow)");
        assertTrue(engine.completedBeforeStart.get(c).contains(b),
                "C spawned strictly AFTER B's execution completed (Anti-Hollow)");

        // Spawner was consulted exactly once per task (3 total — no manual binding).
        assertEquals(3, spawner.invocations.get(),
                "spawner consulted exactly once per task (no bound member to bypass it)");

        // Every spawned execution used a fresh session (per-task spawn).
        Set<String> sessionIds = new HashSet<>();
        for (AgentMessageRequest req : engine.capturedRequests) {
            sessionIds.add(req.getSessionId());
        }
        assertEquals(3, sessionIds.size(),
                "each spawn got a fresh session id (per-task spawn, no reuse)");

        daemon.stop();
    }

    // ========================================================================
    // 2. Diamond A → {B, C} → D unattended auto-spawn
    // ========================================================================

    @Test
    void diamondUnattendedAutoSpawnDagCompletesNoManualBind() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        CountingSpawner spawner = new CountingSpawner(engine);
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);
        daemon.start();

        runScansUntilAllComplete(daemon, store,
                new HashSet<>(Arrays.asList(a, b, c, d)));

        for (String id : Arrays.asList(a, b, c, d)) {
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                    "task " + id + " COMPLETED unattended via auto-spawn (no manual bind)");
        }

        // A first, D last.
        List<String> taskIds = extractTaskIds(engine.capturedRequests);
        assertEquals(a, taskIds.get(0), "A spawned first (no deps)");
        assertEquals(d, taskIds.get(taskIds.size() - 1), "D spawned last (depends on B and C)");

        // B and C both after A; D after both B and C completed.
        int startB = engine.startSeq.get(b);
        int startC = engine.startSeq.get(c);
        int startA = engine.startSeq.get(a);
        assertTrue(startB > startA, "B spawned after A");
        assertTrue(startC > startA, "C spawned after A");
        assertTrue(engine.completedBeforeStart.get(d).contains(b),
                "D spawned after B completed");
        assertTrue(engine.completedBeforeStart.get(d).contains(c),
                "D spawned after C completed");

        daemon.stop();
    }

    // ========================================================================
    // 3. Zero-regression: NoOp spawner + unbound team = abandon
    //    (the pre-237 behaviour, preserved verbatim)
    // ========================================================================

    @Test
    void noOpSpawnerZeroRegressionAbandonsNoSpawn() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        // NoOp spawner: the shipped default (no auto-spawn opt-in).
        daemon.setMemberSpawner(NoOpMemberSpawner.noOp());
        daemon.start();

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getReadyCreatedTasks());
        assertEquals(1, r.getClaimedTasks());
        assertEquals(1, r.getAbandonedTasks(),
                "NoOp spawner → UNBOUND_MEMBER abandon (zero regression vs pre-237)");
        assertEquals(0, r.getCompletedTasks());
        assertEquals(0, r.getDispatchedTasks(),
                "engine.execute NOT invoked (spawner honestly declined)");
        assertEquals(0, engine.capturedRequests.size());
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus(),
                "A abandoned — pre-spawn behaviour preserved (NoOp shipped default)");

        daemon.stop();
    }

    // ========================================================================
    // 4. Bound-priority: bound member present → spawner NOT consulted,
    //    dispatch uses bound session.
    // ========================================================================

    @Test
    void boundMemberPrioritySpawnerNotConsultedE2e() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "bound-worker-session",
                "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        CountingSpawner spawner = new CountingSpawner(engine);
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);
        daemon.start();

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getCompletedTasks(),
                "task COMPLETED via bound-member path (not spawn)");
        assertEquals(0, spawner.invocations.get(),
                "spawner NOT consulted — bound member takes priority (decision 3)");
        assertEquals(1, engine.capturedRequests.size());
        assertEquals("bound-worker-session", engine.capturedRequests.get(0).getSessionId(),
                "dispatch used the BOUND session (not a spawned session)");

        // Anti-Hollow: the bound session id does NOT have the spawned- prefix
        // (would have if the spawner had been consulted).
        assertFalse(engine.capturedRequests.get(0).getSessionId().startsWith("spawned-"),
                "dispatch used bound session (no spawned- prefix → spawner was bypassed)");

        daemon.stop();
    }

    // ========================================================================
    // 5. Anti-Hollow: spawned agentModel comes from TeamMemberSpec (not
    //    hardcoded in the daemon or spawner).
    // ========================================================================

    @Test
    void spawnUsesAgentModelFromTeamMemberSpecNotHardcoded() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        // Distinctive agentModel name — if the daemon or spawner hardcoded
        // anything, this test would fail.
        Team team = createTeamWithoutBoundMember(mgr, "worker",
                "very-distinctive-member-agent-model-xyz");
        String a = createTask(store, team.getTeamId(), "A", Collections.emptyList());

        CountingSpawner spawner = new CountingSpawner(engine);
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);

        daemon.scanOnce();

        assertEquals(1, engine.capturedRequests.size());
        AgentMessageRequest captured = engine.capturedRequests.get(0);
        assertEquals("very-distinctive-member-agent-model-xyz", captured.getAgentName(),
                "spawned agent name = TeamMemberSpec.agentModel (read from spec, not hardcoded)");
    }

    // ========================================================================
    // 6. Wiring: DefaultAgentEngine + functional daemon + spawner via daemon
    //    setter (consumer-side wiring, design decision 5 — spawner injected
    //    into the daemon, NOT into the engine).
    // ========================================================================

    @Test
    void daemonWiredIntoDefaultEngineSpawnerInjectedViaSetter() {
        // Construct a DefaultAgentEngine (minimal — we only test the wiring
        // shape, not its execution path).
        DefaultAgentEngine engine = new DefaultAgentEngine(
                null, null, new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new io.nop.ai.agent.security.AllowAllToolAccessChecker());

        ITeamManager mgr = new InMemoryTeamManager();
        ITeamTaskStore store = new InMemoryTeamTaskStore();

        // Daemon consumer-side wiring (decision 5): the spawner goes into
        // the daemon, NOT into the engine.
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);
        daemon.setMemberSpawner(spawner);

        // Spawner is on the daemon, not the engine.
        assertEquals(spawner, daemon.getMemberSpawner(),
                "spainer injected via daemon setter (consumer-side wiring)");

        // The engine exposes the daemon but NOT a spawner (engine is unaware
        // of the spawner extension point — it is a daemon-only concern).
        engine.setTeamTaskSchedulerDaemon(daemon);
        assertEquals(daemon, engine.getTeamTaskSchedulerDaemon(),
                "daemon wired into engine (engine still owns the daemon lifecycle)");

        // Reconfigure: clear the spawner (null-safe → NoOp).
        daemon.setMemberSpawner(null);
        assertTrue(daemon.getMemberSpawner() instanceof NoOpMemberSpawner,
                "null-safe setter resets the daemon to NoOp spawner (zero regression)");
    }

    // ========================================================================
    // Internal helper
    // ========================================================================

    private static List<String> extractTaskIds(List<AgentMessageRequest> requests) {
        List<String> ids = new ArrayList<>();
        for (AgentMessageRequest r : requests) {
            ids.add((String) r.getMetadata().get("teamTaskId"));
        }
        return ids;
    }

    // ========================================================================
    // Minimal recording IScheduledExecutor stub (lifecycle-wiring only).
    // ========================================================================
    static final class RecordingScheduler implements IScheduledExecutor, IDestroyable {
        final AtomicInteger scheduleCount = new AtomicInteger(0);
        final AtomicReference<Runnable> lastCommand = new AtomicReference<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            scheduleCount.incrementAndGet();
            lastCommand.set(command);
            return new Future<Object>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    cancelled.set(true);
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get();
                }

                @Override
                public boolean isDone() {
                    return cancelled.get();
                }

                @Override
                public Object get() {
                    return null;
                }

                @Override
                public Object get(long timeout, TimeUnit u) {
                    return null;
                }
            };
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
