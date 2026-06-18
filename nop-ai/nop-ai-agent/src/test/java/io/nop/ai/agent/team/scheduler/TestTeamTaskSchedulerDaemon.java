package io.nop.ai.agent.team.scheduler;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 236 Phase 1 focused tests for {@link TeamTaskSchedulerDaemon}.
 *
 * <p>Coverage map (maps 1:1 to Phase 1 Exit Criteria):
 * <ul>
 *   <li>{@link #singleScanResolvesLinearReadySetAndFiltersToCreated} — 单周期
 *       就绪解析（线性依赖，过滤至 CREATED）</li>
 *   <li>{@link #singleScanResolvesDiamondReadySet} — 单周期就绪解析（菱形依赖）</li>
 *   <li>{@link #casIdempotentMultiScanDoesNotRedispatch} — CAS idempotent（多周期
 *       扫描不重复派发）</li>
 *   <li>{@link #casClaimLossRecordedAsClaimLostNotAbandoned} — CAS 认领失败 =
 *       合法并发，不 abandon</li>
 *   <li>{@link #dependencyOrderAutoAdvancesAcrossScans} — 依赖序自动推进（A 完成
 *       后下周期 B 就绪）</li>
 *   <li>{@link #claimedTasksFromTopologyAreSkippedNotAbandoned} — CLAIMED 他人任务
 *       不被误弃（getReadyTasks 含 CLAIMED 时跳过不 abandon）</li>
 *   <li>{@link #stopCancelsPeriodicScheduleSoNewTasksNotAutoDispatched} — 生命周期
 *       start/stop（stop 后不再派发新任务）</li>
 *   <li>{@link #startAndStopAreIdempotent} / {@link #stopBeforeStartIsNoOp} —
 *       生命周期幂等性</li>
 *   <li>{@link #emptyTeamIdleIsLegitimate} / {@link #noCreatedReadyTasksIdleIsLegitimate}
 *       — 空团队 / 无 CREATED 就绪任务诚实空转（合法正常状态）</li>
 *   <li>{@link #unboundMemberAbandonsClaimedTaskNoSilentSkip} — 未绑定成员快速失败
 *       （No Silent No-Op）—— 与「无就绪任务空转」相区分</li>
 *   <li>{@link #dispatchFailureByExceptionAbandonsClaimedTask} / {@link #dispatchFailureByNonCompletedStatusAbandonsClaimedTask}
 *       — 派发失败诚实 abandon（不静默跳过）</li>
 *   <li>{@link #wiringDaemonInvokesReadyQueryClaimExecuteComplete} — 接线验证
 *       （#23）：守护进程确实调用 getReadyTasks + claimTask + IAgentEngine.execute +
 *       completeTask</li>
 *   <li>{@link #constructorValidatesArguments} — 输入契约</li>
 *   <li>{@link #noOpDaemonReturnsAllZeroResult} — NoOp 默认语义（零回归）</li>
 *   <li>{@link #targetTeamScopeRestrictsScan} — 团队范围可配置</li>
 * </ul>
 */
public class TestTeamTaskSchedulerDaemon {

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
     * Records each invocation's task id (via the {@code teamTaskId} metadata)
     * and start sequence. Configurable to fail on a specific task id either
     * by exception or by returning a non-completed terminal status.
     */
    static final class RecordingAgentEngine implements IAgentEngine {
        final List<String> invocationOrder = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Integer> startSeq = new java.util.concurrent.ConcurrentHashMap<>();
        final AtomicInteger seq = new AtomicInteger(0);
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
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            String taskId = (String) request.getMetadata().get("teamTaskId");
            invocationOrder.add(taskId);
            startSeq.put(taskId, seq.incrementAndGet());

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
        TeamSpec spec = new TeamSpec("daemon-team", "d", "lead-agent",
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

    // ========================================================================
    // 1. Single-scan ready resolution (linear + diamond) + filter to CREATED
    // ========================================================================

    @Test
    void singleScanResolvesLinearReadySetAndFiltersToCreated() {
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

        // First scan: only A is ready (B/C blocked). A should be claimed +
        // dispatched + completed in one scan.
        SchedulerScanResult r1 = daemon.scanOnce();

        assertEquals(1, r1.getReadyCreatedTasks(),
                "only A is CREATED + ready (B/C blocked by uncompleted deps)");
        assertEquals(1, r1.getClaimedTasks());
        assertEquals(0, r1.getClaimLostTasks());
        assertEquals(1, r1.getDispatchedTasks());
        assertEquals(1, r1.getCompletedTasks());
        assertEquals(0, r1.getAbandonedTasks());
        assertEquals(Collections.singletonList(a), r1.getCompletedTaskIds());

        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertNotEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus(),
                "B not yet ready (blocked by A which is now done, but this scan already ran)");
        assertEquals(TeamTaskStatus.CREATED, store.getTask(c).orElseThrow().getStatus());
    }

    @Test
    void singleScanResolvesDiamondReadySet() {
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

        // First scan: only A ready (B/C/D blocked). A completes.
        SchedulerScanResult r1 = daemon.scanOnce();
        assertEquals(1, r1.getReadyCreatedTasks());
        assertEquals(1, r1.getCompletedTasks());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
    }

    // ========================================================================
    // 2. CAS idempotent — multi-scan never re-dispatches the same task
    // ========================================================================

    @Test
    void casIdempotentMultiScanDoesNotRedispatch() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());

        daemon.scanOnce();
        int invocationsAfterFirst = engine.invocationOrder.size();
        assertEquals(1, invocationsAfterFirst, "A dispatched once in scan 1");

        // Second scan: A is COMPLETED → excluded from ready set → not re-dispatched.
        SchedulerScanResult r2 = daemon.scanOnce();
        assertEquals(0, r2.getReadyCreatedTasks(), "no CREATED ready tasks remain (A is COMPLETED)");
        assertEquals(0, r2.getClaimedTasks());
        assertEquals(0, r2.getCompletedTasks());
        assertEquals(invocationsAfterFirst, engine.invocationOrder.size(),
                "engine.execute NOT invoked again — CAS idempotent across scans");

        // Third scan: still no re-dispatch.
        daemon.scanOnce();
        assertEquals(invocationsAfterFirst, engine.invocationOrder.size(),
                "still no re-dispatch after scan 3");
    }

    @Test
    void casClaimLossRecordedAsClaimLostNotAbandoned() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());

        // A store wrapper that makes the FIRST claim attempt on A lose the CAS
        // (simulating another claimer raced ahead), then succeed on retry.
        java.util.concurrent.atomic.AtomicInteger claimAttempts = new java.util.concurrent.atomic.AtomicInteger();
        io.nop.ai.agent.team.ITeamTaskStore racingStore = new io.nop.ai.agent.team.ITeamTaskStore() {
            @Override
            public io.nop.ai.agent.team.TeamTask createTask(String t, String s, String d, List<String> b, String c) {
                return store.createTask(t, s, d, b, c);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> getTask(String taskId) {
                return store.getTask(taskId);
            }

            @Override
            public List<io.nop.ai.agent.team.TeamTask> getTasksByTeam(String tid) {
                return store.getTasksByTeam(tid);
            }

            @Override
            public List<io.nop.ai.agent.team.TeamTask> getTasksByCreator(String c) {
                return store.getTasksByCreator(c);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> claimTask(String taskId, String by) {
                // First claim on A returns empty (lost race); subsequent succeed.
                if (taskId.equals(a) && claimAttempts.incrementAndGet() == 1) {
                    return java.util.Optional.empty();
                }
                return store.claimTask(taskId, by);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> completeTask(String t, String b) {
                return store.completeTask(t, b);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> abandonTask(String t, String b) {
                return store.abandonTask(t, b);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> reclaimTask(String t, String b) {
                return store.reclaimTask(t, b);
            }
        };

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, racingStore, mgr, new RecordingScheduler());

        SchedulerScanResult r = daemon.scanOnce();
        assertEquals(1, r.getClaimLostTasks(),
                "CAS loss on A recorded as claimLost (legitimate concurrency)");
        assertEquals(0, r.getClaimedTasks(), "no claim succeeded this scan");
        assertEquals(0, r.getAbandonedTasks(),
                "CAS-lost task NOT abandoned (daemon never owned it)");
        assertEquals(TeamTaskStatus.CREATED, store.getTask(a).orElseThrow().getStatus(),
                "A still CREATED — not mutated by a CAS loss");

        // Second scan: claim succeeds this time, A completes.
        SchedulerScanResult r2 = daemon.scanOnce();
        assertEquals(1, r2.getClaimedTasks());
        assertEquals(1, r2.getCompletedTasks());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
    }

    // ========================================================================
    // 3. Dependency order auto-advances across scans
    // ========================================================================

    @Test
    void dependencyOrderAutoAdvancesAcrossScans() {
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

        // scan 1: A ready → A completed. B/C still blocked.
        daemon.scanOnce();
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.CREATED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.CREATED, store.getTask(c).orElseThrow().getStatus());

        // scan 2: B now ready (A completed) → B completed. C still blocked.
        SchedulerScanResult r2 = daemon.scanOnce();
        assertEquals(1, r2.getReadyCreatedTasks(), "B is now the only CREATED ready task");
        assertEquals(1, r2.getCompletedTasks());
        assertTrue(r2.getCompletedTaskIds().contains(b));
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.CREATED, store.getTask(c).orElseThrow().getStatus());

        // scan 3: C now ready → C completed. Whole DAG done.
        SchedulerScanResult r3 = daemon.scanOnce();
        assertTrue(r3.getCompletedTaskIds().contains(c));
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus());

        // Dependency order enforced via ready-query gating: B's dispatch never
        // preceded A's completion, C's dispatch never preceded B's completion.
        // (Within each scan, only the unblocked tasks are even considered.)
        int startA = engine.startSeq.get(a);
        int startB = engine.startSeq.get(b);
        int startC = engine.startSeq.get(c);
        assertTrue(startB > startA, "B dispatched after A (different scans)");
        assertTrue(startC > startB, "C dispatched after B (different scans)");
    }

    // ========================================================================
    // 4. CLAIMED tasks (other members' in-progress) are skipped, never abandoned
    // ========================================================================

    @Test
    void claimedTasksFromTopologyAreSkippedNotAbandoned() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        // Pre-claim A by SOMEONE ELSE (simulating another member/daemon racing ahead).
        store.claimTask(a, "another-member-session");
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus());

        // Verify the topology DOES return CLAIMED A as ready (deps satisfied,
        // non-terminal) — this is the precondition for the safety check.
        assertTrue(new io.nop.ai.agent.team.flow.TeamTaskTopology(store.getTasksByTeam(teamId))
                .getReadyTasks().stream().anyMatch(t -> t.getTaskId().equals(a)),
                "precondition: topology.getReadyTasks() includes the CLAIMED task A");

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());

        SchedulerScanResult r = daemon.scanOnce();
        assertEquals(0, r.getReadyCreatedTasks(),
                "CLAIMED A is filtered out (only CREATED ready tasks counted)");
        assertEquals(0, r.getClaimedTasks(), "daemon did NOT try to claim CLAIMED A");
        assertEquals(0, r.getAbandonedTasks(),
                "CLAIMED A NOT abandoned by the daemon (critical safety constraint)");
        assertEquals(0, engine.invocationOrder.size(),
                "engine.execute NOT invoked for CLAIMED A");

        // The other member's in-progress task is untouched.
        TeamTask aAfter = store.getTask(a).orElseThrow();
        assertEquals(TeamTaskStatus.CLAIMED, aAfter.getStatus(),
                "A still CLAIMED — daemon neither stole nor abandoned it");
        assertEquals("another-member-session", aAfter.getClaimedBy(),
                "A's claimedBy unchanged — daemon did not interfere");
    }

    // ========================================================================
    // 5. Lifecycle start / stop — periodic schedule cancellation
    // ========================================================================

    @Test
    void stopCancelsPeriodicScheduleSoNewTasksNotAutoDispatched() {
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
        assertEquals(1, scheduler.scheduleCount.get(),
                "start() registered exactly one periodic task");
        assertEquals((Long) TeamTaskSchedulerDaemon.DEFAULT_SCAN_INTERVAL_SEC,
                scheduler.lastDelay.get(),
                "scanIntervalSec propagated to scheduler");

        // Simulate ONE scheduled tick: the task A is dispatched + completed.
        scheduler.lastCommand.get().run();
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "the scheduled tick dispatched and completed A");

        // Create a NEW task B that would be ready in the next tick.
        String b = createTask(store, teamId, "B", Collections.emptyList());
        assertEquals(TeamTaskStatus.CREATED, store.getTask(b).orElseThrow().getStatus());

        daemon.stop();
        assertTrue(scheduler.cancelled.get(), "stop() cancelled the registered Future");
        assertFalse(scheduler.mayInterruptIfRunning,
                "stop() is graceful (mayInterruptIfRunning=false) — in-progress tasks not interrupted");

        // With the schedule cancelled, the executor will not invoke the
        // periodic task again. The new task B remains CREATED (not dispatched).
        // (We do NOT manually invoke scheduler.lastCommand here — that would
        // simulate a tick that the cancelled schedule correctly prevents.)
        assertEquals(TeamTaskStatus.CREATED, store.getTask(b).orElseThrow().getStatus(),
                "new task B NOT dispatched after stop (periodic schedule cancelled)");
        assertFalse(engine.invocationOrder.contains(b),
                "engine.execute never invoked for B after stop");

        // scanOnce remains available for explicit on-demand scans (lifecycle-
        // independent), but the AUTOMATIC periodic dispatch is stopped.
    }

    @Test
    void startAndStopAreIdempotent() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        createTeamWithBoundMember(mgr, "worker", "worker-session");

        RecordingScheduler scheduler = new RecordingScheduler();
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, scheduler);

        daemon.start();
        daemon.start();
        assertEquals(1, scheduler.scheduleCount.get(),
                "double start → exactly one scheduleWithFixedDelay call");

        daemon.stop();
        assertTrue(scheduler.cancelled.get());
        boolean firstCancelFlag = scheduler.cancelled.get();

        daemon.stop();
        assertEquals(firstCancelFlag, scheduler.cancelled.get(),
                "double stop → no additional effect (still cancelled, idempotent)");
    }

    @Test
    void stopBeforeStartIsNoOp() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        createTeamWithBoundMember(mgr, "worker", "worker-session");

        RecordingScheduler scheduler = new RecordingScheduler();
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, scheduler);

        daemon.stop();
        assertFalse(scheduler.cancelled.get(),
                "stop() before start() is a no-op (nothing to cancel)");
        assertEquals(0, scheduler.scheduleCount.get());
    }

    // ========================================================================
    // 6. Empty team / no CREATED ready tasks — legitimate idle (not silent skip)
    // ========================================================================

    @Test
    void emptyTeamIdleIsLegitimate() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        createTeamWithBoundMember(mgr, "worker", "worker-session");

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());

        SchedulerScanResult r = daemon.scanOnce();
        assertEquals(0, r.getReadyCreatedTasks(), "no tasks → no ready tasks");
        assertEquals(0, r.getClaimedTasks());
        assertEquals(0, r.getCompletedTasks());
        assertEquals(0, r.getAbandonedTasks(),
                "empty team is a legitimate idle state, not an abandoned-task signal");
        assertEquals(0, engine.invocationOrder.size());
    }

    @Test
    void noCreatedReadyTasksIdleIsLegitimate() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();

        // Task A has an UNSATISFIED blockedBy (B does not exist) — it is NOT
        // ready. But wait: dangling blockedBy references are ignored by the
        // topology, so A with blockedBy=["nonexistent"] WOULD be ready. Use
        // a real unsatisfied dep instead: create B after A, A blockedBy B.
        String b = createTask(store, teamId, "B", Collections.emptyList());
        String a = createTask(store, teamId, "A", Collections.singletonList(b));
        // Now A is blocked by B (CREATED, not completed) → A not ready.
        // B is ready + CREATED.

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());

        SchedulerScanResult r1 = daemon.scanOnce();
        // B ready+CREATED → dispatched + completed. A still blocked.
        assertEquals(1, r1.getCompletedTasks());
        assertTrue(r1.getCompletedTaskIds().contains(b));
        assertEquals(TeamTaskStatus.CREATED, store.getTask(a).orElseThrow().getStatus(),
                "A still CREATED (blocked by B in this scan's snapshot)");

        // Now B is COMPLETED. Run another scan: A becomes ready.
        // But to test "no CREATED ready tasks" idle, complete A manually out
        // of band then scan — nothing left to do.
        // (Already B is done; scan again to complete A.)
        daemon.scanOnce();
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());

        // Now everything is COMPLETED — a legitimate idle scan.
        SchedulerScanResult r3 = daemon.scanOnce();
        assertEquals(0, r3.getReadyCreatedTasks(), "no CREATED ready tasks remain");
        assertEquals(0, r3.getCompletedTasks());
        assertEquals(0, r3.getAbandonedTasks(),
                "idle scan with no work is legitimate, not an abandoned-task signal");
    }

    // ========================================================================
    // 7. Unbound member — fast-failure (abandon), No Silent No-Op
    //    (distinct from legitimate idle)
    // ========================================================================

    @Test
    void unboundMemberAbandonsClaimedTaskNoSilentSkip() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();

        // Team with NO bound members.
        TeamSpec spec = new TeamSpec("unbound-team", "d", "lead-agent",
                Collections.singletonList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD)),
                0);
        Team team = mgr.createTeam(spec);
        String a = createTask(store, team.getTeamId(), "A", Collections.emptyList());

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());

        SchedulerScanResult r = daemon.scanOnce();
        assertEquals(1, r.getReadyCreatedTasks(), "A is ready + CREATED");
        assertEquals(1, r.getClaimedTasks(), "daemon claimed A (CREATED → CLAIMED)");
        assertEquals(1, r.getAbandonedTasks(),
                "unbound member → task abandoned (honest failure, not silent skip)");
        assertTrue(r.getAbandonedTaskIds().contains(a));
        assertEquals(0, r.getDispatchedTasks(),
                "engine.execute NOT invoked (no member to dispatch to)");
        assertEquals(0, engine.invocationOrder.size());

        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus(),
                "A transitioned CLAIMED → ABANDONED by the daemon");
    }

    // ========================================================================
    // 8. Dispatch failure (exception / non-completed) → honest abandon
    // ========================================================================

    @Test
    void dispatchFailureByExceptionAbandonsClaimedTask() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String a = createTask(store, team.getTeamId(), "A", Collections.emptyList());

        // Engine throws on A.
        RecordingAgentEngine engine = new RecordingAgentEngine(a, true);

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());

        SchedulerScanResult r = daemon.scanOnce();
        assertEquals(1, r.getClaimedTasks());
        assertEquals(1, r.getDispatchedTasks(), "execute WAS invoked (then threw)");
        assertEquals(1, r.getAbandonedTasks(),
                "member-agent exception → task abandoned (not silently swallowed)");
        assertEquals(0, r.getCompletedTasks());
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus());
    }

    @Test
    void dispatchFailureByNonCompletedStatusAbandonsClaimedTask() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String a = createTask(store, team.getTeamId(), "A", Collections.emptyList());

        // Engine returns failed status on A.
        RecordingAgentEngine engine = new RecordingAgentEngine(a, false);

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());

        SchedulerScanResult r = daemon.scanOnce();
        assertEquals(1, r.getDispatchedTasks());
        assertEquals(1, r.getAbandonedTasks(),
                "non-completed terminal status → task abandoned (honest failure)");
        assertEquals(0, r.getCompletedTasks());
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus());
    }

    // ========================================================================
    // 9. Wiring verification (#23) — daemon really invokes ready query +
    //    claimTask + IAgentEngine.execute + completeTask (not just self-loop)
    // ========================================================================

    @Test
    void wiringDaemonInvokesReadyQueryClaimExecuteComplete() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-session");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler());

        SchedulerScanResult r = daemon.scanOnce();

        // Wiring: engine.execute actually invoked (with teamTaskId metadata).
        assertEquals(1, engine.invocationOrder.size());
        assertEquals(a, engine.invocationOrder.get(0),
                "engine.execute invoked for task A with teamTaskId metadata");

        // Wiring: claimTask actually mutated store state (CREATED → CLAIMED).
        // Wiring: completeTask actually mutated store state (CLAIMED → COMPLETED).
        // If completeTask had not been called, the task would be stuck in CLAIMED.
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "completeTask actually transitioned A to COMPLETED (store mutated, not type-only)");
        assertEquals(1, r.getCompletedTasks());

        // The claimedBy records the daemon session (audit trail).
        assertEquals(TeamTaskSchedulerDaemon.DEFAULT_DAEMON_SESSION_ID,
                store.getTask(a).orElseThrow().getClaimedBy(),
                "claimTask recorded daemon session id in claimedBy");
    }

    // ========================================================================
    // 10. Constructor input contract
    // ========================================================================

    @Test
    void constructorValidatesArguments() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        RecordingScheduler scheduler = new RecordingScheduler();

        assertThrows(NullPointerException.class,
                () -> new TeamTaskSchedulerDaemon(null, store, mgr, scheduler));
        assertThrows(NullPointerException.class,
                () -> new TeamTaskSchedulerDaemon(engine, null, mgr, scheduler));
        assertThrows(NullPointerException.class,
                () -> new TeamTaskSchedulerDaemon(engine, store, null, scheduler));
        assertThrows(NullPointerException.class,
                () -> new TeamTaskSchedulerDaemon(engine, store, mgr, null));
        assertThrows(NopAiAgentException.class,
                () -> new TeamTaskSchedulerDaemon(engine, store, mgr, scheduler,
                        0L, TeamTaskSchedulerDaemon.DEFAULT_DAEMON_SESSION_ID, null),
                "scanIntervalSec must be > 0");
        assertThrows(NopAiAgentException.class,
                () -> new TeamTaskSchedulerDaemon(engine, store, mgr, scheduler,
                        DEFAULT_SCAN_INTERVAL, "  ", null),
                "daemonSessionId must not be blank");
    }

    private static final long DEFAULT_SCAN_INTERVAL = TeamTaskSchedulerDaemon.DEFAULT_SCAN_INTERVAL_SEC;

    // ========================================================================
    // 11. NoOp default — zero regression
    // ========================================================================

    @Test
    void noOpDaemonReturnsAllZeroResult() {
        NoOpTeamTaskSchedulerDaemon noOp = NoOpTeamTaskSchedulerDaemon.noOp();
        // start/stop are no-ops (no scheduler wired — must not throw).
        noOp.start();
        noOp.stop();
        SchedulerScanResult r = noOp.scanOnce();
        assertEquals(0, r.getTeamsScanned());
        assertEquals(0, r.getReadyCreatedTasks());
        assertEquals(0, r.getCompletedTasks());
        assertEquals(0, r.getAbandonedTasks());
        assertTrue(r.getCompletedTaskIds().isEmpty());
    }

    // ========================================================================
    // 12. Target team scope restricts scan
    // ========================================================================

    @Test
    void targetTeamScopeRestrictsScan() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();

        Team team1 = createTeamWithBoundMember(mgr, "worker1", "s1");
        Team team2 = createTeamWithBoundMember(mgr, "worker2", "s2");
        String a1 = createTask(store, team1.getTeamId(), "A1", Collections.emptyList());
        String a2 = createTask(store, team2.getTeamId(), "A2", Collections.emptyList());

        // Daemon restricted to team1 only.
        TeamTaskSchedulerDaemon daemon = new TeamTaskSchedulerDaemon(
                engine, store, mgr, new RecordingScheduler(),
                DEFAULT_SCAN_INTERVAL, TeamTaskSchedulerDaemon.DEFAULT_DAEMON_SESSION_ID,
                Collections.singleton(team1.getTeamId()));

        SchedulerScanResult r = daemon.scanOnce();
        assertEquals(1, r.getTeamsScanned(), "only team1 scanned (scope restricted)");
        assertEquals(1, r.getCompletedTasks());
        assertTrue(r.getCompletedTaskIds().contains(a1));
        assertFalse(r.getCompletedTaskIds().contains(a2),
                "team2 task NOT dispatched (out of scope)");

        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a1).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.CREATED, store.getTask(a2).orElseThrow().getStatus(),
                "team2 task untouched");
    }

    // ========================================================================
    // Minimal recording IScheduledExecutor stub for lifecycle-wiring tests.
    // Records scheduleWithFixedDelay args and exposes a cancel-recording Future.
    // Does NOT actually schedule anything — tests invoke the recorded Runnable
    // manually to simulate a scheduled tick. Mirrors the pattern in
    // TestScheduledRecoveryManager.RecordingScheduler.
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
