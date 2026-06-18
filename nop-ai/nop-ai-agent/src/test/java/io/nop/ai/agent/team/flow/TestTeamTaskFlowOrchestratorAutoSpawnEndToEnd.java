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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 238 (L4-orchestrator-auto-spawn-integration) end-to-end tests for the
 * orchestrator's run-time auto-spawn execution path.
 *
 * <p>These tests drive the full programmatic orchestration path end to end:
 * {@code orchestrator.execute(teamId)} → nop-task DAG scheduler → unbound-
 * member node → run-time {@link IMemberSpawner#spawnMember} →
 * {@link IAgentEngine#execute} → {@code completeTask} → COMPLETED, with
 * <b>no manual {@code bindMemberSession} call at all</b>. This is the
 * programmatic-orchestrator auto-spawn closure proof (Anti-Hollow #22): the
 * team declares its member spec but never binds a session, yet the
 * orchestrator materialises a fresh member-agent execution per node via the
 * spawner at the correct DAG point.
 *
 * <p>Coverage map (maps to Phase 2 Exit Criteria):
 * <ul>
 *   <li>{@link #linearDagAutoSpawnCompletesNoManualBind} — end-to-end linear
 *       A→B→C multi-node dependency DAG auto-spawn: all tasks COMPLETED,
 *       dependency-ordered spawn execution (B strictly after A completed),
 *       NO manual member binding.</li>
 *   <li>{@link #diamondDagAutoSpawnCompletesDependencyOrdered} — end-to-end
 *       diamond A→{B,C}→D multi-node DAG auto-spawn + dependency order
 *       (D strictly after B and C completed).</li>
 *   <li>{@link #noOpSpawnerZeroRegressionFails} — zero-regression comparison:
 *       same DAG + NoOp spawner = failed result (honest failure, no spawn).</li>
 *   <li>{@link #boundPriorityE2eSpawnerNotConsulted} — bound-priority e2e: a
 *       bound member + functional spawner = no spawn (spawner not consulted),
 *       dispatch uses the bound session.</li>
 * </ul>
 */
public class TestTeamTaskFlowOrchestratorAutoSpawnEndToEnd {

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

    /**
     * Counting spawner wrapping {@link DefaultMemberSpawner} (so the bound-
     * priority test can assert "spawner not consulted when a bound member
     * exists").
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
        TeamSpec spec = new TeamSpec("e2e-orch-spawn-team", "d", "lead-agent",
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
        TeamSpec spec = new TeamSpec("e2e-orch-bound-team", "d", "lead-agent",
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

    private static List<String> extractTaskIds(List<AgentMessageRequest> requests) {
        List<String> ids = new ArrayList<>();
        for (AgentMessageRequest r : requests) {
            ids.add((String) r.getMetadata().get("teamTaskId"));
        }
        return ids;
    }

    // ========================================================================
    // 1. Linear A → B → C end-to-end auto-spawn: full DAG path, dependency-
    //    ordered spawn execution, NO manual bindMemberSession (Anti-Hollow #22).
    // ========================================================================

    @Test
    void linearDagAutoSpawnCompletesNoManualBind() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(b));

        CountingSpawner spawner = new CountingSpawner(engine);
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        // Whole DAG COMPLETED — programmatic orchestration with auto-spawn,
        // NO manual bindMemberSession.
        assertTrue(result.isSuccess(),
                "linear A->B->C DAG auto-spawn completes: " + result);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus());

        // Anti-Hollow: spawn execution happened once per task via the spawner
        // → IAgentEngine.execute, in dependency order A → B → C.
        assertEquals(3, engine.capturedRequests.size(),
                "exactly 3 spawned executions (one per node), no manual binding");
        assertEquals(Arrays.asList(a, b, c), extractTaskIds(engine.capturedRequests),
                "spawn dispatch order is A -> B -> C (dependency-ordered)");

        // Stronger Anti-Hollow dependency-order evidence: B spawned strictly
        // AFTER A's spawned execution completed (proves spawn happened at node
        // run time, not at graph build time — plan 238 decision 1).
        assertTrue(engine.completedBeforeStart.get(b).contains(a),
                "B spawned strictly AFTER A's execution completed (run-time scheduling)");
        assertTrue(engine.completedBeforeStart.get(c).contains(b),
                "C spawned strictly AFTER B's execution completed (run-time scheduling)");

        // Wiring #23: the spawner was consulted exactly once per node.
        assertEquals(3, spawner.invocations.get(),
                "spawner consulted exactly once per node (no bound member to bypass it)");

        // Anti-Hollow: every spawned execution used a fresh spawned session
        // (per-node spawn, no reuse).
        Set<String> sessionIds = new HashSet<>();
        for (AgentMessageRequest req : engine.capturedRequests) {
            sessionIds.add(req.getSessionId());
        }
        assertEquals(3, sessionIds.size(),
                "each spawn got a fresh session id (per-node spawn, no reuse)");
        for (String sid : sessionIds) {
            assertTrue(sid.startsWith("spawned-"),
                    "spawned session has spawned- prefix (not a bound session)");
        }
    }

    // ========================================================================
    // 2. Diamond A → {B, C} → D end-to-end auto-spawn + dependency order.
    // ========================================================================

    @Test
    void diamondDagAutoSpawnCompletesDependencyOrdered() {
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
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertTrue(result.isSuccess(),
                "diamond A->{B,C}->D DAG auto-spawn completes: " + result);
        for (String id : Arrays.asList(a, b, c, d)) {
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                    "task " + id + " COMPLETED via auto-spawn (no manual bind)");
        }

        List<String> taskIds = extractTaskIds(engine.capturedRequests);
        assertEquals(a, taskIds.get(0), "A spawned first (no deps)");
        assertEquals(d, taskIds.get(taskIds.size() - 1), "D spawned last (depends on B and C)");

        // Anti-Hollow dependency order: B and C both after A; D after both.
        assertTrue(engine.completedBeforeStart.get(b).contains(a), "B spawned after A completed");
        assertTrue(engine.completedBeforeStart.get(c).contains(a), "C spawned after A completed");
        assertTrue(engine.completedBeforeStart.get(d).contains(b), "D spawned after B completed");
        assertTrue(engine.completedBeforeStart.get(d).contains(c), "D spawned after C completed");
    }

    // ========================================================================
    // 3. Zero-regression comparison: same DAG + NoOp spawner = failed result
    //    (no bound member and no spawn → honest failure, never silent success).
    // ========================================================================

    @Test
    void noOpSpawnerZeroRegressionFails() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        // NoOp spawner: the shipped default (no auto-spawn opt-in).
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, NoOpMemberSpawner.noOp());
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertFalse(result.isSuccess(),
                "NoOp spawner + unbound team = honest failure (zero regression): " + result);
        // Honest failure: engine never invoked (NoOp declines to spawn).
        assertEquals(0, engine.capturedRequests.size(),
                "engine.execute NOT invoked (spawner honestly declined)");
        // The failed task is reported.
        assertEquals(a, result.getFailedTaskId());
    }

    // ========================================================================
    // 4. Bound-priority end-to-end: a bound member + functional spawner =
    //    no spawn (spawner not consulted), dispatch uses the bound session.
    // ========================================================================

    @Test
    void boundPriorityE2eSpawnerNotConsulted() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "bound-worker-session",
                "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));

        CountingSpawner spawner = new CountingSpawner(engine);
        TeamTaskFlowOrchestrator orchestrator =
                new TeamTaskFlowOrchestrator(engine, store, mgr, null, spawner);
        TeamTaskFlowResult result = orchestrator.execute(teamId);

        assertTrue(result.isSuccess(),
                "bound-member team completes via the bound path (not spawn): " + result);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        // Spawner NOT consulted — bound member takes priority (decision 2).
        assertEquals(0, spawner.invocations.get(),
                "spawner NOT consulted — bound member takes priority");
        // Dispatch used the BOUND session (no spawned- prefix).
        assertEquals(2, engine.capturedRequests.size());
        for (AgentMessageRequest req : engine.capturedRequests) {
            assertFalse(req.getSessionId().startsWith("spawned-"),
                    "dispatch used bound session (no spawned- prefix → spawner bypassed)");
        }
    }
}
