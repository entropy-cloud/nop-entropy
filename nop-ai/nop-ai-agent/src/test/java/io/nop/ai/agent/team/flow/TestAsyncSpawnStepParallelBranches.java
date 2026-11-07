package io.nop.ai.agent.team.flow;

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
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 243 (L4-spawn-step-async) focused tests for <b>real concurrency</b>
 * between spawn-on-demand nodes in the async team-task DAG.
 *
 * <p><b>Anti-Hollow #22 verification</b>: the diamond DAG A→{B,C}→D where
 * <b>both B and C are spawn nodes</b> (no bound member) exercised through
 * {@link TeamTaskFlowOrchestrator#executeAsync(String)} exhibits <b>real
 * concurrency</b> between B and C (no {@code blockedBy} edge between them)
 * and <b>strict dependency ordering</b> for D (D blockedBy {B, C}). Pre-243
 * the spawn step synchronously blocked the DAG scheduler thread, so B and C
 * serialized (peak concurrent = 1). Post-243 the spawn work is offloaded to
 * a dedicated executor via {@code supplyAsync}, so B and C truly overlap
 * (peak concurrent ≥ 2). The test captures observable concurrency evidence
 * (overlapping spawnMember intervals + peak-concurrent count) rather than
 * relying on final COMPLETED status alone.
 *
 * <p>Coverage map (maps to Phase 2 Exit Criteria):
 * <ul>
 *   <li>{@link #diamondSpawnBranchesRunConcurrentlyAndDAfterBoth} — B and C
 *       spawnMember calls overlap in time (real concurrency) and D strictly
 *       follows both B and C (dependency order).</li>
 *   <li>{@link #diamondSpawnDependencyOrderStrict} — D.start strictly later
 *       than B.complete and C.complete (proves nop-task {@code waitSteps} is
 *       enforced for parallel spawn branches under async scheduling).</li>
 * </ul>
 */
public class TestAsyncSpawnStepParallelBranches {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Spawner that records per-task wall-clock intervals of
     * {@code spawnMember} and sleeps briefly to make the concurrency window
     * observable. Each spawnMember call: increment active counter + update
     * peak → record enter time → sleep → decrement active → record exit time.
     * Returns a completed DISPATCHED result (no engine needed — this tests
     * the spawn step's own concurrency, not the engine's).
     */
    static final class ConcurrencyRecordingSpawner implements IMemberSpawner {
        final Map<String, Long> enterNanoTime = new ConcurrentHashMap<>();
        final Map<String, Long> exitNanoTime = new ConcurrentHashMap<>();
        final Map<String, Integer> startOrder = new ConcurrentHashMap<>();
        final Map<String, Integer> completionOrder = new ConcurrentHashMap<>();
        final AtomicInteger startSeq = new AtomicInteger();
        final AtomicInteger completionSeq = new AtomicInteger();
        final AtomicInteger peakConcurrent = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
    /**
     * 时间确定化（plan 2511-WI5）：并发重叠证据由显式 rendezvous 产生——同一轮的
     * 各执行在共享 {@link java.util.concurrent.CyclicBarrier} 上等待，直到全轮
     * 进入（结构性并发证明），不再用真实毫秒 sleep 制造时间窗。超时仅挂死保护。
     */
    static final class RoundRendezvous {
        final Map<String, String> taskRound = new ConcurrentHashMap<>();
        final Map<String, Integer> roundParties = new ConcurrentHashMap<>();
        final Map<String, java.util.concurrent.CyclicBarrier> barriers = new ConcurrentHashMap<>();

        void expectConcurrent(String roundKey, int parties, String... taskIds) {
            roundParties.put(roundKey, parties);
            for (String id : taskIds) {
                taskRound.put(id, roundKey);
            }
        }

        void awaitRound(String taskId) {
            String roundKey = taskRound.get(taskId);
            if (roundKey == null) {
                return;
            }
            java.util.concurrent.CyclicBarrier barrier = barriers.computeIfAbsent(roundKey,
                    k -> new java.util.concurrent.CyclicBarrier(roundParties.get(k)));
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NopAiAgentException("interrupted while awaiting round rendezvous", e);
            } catch (Exception e) {
                throw new NopAiAgentException("round rendezvous broken", e);
            }
        }
    }
        final RoundRendezvous rendezvous = new RoundRendezvous();

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            String taskId = request.getTask().getTaskId();
            int nowActive = active.incrementAndGet();
            peakConcurrent.accumulateAndGet(nowActive, Math::max);
            enterNanoTime.put(taskId, System.nanoTime());
            startOrder.put(taskId, startSeq.incrementAndGet());
            // 结构性并发 rendezvous（时间确定化）：同轮执行全部进入后一起放行
            rendezvous.awaitRound(taskId);
            active.decrementAndGet();
            exitNanoTime.put(taskId, System.nanoTime());
            completionOrder.put(taskId, completionSeq.incrementAndGet());
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null),
                    "worker-agent-model", "spawned-" + taskId);
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Team createTeamWithoutBoundMember(InMemoryTeamManager mgr) {
        TeamSpec spec = new TeamSpec("spawn-parallel-team", "test", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent-model", MemberRole.MEMBER)),
                0);
        return mgr.createTeam(spec);
        // Deliberately NOT calling mgr.bindMemberSession(...).
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId,
                                      String subject, java.util.List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session").getTaskId();
    }

    // ========================================================================
    // 1. Diamond A → {B, C} → D: B and C spawn nodes run concurrently; D after both.
    // ========================================================================

    @Test
    void diamondSpawnBranchesRunConcurrentlyAndDAfterBoth() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingSpawner spawner = new ConcurrencyRecordingSpawner();
        Team team = createTeamWithoutBoundMember(mgr);
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        // 时间确定化（plan 2511-WI5）：B/C 并发重叠由 rendezvous 结构性保证
        spawner.rendezvous.expectConcurrent("diamond-spawn-bc", 2, b, c);

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            CompletableFuture<TeamTaskFlowResult> future = orchestrator.executeAsync(teamId);
            TeamTaskFlowResult result = future.get(20, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(),
                    "diamond spawn DAG should complete successfully: " + result);
            assertEquals(4, result.getCompletedTaskIds().size(),
                    "all four spawn tasks completed: " + result);
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                        "task " + id + " COMPLETED via async spawn (no manual bind)");
            }

            // Anti-Hollow #22 — REAL CONCURRENCY EVIDENCE for spawn nodes:
            // The peak number of simultaneously-active spawnMember() calls
            // must be >= 2 (proving B and C overlapped). Pre-243 the spawn
            // step synchronously blocked the scheduler thread, so peak would
            // be 1 (B fully completes before C starts).
            assertTrue(spawner.peakConcurrent.get() >= 2,
                    "B and C spawn nodes MUST run concurrently — peak concurrent spawnMember()="
                            + spawner.peakConcurrent.get()
                            + " (pre-243 sync spawn step would yield 1)");

            // Stronger: the spawnMember intervals of B and C actually overlap.
            long bEnter = spawner.enterNanoTime.get(b);
            long bExit = spawner.exitNanoTime.get(b);
            long cEnter = spawner.enterNanoTime.get(c);
            long cExit = spawner.exitNanoTime.get(c);
            boolean overlap = (bEnter < cExit) && (cEnter < bExit);
            assertTrue(overlap,
                    "B and C spawnMember intervals MUST overlap (real concurrency): "
                            + "B[" + bEnter + "," + bExit + "], C[" + cEnter + "," + cExit + "]");

            // Anti-Hollow — DEPENDENCY ORDER STRICT for parallel spawn branches:
            // D started strictly AFTER both B and C completed.
            int completionB = spawner.completionOrder.get(b);
            int completionC = spawner.completionOrder.get(c);
            int startD = spawner.startOrder.get(d);
            assertTrue(startD > completionB,
                    "D must start AFTER B completes: startD=" + startD + ", completionB=" + completionB);
            assertTrue(startD > completionC,
                    "D must start AFTER C completes: startD=" + startD + ", completionC=" + completionC);
        } finally {
            orchestrator.close();
        }
    }

    @Test
    void diamondSpawnDependencyOrderStrict() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingSpawner spawner = new ConcurrencyRecordingSpawner();
        Team team = createTeamWithoutBoundMember(mgr);
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(20, TimeUnit.SECONDS);

            // D started strictly AFTER both B and C completed (nop-task
            // waitSteps enforced under async spawn scheduling).
            int completionA = result.getCompletionOrder().get(a);
            int startB = result.getStartOrder().get(b);
            int startC = result.getStartOrder().get(c);
            int completionB = result.getCompletionOrder().get(b);
            int completionC = result.getCompletionOrder().get(c);
            int startD = result.getStartOrder().get(d);

            assertTrue(startB > completionA, "B starts after A completes");
            assertTrue(startC > completionA, "C starts after A completes");
            assertTrue(startD > completionB, "D starts after B completes");
            assertTrue(startD > completionC, "D starts after C completes");
        } finally {
            orchestrator.close();
        }
    }
}
