package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 237 Phase 2 focused tests for the daemon ↔ spawner integration
 * (dispatch-path wiring of {@link IMemberSpawner} into
 * {@link TeamTaskSchedulerDaemon}).
 *
 * <p>Coverage map (maps 1:1 to Phase 2 Exit Criteria for the integration +
 * zero-regression + bound-priority + honest-failure aspects):
 * <ul>
 *   <li>{@link #boundMemberPriorityDoesNotConsultSpawner} — 有 bound member +
 *       functional spawner = 不 spawn（直接用 bound session，spawner 未被调用）</li>
 *   <li>{@link #noBoundMemberWithFunctionalSpawnerSpawnsAndCompletes} — 无 bound
 *       member + functional spawner = spawn 后委派（与 bound-member 同一
 *       complete/abandon 语义）</li>
 *   <li>{@link #noBoundMemberWithNoOpSpawnerAbandonsZeroRegression} — 无 bound
 *       member + NoOp spawner = abandon（零回归，既有行为不变）</li>
 *   <li>{@link #noBoundMemberWithShippedDefaultSpawnerAbandonsZeroRegression}
 *       — 无 bound member + 未 set spawner = abandon（daemon shipped 默认 =
 *       NoOp，零回归）</li>
 *   <li>{@link #spawnDispatchedNonCompletedHonestAbandon} — spawn DISPATCHED 但
 *       非 completed 终态 = 诚实 abandon（不静默跳过，与 bound-member 同一
 *       complete/abandon 语义）</li>
 *   <li>{@link #spawnFailedHonestAbandon} — spawn SPAWN_FAILED = 诚实 abandon
 *       （DISPATCH_FAILED 语义）</li>
 *   <li>{@link #spawnNoSpawnHonestAbandonUnboundMember} — spawn NO_SPAWN = 诚实
 *       abandon（UNBOUND_MEMBER 语义）</li>
 *   <li>{@link #spawnerThrowsHonestAbandon} — spawner 抛异常 = 诚实 abandon
 *       （防御性，contract violation）</li>
 *   <li>{@link #setMemberSpawnerIsNullableFallsBackToNoOp} — {@code setMemberSpawner}
 *       null-safe 回退 NoOp</li>
 *   <li>{@link #spawnerWiringViaConstructor} — spawner 经 spawner-aware 构造器注入</li>
 *   <li>{@link #spawnerWiringVerifiedEngineExecuteActuallyInvoked} — **接线验证
 *       #23**：端到端 spawner 经 IAgentEngine.execute 真实调用（非仅状态变化）+
 *       agentModel 来自 TeamMemberSpec（非硬编码）</li>
 * </ul>
 */
public class TestTeamTaskSchedulerDaemonMemberSpawner {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Recording stubs
    // ========================================================================

    /**
     * Minimal {@link IAgentEngine} stub that records each invocation and
     * returns a configurable result (default: completed).
     */
    static final class RecordingAgentEngine implements IAgentEngine {
        final List<AgentMessageRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<AgentExecutionResult> nextResult = new AtomicReference<>();
        final AtomicReference<RuntimeException> nextException = new AtomicReference<>();

        RecordingAgentEngine returningCompleted() {
            this.nextResult.set(new AgentExecutionResult(AgentExecStatus.completed, "ok",
                    Collections.emptyList(), 1, 10L, 1L, null));
            this.nextException.set(null);
            return this;
        }

        RecordingAgentEngine returning(AgentExecutionResult result) {
            this.nextResult.set(result);
            this.nextException.set(null);
            return this;
        }

        RecordingAgentEngine throwing(RuntimeException ex) {
            this.nextResult.set(null);
            this.nextException.set(ex);
            return this;
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            capturedRequests.add(request);
            RuntimeException ex = nextException.get();
            if (ex != null) {
                CompletableFuture<AgentExecutionResult> f = new CompletableFuture<>();
                f.completeExceptionally(ex);
                return f;
            }
            AgentExecutionResult result = nextResult.get();
            if (result == null) {
                result = new AgentExecutionResult(AgentExecStatus.completed, "ok",
                        Collections.emptyList(), 1, 10L, 1L, null);
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Minimal {@link IMemberSpawner} stub that records each invocation and
     * returns a configurable result (default: NO_SPAWN).
     */
    static final class RecordingSpawner implements IMemberSpawner {
        final AtomicInteger invocations = new AtomicInteger(0);
        final List<SpawnMemberRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<SpawnMemberResult> nextResult = new AtomicReference<>();
        final AtomicReference<RuntimeException> nextException = new AtomicReference<>();

        RecordingSpawner returning(SpawnMemberResult result) {
            this.nextResult.set(result);
            this.nextException.set(null);
            return this;
        }

        RecordingSpawner throwing(RuntimeException ex) {
            this.nextException.set(ex);
            this.nextResult.set(null);
            return this;
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            invocations.incrementAndGet();
            capturedRequests.add(request);
            RuntimeException ex = nextException.get();
            if (ex != null) {
                throw ex;
            }
            SpawnMemberResult result = nextResult.get();
            if (result == null) {
                result = SpawnMemberResult.noSpawn("default-no-spawn");
            }
            return result;
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                   String memberName, String sessionId,
                                                   String memberAgentModel) {
        TeamSpec spec = new TeamSpec("daemon-team", "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberAgentModel, MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), memberName, sessionId, "actor-" + memberName);
        return team;
    }

    private static Team createTeamWithoutBoundMember(InMemoryTeamManager mgr,
                                                      String memberName,
                                                      String memberAgentModel) {
        TeamSpec spec = new TeamSpec("unbound-team", "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberAgentModel, MemberRole.MEMBER)),
                0);
        return mgr.createTeam(spec);
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId, String subject) {
        return store.createTask(teamId, subject, "desc-" + subject, Collections.emptyList(),
                "lead-session").getTaskId();
    }

    // ========================================================================
    // 1. Bound-member priority — spawner NEVER consulted when bound member exists
    //    (design 裁定 3)
    // ========================================================================

    @Test
    void boundMemberPriorityDoesNotConsultSpawner() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().returningCompleted();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session",
                "worker-agent-model");
        String a = createTask(store, team.getTeamId(), "A");

        RecordingSpawner spawner = new RecordingSpawner().returning(
                SpawnMemberResult.dispatched(
                        new AgentExecutionResult(AgentExecStatus.completed, "spawned-ok",
                                Collections.emptyList(), 1, 1L, 1L, null),
                        "worker-agent-model", "spawned-session-should-not-happen"));

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getCompletedTasks(),
                "task COMPLETED via the bound-member path (not the spawner)");
        assertEquals(0, spawner.invocations.get(),
                "spawner NOT consulted — bound member takes priority (design 裁定 3)");
        assertEquals(1, engine.capturedRequests.size(),
                "bound-member engine.execute was invoked directly");
        assertEquals("worker-session", engine.capturedRequests.get(0).getSessionId(),
                "dispatch used the BOUND session (not a spawned session)");
    }

    // ========================================================================
    // 2. No bound member + functional spawner = spawn and complete
    //    (the core auto-spawn case, plan 237 goal)
    // ========================================================================

    @Test
    void noBoundMemberWithFunctionalSpawnerSpawnsAndCompletes() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().returningCompleted();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String a = createTask(store, team.getTeamId(), "A");

        // Use the real DefaultMemberSpawner — this exercises the actual
        // spawn-target resolution + execute wiring (not just a stub).
        io.nop.ai.agent.team.DefaultMemberSpawner spawner =
                new io.nop.ai.agent.team.DefaultMemberSpawner(engine);

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getReadyCreatedTasks(), "A is CREATED + ready");
        assertEquals(1, r.getClaimedTasks(), "daemon claimed A");
        assertEquals(1, r.getCompletedTasks(),
                "task COMPLETED via the spawned-member path");
        assertEquals(0, r.getAbandonedTasks(),
                "spawn-and-dispatch succeeded — no abandon");
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "A transitioned to COMPLETED via the spawn path");
        assertEquals(1, engine.capturedRequests.size(),
                "spawner invoked engine.execute exactly once (via the daemon dispatch)");
    }

    // ========================================================================
    // 3. No bound member + NoOp spawner = abandon (zero regression)
    //    (NoOp shipped default = pre-spawn behaviour preserved)
    // ========================================================================

    @Test
    void noBoundMemberWithNoOpSpawnerAbandonsZeroRegression() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().returningCompleted();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String a = createTask(store, team.getTeamId(), "A");

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        // Explicitly wire NoOp (the shipped default).
        daemon.setMemberSpawner(NoOpMemberSpawner.noOp());

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getReadyCreatedTasks());
        assertEquals(1, r.getClaimedTasks());
        assertEquals(1, r.getAbandonedTasks(),
                "NoOp spawner → UNBOUND_MEMBER abandon (zero regression)");
        assertEquals(0, r.getDispatchedTasks(),
                "engine.execute NOT invoked (no spawn, no bound member)");
        assertEquals(0, engine.capturedRequests.size(),
                "engine NOT invoked when NoOp spawner declines");
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus(),
                "A transitioned to ABANDONED (pre-spawn behaviour preserved)");
    }

    // ========================================================================
    // 4. No bound member + no spawner wired = abandon (shipped default = NoOp,
    //    zero regression even if integrator never calls setMemberSpawner)
    // ========================================================================

    @Test
    void noBoundMemberWithShippedDefaultSpawnerAbandonsZeroRegression() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().returningCompleted();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String a = createTask(store, team.getTeamId(), "A");

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        // Do NOT call setMemberSpawner — verify the field default (NoOp)
        // is what the dispatch path actually sees.
        assertNotNull(daemon.getMemberSpawner());
        assertTrue(daemon.getMemberSpawner() instanceof NoOpMemberSpawner,
                "shipped default spawner field = NoOp (zero regression without explicit wiring)");

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getAbandonedTasks(),
                "shipped-default NoOp spawner → UNBOUND_MEMBER abandon");
        assertEquals(0, r.getCompletedTasks());
        assertEquals(0, engine.capturedRequests.size());
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus());
    }

    // ========================================================================
    // 5. Spawn DISPATCHED but non-completed → honest abandon
    //    (daemon's complete/abandon logic shared between bound and spawned paths)
    // ========================================================================

    @Test
    void spawnDispatchedNonCompletedHonestAbandon() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().returning(
                new AgentExecutionResult(AgentExecStatus.failed, null, Collections.emptyList(),
                        1, 1L, 1L, "spawn-failed-status"));
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String a = createTask(store, team.getTeamId(), "A");

        io.nop.ai.agent.team.DefaultMemberSpawner spawner =
                new io.nop.ai.agent.team.DefaultMemberSpawner(engine);
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getDispatchedTasks(),
                "execute WAS invoked (then returned non-completed)");
        assertEquals(1, r.getAbandonedTasks(),
                "spawn DISPATCHED non-completed = honest abandon (not silent skip)");
        assertEquals(0, r.getCompletedTasks());
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus());
    }

    // ========================================================================
    // 6. Spawn SPAWN_FAILED → honest abandon (DISPATCH_FAILED semantics)
    // ========================================================================

    @Test
    void spawnFailedHonestAbandon() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().throwing(
                new RuntimeException("spawn-agent-boom"));
        // ^ the engine throws — the spawner will catch and return SPAWN_FAILED
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String a = createTask(store, team.getTeamId(), "A");

        io.nop.ai.agent.team.DefaultMemberSpawner spawner =
                new io.nop.ai.agent.team.DefaultMemberSpawner(engine);
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getDispatchedTasks(),
                "execute WAS invoked (then threw) — DISPATCH_FAILED counts as dispatched");
        assertEquals(1, r.getAbandonedTasks(),
                "SPAWN_FAILED = honest abandon (DISPATCH_FAILED semantics)");
        assertEquals(0, r.getCompletedTasks());
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus());
    }

    // ========================================================================
    // 7. Spawn NO_SPAWN (functional spawner honestly declined) = abandon
    //    (UNBOUND_MEMBER semantics — same as NoOp)
    // ========================================================================

    @Test
    void spawnNoSpawnHonestAbandonUnboundMember() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().returningCompleted();
        // Team with NO memberSpecs — the functional spawner will honestly
        // decline (NO_SPAWN) because there is no declarative member to spawn.
        TeamSpec emptySpec = new TeamSpec("empty-spec-team", "d", "lead-agent",
                Collections.emptyList(), 0);
        Team team = mgr.createTeam(emptySpec);
        String a = createTask(store, team.getTeamId(), "A");

        io.nop.ai.agent.team.DefaultMemberSpawner spawner =
                new io.nop.ai.agent.team.DefaultMemberSpawner(engine);
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getAbandonedTasks(),
                "functional spawner NO_SPAWN (no memberSpec) = honest UNBOUND_MEMBER abandon");
        assertEquals(0, r.getDispatchedTasks(),
                "engine.execute NOT invoked (spawner honestly declined before invocation)");
        assertEquals(0, engine.capturedRequests.size());
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus());
    }

    // ========================================================================
    // 8. Spawner throws (contract violation) — defensive honest abandon
    // ========================================================================

    @Test
    void spawnerThrowsHonestAbandon() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().returningCompleted();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String a = createTask(store, team.getTeamId(), "A");

        RecordingSpawner spawner = new RecordingSpawner().throwing(
                new RuntimeException("spawner-contract-violation"));
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);

        SchedulerScanResult r = daemon.scanOnce();

        assertEquals(1, r.getAbandonedTasks(),
                "spawner contract violation (throw) = honest abandon, not silent skip or NPE propagation");
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus());
    }

    // ========================================================================
    // 9. setMemberSpawner null-safe fallback to NoOp
    // ========================================================================

    @Test
    void setMemberSpawnerIsNullableFallsBackToNoOp() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().returningCompleted();

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(null);

        assertTrue(daemon.getMemberSpawner() instanceof NoOpMemberSpawner,
                "setMemberSpawner(null) falls back to NoOp (null-safe)");
    }

    // ========================================================================
    // 10. Spawner wiring via the spawner-aware constructor
    // ========================================================================

    @Test
    void spawnerWiringViaConstructor() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().returningCompleted();

        RecordingSpawner spawner = new RecordingSpawner().returning(
                SpawnMemberResult.noSpawn("constructor-wiring-test"));

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler(),
                TeamTaskSchedulerDaemon.DEFAULT_SCAN_INTERVAL_SEC,
                TeamTaskSchedulerDaemon.DEFAULT_DAEMON_SESSION_ID,
                null, spawner);

        assertEquals(spawner, daemon.getMemberSpawner(),
                "spawner wired via the 8-arg constructor is returned by getter");

        // null in constructor → NoOp fallback
        TeamTaskSchedulerDaemon daemon2 = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler(),
                TeamTaskSchedulerDaemon.DEFAULT_SCAN_INTERVAL_SEC,
                TeamTaskSchedulerDaemon.DEFAULT_DAEMON_SESSION_ID,
                null, null);
        assertTrue(daemon2.getMemberSpawner() instanceof NoOpMemberSpawner,
                "null spawner in constructor falls back to NoOp");
    }

    // ========================================================================
    // 11. Wiring verification #23 — spawner actually invokes
    //     IAgentEngine.execute with agentModel from TeamMemberSpec (not
    //     hardcoded), and the full chain runs end-to-end through the daemon
    // ========================================================================

    @Test
    void spawnerWiringVerifiedEngineExecuteActuallyInvoked() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine().returningCompleted();
        Team team = createTeamWithoutBoundMember(mgr, "worker",
                "very-distinctive-agent-model");
        String a = createTask(store, team.getTeamId(), "A");

        io.nop.ai.agent.team.DefaultMemberSpawner spawner =
                new io.nop.ai.agent.team.DefaultMemberSpawner(engine);
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());
        daemon.setMemberSpawner(spawner);

        daemon.scanOnce();

        // Wiring #23: the daemon really consulted the spawner, which really
        // invoked IAgentEngine.execute (not just returned a state change).
        assertEquals(1, engine.capturedRequests.size(),
                "spawner really invoked engine.execute (not a placeholder)");
        AgentMessageRequest captured = engine.capturedRequests.get(0);
        assertEquals("very-distinctive-agent-model", captured.getAgentName(),
                "spawned agent name = TeamMemberSpec.agentModel (not hardcoded)");
        assertEquals(a, captured.getMetadata().get("teamTaskId"),
                "spawned execution carries the team task id");
        assertEquals(team.getTeamId(), captured.getMetadata().get("teamId"),
                "spawned execution carries the team id");
        assertNotNull(captured.getSessionId(),
                "spawned execution carries a fresh session id");
        assertTrue(captured.getSessionId().startsWith("spawned-"),
                "spawned session id has the spawned- prefix (per-task spawn)");
    }

    // ========================================================================
    // Minimal recording IScheduledExecutor stub (lifecycle-wiring only; no
    // real scheduling — these focused tests call scanOnce directly).
    // ========================================================================
    static final class RecordingScheduler implements IScheduledExecutor, IDestroyable {
        final AtomicInteger scheduleCount = new AtomicInteger(0);

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            scheduleCount.incrementAndGet();
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
