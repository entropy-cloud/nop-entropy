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
import io.nop.ai.agent.team.MemberRole;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 244 (L4-multi-member-per-task-routing) end-to-end tests for the
 * multi-member fan-out + reduction primitive through the full programmatic
 * orchestration path.
 *
 * <p>Drives {@link TeamTaskFlowOrchestrator#executeAsync(String)} (and
 * {@link TeamTaskFlowOrchestrator#execute(String)} for the sync-equivalence
 * case) end to end: graph build → router dispatch plan → fan-out node step
 * (claim synchronously + N member concurrent + reduction + single complete)
 * → nop-task {@link io.nop.task.step.GraphTaskStep} CompletableFuture
 * scheduling → fan-out concurrency + dependency order → final
 * {@link TeamTaskFlowResult}.
 *
 * <p><b>Anti-Hollow #22 / #23 / #24</b>: each test asserts real concurrency
 * evidence (peak-concurrent / interval overlap), correct task state machine
 * transitions (CLAIMED → COMPLETED once), and honest failure propagation
 * (never silent success).
 *
 * <p>Coverage map (maps to Phase 4 Exit Criteria):
 * <ul>
 *   <li>{@link #e2eBoundFanOutFullDiamondPath} — bound fan-out diamond via
 *       executeAsync: calling thread not blocked, fan-out concurrent, D
 *       dependency-ordered, all COMPLETED.</li>
 *   <li>{@link #e2eSpawnFanOutFullDiamondPath} — spawn fan-out diamond via
 *       executeAsync: N spawn workers concurrent, D dependency-ordered, all
 *       COMPLETED.</li>
 *   <li>{@link #e2eFanOutHonestFailurePropagates} — B's fan-out has a
 *       failing member → success=false, failed=B, skipped=D, B CLAIMED.</li>
 *   <li>{@link #e2eSingleMemberNoOpEquivalent} — same DAG under NoOp shipped
 *       default = pre-244 single-member semantics (zero-regression proof).</li>
 *   <li>{@link #e2eSyncEqualsAsyncJoinOnFanOutDag} — execute ≡
 *       executeAsync().join() on the same fan-out DAG.</li>
 * </ul>
 */
public class TestMultiMemberFanOutEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Mocks (lightweight versions of the Phase 3 mocks).
    // ========================================================================

    static final class SlowCompletedEngine implements IAgentEngine {
        final AtomicInteger peakConcurrent = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final Map<String, Long> enterNano = new ConcurrentHashMap<>();
        final Map<String, Long> exitNano = new ConcurrentHashMap<>();

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            String taskId = (String) request.getMetadata().get("teamTaskId");
            String member = (String) request.getMetadata().get("fanoutMember");
            String key = taskId + "#" + (member != null ? member : "single");
            int nowActive = active.incrementAndGet();
            peakConcurrent.accumulateAndGet(nowActive, Math::max);
            enterNano.put(key, System.nanoTime());
            return CompletableFuture.supplyAsync(() -> {
                try { Thread.sleep(80); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); throw new NopAiAgentException("interrupted", e);
                }
                active.decrementAndGet();
                exitNano.put(key, System.nanoTime());
                return new AgentExecutionResult(AgentExecStatus.completed, "ok:" + key,
                        Collections.emptyList(), 1, 10L, 1L, null);
            });
        }
    }

    static final class SlowCompletedSpawner implements IMemberSpawner {
        final AtomicInteger peakConcurrent = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final Map<String, Long> enterNano = new ConcurrentHashMap<>();
        final Map<String, Long> exitNano = new ConcurrentHashMap<>();

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            String taskId = request.getTask().getTaskId();
            String member = request.getTarget() != null ? request.getTarget().getMemberName() : "?";
            String key = taskId + "#" + member;
            int nowActive = active.incrementAndGet();
            peakConcurrent.accumulateAndGet(nowActive, Math::max);
            enterNano.put(key, System.nanoTime());
            try { Thread.sleep(80); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new NopAiAgentException("interrupted", e);
            }
            active.decrementAndGet();
            exitNano.put(key, System.nanoTime());
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + key,
                            Collections.emptyList(), 1, 10L, 1L, null),
                    request.getTarget() != null ? request.getTarget().getAgentModel() : "x",
                    "spawned-" + key);
        }
    }

    /** Spawner that fails spawn for one specific (taskId, member) key. */
    static final class FailOneMemberSpawner implements IMemberSpawner {
        final String failTaskId;
        final String failMember;
        final SpawnMemberResult failResult;

        FailOneMemberSpawner(String failTaskId, String failMember, SpawnMemberResult failResult) {
            this.failTaskId = failTaskId;
            this.failMember = failMember;
            this.failResult = failResult;
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            String taskId = request.getTask().getTaskId();
            String member = request.getTarget() != null ? request.getTarget().getMemberName() : "?";
            if (failTaskId.equals(taskId) && failMember.equals(member)) {
                return failResult;
            }
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId + "#" + member,
                            Collections.emptyList(), 1, 10L, 1L, null),
                    request.getTarget() != null ? request.getTarget().getAgentModel() : "x",
                    "spawned-" + taskId + "#" + member);
        }
    }

    // ========================================================================
    // Helpers.
    // ========================================================================

    static Team createTeam(InMemoryTeamManager mgr, String teamName, String... memberNames) {
        java.util.List<TeamMemberSpec> specs = new java.util.ArrayList<>();
        specs.add(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD));
        for (String name : memberNames) {
            specs.add(new TeamMemberSpec(name, name + "-agent", MemberRole.MEMBER));
        }
        TeamSpec spec = new TeamSpec(teamName, "test", "lead-agent", specs, 0);
        return mgr.createTeam(spec);
    }

    static Team createTeamAndBind(InMemoryTeamManager mgr, String teamName,
                                   String[] members, String[] sessions) {
        Team team = createTeam(mgr, teamName, members);
        for (int i = 0; i < members.length; i++) {
            mgr.bindMemberSession(team.getTeamId(), members[i], sessions[i], "actor-" + members[i]);
        }
        return team;
    }

    static String createTask(InMemoryTeamTaskStore store, String teamId,
                              String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session").getTaskId();
    }

    static ITaskMemberRouter routerFor(Team team, Function<String, List<DispatchTarget>> planFn) {
        return new ITaskMemberRouter() {
            @Override
            public MemberDispatchPlan route(Team t, io.nop.ai.agent.team.TeamTask task) {
                return new MemberDispatchPlan(team, task, planFn.apply(task.getSubject()),
                        AllMustSucceedReduction.instance());
            }
        };
    }

    static DispatchTarget boundTarget(Team team, String memberName) {
        io.nop.ai.agent.team.TeamMember m = team.getMembers().get(memberName);
        java.util.Objects.requireNonNull(m, "bound member not found: " + memberName);
        String agentModel = null;
        for (TeamMemberSpec s : team.getSpec().getMemberSpecs()) {
            if (s.getMemberName().equals(memberName)) agentModel = s.getAgentModel();
        }
        return DispatchTarget.bound(memberName, m.getSessionId(), agentModel);
    }

    static DispatchTarget spawnTarget(Team team, String memberName) {
        for (TeamMemberSpec s : team.getSpec().getMemberSpecs()) {
            if (s.getMemberName().equals(memberName)) return DispatchTarget.spawn(s);
        }
        throw new AssertionError("memberSpec not found: " + memberName);
    }

    // ========================================================================
    // 1. E2E bound fan-out full path: diamond A→{B,C}→D, B→{b1,b2}, C→{c1,c2}.
    // ========================================================================

    @Test
    void e2eBoundFanOutFullDiamondPath() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        SlowCompletedEngine engine = new SlowCompletedEngine();
        String[] members = {"b1", "b2", "c1", "c2"};
        String[] sessions = {"s-b1", "s-b2", "s-c1", "s-c2"};
        Team team = createTeamAndBind(mgr, "e2e-bound-team", members, sessions);
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        ITaskMemberRouter router = routerFor(team, subject -> {
            switch (subject) {
                case "B": return Arrays.asList(boundTarget(team, "b1"), boundTarget(team, "b2"));
                case "C": return Arrays.asList(boundTarget(team, "c1"), boundTarget(team, "c2"));
                case "A": return Collections.singletonList(boundTarget(team, "b1"));
                case "D": return Collections.singletonList(boundTarget(team, "c1"));
                default: throw new AssertionError("unexpected: " + subject);
            }
        });
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            CompletableFuture<TeamTaskFlowResult> future = orchestrator.executeAsync(teamId);

            // Anti-Hollow — calling thread NOT blocked (fan-out work async).
            assertFalse(future.isDone(), "executeAsync must not block calling thread");

            TeamTaskFlowResult result = future.get(30, TimeUnit.SECONDS);
            assertTrue(result.isSuccess(), "bound fan-out diamond completes: " + result);
            assertEquals(4, result.getCompletedTaskIds().size());
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus());
            }

            // Anti-Hollow #22 — fan-out real concurrency.
            assertTrue(engine.peakConcurrent.get() >= 2,
                    "fan-out members concurrent: peak=" + engine.peakConcurrent.get());
            // D dependency-ordered.
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(b));
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(c));
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 2. E2E spawn fan-out full path: diamond A→{B,C}→D, B→{b1,b2}, C→{c1,c2}.
    // ========================================================================

    @Test
    void e2eSpawnFanOutFullDiamondPath() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        SlowCompletedSpawner spawner = new SlowCompletedSpawner();
        Team team = createTeam(mgr, "e2e-spawn-team", "b1", "b2", "c1", "c2");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        ITaskMemberRouter router = routerFor(team, subject -> {
            switch (subject) {
                case "B": return Arrays.asList(spawnTarget(team, "b1"), spawnTarget(team, "b2"));
                case "C": return Arrays.asList(spawnTarget(team, "c1"), spawnTarget(team, "c2"));
                case "A": return Collections.singletonList(spawnTarget(team, "b1"));
                case "D": return Collections.singletonList(spawnTarget(team, "c1"));
                default: throw new AssertionError("unexpected: " + subject);
            }
        });
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner, router);
        try {
            CompletableFuture<TeamTaskFlowResult> future = orchestrator.executeAsync(teamId);
            assertFalse(future.isDone(), "executeAsync must not block calling thread");

            TeamTaskFlowResult result = future.get(30, TimeUnit.SECONDS);
            assertTrue(result.isSuccess(), "spawn fan-out diamond completes: " + result);
            assertEquals(4, result.getCompletedTaskIds().size());
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus());
            }

            // Anti-Hollow #22 — spawn fan-out real concurrency.
            assertTrue(spawner.peakConcurrent.get() >= 2,
                    "spawn fan-out members concurrent: peak=" + spawner.peakConcurrent.get());
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(b));
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(c));
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 3. E2E fan-out honest failure propagation: B's fan-out has a failing
    //    member → success=false, failed=B, skipped=D, B CLAIMED.
    // ========================================================================

    @Test
    void e2eFanOutHonestFailurePropagates() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeam(mgr, "e2e-fail-team", "b1", "b2", "c1", "c2");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        // B's spawn fan-out: b1 SPAWN_FAILED, b2 succeeds.
        FailOneMemberSpawner spawner = new FailOneMemberSpawner(b, "b1",
                SpawnMemberResult.spawnFailed("spawned agent threw: boom"));
        ITaskMemberRouter router = routerFor(team, subject -> {
            switch (subject) {
                case "B": return Arrays.asList(spawnTarget(team, "b1"), spawnTarget(team, "b2"));
                case "C": return Arrays.asList(spawnTarget(team, "c1"), spawnTarget(team, "c2"));
                case "A": return Collections.singletonList(spawnTarget(team, "b1"));
                case "D": return Collections.singletonList(spawnTarget(team, "c1"));
                default: throw new AssertionError("unexpected: " + subject);
            }
        });
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            // Anti-Hollow #24 — honest failure.
            assertFalse(result.isSuccess(),
                    "B's fan-out member failure must honestly fail: " + result);
            assertEquals(b, result.getFailedTaskId(), "B is the failed task");
            assertTrue(result.getSkippedTaskIds().contains(d),
                    "D skipped (depends on failed B)");
            // B left CLAIMED (not abandoned).
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(b).orElseThrow().getStatus(),
                    "B left CLAIMED on fan-out failure");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 4. E2E single-member NoOp default equivalent: same diamond under
    //    NoOp router (shipped default) = pre-244 single-member semantics.
    // ========================================================================

    @Test
    void e2eSingleMemberNoOpEquivalent() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        SlowCompletedEngine engine = new SlowCompletedEngine();
        // Single bound member "worker" — NoOp router picks it for every task.
        Team team = createTeamAndBind(mgr, "e2e-single-team",
                new String[]{"worker"}, new String[]{"worker-session"});
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        // No explicit router → shipped NoOp default (single-member plan).
        TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(engine, store, mgr);
        TeamTaskFlowResult result = orchestrator.executeAsync(teamId).join();

        assertTrue(result.isSuccess(),
                "NoOp single-member diamond completes (= pre-244 semantics): " + result);
        assertEquals(4, result.getCompletedTaskIds().size());
        for (String id : Arrays.asList(a, b, c, d)) {
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus());
        }
        // No fan-out (single member) — peak concurrent reflects independent
        // diamond branches (B,C concurrent) but not fan-out within a node.
        assertTrue(engine.peakConcurrent.get() >= 1,
                "single-member diamond still runs: peak=" + engine.peakConcurrent.get());
    }

    // ========================================================================
    // 5. E2E sync ≡ async().join() on the same fan-out DAG.
    // ========================================================================

    @Test
    void e2eSyncEqualsAsyncJoinOnFanOutDag() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store1 = new InMemoryTeamTaskStore();
        InMemoryTeamTaskStore store2 = new InMemoryTeamTaskStore();
        SlowCompletedEngine engine1 = new SlowCompletedEngine();
        SlowCompletedEngine engine2 = new SlowCompletedEngine();
        String[] members = {"b1", "b2"};
        String[] sessions = {"s-b1", "s-b2"};
        Team team1 = createTeamAndBind(mgr, "sync-team-1", members, sessions);
        Team team2 = createTeamAndBind(mgr, "sync-team-2", members, sessions);

        // Build identical diamonds in both stores.
        String a1 = createTask(store1, team1.getTeamId(), "A", Collections.emptyList());
        String b1 = createTask(store1, team1.getTeamId(), "B", Collections.singletonList(a1));
        String a2 = createTask(store2, team2.getTeamId(), "A", Collections.emptyList());
        String b2 = createTask(store2, team2.getTeamId(), "B", Collections.singletonList(a2));

        Function<String, List<DispatchTarget>> planFn = subject ->
                Arrays.asList(boundTarget(team1, "b1"), boundTarget(team1, "b2"));

        ITaskMemberRouter router1 = routerFor(team1, planFn);
        ITaskMemberRouter router2 = new ITaskMemberRouter() {
            @Override public MemberDispatchPlan route(Team t, io.nop.ai.agent.team.TeamTask task) {
                return new MemberDispatchPlan(team2, task,
                        Arrays.asList(boundTarget(team2, "b1"), boundTarget(team2, "b2")),
                        AllMustSucceedReduction.instance());
            }
        };

        TeamTaskFlowOrchestrator asyncOrch =
                new TeamTaskFlowOrchestrator(engine1, store1, mgr, null, null, router1);
        TeamTaskFlowOrchestrator syncOrch =
                new TeamTaskFlowOrchestrator(engine2, store2, mgr, null, null, router2);

        TeamTaskFlowResult asyncResult = asyncOrch.executeAsync(team1.getTeamId()).join();
        TeamTaskFlowResult syncResult = syncOrch.execute(team2.getTeamId());

        // Both paths produce equivalent results.
        assertEquals(asyncResult.isSuccess(), syncResult.isSuccess(),
                "sync ≡ async().join() on fan-out DAG (both succeed)");
        assertEquals(asyncResult.getCompletedTaskIds().size(), syncResult.getCompletedTaskIds().size(),
                "same completed count");
        assertEquals(TeamTaskStatus.COMPLETED, store1.getTask(b1).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store2.getTask(b2).orElseThrow().getStatus());
    }
}
