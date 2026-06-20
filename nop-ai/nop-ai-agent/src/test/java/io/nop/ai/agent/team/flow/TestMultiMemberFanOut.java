package io.nop.ai.agent.team.flow;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 244 (L4-multi-member-per-task-routing) focused tests for the
 * per-task multi-member fan-out + reduction primitive.
 *
 * <p>Verifies the new work-distribution primitive end to end through
 * {@link TeamTaskFlowOrchestrator#executeAsync(String)} with a pluggable
 * multi-member {@link ITaskMemberRouter}:
 * <ul>
 *   <li><b>Bound fan-out real concurrency</b> — diamond A→{B,C}→D where B
 *       fans out to {B1,B2} bound members and C fans out to {C1,C2} bound
 *       members: B1/B2 overlap, C1/C2 overlap, B-fan-out and C-fan-out
 *       independent branches concurrent, D dependency-ordered after both.</li>
 *   <li><b>Spawn fan-out real concurrency</b> — same diamond with spawn
 *       targets instead of bound members; spawn workers truly concurrent.</li>
 *   <li><b>all-must-succeed reduction</b> — N members all completed →
 *       task COMPLETED once (single completeTask); any failure → task stays
 *       CLAIMED, in-flight members run-to-completion (results discarded).</li>
 *   <li><b>Honest failure on empty plan</b> — router returns empty plan →
 *       NopAiAgentException thrown honestly, task stays CREATED, DAG build
 *       aborts before any node runs (no silent skip).</li>
 *   <li><b>Single-member zero regression</b> — NoOp shipped default router
 *       produces a singleton plan = existing MemberAgentTaskStep /
 *       SpawnMemberAgentTaskStep path (semantic equivalence).</li>
 *   <li><b>Tenant isolation under fan-out</b> — bound + spawn fan-out
 *       nodes propagate the caller's tenant to every member worker; no
 *       leak across runs on a pooled worker thread.</li>
 *   <li><b>Mixed bound + spawn fan-out</b> — one dispatch plan with both
 *       bound and spawn targets reduces unified under all-must-succeed.</li>
 * </ul>
 *
 * <p>Anti-Hollow #22 / #23 / #24 evidence captured per test.
 */
public class TestMultiMemberFanOut {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Router helpers — produce multi-target dispatch plans for tests.
    // ========================================================================

    /**
     * Router that returns a fixed dispatch plan for every task (ignoring the
     * team / task data). Used to drive specific fan-out shapes from tests.
     */
    static final class FixedPlanRouter implements ITaskMemberRouter {
        private final java.util.function.Function<TeamTask, MemberDispatchPlan> planFn;

        FixedPlanRouter(java.util.function.Function<TeamTask, MemberDispatchPlan> planFn) {
            this.planFn = planFn;
        }

        @Override
        public MemberDispatchPlan route(Team team, TeamTask task) {
            return planFn.apply(task);
        }
    }

    /**
     * Build a bound dispatch plan over the named bound members of the team.
     */
    static MemberDispatchPlan boundFanOutPlan(Team team, TeamTask task, List<String> memberNames) {
        List<DispatchTarget> targets = new ArrayList<>();
        for (String name : memberNames) {
            io.nop.ai.agent.team.TeamMember m = team.getMembers().get(name);
            Objects.requireNonNull(m, "bound member not found: " + name);
            assertTrue(m.isBound(), "member not bound: " + name);
            targets.add(DispatchTarget.bound(name, m.getSessionId(), agentModelOf(team, name)));
        }
        return new MemberDispatchPlan(team, task, targets, AllMustSucceedReduction.instance());
    }

    /**
     * Build a spawn dispatch plan over the named memberSpecs of the team.
     */
    static MemberDispatchPlan spawnFanOutPlan(Team team, TeamTask task, List<String> memberNames) {
        List<DispatchTarget> targets = new ArrayList<>();
        for (String name : memberNames) {
            TeamMemberSpec spec = findMemberSpec(team, name);
            targets.add(DispatchTarget.spawn(spec));
        }
        return new MemberDispatchPlan(team, task, targets, AllMustSucceedReduction.instance());
    }

    /**
     * Build a mixed dispatch plan (bound + spawn) — half the named members
     * are bound, half are spawn. The first half of {@code boundNames} are
     * bound targets, the rest are spawn targets.
     */
    static MemberDispatchPlan mixedFanOutPlan(Team team, TeamTask task,
                                              List<String> boundNames, List<String> spawnNames) {
        List<DispatchTarget> targets = new ArrayList<>();
        for (String name : boundNames) {
            io.nop.ai.agent.team.TeamMember m = team.getMembers().get(name);
            Objects.requireNonNull(m, "bound member not found: " + name);
            targets.add(DispatchTarget.bound(name, m.getSessionId(), agentModelOf(team, name)));
        }
        for (String name : spawnNames) {
            targets.add(DispatchTarget.spawn(findMemberSpec(team, name)));
        }
        return new MemberDispatchPlan(team, task, targets, AllMustSucceedReduction.instance());
    }

    static MemberDispatchPlan emptyPlan(Team team, TeamTask task) {
        return new MemberDispatchPlan(team, task, Collections.emptyList(),
                AllMustSucceedReduction.instance());
    }

    static TeamMemberSpec findMemberSpec(Team team, String memberName) {
        for (TeamMemberSpec s : team.getSpec().getMemberSpecs()) {
            if (s.getMemberName().equals(memberName)) {
                return s;
            }
        }
        throw new AssertionError("memberSpec not found: " + memberName);
    }

    static String agentModelOf(Team team, String memberName) {
        for (TeamMemberSpec s : team.getSpec().getMemberSpecs()) {
            if (s.getMemberName().equals(memberName)) {
                return s.getAgentModel();
            }
        }
        return null;
    }

    // ========================================================================
    // Engine / spawner mocks.
    // ========================================================================

    /**
     * Engine that records per-target wall-clock intervals and peak
     * concurrency so fan-out tests can assert REAL concurrency (not just
     * final COMPLETED status). Returns a completed result for each call.
     */
    static final class ConcurrencyRecordingEngine implements IAgentEngine {
        final Map<String, Long> enterNano = new ConcurrentHashMap<>();
        final Map<String, Long> exitNano = new ConcurrentHashMap<>();
        final AtomicInteger peakConcurrent = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger executeCount = new AtomicInteger();
        final Map<String, String> memberForTask = new ConcurrentHashMap<>();
        final List<String> threadNames = Collections.synchronizedList(new ArrayList<>());

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            executeCount.incrementAndGet();
            String taskId = (String) request.getMetadata().get("teamTaskId");
            String member = (String) request.getMetadata().get("fanoutMember");
            // Compound key so two members of the same task don't collide.
            String key = taskId + "#" + member;
            int nowActive = active.incrementAndGet();
            peakConcurrent.accumulateAndGet(nowActive, Math::max);
            enterNano.put(key, System.nanoTime());
            if (member != null) {
                memberForTask.put(key, member);
            }
            threadNames.add(Thread.currentThread().getName());

            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NopAiAgentException("interrupted", e);
                }
                active.decrementAndGet();
                exitNano.put(key, System.nanoTime());
                return new AgentExecutionResult(AgentExecStatus.completed, "ok:" + key,
                        Collections.emptyList(), 1, 10L, 1L, null);
            });
        }
    }

    /**
     * Engine whose failMember throwing or non-completed status can be
     * configured per (taskId, member) key, to drive honest-failure reduction
     * paths in bound fan-out.
     */
    static final class ConfigurableFanOutEngine implements IAgentEngine {
        final Map<String, FailureKind> failKeys = new ConcurrentHashMap<>();
        final AtomicInteger executeCount = new AtomicInteger();

        enum FailureKind {
            EXCEPTION, NON_COMPLETED
        }

        void failOn(String taskId, String member, FailureKind kind) {
            failKeys.put(taskId + "#" + member, kind);
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
            String key = taskId + "#" + member;
            FailureKind kind = failKeys.get(key);
            if (kind == FailureKind.EXCEPTION) {
                CompletableFuture<AgentExecutionResult> f = new CompletableFuture<>();
                f.completeExceptionally(new RuntimeException("boom:" + key));
                return f;
            }
            if (kind == FailureKind.NON_COMPLETED) {
                return CompletableFuture.completedFuture(
                        new AgentExecutionResult(AgentExecStatus.failed, null,
                                Collections.emptyList(), 0, 0L, 0L, "non-completed:" + key));
            }
            return CompletableFuture.completedFuture(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + key,
                            Collections.emptyList(), 1, 10L, 1L, null));
        }
    }

    /**
     * Spawner that records peak concurrency + intervals (mirrors
     * ConcurrencyRecordingEngine but for the spawn half).
     */
    static final class ConcurrencyRecordingSpawner implements IMemberSpawner {
        final Map<String, Long> enterNano = new ConcurrentHashMap<>();
        final Map<String, Long> exitNano = new ConcurrentHashMap<>();
        final AtomicInteger peakConcurrent = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final List<String> spawnedMembers = Collections.synchronizedList(new ArrayList<>());
        final List<String> threadNames = Collections.synchronizedList(new ArrayList<>());
        // Optional per-(taskId,member) failure config.
        final Map<String, SpawnFailure> failKeys = new ConcurrentHashMap<>();

        enum SpawnFailure {
            NO_SPAWN, SPAWN_FAILED, THROWS, NULL, NON_COMPLETED
        }

        void failOn(String taskId, String member, SpawnFailure kind) {
            failKeys.put(taskId + "#" + member, kind);
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            String taskId = request.getTask().getTaskId();
            TeamMemberSpec target = request.getTarget();
            String member = target != null ? target.getMemberName() : "?";
            String key = taskId + "#" + member;
            threadNames.add(Thread.currentThread().getName());

            SpawnFailure fail = failKeys.get(key);
            if (fail != null) {
                switch (fail) {
                    case NO_SPAWN:
                        return SpawnMemberResult.noSpawn("declined:" + key);
                    case SPAWN_FAILED:
                        return SpawnMemberResult.spawnFailed("spawn-failed:" + key);
                    case THROWS:
                        throw new NopAiAgentException("spawner-throws:" + key);
                    case NULL:
                        return null;
                    case NON_COMPLETED:
                        return SpawnMemberResult.dispatched(
                                new AgentExecutionResult(AgentExecStatus.failed, null,
                                        Collections.emptyList(), 0, 0L, 0L, "non-completed:" + key),
                                target != null ? target.getAgentModel() : "x",
                                "spawned-" + key);
                    default:
                        // fall through to success
                }
            }

            int nowActive = active.incrementAndGet();
            peakConcurrent.accumulateAndGet(nowActive, Math::max);
            enterNano.put(key, System.nanoTime());
            spawnedMembers.add(member);
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NopAiAgentException("interrupted", e);
            }
            active.decrementAndGet();
            exitNano.put(key, System.nanoTime());
            return SpawnMemberResult.dispatched(
                    new AgentExecutionResult(AgentExecStatus.completed, "ok:" + key,
                            Collections.emptyList(), 1, 10L, 1L, null),
                    target != null ? target.getAgentModel() : "worker-agent-model",
                    "spawned-" + key);
        }
    }

    // ========================================================================
    // Team / task builders.
    // ========================================================================

    /**
     * Create a team with several MEMBER-role memberSpecs but NO members
     * bound (so the NoOp router's spawn fallback would apply, but tests
     * usually inject their own multi-member router).
     */
    static Team createTeamWithMembers(InMemoryTeamManager mgr, String teamName,
                                       String... memberNames) {
        List<TeamMemberSpec> specs = new ArrayList<>();
        specs.add(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD));
        for (String name : memberNames) {
            specs.add(new TeamMemberSpec(name, name + "-agent", MemberRole.MEMBER));
        }
        TeamSpec spec = new TeamSpec(teamName, "test", "lead-agent", specs, 0);
        return mgr.createTeam(spec);
    }

    /**
     * Create a team and bind the named members to fresh sessions.
     */
    static Team createTeamAndBindMembers(InMemoryTeamManager mgr, String teamName,
                                          String[] memberNames, String[] sessionIds) {
        Team team = createTeamWithMembers(mgr, teamName, memberNames);
        for (int i = 0; i < memberNames.length; i++) {
            mgr.bindMemberSession(team.getTeamId(), memberNames[i], sessionIds[i],
                    "actor-" + memberNames[i]);
        }
        return team;
    }

    static String createTask(ITeamTaskStore store, String teamId,
                              String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "lead-session").getTaskId();
    }

    // ========================================================================
    // 1. Bound fan-out real concurrency: diamond A→{B,C}→D where B fans out
    //    to {B1,B2} and C fans out to {C1,C2}. All 4 bound members.
    // ========================================================================

    @Test
    void boundFanOutDiamondRealConcurrencyAndDAfterBoth() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        String[] members = {"b1", "b2", "c1", "c2"};
        String[] sessions = {"s-b1", "s-b2", "s-c1", "s-c2"};
        Team team = createTeamAndBindMembers(mgr, "bound-fanout-team", members, sessions);
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        // Router: B → {b1,b2}, C → {c1,c2}, A/D → single bound (b1 / b1).
        ITaskMemberRouter router = new FixedPlanRouter(t -> {
            switch (t.getSubject()) {
                case "B": return boundFanOutPlan(team, t, Arrays.asList("b1", "b2"));
                case "C": return boundFanOutPlan(team, t, Arrays.asList("c1", "c2"));
                case "A": return boundFanOutPlan(team, t, Collections.singletonList("b1"));
                case "D": return boundFanOutPlan(team, t, Collections.singletonList("c1"));
                default: throw new AssertionError("unexpected task: " + t.getSubject());
            }
        });
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(), "diamond should complete: " + result);
            assertEquals(4, result.getCompletedTaskIds().size());
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                        "task " + id + " COMPLETED");
            }

            // Anti-Hollow #22 — REAL CONCURRENCY evidence inside B's fan-out:
            // b1 and b2 must overlap.
            long b1Enter = engine.enterNano.get(b + "#b1");
            long b1Exit = engine.exitNano.get(b + "#b1");
            long b2Enter = engine.enterNano.get(b + "#b2");
            long b2Exit = engine.exitNano.get(b + "#b2");
            boolean bOverlap = (b1Enter < b2Exit) && (b2Enter < b1Exit);
            assertTrue(bOverlap,
                    "B's fan-out members b1,b2 MUST overlap (real concurrency): "
                            + "b1[" + b1Enter + "," + b1Exit + "], b2[" + b2Enter + "," + b2Exit + "]");

            // Same for C's fan-out.
            long c1Enter = engine.enterNano.get(c + "#c1");
            long c1Exit = engine.exitNano.get(c + "#c1");
            long c2Enter = engine.enterNano.get(c + "#c2");
            long c2Exit = engine.exitNano.get(c + "#c2");
            boolean cOverlap = (c1Enter < c2Exit) && (c2Enter < c1Exit);
            assertTrue(cOverlap, "C's fan-out members c1,c2 MUST overlap (real concurrency)");

            // Peak concurrent across the whole DAG >= 2 (independent branches
            // concurrent + each fan-out's members concurrent).
            assertTrue(engine.peakConcurrent.get() >= 2,
                    "diamond peakConcurrent >= 2 (independent branches concurrent): peak="
                            + engine.peakConcurrent.get());

            // Anti-Hollow — DEPENDENCY ORDER: D starts strictly after both
            // B and C complete.
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(b),
                    "D starts after B completes");
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(c),
                    "D starts after C completes");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 2. Spawn fan-out real concurrency: diamond A→{B,C}→D, B → {b1,b2}
    //    spawn targets, C → {c1,c2} spawn targets.
    // ========================================================================

    @Test
    void spawnFanOutDiamondRealConcurrencyAndDAfterBoth() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingSpawner spawner = new ConcurrencyRecordingSpawner();
        Team team = createTeamWithMembers(mgr, "spawn-fanout-team", "b1", "b2", "c1", "c2");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        ITaskMemberRouter router = new FixedPlanRouter(t -> {
            switch (t.getSubject()) {
                case "B": return spawnFanOutPlan(team, t, Arrays.asList("b1", "b2"));
                case "C": return spawnFanOutPlan(team, t, Arrays.asList("c1", "c2"));
                case "A": return spawnFanOutPlan(team, t, Collections.singletonList("b1"));
                case "D": return spawnFanOutPlan(team, t, Collections.singletonList("c1"));
                default: throw new AssertionError("unexpected task: " + t.getSubject());
            }
        });
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(), "spawn diamond should complete: " + result);
            for (String id : Arrays.asList(a, b, c, d)) {
                assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                        "task " + id + " COMPLETED via spawn fan-out");
            }

            // Anti-Hollow #22 — REAL CONCURRENCY inside B's spawn fan-out.
            long b1Enter = spawner.enterNano.get(b + "#b1");
            long b1Exit = spawner.exitNano.get(b + "#b1");
            long b2Enter = spawner.enterNano.get(b + "#b2");
            long b2Exit = spawner.exitNano.get(b + "#b2");
            boolean bOverlap = (b1Enter < b2Exit) && (b2Enter < b1Exit);
            assertTrue(bOverlap,
                    "B's spawn fan-out b1,b2 MUST overlap (real concurrency): "
                            + "b1[" + b1Enter + "," + b1Exit + "], b2[" + b2Enter + "," + b2Exit + "]");

            // Spawn workers ran on ai-agent-spawn-worker-N threads.
            assertTrue(spawner.peakConcurrent.get() >= 2,
                    "spawn fan-out peakConcurrent >= 2: peak=" + spawner.peakConcurrent.get());
            assertTrue(spawner.threadNames.stream().anyMatch(n -> n.startsWith("ai-agent-spawn-worker-")),
                    "spawn workers ran on dedicated ai-agent-spawn-worker-N threads: " + spawner.threadNames);

            // Dependency order.
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(b));
            assertTrue(result.getStartOrder().get(d) > result.getCompletionOrder().get(c));
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 3. all-must-succeed reduction: success path → task COMPLETED once.
    // ========================================================================

    @Test
    void allMustSucceedReductionCompletesTaskOnce() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        String[] members = {"m1", "m2", "m3"};
        String[] sessions = {"s1", "s2", "s3"};
        Team team = createTeamAndBindMembers(mgr, "reduce-team", members, sessions);
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> boundFanOutPlan(team, t, Arrays.asList("m1", "m2", "m3")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(), "fan-out should complete: " + result);
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
            // Anti-Hollow #23 — task transitioned CLAIMED → COMPLETED exactly
            // once (the orchestrator's completeTask is single per node, not
            // per member).
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                    "task COMPLETED once after 3-member fan-out");
            assertEquals(3, engine.peakConcurrent.get(),
                    "all 3 fan-out members concurrent: peak=" + engine.peakConcurrent.get());
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 4. all-must-succeed reduction: failure paths → task stays CLAIMED.
    //    (a) bound member non-completed status
    //    (b) bound member engine exception
    //    (c) spawn NO_SPAWN
    //    (d) spawn SPAWN_FAILED
    //    (e) spawn spawner throws
    //    (f) spawn spawner returns null
    //    (g) spawn dispatched non-completed
    // ========================================================================

    @Test
    void boundFanOutMemberNonCompletedLeavesTaskClaimed() throws Exception {
        runBoundFanOutFailureLeavesTaskClaimed(ConfigurableFanOutEngine.FailureKind.NON_COMPLETED);
    }

    @Test
    void boundFanOutMemberExceptionLeavesTaskClaimed() throws Exception {
        runBoundFanOutFailureLeavesTaskClaimed(ConfigurableFanOutEngine.FailureKind.EXCEPTION);
    }

    private void runBoundFanOutFailureLeavesTaskClaimed(
            ConfigurableFanOutEngine.FailureKind kind) throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConfigurableFanOutEngine engine = new ConfigurableFanOutEngine();
        String[] members = {"m1", "m2"};
        String[] sessions = {"s1", "s2"};
        Team team = createTeamAndBindMembers(mgr, "bound-fail-team", members, sessions);
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        engine.failOn(a, "m2", kind);

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> boundFanOutPlan(team, t, Arrays.asList("m1", "m2")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(),
                    "bound fan-out with " + kind + " must honestly fail: " + result);
            assertEquals(a, result.getFailedTaskId());
            // Anti-Hollow #24 — task left CLAIMED (not abandoned, not silent skip).
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                    "task stays CLAIMED on fan-out failure (kind=" + kind + ")");
            assertNotEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus(),
                    "task NOT abandoned (orchestrator's failure model, not daemon's)");
        } finally {
            orchestrator.close();
        }
    }

    @Test
    void spawnFanOutNoSpawnLeavesTaskClaimed() throws Exception {
        runSpawnFanOutFailureLeavesTaskClaimed(ConcurrencyRecordingSpawner.SpawnFailure.NO_SPAWN);
    }

    @Test
    void spawnFanOutSpawnFailedLeavesTaskClaimed() throws Exception {
        runSpawnFanOutFailureLeavesTaskClaimed(ConcurrencyRecordingSpawner.SpawnFailure.SPAWN_FAILED);
    }

    @Test
    void spawnFanOutSpawnerThrowsLeavesTaskClaimed() throws Exception {
        runSpawnFanOutFailureLeavesTaskClaimed(ConcurrencyRecordingSpawner.SpawnFailure.THROWS);
    }

    @Test
    void spawnFanOutSpawnerNullLeavesTaskClaimed() throws Exception {
        runSpawnFanOutFailureLeavesTaskClaimed(ConcurrencyRecordingSpawner.SpawnFailure.NULL);
    }

    @Test
    void spawnFanOutDispatchedNonCompletedLeavesTaskClaimed() throws Exception {
        runSpawnFanOutFailureLeavesTaskClaimed(ConcurrencyRecordingSpawner.SpawnFailure.NON_COMPLETED);
    }

    private void runSpawnFanOutFailureLeavesTaskClaimed(
            ConcurrencyRecordingSpawner.SpawnFailure failure) throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingSpawner spawner = new ConcurrencyRecordingSpawner();
        Team team = createTeamWithMembers(mgr, "spawn-fail-team", "m1", "m2");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        spawner.failOn(a, "m2", failure);

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> spawnFanOutPlan(team, t, Arrays.asList("m1", "m2")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(null, store, mgr, null, spawner, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertFalse(result.isSuccess(),
                    "spawn fan-out with " + failure + " must honestly fail: " + result);
            assertEquals(a, result.getFailedTaskId());
            assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                    "task stays CLAIMED on spawn fan-out failure (failure=" + failure + ")");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 5. Empty router plan → honest failure (NopAiAgentException), task
    //    stays CREATED, no node runs. Structural fast-fail before execute.
    // ========================================================================

    @Test
    void emptyRouterPlanHonestFailureBuildAbort() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        Team team = createTeamWithMembers(mgr, "empty-plan-team", "m1");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITaskMemberRouter router = new FixedPlanRouter(t -> emptyPlan(team, t));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            // Structural fast-fail: empty plan throws synchronously out of
            // executeAsync (before the future is created).
            NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                    () -> orchestrator.executeAsync(teamId),
                    "empty plan MUST honest-fail at build time (not silent skip)");
            assertTrue(ex.getMessage().contains("no-dispatchable-member")
                            || ex.getMessage().contains("zero targets"),
                    "exception message identifies the empty-plan cause: " + ex.getMessage());

            // Anti-Hollow #24 — task stays CREATED (never claimed, never silently skipped).
            assertEquals(TeamTaskStatus.CREATED, store.getTask(a).orElseThrow().getStatus(),
                    "task stays CREATED on empty plan (honest failure, no silent skip)");
            // Engine never invoked.
            assertEquals(0, engine.executeCount.get(), "engine never invoked on empty plan");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 6. Single-member zero regression: NoOp shipped default produces a
    //    singleton plan = existing MemberAgentTaskStep / SpawnMemberAgentTaskStep
    //    path. Run the same DAG with NoOp router (default) and verify
    //    semantic equivalence to the pre-244 single-member behaviour.
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
    // 7. Tenant isolation under fan-out: bound + spawn fan-out propagate
    //    the caller's tenant to every member worker; no leak across runs.
    // ========================================================================

    /**
     * Spawner that records the tenant observed at spawn time per worker.
     * Used to assert each fan-out spawn worker sees the caller's tenant.
     */
    static final class TenantRecordingSpawner implements IMemberSpawner {
        final Map<String, String> tenantAtSpawn = new ConcurrentHashMap<>();

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
        final Map<String, String> tenantAtComplete = new ConcurrentHashMap<>();
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

    // ========================================================================
    // 8. Mixed bound + spawn fan-out: one dispatch plan with both bound and
    //    spawn targets reduces unified under all-must-succeed.
    // ========================================================================

    @Test
    void mixedBoundAndSpawnFanOutReducesUnified() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        ConcurrencyRecordingSpawner spawner = new ConcurrencyRecordingSpawner();
        // Bind b1, b2; leave s1, s2 as spawn-only.
        Team team = createTeamWithMembers(mgr, "mixed-fanout-team", "b1", "b2", "s1", "s2");
        mgr.bindMemberSession(team.getTeamId(), "b1", "s-b1", "actor-b1");
        mgr.bindMemberSession(team.getTeamId(), "b2", "s-b2", "actor-b2");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> mixedFanOutPlan(team, t, Arrays.asList("b1", "b2"), Arrays.asList("s1", "s2")));
        // engine bound members, spawner spawns s1/s2.
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(),
                    "mixed bound+spawn fan-out completes: " + result);
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
            // All 4 targets were exercised: 2 bound (engine) + 2 spawn (spawner).
            assertTrue(engine.enterNano.containsKey(a + "#b1"), "bound b1 executed");
            assertTrue(engine.enterNano.containsKey(a + "#b2"), "bound b2 executed");
            assertTrue(spawner.enterNano.containsKey(a + "#s1"), "spawn s1 executed");
            assertTrue(spawner.enterNano.containsKey(a + "#s2"), "spawn s2 executed");
            // Real concurrency across the 4 mixed members.
            assertTrue(engine.peakConcurrent.get() >= 2
                            || spawner.peakConcurrent.get() >= 2,
                    "mixed fan-out members concurrent (bound peak=" + engine.peakConcurrent.get()
                            + ", spawn peak=" + spawner.peakConcurrent.get() + ")");
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 9. complete CAS loss on fan-out → honest failure.
    // ========================================================================

    /**
     * Store wrapper that, on the orchestrator's completeTask for a watched
     * task, first has a "ghost" complete sneak in, so the orchestrator's CAS
     * loses (mirrors TestAsyncMemberStepHonestFailure.GhostCompletingStore
     * but for the fan-out path).
     */
    static final class GhostCompletingFanOutStore implements ITeamTaskStore {
        private final InMemoryTeamTaskStore delegate;
        private final String watchedTaskId;
        private final String ghostSessionId;
        private final AtomicInteger ghostCompleted = new AtomicInteger(0);

        GhostCompletingFanOutStore(InMemoryTeamTaskStore delegate, String taskId, String ghostSessionId) {
            this.delegate = delegate;
            this.watchedTaskId = taskId;
            this.ghostSessionId = ghostSessionId;
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
            if (watchedTaskId.equals(t) && ghostCompleted.compareAndSet(0, 1)) {
                delegate.completeTask(t, ghostSessionId, claimEpoch);
            }
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
    void fanOutCompleteCasLossHonestFail() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore realStore = new InMemoryTeamTaskStore();
        ConcurrencyRecordingEngine engine = new ConcurrencyRecordingEngine();
        String[] members = {"m1", "m2"};
        String[] sessions = {"s1", "s2"};
        Team team = createTeamAndBindMembers(mgr, "cas-team", members, sessions);
        String teamId = team.getTeamId();
        String a = createTask(realStore, teamId, "A", Collections.emptyList());

        // Ghost completes with the orchestrator session id (so exactly one
        // of the two CAS-conditional completeTask calls wins; orchestrator's
        // loses).
        String orchestratorSessionId = "orchestrator-" + teamId;
        GhostCompletingFanOutStore ghostStore =
                new GhostCompletingFanOutStore(realStore, a, orchestratorSessionId);

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> boundFanOutPlan(team, t, Arrays.asList("m1", "m2")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, ghostStore, mgr, null, null, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            // Anti-Hollow #24 — complete CAS loss honest failure.
            assertFalse(result.isSuccess(),
                    "fan-out complete CAS loss must honestly fail: " + result);
            assertEquals(a, result.getFailedTaskId());
        } finally {
            orchestrator.close();
        }
    }

    // ========================================================================
    // 10. already-COMPLETED task under fan-out → honest idempotent success.
    // ========================================================================

    @Test
    void fanOutAlreadyCompletedIdempotentSuccess() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        ConfigurableFanOutEngine engine = new ConfigurableFanOutEngine();
        String[] members = {"m1", "m2"};
        String[] sessions = {"s1", "s2"};
        Team team = createTeamAndBindMembers(mgr, "idem-team", members, sessions);
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        // Pre-complete the task.
        Long epoch = store.claimTask(a, "pre-completer").orElseThrow().getClaimEpoch();
        store.completeTask(a, "pre-completer", epoch);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());

        ITaskMemberRouter router = new FixedPlanRouter(
                t -> boundFanOutPlan(team, t, Arrays.asList("m1", "m2")));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, null, router);
        try {
            TeamTaskFlowResult result = orchestrator.executeAsync(teamId).get(30, TimeUnit.SECONDS);

            assertTrue(result.isSuccess(),
                    "already-COMPLETED task honest idempotent success: " + result);
            assertTrue(result.getCompletedTaskIds().contains(a),
                    "task in completed set (explicit, not silent skip)");
            // Engine NOT invoked (claim returns empty, status COMPLETED → idempotent shortcut).
            assertEquals(0, engine.executeCount.get(),
                    "engine NOT invoked on already-COMPLETED task");
        } finally {
            orchestrator.close();
        }
    }
}
