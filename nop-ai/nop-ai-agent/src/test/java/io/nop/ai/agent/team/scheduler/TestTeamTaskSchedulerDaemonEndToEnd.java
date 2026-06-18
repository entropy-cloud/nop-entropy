package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
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
import java.util.List;
import java.util.Map;
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
 * Plan 236 Phase 2 end-to-end unattended tests for
 * {@link TeamTaskSchedulerDaemon}.
 *
 * <p>These tests drive the full unattended path: daemon start → periodic scan
 * (simulated via manual ticks on a recording scheduler) → ready resolution →
 * CAS claim → member-agent dispatch → completeTask → successor ready next
 * scan → ... → whole DAG COMPLETED, with <b>no manual
 * {@code TeamTaskFlowOrchestrator.execute(teamId)} invocation at all</b>.
 * This is the "unattended multi-agent orchestration" closure proof
 * (Anti-Hollow #22).
 *
 * <p>Coverage map (maps to Phase 2 Exit Criteria):
 * <ul>
 *   <li>{@link #linearUnattended_dagAutoCompletesInDependencyOrder} — 端到端
 *       线性 A→B→C 无人值守自动完成 + 完成顺序 A→B→C（Anti-Hollow 执行序证据）</li>
 *   <li>{@link #diamondUnattended_dagAutoCompletesInDependencyOrder} — 端到端
 *       菱形 A→{B,C}→D 无人值守自动完成 + 依赖序</li>
 *   <li>{@link #stopAfterStartNewTasksNotAutoDispatched_e2e} — 生命周期端到端：
 *       stop 后新建 CREATED 就绪任务不被派发</li>
 *   <li>{@link #daemonWiredIntoDefaultAgentEngine} — 接线验证 #23：
 *       {@code DefaultAgentEngine.setTeamTaskSchedulerDaemon} 真实注入 +
 *       getter 返回 functional daemon（非 NoOp 默认）</li>
 *   <li>{@link #noOpDaemonOnEngineByDefault_zeroRegression} — shipped 默认 NoOp
 *       零回归（未 set 时为 NoOp 实例）</li>
 *   <li>{@link #wiringDaemonInvokesRealTopologyClaimEngineComplete_e2e} — 接线
 *       端到端：守护进程确实经 {@code TeamTaskTopology.getReadyTasks()} +
 *       {@code claimTask} + {@code IAgentEngine.execute} + {@code completeTask}
 *       （completeTask 实际改 store status，非仅类型存在）</li>
 * </ul>
 */
public class TestTeamTaskSchedulerDaemonEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Recording mock member-agent engine — captures execution order for the
    // Anti-Hollow "B dispatched strictly after A completed" assertion.
    // ========================================================================

    /**
     * Member-agent engine mock that records the start sequence (per task) and
     * the completion sequence (the daemon's {@code completeTask} is observed
     * via the store, but we also record here when execute returns, which is
     * the moment the daemon will call completeTask). The
     * {@code completionBeforeStartOf} map records, for each task's start, the
     * set of tasks whose {@code execute} future had already completed — this
     * is the Anti-Hollow evidence that "B dispatched strictly after A
     * completed".
     */
    static final class RecordingAgentEngine implements IAgentEngine {
        final List<String> invocationOrder = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Integer> startSeq = new ConcurrentHashMap<>();
        final Map<String, Integer> executeCompletedSeq = new ConcurrentHashMap<>();
        final AtomicInteger seq = new AtomicInteger(0);
        final AtomicInteger completedSeq = new AtomicInteger(0);
        /** For task B (key), the set of task ids whose execute had completed before B started. */
        final Map<String, java.util.Set<String>> completedBeforeStart = new ConcurrentHashMap<>();

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            String taskId = (String) request.getMetadata().get("teamTaskId");
            int order = seq.incrementAndGet();
            invocationOrder.add(taskId);
            startSeq.put(taskId, order);
            // Snapshot which tasks have already had their execute future completed
            // at the moment this task starts. This proves dependency order across scans.
            completedBeforeStart.put(taskId, new java.util.HashSet<>(executeCompletedSeq.keySet()));

            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null))
                    .whenComplete((r, err) -> executeCompletedSeq.put(taskId, completedSeq.incrementAndGet()));
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                   String memberName, String sessionId) {
        TeamSpec spec = new TeamSpec("e2e-team", "d", "lead-agent",
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
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session")
                .getTaskId();
    }

    /**
     * Run scans until the given predicate is satisfied (all tasks COMPLETED)
     * or a max-iteration safety bound is hit. Each iteration is one
     * {@code daemon.scanOnce()} — simulating one periodic tick. Returns the
     * number of scans performed.
     */
    private static int runScansUntilAllComplete(TeamTaskSchedulerDaemon daemon,
                                                 ITeamTaskStore store, String teamId,
                                                 java.util.Set<String> expectedIds) {
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
    // 1. Linear A → B → C unattended: full path, dependency-ordered, no manual
    //    orchestrator call (Anti-Hollow #22).
    // ========================================================================

    @Test
    void linearUnattended_dagAutoCompletesInDependencyOrder() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(b));

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.start();

        // Unattended: just run periodic ticks until the whole DAG completes.
        // NO call to TeamTaskFlowOrchestrator.execute anywhere in this test.
        int scans = runScansUntilAllComplete(daemon, store, teamId,
                new java.util.HashSet<>(Arrays.asList(a, b, c)));

        // Whole DAG COMPLETED — unattended.
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus());

        // Anti-Hollow: dependency-order evidence. Each task was dispatched in
        // its own scan (B after A completed, C after B completed). The
        // invocation order must be exactly A, B, C.
        assertEquals(Arrays.asList(a, b, c), new ArrayList<>(engine.invocationOrder),
                "dispatch order is A → B → C (dependency-ordered across scans), scans=" + scans);

        // Stronger Anti-Hollow assertion: when B started, A's execute had
        // already completed (B's completedBeforeStart snapshot contains A).
        assertTrue(engine.completedBeforeStart.get(b).contains(a),
                "B dispatched strictly AFTER A completed (Anti-Hollow execution-order evidence)");
        assertTrue(engine.completedBeforeStart.get(c).contains(b),
                "C dispatched strictly AFTER B completed (Anti-Hollow execution-order evidence)");

        daemon.stop();
    }

    // ========================================================================
    // 2. Diamond A → {B, C} → D unattended
    // ========================================================================

    @Test
    void diamondUnattended_dagAutoCompletesInDependencyOrder() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.start();

        runScansUntilAllComplete(daemon, store, teamId,
                new java.util.HashSet<>(Arrays.asList(a, b, c, d)));

        // All four COMPLETED unattended.
        for (String id : Arrays.asList(a, b, c, d)) {
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                    "task " + id + " COMPLETED unattended");
        }

        // A first.
        assertEquals(a, engine.invocationOrder.get(0), "A dispatched first (no deps)");
        // D last.
        assertEquals(d, engine.invocationOrder.get(engine.invocationOrder.size() - 1),
                "D dispatched last (depends on B and C)");
        // B and C both after A.
        int startB = engine.startSeq.get(b);
        int startC = engine.startSeq.get(c);
        int startA = engine.startSeq.get(a);
        assertTrue(startB > startA, "B dispatched after A");
        assertTrue(startC > startA, "C dispatched after A");
        // D after both B and C completed (Anti-Hollow).
        assertTrue(engine.completedBeforeStart.get(d).contains(b),
                "D dispatched after B completed");
        assertTrue(engine.completedBeforeStart.get(d).contains(c),
                "D dispatched after C completed");

        daemon.stop();
    }

    // ========================================================================
    // 3. Lifecycle e2e: stop → new CREATED ready task not dispatched
    // ========================================================================

    @Test
    void stopAfterStartNewTasksNotAutoDispatched_e2e() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        RecordingScheduler scheduler = new RecordingScheduler();
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, scheduler);
        daemon.start();

        // One periodic tick: A dispatched + completed.
        scheduler.lastCommand.get().run();
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());

        // Create a new task B that would be ready in the next tick.
        String b = createTask(store, teamId, "B", Collections.emptyList());

        daemon.stop();
        assertTrue(scheduler.cancelled.get(), "stop cancelled the periodic schedule");

        // After stop, the schedule is cancelled: no further automatic ticks.
        // The new task B remains CREATED — NOT auto-dispatched. (We do NOT
        // invoke scheduler.lastCommand here, faithfully simulating the
        // cancelled schedule.)
        assertEquals(TeamTaskStatus.CREATED, store.getTask(b).orElseThrow().getStatus(),
                "new task B NOT auto-dispatched after stop (schedule cancelled)");
        assertFalse(engine.invocationOrder.contains(b),
                "engine.execute never invoked for B after stop");

        // scanOnce remains available for explicit on-demand scans
        // (lifecycle-independent), but the AUTOMATIC periodic dispatch is
        // stopped — that is the unattended-semantic under test.
    }

    // ========================================================================
    // 4. Wiring into DefaultAgentEngine
    // ========================================================================

    @Test
    void daemonWiredIntoDefaultAgentEngine() {
        // Construct a DefaultAgentEngine (minimal — we only test the daemon
        // wiring, not its execution path).
        DefaultAgentEngine engine = new DefaultAgentEngine(
                null, null, new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new io.nop.ai.agent.security.AllowAllToolAccessChecker());

        // Shipped default is NoOp.
        assertTrue(engine.getTeamTaskSchedulerDaemon() instanceof NoOpTeamTaskSchedulerDaemon,
                "shipped default daemon is NoOp (zero regression)");

        // Wire a functional daemon — the setter must accept and store it.
        ITeamManager mgr = new InMemoryTeamManager();
        ITeamTaskStore store = new InMemoryTeamTaskStore();
        TeamTaskSchedulerDaemon functional = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        engine.setTeamTaskSchedulerDaemon(functional);

        assertSame(functional, engine.getTeamTaskSchedulerDaemon(),
                "functional daemon wired and returned by getter (not NoOp)");

        // null falls back to NoOp (null-safe setter, consistent with
        // setRecoveryManager / setTeamManager adjudication).
        engine.setTeamTaskSchedulerDaemon(null);
        assertTrue(engine.getTeamTaskSchedulerDaemon() instanceof NoOpTeamTaskSchedulerDaemon,
                "null setter falls back to NoOp");
    }

    @Test
    void noOpDaemonOnEngineByDefault_zeroRegression() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                null, null, new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new io.nop.ai.agent.security.AllowAllToolAccessChecker());

        ITeamTaskSchedulerDaemon daemon = engine.getTeamTaskSchedulerDaemon();
        // NoOp lifecycle: start/stop are no-ops (must not throw, no scheduler).
        daemon.start();
        daemon.stop();
        // scanOnce returns all-zero (explicit "no scheduling scanning" semantic).
        SchedulerScanResult r = daemon.scanOnce();
        assertEquals(0, r.getTeamsScanned());
        assertEquals(0, r.getCompletedTasks());
        assertEquals(0, r.getAbandonedTasks());
    }

    // ========================================================================
    // 5. Wiring e2e — daemon really drives the full chain on a real engine
    //    wiring (completeTask actually mutates store status).
    // ========================================================================

    @Test
    void wiringDaemonInvokesRealTopologyClaimEngineComplete_e2e() {
        // Build the real wiring: DefaultAgentEngine as the IAgentEngine for
        // member delegation + InMemory team manager/task store. The engine's
        // own execution path is NOT exercised here (no chat service), but the
        // daemon's wiring through the engine's IAgentEngine interface IS —
        // we use a recording IAgentEngine to observe the actual dispatch.
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine memberEngine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        // The daemon consumes memberEngine as its IAgentEngine — this is the
        // exact wiring DefaultAgentEngine.setTeamTaskSchedulerDaemon +
        // TeamTaskSchedulerDaemon(agentEngine=this, ...) would use.
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                memberEngine, store, mgr, new RecordingScheduler());
        daemon.start();

        SchedulerScanResult r = daemon.scanOnce();

        // Wiring #23 end-to-end: the chain topology.getReadyTasks → claimTask
        // → engine.execute → completeTask is actually traversed.
        assertEquals(1, r.getCompletedTasks(), "A completed via the full chain");
        assertTrue(r.getCompletedTaskIds().contains(a));
        assertEquals(1, memberEngine.invocationOrder.size(),
                "engine.execute actually invoked for A");
        assertEquals(a, memberEngine.invocationOrder.get(0));

        // completeTask actually mutated the store status (not just type-present).
        TeamTask aAfter = store.getTask(a).orElseThrow();
        assertEquals(TeamTaskStatus.COMPLETED, aAfter.getStatus(),
                "completeTask actually transitioned A CLAIMED → COMPLETED in the live store");
        assertEquals(TeamTaskSchedulerDaemon.DEFAULT_DAEMON_SESSION_ID, aAfter.getClaimedBy(),
                "claimTask recorded the daemon session in claimedBy (audit trail)");

        daemon.stop();
    }

    private static void assertSame(Object expected, Object actual, String message) {
        // Use junit's assertSame semantics via reference equality.
        if (expected != actual) {
            throw new AssertionError(message + " (expected " + expected + " but got " + actual + ")");
        }
    }

    // ========================================================================
    // Minimal recording IScheduledExecutor (same as Phase 1 focused tests).
    // ========================================================================
    static final class RecordingScheduler implements IScheduledExecutor, IDestroyable {
        final AtomicInteger scheduleCount = new AtomicInteger(0);
        final AtomicReference<Runnable> lastCommand = new AtomicReference<>();
        final AtomicReference<Long> lastInitialDelay = new AtomicReference<>();
        final AtomicReference<Long> lastDelay = new AtomicReference<>();
        final AtomicReference<TimeUnit> lastUnit = new AtomicReference<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        boolean mayInterruptIfRunning;
        final AtomicBoolean destroyed = new AtomicBoolean(false);

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            scheduleCount.incrementAndGet();
            lastCommand.set(command);
            lastInitialDelay.set(initialDelay);
            lastDelay.set(delay);
            lastUnit.set(unit);
            return new Future<Object>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    cancelled.set(true);
                    RecordingScheduler.this.mayInterruptIfRunning = mayInterruptIfRunning;
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
            destroyed.set(true);
        }

        @Override
        public boolean isDestroyed() {
            return destroyed.get();
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
