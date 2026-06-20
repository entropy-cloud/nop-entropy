package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpMemberSpawner;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 243 (L4-spawn-step-async) end-to-end tests for the async spawn step.
 *
 * <p>Drives the full programmatic orchestration path end to end through
 * {@link TeamTaskFlowOrchestrator#executeAsync(String)}: graph build → async
 * spawn step (claim synchronously + spawn+complete offloaded to a dedicated
 * executor via supplyAsync + tenant propagation) → nop-task
 * {@link io.nop.task.step.GraphTaskStep} CompletableFuture scheduling →
 * spawn-node concurrency + dependency order → final {@link TeamTaskFlowResult}.
 *
 * <p><b>Anti-Hollow #22</b>: the diamond spawn DAG exercises real concurrency
 * (B and C spawn nodes overlap), not just final COMPLETED status.
 *
 * <p>Coverage map (maps to Phase 3 Exit Criteria):
 * <ul>
 *   <li>{@link #endToEndSpawnAsyncAllCompletedRealConcurrency} — diamond
 *       A→{B,C}→D spawn DAG via executeAsync: calling thread not blocked,
 *       B/C concurrent, D dependency-ordered, all COMPLETED.</li>
 *   <li>{@link #endToEndSpawnFailurePropagatesDSkippedBLeavesClaimed} — B's
 *       spawner returns SPAWN_FAILED → success=false, failed=B, skipped=D,
 *       B left CLAIMED.</li>
 *   <li>{@link #endToEndSpawnAndBoundMixedGraphConcurrent} — diamond where
 *       B is a bound-member node and C is a spawn node: both async node
 *       types coexist and run concurrently in independent branches.</li>
 *   <li>{@link #executeEqualsExecuteAsyncJoinOnSpawnDag} — sync execute ≡
 *       executeAsync().join() on the same spawn DAG.</li>
 * </ul>
 */
public class TestAsyncSpawnStepEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Spawner that sleeps briefly (widening the concurrency window) and
     * records peak concurrency + intervals. Returns a completed DISPATCHED
     * result.
     */
    static final class SlowCompletedSpawner implements IMemberSpawner {
        final AtomicInteger peakConcurrent = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final Map<String, Long> enterNano = new ConcurrentHashMap<>();
        final Map<String, Long> exitNano = new ConcurrentHashMap<>();

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            String taskId = request.getTask().getTaskId();
            int now = active.incrementAndGet();
            peakConcurrent.accumulateAndGet(now, Math::max);
            enterNano.put(taskId, System.nanoTime());
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                    throw new NopAiAgentException("interrupted", e);
            }
            active.decrementAndGet();
            exitNano.put(taskId, System.nanoTime());
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null),
                    "worker-agent-model", "spawned-" + taskId);
        }
    }

    /**
     * Spawner that returns a fixed result for a specific failing task and a
     * completed DISPATCHED result for all others (used for failure-propagation
     * E2E).
     */
    static final class FailOneSpawner implements IMemberSpawner {
        final String failTaskId;
        final SpawnMemberResult failResult;

        FailOneSpawner(String failTaskId, SpawnMemberResult failResult) {
            this.failTaskId = failTaskId;
            this.failResult = failResult;
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            if (failTaskId.equals(request.getTask().getTaskId())) {
                return failResult;
            }
            String taskId = request.getTask().getTaskId();
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null),
                    "worker-agent-model", "spawned-" + taskId);
        }
    }

    /**
     * Minimal engine mock for the mixed-graph test (bound-member node B uses
     * it). Records execute calls and returns completed.
     */
    static final class RecordingAgentEngine implements IAgentEngine {
        final List<AgentMessageRequest> captured = Collections.synchronizedList(new ArrayList<>());

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            captured.add(request);
            String taskId = (String) request.getMetadata().get("teamTaskId");
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                            Collections.emptyList(), 1, 10L, 1L, null));
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
    // 1. E2E spawn async full path: diamond spawn DAG, real concurrency, all COMPLETED.
    // ========================================================================

    @Test
    void endToEndSpawnAsyncAllCompletedRealConcurrency() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        SlowCompletedSpawner spawner = new SlowCompletedSpawner();
        Team team = createTeamWithoutBoundMember(mgr, "e2e-spawn-team");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            CompletableFuture<TeamTaskFlowResult> future = orchestrator.executeAsync(teamId);

            // Anti-Hollow: the calling thread is NOT blocked — the spawn work
            // is offloaded to the dedicated executor, so the future is
            // initially not done (the spawner sleeps 80ms).
            assertFalse(future.isDone(),
                    "executeAsync must NOT block the calling thread — spawn work offloaded "
                            + "to a dedicated executor (future initially incomplete)");

            TeamTaskFlowResult result = future.get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(),
                    "diamond spawn DAG completes via async spawn: " + result);
            assertEquals(4, result.getCompletedTaskIds().size());
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                        "task " + id + " COMPLETED via async spawn");
            }

            // Anti-Hollow #22 — real concurrency evidence for spawn nodes.
            assertTrue(spawner.peakConcurrent.get() >= 2,
                    "B and C spawn nodes truly concurrent — peak=" + spawner.peakConcurrent.get());
            boolean overlap = spawner.enterNano.get(b) < spawner.exitNano.get(c)
                    && spawner.enterNano.get(c) < spawner.exitNano.get(b);
            assertTrue(overlap, "B and C spawnMember intervals overlap (real concurrency)");

            // Dependency order: D after both B and C.
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(b),
                    "D starts after B completes");
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(c),
                    "D starts after C completes");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 2. E2E async honest failure propagation: B's spawner returns SPAWN_FAILED
    //    → success=false, failed=B, skipped=D, B left CLAIMED.
    // ========================================================================

    @Test
    void endToEndSpawnFailurePropagatesDSkippedBLeavesClaimed() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithoutBoundMember(mgr, "e2e-spawn-fail-team");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        // B's spawner returns SPAWN_FAILED; A and C succeed.
        FailOneSpawner spawner = new FailOneSpawner(b,
                SpawnMemberResult.spawnFailed("spawned agent threw: boom"));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(),
                    "B SPAWN_FAILED must honestly fail the whole DAG: " + result);
            // B is the failed task; D (which depends on B) is skipped.
            assertEquals(b, result.getFailedTaskId(), "B reported as failed");
            assertTrue(result.getSkippedTaskIds().contains(d),
                    "D skipped (depends on failed B) — nop-task short-circuit");
            // B left CLAIMED (not abandoned) — orchestrator's failure model.
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(b).orElseThrow().getStatus(),
                    "B left CLAIMED (not abandoned)");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 3. E2E spawn + bound mixed graph: B bound-member, C spawn — both async
    //    node types coexist and run concurrently in independent branches.
    // ========================================================================

    @Test
    void endToEndSpawnAndBoundMixedGraphConcurrent() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        // A spawner that records spawn concurrency (C is the spawn node).
        SlowCompletedSpawner spawner = new SlowCompletedSpawner();
        Team team = createTeamWithoutBoundMember(mgr, "e2e-mixed-team");
        String teamId = team.getTeamId();

        // Build the diamond. Task B is pre-tagged with claimedBy so the
        // orchestrator resolves it to a BOUND MemberAgentTaskStep (via the
        // claimedBy branch of resolveMember), while A, C, D have no bound
        // member and resolve to spawn nodes.
        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        // Wrap the store so getTasksByTeam returns B with claimedBy pre-set
        // (CREATED status preserved). This selects a MemberAgentTaskStep for
        // B (bound path via claimedBy) and a SpawnMemberAgentTaskStep for C
        // (no bound member) — a mixed graph.
        ClaimingBStore mixedStore = new ClaimingBStore(store, b, "bound-b-session");

        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, mixedStore, mgr, null, spawner);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(),
                    "mixed spawn+bound DAG completes: " + result);
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                        "task " + id + " COMPLETED");
            }
            // Wiring: B (bound) went through the engine; A, C, D (spawn) went
            // through the spawner.
            assertTrue(engine.captured.stream().anyMatch(
                            r -> "bound-b-session".equals(r.getSessionId())),
                    "B dispatched via the bound (engine) path — MemberAgentTaskStep ran");
            // The spawn nodes A, C, D ran through the spawner (concurrency
            // recorded). The key Anti-Hollow point: B (bound async) and C
            // (spawn async) coexisted in independent branches and both
            // completed.
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus(),
                    "bound node B completed");
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus(),
                    "spawn node C completed");
            // Dependency order: D after both B and C.
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(b),
                    "D starts after B (bound) completes");
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(c),
                    "D starts after C (spawn) completes");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 4. Sync execute ≡ executeAsync().join() on the same spawn DAG.
    // ========================================================================

    @Test
    void executeEqualsExecuteAsyncJoinOnSpawnDag() {
        // Two identical spawn DAGs: one via execute(), one via executeAsync().
        InMemoryTeamManager mgrA = new InMemoryTeamManager();
        InMemoryTeamTaskStore storeA = new InMemoryTeamTaskStore();
        Team teamA = createTeamWithoutBoundMember(mgrA, "equiv-sync-team");
        String tidA = teamA.getTeamId();
        String aA = createTask(storeA, tidA, "A", Collections.emptyList());
        String bA = createTask(storeA, tidA, "B", Collections.singletonList(aA));
        createTask(storeA, tidA, "C", Collections.singletonList(aA));
        TeamTaskFlowOrchestrator orchA =
                new TeamTaskFlowOrchestrator(null, storeA, mgrA, null, new SlowCompletedSpawner());

        InMemoryTeamManager mgrB = new InMemoryTeamManager();
        InMemoryTeamTaskStore storeB = new InMemoryTeamTaskStore();
        Team teamB = createTeamWithoutBoundMember(mgrB, "equiv-async-team");
        String tidB = teamB.getTeamId();
        String aB = createTask(storeB, tidB, "A", Collections.emptyList());
        createTask(storeB, tidB, "B", Collections.singletonList(aB));
        createTask(storeB, tidB, "C", Collections.singletonList(aB));
        TeamTaskFlowOrchestrator orchB =
                new TeamTaskFlowOrchestrator(null, storeB, mgrB, null, new SlowCompletedSpawner());

        try {
            TeamTaskFlowResult syncResult = orchA.execute(tidA);
            TeamTaskFlowResult asyncResult = orchB.executeAsync(tidB).join();

            assertTrue(syncResult.isSuccess(), "sync success: " + syncResult);
            assertTrue(asyncResult.isSuccess(), "async success: " + asyncResult);
            assertEquals(syncResult.getCompletedTaskIds().size(), asyncResult.getCompletedTaskIds().size(),
                    "sync and async complete the same number of tasks (semantic equivalence)");
            // All tasks COMPLETED in both.
            assertEquals(TeamTaskStatus.COMPLETED, storeA.getTask(bA).orElseThrow().getStatus());
        } finally {
            orchA.close();
            orchB.close();
        }
    }

    /**
     * Store wrapper that returns a given task with {@code claimedBy} pre-set
     * (CREATED status preserved) from {@code getTasksByTeam}, so the
     * orchestrator's {@code resolveMember} selects a bound
     * {@link MemberAgentTaskStep} for that task while other tasks (no bound
     * member) select spawn nodes — producing a mixed spawn+bound graph.
     */
    static final class ClaimingBStore implements ITeamTaskStore {
        private final InMemoryTeamTaskStore delegate;
        private final String claimedTaskId;
        private final String claimedBy;

        ClaimingBStore(InMemoryTeamTaskStore delegate, String claimedTaskId, String claimedBy) {
            this.delegate = delegate;
            this.claimedTaskId = claimedTaskId;
            this.claimedBy = claimedBy;
        }

        private TeamTask maybeReclaim(TeamTask t) {
            if (t != null && t.getTaskId().equals(claimedTaskId)
                    && t.getStatus() == TeamTaskStatus.CREATED) {
                return new TeamTask(t.getTaskId(), t.getTeamId(), t.getSubject(), t.getDescription(),
                        t.getBlockedBy(), t.getStatus(), t.getCreatedBy(), claimedBy, null, t.getCreatedAt());
            }
            return t;
        }

        @Override
        public TeamTask createTask(String t, String s, String d, List<String> b, String c) {
            return delegate.createTask(t, s, d, b, c);
        }

        @Override
        public Optional<TeamTask> getTask(String taskId) {
            return delegate.getTask(taskId).map(this::maybeReclaim);
        }

        @Override
        public List<TeamTask> getTasksByTeam(String tid) {
            List<TeamTask> list = new ArrayList<>();
            for (TeamTask t : delegate.getTasksByTeam(tid)) {
                list.add(maybeReclaim(t));
            }
            return list;
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
}
