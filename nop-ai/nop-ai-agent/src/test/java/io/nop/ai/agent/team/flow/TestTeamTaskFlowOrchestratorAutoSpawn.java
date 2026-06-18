package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.DefaultMemberSpawner;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 238 (L4-orchestrator-auto-spawn-integration) focused tests for the
 * orchestrator's run-time auto-spawn execution path.
 *
 * <p>These tests verify the core behaviour change delivered by plan 238:
 * when a team task node has no already-bound member, the orchestrator no
 * longer fast-fails at build time. Instead it selects a
 * {@link SpawnMemberAgentTaskStep} and, at <b>node run time</b> (when the
 * nop-task DAG scheduler triggers the node, after its {@code blockedBy}
 * predecessors have completed), the step consults the injected
 * {@link IMemberSpawner} to spawn a member agent.
 *
 * <p>Coverage map (maps to Phase 1 Exit Criteria):
 * <ul>
 *   <li>{@link #boundMemberPrioritySpawnerNotConsulted} — bound-member
 *       priority: a bound member means the spawner is NEVER called (the bound
 *       path is line-for-line unchanged from plan 233).</li>
 *   <li>{@link #noBoundMemberFunctionalSpawnerSpawnsAtRunTime} — no bound
 *       member + functional spawner = the node spawns and executes at run
 *       time, task reaches COMPLETED.</li>
 *   <li>{@link #spawnHappensAtRunTimeNotBuildTimeDependencyOrderPreserved} —
 *       the spawn order follows the DAG dependency order (not the task-store
 *       insertion order), proving the spawn is driven by the runtime DAG
 *       scheduler, not the build-time loop (plan 238 decision 1).</li>
 *   <li>{@link #noBoundMemberNoOpSpawnerHonestFailLeavesClaimed} — NoOp
 *       spawner honestly declines (NO_SPAWN) → honest failed result; the task
 *       is left in CLAIMED (not abandoned), per decision 3.</li>
 *   <li>{@link #dispatchedNonCompletedHonestFailLeavesClaimed} — a DISPATCHED
 *       result whose wrapped execution is non-completed → honest failed
 *       result, task left CLAIMED.</li>
 *   <li>{@link #spawnFailedHonestFailLeavesClaimed} — SPAWN_FAILED → honest
 *       failed result, task left CLAIMED.</li>
 *   <li>{@link #spawnerThrowsHonestFailLeavesClaimed} — a spawner that throws
 *       (contract violation) → honest failed result, task left CLAIMED.</li>
 *   <li>{@link #spawnerWiredViaConstructorAndSetterWireAtConsumer} — wiring
 *       verification (#23): spawner injected via constructor and setter
 *       (wire-at-consumer, null-safe → NoOp).</li>
 *   <li>{@link #spawnUsesAgentModelFromTeamMemberSpecNotHardcoded} — Anti-
 *       Hollow: the spawned agentModel comes from the {@link TeamMemberSpec}
 *       (not hardcoded).</li>
 * </ul>
 */
public class TestTeamTaskFlowOrchestratorAutoSpawn {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Recording mock member-agent engine — captures spawned execution order,
    //   the agentName / sessionId used (Anti-Hollow evidence), and can be
    //   configured to fail (by status or exception) on a specific task.
    // ========================================================================

    static final class RecordingAgentEngine implements IAgentEngine {
        final List<AgentMessageRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Integer> startSeq = new ConcurrentHashMap<>();
        final Map<String, Integer> executeCompletedSeq = new ConcurrentHashMap<>();
        final AtomicInteger seq = new AtomicInteger(0);
        final AtomicInteger completedSeq = new AtomicInteger(0);
        /** For task B (key), the set of task ids whose execute had completed before B started. */
        final Map<String, Set<String>> completedBeforeStart = new ConcurrentHashMap<>();
        final String failOnTaskId;
        final boolean failByStatus;

        RecordingAgentEngine() {
            this(null, false);
        }

        RecordingAgentEngine(String failOnTaskId, boolean failByStatus) {
            this.failOnTaskId = failOnTaskId;
            this.failByStatus = failByStatus;
        }

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

            if (failOnTaskId != null && failOnTaskId.equals(taskId)) {
                if (!failByStatus) {
                    CompletableFuture<AgentExecutionResult> f = new CompletableFuture<>();
                    f.completeExceptionally(new RuntimeException("spawned-agent-boom:" + taskId));
                    return f;
                }
                return CompletableFuture.completedFuture(
                                new AgentExecutionResult(AgentExecStatus.failed, null,
                                        Collections.emptyList(), 0, 0L, 0L, "spawned-failed:" + taskId))
                        .whenComplete((r, err) -> executeCompletedSeq.put(taskId, completedSeq.incrementAndGet()));
            }
            return CompletableFuture.completedFuture(
                            new AgentExecutionResult(AgentExecStatus.completed, "ok:" + taskId,
                                    Collections.emptyList(), 1, 10L, 1L, null))
                    .whenComplete((r, err) -> executeCompletedSeq.put(taskId, completedSeq.incrementAndGet()));
        }
    }

    /**
     * Counting spawner that wraps a delegate and records each invocation's
     * task id, so tests can assert "spawner consulted / not consulted" and
     * the spawn order.
     */
    static final class CountingSpawner implements IMemberSpawner {
        final AtomicInteger invocations = new AtomicInteger(0);
        final List<String> spawnedTaskIds = Collections.synchronizedList(new ArrayList<>());
        final IMemberSpawner delegate;

        CountingSpawner(IMemberSpawner delegate) {
            this.delegate = delegate;
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            invocations.incrementAndGet();
            spawnedTaskIds.add(request.getTask().getTaskId());
            return delegate.spawnMember(request);
        }
    }

    /**
     * Configurable stub spawner that returns a fixed {@link SpawnMemberResult}
     * for every call (used to drive the NO_SPAWN / SPAWN_FAILED /
     * DISPATCHED-non-completed failure paths without a real engine).
     */
    static final class StubSpawner implements IMemberSpawner {
        final SpawnMemberResult fixedResult;
        final RuntimeException throwIfSet;

        StubSpawner(SpawnMemberResult fixedResult) {
            this(fixedResult, null);
        }

        StubSpawner(SpawnMemberResult fixedResult, RuntimeException throwIfSet) {
            this.fixedResult = fixedResult;
            this.throwIfSet = throwIfSet;
        }

        @Override
        public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
            if (throwIfSet != null) {
                throw throwIfSet;
            }
            return fixedResult;
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Create a team with a declarative member spec but NO bound session — the
     * auto-spawn case. The orchestrator will not find a bound member and will
     * select a {@link SpawnMemberAgentTaskStep} for each node.
     */
    private static Team createTeamWithoutBoundMember(InMemoryTeamManager mgr,
                                                     String memberName,
                                                     String memberAgentModel) {
        TeamSpec spec = new TeamSpec("spawn-team", "d", "lead-agent",
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
        TeamSpec spec = new TeamSpec("bound-team", "d", "lead-agent",
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

    // ========================================================================
    // 1. Bound-member priority: a bound member means the spawner is NEVER
    //    consulted (bound path line-for-line unchanged from plan 233).
    // ========================================================================

    @Test
    void boundMemberPrioritySpawnerNotConsulted() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "bound-worker-session", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        CountingSpawner spawner = new CountingSpawner(new DefaultMemberSpawner(engine));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertTrue(result.isSuccess(), "bound-member team completes normally: " + result);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(0, spawner.invocations.get(),
                "spawner NOT consulted — bound member takes priority (plan 238 decision 2)");
        assertEquals(1, engine.capturedRequests.size());
        // Bound path uses the BOUND session (no spawned- prefix).
        assertEquals("bound-worker-session", engine.capturedRequests.get(0).getSessionId(),
                "dispatch used the BOUND session (not a spawned session)");
        assertFalse(engine.capturedRequests.get(0).getSessionId().startsWith("spawned-"),
                "bound session has no spawned- prefix → spawner was bypassed");
    }

    // ========================================================================
    // 2. No bound member + functional spawner = node spawns and executes at
    //    run time, task reaches COMPLETED (NO manual bindMemberSession).
    // ========================================================================

    @Test
    void noBoundMemberFunctionalSpawnerSpawnsAtRunTime() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        CountingSpawner spawner = new CountingSpawner(new DefaultMemberSpawner(engine));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertTrue(result.isSuccess(),
                "unbound team + functional spawner completes via auto-spawn: " + result);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "task COMPLETED via spawned execution (no manual bind)");
        // Wiring #23: the spawner (→ DefaultMemberSpawner → IAgentEngine.execute)
        // was really invoked at run time.
        assertEquals(1, spawner.invocations.get(),
                "spawner consulted exactly once for the unbound node");
        assertEquals(1, engine.capturedRequests.size(),
                "IAgentEngine.execute invoked by the spawner (real spawn execution)");
        // The spawned execution used a fresh spawned session.
        assertTrue(engine.capturedRequests.get(0).getSessionId().startsWith("spawned-"),
                "spawn path used a spawned- session (not a bound session)");
    }

    // ========================================================================
    // 3. Spawn happens at RUN TIME, not BUILD TIME (plan 238 decision 1).
    //    Tasks are inserted in REVERSE dependency order (C, B, A) but the DAG
    //    is A -> B -> C. If spawn happened at build time (iterating the task
    //    store's insertion order), the spawn order would be [C, B, A]. At run
    //    time the DAG scheduler runs A first, then B, then C — so spawn order
    //    [A, B, C] proves the runtime scheduler drove the spawns (not the
    //    build loop). Additionally B spawns strictly AFTER A's spawned
    //    execution completed.
    // ========================================================================

    @Test
    void spawnHappensAtRunTimeNotBuildTimeDependencyOrderPreserved() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();

        // Build the DAG A -> B -> C. (blockedBy must reference an existing task
        // id, so A is created first; then B blockedBy A; then C blockedBy B.)
        String ta = store.createTask(teamId, "A", "d", Collections.emptyList(), "lead").getTaskId();
        String tb = store.createTask(teamId, "B", "d", Collections.singletonList(ta), "lead").getTaskId();
        String tc = store.createTask(teamId, "C", "d", Collections.singletonList(tb), "lead").getTaskId();

        // Wrap the store so getTasksByTeam returns the tasks in REVERSE
        // insertion order [C, B, A] (the build loop iterates this list). If
        // spawn happened at build time (iterating the list), the spawn order
        // would be [C, B, A]; at run time the DAG scheduler yields [A, B, C].
        io.nop.ai.agent.team.ITeamTaskStore reversedStore = reversedInsertionOrder(store);

        CountingSpawner spawner = new CountingSpawner(new DefaultMemberSpawner(engine));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, reversedStore, mgr, null, spawner);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertTrue(result.isSuccess(),
                "unbound team + functional spawner completes the A->B->C DAG: " + result);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(ta).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(tb).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(tc).orElseThrow().getStatus());

        // The spawn order follows DEPENDENCY order [A, B, C], NOT the reversed
        // insertion order [C, B, A] that the build loop iterated — proving the
        // runtime DAG scheduler drove the spawns (plan 238 decision 1). The
        // spawner is zero-called at build time: it is only ever invoked from
        // inside the node step's execute(), which the scheduler triggers.
        assertEquals(Arrays.asList(ta, tb, tc), spawner.spawnedTaskIds,
                "spawn order follows DAG dependency order [A,B,C], not build-loop insertion "
                        + "order [C,B,A] — spawn happened at run time: " + spawner.spawnedTaskIds);

        // Stronger Anti-Hollow: B spawned strictly AFTER A's execution completed.
        assertTrue(engine.completedBeforeStart.get(tb).contains(ta),
                "B spawned strictly AFTER A's spawned execution completed (run-time scheduling)");
        assertTrue(engine.completedBeforeStart.get(tc).contains(tb),
                "C spawned strictly AFTER B's spawned execution completed (run-time scheduling)");
    }

    /**
     * Wrap a store so {@code getTasksByTeam} returns tasks in <b>reverse</b>
     * order — simulates a store whose insertion order differs from the
     * dependency order, so a build-time spawn (iterating the list) is
     * distinguishable from a run-time spawn (driven by the DAG scheduler).
     */
    private static io.nop.ai.agent.team.ITeamTaskStore reversedInsertionOrder(
            io.nop.ai.agent.team.ITeamTaskStore delegate) {
        return new io.nop.ai.agent.team.ITeamTaskStore() {
            @Override
            public TeamTask createTask(String t, String s, String d, List<String> b, String c) {
                return delegate.createTask(t, s, d, b, c);
            }

            @Override
            public java.util.Optional<TeamTask> getTask(String taskId) {
                return delegate.getTask(taskId);
            }

            @Override
            public List<TeamTask> getTasksByTeam(String tid) {
                List<TeamTask> list = new ArrayList<>(delegate.getTasksByTeam(tid));
                Collections.reverse(list);
                return list;
            }

            @Override
            public List<TeamTask> getTasksByCreator(String c) {
                return delegate.getTasksByCreator(c);
            }

            @Override
            public java.util.Optional<TeamTask> claimTask(String t, String b) {
                return delegate.claimTask(t, b);
            }

            @Override
            public java.util.Optional<TeamTask> completeTask(String t, String b) {
                return delegate.completeTask(t, b);
            }

            @Override
            public java.util.Optional<TeamTask> abandonTask(String t, String b) {
                return delegate.abandonTask(t, b);
            }

            @Override
            public java.util.Optional<TeamTask> reclaimTask(String t, String b) {
                return delegate.reclaimTask(t, b);
            }
        };
    }

    // ========================================================================
    // 4. No bound member + NoOp spawner = honest fail (NO_SPAWN), task left
    //    CLAIMED (not abandoned) — decision 3.
    // ========================================================================

    @Test
    void noBoundMemberNoOpSpawnerHonestFailLeavesClaimed() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        // Explicit NoOp spawner (also the shipped default).
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, NoOpMemberSpawner.noOp());
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertFalse(result.isSuccess(),
                "NoOp spawner must honestly fail, not silently succeed: " + result);
        assertEquals(a, result.getFailedTaskId());
        // Honest failure: task left CLAIMED (not abandoned), per decision 3.
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                "failed unbound task left CLAIMED (not abandoned)");
        assertNotEquals(TeamTaskStatus.ABANDONED, store.getTask(a).orElseThrow().getStatus(),
                "orchestrator does NOT abandon (that is the daemon's model) — decision 3");
        // Engine never invoked (NoOp declines before any execution).
        assertEquals(0, engine.capturedRequests.size(),
                "NoOp declines to spawn → no engine execution");
    }

    // ========================================================================
    // 5. DISPATCHED but non-completed = honest fail, task left CLAIMED.
    // ========================================================================

    @Test
    void dispatchedNonCompletedHonestFailLeavesClaimed() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        // Spawner returns DISPATCHED with a FAILED execution result.
        AgentExecutionResult failedResult = new AgentExecutionResult(
                AgentExecStatus.failed, null, Collections.emptyList(), 0, 0L, 0L, "agent-failed");
        StubSpawner spawner = new StubSpawner(
                SpawnMemberResult.dispatched(failedResult, "worker-agent-model", "spawned-x"));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);

        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertFalse(result.isSuccess(),
                "DISPATCHED non-completed must honestly fail: " + result);
        assertEquals(a, result.getFailedTaskId());
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                "failed task left CLAIMED (not abandoned) — decision 3");
    }

    // ========================================================================
    // 6. SPAWN_FAILED = honest fail, task left CLAIMED.
    // ========================================================================

    @Test
    void spawnFailedHonestFailLeavesClaimed() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        StubSpawner spawner = new StubSpawner(
                SpawnMemberResult.spawnFailed("spawned agent threw: boom"));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);

        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertFalse(result.isSuccess(), "SPAWN_FAILED must honestly fail: " + result);
        assertEquals(a, result.getFailedTaskId());
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                "failed task left CLAIMED (not abandoned) — decision 3");
    }

    // ========================================================================
    // 7. Spawner THROWS (contract violation) = honest fail, task left CLAIMED.
    //    (Minimum Rules #24: a spawner that throws rather than returning
    //    SPAWN_FAILED is still handled honestly, not silently swallowed.)
    // ========================================================================

    @Test
    void spawnerThrowsHonestFailLeavesClaimed() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        StubSpawner spawner = new StubSpawner(null, new RuntimeException("spawner-contract-violation"));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);

        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertFalse(result.isSuccess(),
                "spawner throwing (contract violation) must honestly fail, not be swallowed: " + result);
        assertEquals(a, result.getFailedTaskId());
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(a).orElseThrow().getStatus(),
                "failed task left CLAIMED (not abandoned) — decision 3");
    }

    // ========================================================================
    // 8. Wiring verification (#23): spawner injected via constructor and
    //    setter (wire-at-consumer, null-safe → NoOp shipped default).
    // ========================================================================

    @Test
    void spawnerWiredViaConstructorAndSetterWireAtConsumer() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();

        // Constructor wiring (wire-at-consumer): spawner into the orchestrator.
        DefaultMemberSpawner spawner = new DefaultMemberSpawner(engine);
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);
        assertEquals(spawner, orchestrator.getMemberSpawner(),
                "spawner injected via the spawner-aware constructor (wire-at-consumer)");

        // Default (no spawner supplied) → NoOp shipped default.
        TeamTaskFlowOrchestrator defaultOrchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr);
        assertTrue(defaultOrchestrator.getMemberSpawner() instanceof NoOpMemberSpawner,
                "default orchestrator ships NoOp spawner (zero regression)");

        // Setter wiring, then null-safe reset → NoOp.
        defaultOrchestrator.setMemberSpawner(spawner);
        assertEquals(spawner, defaultOrchestrator.getMemberSpawner(),
                "spawner re-wired via setter");
        defaultOrchestrator.setMemberSpawner(null);
        assertTrue(defaultOrchestrator.getMemberSpawner() instanceof NoOpMemberSpawner,
                "null setter resets to NoOp shipped default (zero regression)");
    }

    // ========================================================================
    // 9. Anti-Hollow: spawned agentModel comes from TeamMemberSpec (not
    //    hardcoded in the orchestrator or step).
    // ========================================================================

    @Test
    void spawnUsesAgentModelFromTeamMemberSpecNotHardcoded() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        // Distinctive agentModel name — if anything were hardcoded, this fails.
        Team team = createTeamWithoutBoundMember(mgr, "worker",
                "very-distinctive-orchestrator-spawn-agent-model");
        String teamId = team.getTeamId();
        createTask(store, teamId, "A", Collections.emptyList());

        CountingSpawner spawner = new CountingSpawner(new DefaultMemberSpawner(engine));
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);
        orchestrator.execute(teamId);

        assertEquals(1, engine.capturedRequests.size());
        AgentMessageRequest captured = engine.capturedRequests.get(0);
        assertEquals("very-distinctive-orchestrator-spawn-agent-model", captured.getAgentName(),
                "spawned agent name = TeamMemberSpec.agentModel (read from spec, not hardcoded)");
    }
}
