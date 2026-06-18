package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.DefaultMemberSpawner;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.NoOpTeamAclChecker;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.json.JSON;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for the {@code team-execute-flow} LLM tool entry point
 * (plan 239 Phase 2 / {@code L4-team-execute-flow-llm-tool}).
 *
 * <p>These tests drive the <b>full LLM tool entry path</b> end to end:
 * {@link TeamExecuteFlowExecutor#executeAsync} (the LLM tool call) →
 * session→team resolution → ACL check → per-invocation orchestrator
 * construction → nop-task DAG scheduler → unbound-member node → run-time
 * {@link IMemberSpawner#spawnMember} → {@link IAgentEngine#execute} →
 * {@code completeTask} → COMPLETED, with <b>no manual
 * {@code bindMemberSession} call at all</b>.
 *
 * <p>This is the Layer 4 top LLM-entry closure proof (Anti-Hollow #22): the
 * chain from the LLM tool call all the way to auto-spawn execution is fully
 * connected, not merely that each component exists.
 *
 * <p>Coverage map (maps to Phase 2 Exit Criteria):
 * <ul>
 *   <li>{@link #linearDagAutoSpawnViaToolEntryNoManualBind} — end-to-end
 *       linear A→B→C multi-node dependency DAG through the LLM tool entry:
 *       all tasks COMPLETED, dependency-ordered spawn execution, NO manual
 *       member binding.</li>
 *   <li>{@link #diamondDagAutoSpawnViaToolEntryDependencyOrdered} — end-to-end
 *       diamond A→{B,C}→D through the LLM tool entry + dependency order.</li>
 *   <li>{@link #noOpSpawnerZeroRegressionHonestFailureViaToolEntry} — zero-
 *       regression comparison: same DAG + NoOp spawner = honest failure body
 *       (no bound member and no spawn, honest failure, not silent success).</li>
 *   <li>{@link #boundPriorityViaToolEntrySpawnerNotConsulted} — bound-priority
 *       e2e: a bound member + functional spawner = no spawn (spawner not
 *       consulted), dispatch uses the bound session.</li>
 * </ul>
 */
public class TestTeamExecuteFlowEndToEnd {

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
     * Create a team whose members are declared but NONE bound — the auto-spawn
     * case. The LLM tool entry still needs to resolve the caller's team via
     * {@code getTeamBySession}, so we wrap the manager to resolve the caller
     * session to this team (simulating an external session→team association,
     * e.g. the caller is an orchestrator process with a known session).
     */
    private static Team createTeamWithoutBoundMember(InMemoryTeamManager mgr,
                                                     String memberName,
                                                     String memberAgentModel) {
        TeamSpec spec = new TeamSpec("e2e-tool-spawn-team", "d", "lead-agent",
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
        TeamSpec spec = new TeamSpec("e2e-tool-bound-team", "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberAgentModel, MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), memberName, sessionId, "actor-" + memberName);
        return team;
    }

    /**
     * Wrap a manager so {@code getTeamBySession(callerSession)} resolves to the
     * given team even when no member is bound to that session — so the LLM tool
     * entry can drive a fully-unbound team end to end (every node hits the
     * spawn path) with NO manual {@code bindMemberSession}.
     */
    private static ITeamManager sessionResolvesTo(InMemoryTeamManager delegate,
                                                  String callerSession, Team team) {
        return new ITeamManager() {
            @Override
            public Team createTeam(TeamSpec spec) {
                return delegate.createTeam(spec);
            }

            @Override
            public java.util.Optional<Team> getTeam(String teamId) {
                return delegate.getTeam(teamId);
            }

            @Override
            public java.util.Optional<Team> getTeamBySession(String sessionId) {
                if (callerSession.equals(sessionId)) {
                    return java.util.Optional.of(team);
                }
                return delegate.getTeamBySession(sessionId);
            }

            @Override
            public Team disbandTeam(String teamId) {
                return delegate.disbandTeam(teamId);
            }

            @Override
            public java.util.Collection<Team> getActiveTeams() {
                return delegate.getActiveTeams();
            }

            @Override
            public io.nop.ai.agent.team.TeamMember addMember(String teamId, TeamMemberSpec memberSpec) {
                return delegate.addMember(teamId, memberSpec);
            }

            @Override
            public boolean removeMember(String teamId, String memberName) {
                return delegate.removeMember(teamId, memberName);
            }

            @Override
            public boolean bindMemberSession(String teamId, String memberName,
                                             String sessionId, String actorId) {
                return delegate.bindMemberSession(teamId, memberName, sessionId, actorId);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamMember> getMember(String teamId, String memberName) {
                return delegate.getMember(teamId, memberName);
            }
        };
    }

    private static String createTask(InMemoryTeamTaskStore store, String teamId,
                                     String subject, List<String> blockedBy) {
        return store.createTask(teamId, subject, "desc-" + subject, blockedBy, "caller-session")
                .getTaskId();
    }

    private static AiToolCall toolCall() {
        AiToolCall c = new AiToolCall();
        c.setToolName("team-execute-flow");
        c.setId(1);
        c.setInput("{}");
        return c;
    }

    private static AgentToolExecuteContext ctxWith(ITeamManager mgr,
                                                   InMemoryTeamTaskStore store,
                                                   IAgentEngine engine,
                                                   String sessionId) {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engine, null, sessionId, "test-agent",
                null, null, null, null,
                mgr, store, NoOpTeamAclChecker.noOp());
    }

    private static List<String> extractTaskIds(List<AgentMessageRequest> requests) {
        List<String> ids = new ArrayList<>();
        for (AgentMessageRequest r : requests) {
            ids.add((String) r.getMetadata().get("teamTaskId"));
        }
        return ids;
    }

    // ========================================================================
    // 1. Linear A → B → C end-to-end through the LLM tool entry: full DAG
    //    path, dependency-ordered spawn execution, NO manual bindMemberSession
    //    (Anti-Hollow #22).
    // ========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void linearDagAutoSpawnViaToolEntryNoManualBind() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        ITeamManager mgrWithSession = sessionResolvesTo(mgr, "caller-session", team);

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(b));

        TeamExecuteFlowExecutor executor = new TeamExecuteFlowExecutor();
        CountingSpawner spawner = new CountingSpawner(engine);
        executor.setMemberSpawner(spawner);

        AiToolCallResult result = executor.executeAsync(toolCall(), ctxWith(mgrWithSession, store, engine, "caller-session"))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        // The LLM tool entry returns a success body.
        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.TRUE, body.get("success"),
                "LLM tool entry → orchestrator → auto-spawn → all COMPLETED: " + body);

        // Whole DAG COMPLETED — NO manual bindMemberSession at any point.
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(c).orElseThrow().getStatus());

        // Anti-Hollow: spawn execution happened once per task via the spawner
        // → IAgentEngine.execute, in dependency order A → B → C.
        assertEquals(3, engine.capturedRequests.size(),
                "exactly 3 spawned executions (one per node), no manual binding");
        assertEquals(Arrays.asList(a, b, c), extractTaskIds(engine.capturedRequests),
                "spawn dispatch order is A -> B -> C (dependency-ordered)");

        // DAG dependency-order evidence: B spawned strictly AFTER A's spawned
        // execution completed (proves the LLM tool entry → orchestrator → DAG
        // scheduler → run-time spawn chain is fully connected).
        assertTrue(engine.completedBeforeStart.get(b).contains(a),
                "B spawned strictly AFTER A's execution completed (run-time scheduling)");
        assertTrue(engine.completedBeforeStart.get(c).contains(b),
                "C spawned strictly AFTER B's execution completed (run-time scheduling)");

        // Wiring #23: the spawner was consulted exactly once per node.
        assertEquals(3, spawner.invocations.get(),
                "spawner consulted exactly once per node (wire-at-consumer wired through the tool)");

        // Anti-Hollow: every spawned execution used the agentModel from the
        // TeamMemberSpec (not hardcoded), and a fresh spawned session.
        Set<String> sessionIds = new HashSet<>();
        for (AgentMessageRequest req : engine.capturedRequests) {
            assertEquals("worker-agent-model", req.getAgentName(),
                    "spawned agentName = TeamMemberSpec.agentModel (not hardcoded)");
            sessionIds.add(req.getSessionId());
            assertTrue(req.getSessionId().startsWith("spawned-"),
                    "spawned session has spawned- prefix (not a bound session)");
        }
        assertEquals(3, sessionIds.size(),
                "each spawn got a fresh session id (per-node spawn, no reuse)");

        // The success body carries the startOrder / completionOrder evidence.
        assertNotNull(body.get("startOrder"));
        assertNotNull(body.get("completionOrder"));
    }

    // ========================================================================
    // 2. Diamond A → {B, C} → D end-to-end through the LLM tool entry.
    // ========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void diamondDagAutoSpawnViaToolEntryDependencyOrdered() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        ITeamManager mgrWithSession = sessionResolvesTo(mgr, "caller-session", team);

        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        String c = createTask(store, teamId, "C", Collections.singletonList(a));
        String d = createTask(store, teamId, "D", Arrays.asList(b, c));

        TeamExecuteFlowExecutor executor = new TeamExecuteFlowExecutor();
        CountingSpawner spawner = new CountingSpawner(engine);
        executor.setMemberSpawner(spawner);

        AiToolCallResult result = executor.executeAsync(toolCall(), ctxWith(mgrWithSession, store, engine, "caller-session"))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.TRUE, body.get("success"),
                "diamond DAG via LLM tool entry completes: " + body);
        for (String id : Arrays.asList(a, b, c, d)) {
            assertEquals(TeamTaskStatus.COMPLETED, store.getTask(id).orElseThrow().getStatus(),
                    "task " + id + " COMPLETED via auto-spawn through the tool (no manual bind)");
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
    // 3. Zero-regression comparison: same DAG + NoOp spawner = honest failure
    //    body (no bound member and no spawn → honest failure, never silent
    //    success). Exercises the LLM tool entry's failure-body mapping.
    // ========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void noOpSpawnerZeroRegressionHonestFailureViaToolEntry() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        ITeamManager mgrWithSession = sessionResolvesTo(mgr, "caller-session", team);
        String a = createTask(store, teamId, "A", Collections.emptyList());

        // NoOp spawner: the shipped default (no auto-spawn opt-in).
        TeamExecuteFlowExecutor executor = new TeamExecuteFlowExecutor();
        // setMemberSpawner(null) is null-safe → NoOp (zero regression).
        executor.setMemberSpawner(null);

        AiToolCallResult result = executor.executeAsync(toolCall(), ctxWith(mgrWithSession, store, engine, "caller-session"))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        // status="success" (DAG outcome, not technical fault), body success=false (honest).
        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, body.get("success"),
                "NoOp spawner + unbound team = honest failure body via tool entry (not silent success): " + body);
        assertEquals(a, body.get("failedTaskId"));
        // Honest failure: engine never invoked (NoOp declines to spawn).
        assertEquals(0, engine.capturedRequests.size(),
                "engine.execute NOT invoked (spawner honestly declined)");
    }

    // ========================================================================
    // 4. Bound-priority end-to-end via the tool entry: a bound member +
    //    functional spawner = no spawn (spawner not consulted), dispatch uses
    //    the bound session.
    // ========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void boundPriorityViaToolEntrySpawnerNotConsulted() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "bound-worker-session",
                "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));

        TeamExecuteFlowExecutor executor = new TeamExecuteFlowExecutor();
        CountingSpawner spawner = new CountingSpawner(engine);
        executor.setMemberSpawner(spawner);

        // The bound worker session resolves the caller's team.
        AiToolCallResult result = executor.executeAsync(toolCall(),
                ctxWith(mgr, store, engine, "bound-worker-session"))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.TRUE, body.get("success"),
                "bound-member team completes via the bound path through the tool (not spawn): " + body);
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
        // Spawner NOT consulted — bound member takes priority.
        assertEquals(0, spawner.invocations.get(),
                "spawner NOT consulted — bound member takes priority");
        // Dispatch used the BOUND session (no spawned- prefix).
        assertEquals(2, engine.capturedRequests.size());
        for (AgentMessageRequest req : engine.capturedRequests) {
            assertFalse(req.getSessionId().startsWith("spawned-"),
                    "dispatch used bound session (no spawned- prefix → spawner bypassed)");
        }
    }

    private static void assertNotNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNotNull(o);
    }
}
