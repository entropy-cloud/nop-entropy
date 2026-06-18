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
import io.nop.ai.agent.team.ITeamAclChecker;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.NoOpTeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamAclDecision;
import io.nop.ai.agent.team.TeamMember;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for {@link TeamExecuteFlowExecutor} (plan 239 /
 * {@code L4-team-execute-flow-llm-tool}).
 *
 * <p>Verifies every honest / denial / failure path of the tool, plus the
 * wiring of the per-invocation orchestrator + the wire-at-consumer member
 * spawner. Coverage map (maps to Phase 1 Exit Criteria):
 * <ul>
 *   <li>{@link #teamResolvedFromSessionNotFromParameter} — DD#2: caller's team
 *       is resolved from the session, no teamId parameter exposed.</li>
 *   <li>{@link #noOpTeamManagerReportsHonestly} / {@link #noOpTaskStoreReportsHonestly}
 *       — Minimum Rules #24: NoOp/null services short-circuit with an honest
 *       "not enabled" report.</li>
 *   <li>{@link #callerNotInTeamReturnsError} — caller not bound to a team =
 *       honest errorResult.</li>
 *   <li>{@link #aclDenialHonestDeniedBodyOrchestratorNotInvoked} — DD#5: ACL
 *       denial returns an honest denied body; the store is NOT touched and the
 *       orchestrator is NOT invoked (Wiring #23).</li>
 *   <li>{@link #successDagMapsToSuccessBody} — DD#4: a successful DAG maps to
 *       a success body carrying completedTaskIds / startOrder / completionOrder.</li>
 *   <li>{@link #nodeFailureMapsToHonestFailureBody} — DD#4: a node failure
 *       maps to a {@code success:false} body carrying failedTaskId /
 *       skippedTaskIds (honest, not silent success).</li>
 *   <li>{@link #emptyTaskSetStructuralFailureMapsToErrorResult} /
 *       {@link #unknownTeamStructuralFailureMapsToErrorResult} /
 *       {@link #cyclicBlockedByStructuralFailureMapsToErrorResult} — DD#4:
 *       structural fast-failures map to {@link AiToolCallResult#errorResult}.</li>
 *   <li>{@link #functionalSpawnerUnboundMemberRunTimeSpawn} — DD#3 + Wiring
 *       #23: a functional spawner is consulted at run time and the unbound
 *       node completes via spawn (no manual bindMemberSession).</li>
 *   <li>{@link #noOpSpawnerUnboundMemberHonestFailure} — NoOp spawner +
 *       unbound member = honest {@code success:false} body (no silent
 *       success).</li>
 *   <li>{@link #spawnerWiredViaSetterWireAtConsumer} — Wiring #23: spawner is
 *       wire-at-consumer injected via the setter, null-safe → NoOp default.</li>
 * </ul>
 */
public class TestTeamExecuteFlowExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Recording mock member-agent engine (completed by default; can be told to
    //   fail a specific task by status).
    // ========================================================================

    static final class RecordingAgentEngine implements IAgentEngine {
        final List<AgentMessageRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());
        final String failOnTaskId;

        RecordingAgentEngine() {
            this(null);
        }

        RecordingAgentEngine(String failOnTaskId) {
            this.failOnTaskId = failOnTaskId;
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            capturedRequests.add(request);
            String taskId = (String) request.getMetadata().get("teamTaskId");
            if (failOnTaskId != null && failOnTaskId.equals(taskId)) {
                return CompletableFuture.completedFuture(new AgentExecutionResult(
                        AgentExecStatus.failed, null, Collections.emptyList(), 0, 0L, 0L, "agent-failed"));
            }
            return CompletableFuture.completedFuture(new AgentExecutionResult(
                    AgentExecStatus.completed, "ok:" + taskId,
                    Collections.emptyList(), 1, 10L, 1L, null));
        }
    }

    /**
     * Counting spawner wrapping {@link DefaultMemberSpawner} (so tests can
     * assert spawner consulted / not consulted).
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

    private static AiToolCall call() {
        AiToolCall c = new AiToolCall();
        c.setToolName("team-execute-flow");
        c.setId(1);
        // The tool takes no parameters (team resolved from session).
        c.setInput("{}");
        return c;
    }

    private static AgentToolExecuteContext ctxWith(ITeamManager mgr,
                                                   ITeamTaskStore store,
                                                   IAgentEngine engine,
                                                   ITeamAclChecker checker,
                                                   IMemberSpawner spawner,
                                                   String sessionId) {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engine, null, sessionId, "test-agent",
                null, null, null, null,
                mgr, store, checker);
        // Note: the spawner is wire-at-consumer on the EXECUTOR (DD#3), not on
        // the context — set via TeamExecuteFlowExecutor.setMemberSpawner.
    }

    /**
     * Create a team whose members are ALL declared but NONE bound (the
     * auto-spawn case). The orchestrator's resolveMember will return null for
     * every node → spawn path. NB: because no session is bound,
     * {@code getTeamBySession} cannot resolve the caller on its own — pair
     * this with {@link #sessionResolvesTo} so the tool can still resolve the
     * caller's team (simulating an external session→team association).
     */
    private static Team createTeamWithoutBoundMember(InMemoryTeamManager mgr,
                                                     String memberName,
                                                     String memberAgentModel) {
        TeamSpec spec = new TeamSpec("ExecTeam", "d", "lead-agent",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec(memberName, memberAgentModel, MemberRole.MEMBER)),
                0);
        return mgr.createTeam(spec);
        // Deliberately NOT calling mgr.bindMemberSession(...).
    }

    /**
     * Wrap a manager so {@code getTeamBySession(callerSession)} resolves to the
     * given team even when no member is bound to that session — simulating an
     * external session→team association so the tool can drive a fully-unbound
     * team (every node hits the spawn path).
     */
    private static ITeamManager sessionResolvesTo(InMemoryTeamManager delegate,
                                                  String callerSession, Team team) {
        return new ForwardingTeamManager(delegate) {
            @Override
            public java.util.Optional<Team> getTeamBySession(String sessionId) {
                if (callerSession.equals(sessionId)) {
                    return java.util.Optional.of(team);
                }
                return delegate.getTeamBySession(sessionId);
            }
        };
    }


    /**
     * Create a team whose MEMBER member IS bound (bound-member priority case).
     */
    private static Team createTeamWithBoundMember(InMemoryTeamManager mgr,
                                                  String memberName, String sessionId,
                                                  String memberAgentModel) {
        TeamSpec spec = new TeamSpec("ExecTeam", "d", "lead-agent",
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
    // 1. DD#2: caller's team resolved from session, not from a parameter.
    // ========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void teamResolvedFromSessionNotFromParameter() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-sess", "worker-agent-model");
        String teamId = team.getTeamId();
        createTask(store, teamId, "A", Collections.emptyList());

        // The caller's session resolves the team — the tool call carries no
        // teamId parameter (see call()).
        AgentToolExecuteContext ctx = ctxWith(mgr, store, engine,
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.TRUE, body.get("success"),
                "team resolved from session → DAG executed → success body");
        // The bound member session was used to execute the member agent —
        // proves the session→team resolution happened (no teamId in the call).
        assertEquals(1, engine.capturedRequests.size(),
                "bound-member dispatch happened once for the single task");
    }

    // ========================================================================
    // 2. Minimum Rules #24: NoOp/null teamManager + taskStore honest reports.
    // ========================================================================

    @Test
    void noOpTeamManagerReportsHonestly() throws Exception {
        AgentToolExecuteContext ctx = ctxWith(NoOpTeamManager.noOp(),
                new InMemoryTeamTaskStore(), new RecordingAgentEngine(),
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "sess-1");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "NoOp teamManager → success (honest report, not crash)");
        assertTrue(result.getOutput().getBody().contains("not enabled"),
                "NoOp teamManager must honestly report not-enabled: "
                        + result.getOutput().getBody());
    }

    @Test
    void noOpTaskStoreReportsHonestly() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        createTeamWithBoundMember(mgr, "worker", "worker-sess", "worker-agent-model");
        // Functional teamManager but NoOp taskStore.
        AgentToolExecuteContext ctx = ctxWith(mgr, NoOpTeamTaskStore.noOp(),
                new RecordingAgentEngine(),
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("task store is not enabled"),
                "NoOp taskStore must honestly report not-enabled: "
                        + result.getOutput().getBody());
    }

    // ========================================================================
    // 3. Caller not bound to any team → honest errorResult.
    // ========================================================================

    @Test
    void callerNotInTeamReturnsError() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        createTeamWithBoundMember(mgr, "worker", "worker-sess", "worker-agent-model");
        AgentToolExecuteContext ctx = ctxWith(mgr, new InMemoryTeamTaskStore(),
                new RecordingAgentEngine(),
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "stranger-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("not bound to any team"));
    }

    // ========================================================================
    // 4. DD#5: ACL denial → honest denied body, store NOT touched + orchestrator
    //    NOT invoked (Wiring #23).
    // ========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void aclDenialHonestDeniedBodyOrchestratorNotInvoked() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        // Recording engine: if the orchestrator were (wrongly) invoked, the
        // engine would record a request.
        RecordingAgentEngine engine = new RecordingAgentEngine();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-sess", "worker-agent-model");
        createTask(store, team.getTeamId(), "A", Collections.emptyList());

        ITeamAclChecker denyAll = (teamId, sess, tool, action) ->
                TeamAclDecision.deny(MemberRole.MEMBER, "stub-deny: " + tool + "/" + action);

        AgentToolExecuteContext ctx = ctxWith(mgr, store, engine, denyAll,
                NoOpMemberSpawner.noOp(), "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "ACL denial returns success (honest report, not crash)");
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, body.get("allowed"));
        assertEquals("team-execute-flow", body.get("toolName"));
        assertEquals("execute", body.get("action"));
        // Wiring #23: orchestrator NOT invoked on ACL denial.
        assertEquals(0, engine.capturedRequests.size(),
                "orchestrator must NOT be invoked when ACL denies (Wiring #23)");
        // The task is still CREATED — the orchestrator never mutated state.
        assertEquals(TeamTaskStatus.CREATED, store.getTasksByTeam(team.getTeamId()).get(0).getStatus(),
                "task store NOT touched on ACL denial");
    }

    // ========================================================================
    // 5. DD#4: successful DAG → success body with completedTaskIds + startOrder
    //    + completionOrder.
    // ========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void successDagMapsToSuccessBody() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-sess", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));

        AgentToolExecuteContext ctx = ctxWith(mgr, store, engine,
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.TRUE, body.get("success"));
        List<String> completed = (List<String>) body.get("completedTaskIds");
        assertTrue(completed.contains(a) && completed.contains(b),
                "success body carries both completedTaskIds: " + completed);
        assertNotNull(body.get("startOrder"), "success body carries startOrder");
        assertNotNull(body.get("completionOrder"), "success body carries completionOrder");
        // The body does NOT carry the failure-only keys.
        assertFalse(body.containsKey("failedTaskId"),
                "success body must not carry failedTaskId");
        // Both tasks actually reached COMPLETED.
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus());
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(b).orElseThrow().getStatus());
    }

    // ========================================================================
    // 6. DD#4: node failure → honest failure body with failedTaskId +
    //    skippedTaskIds (success:false, not silent success).
    // ========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void nodeFailureMapsToHonestFailureBody() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-sess", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());
        String b = createTask(store, teamId, "B", Collections.singletonList(a));
        // Engine fails task A → graph short-circuits → B skipped.
        RecordingAgentEngine engine = new RecordingAgentEngine(a);

        AgentToolExecuteContext ctx = ctxWith(mgr, store, engine,
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        // status="success" (so ReAct does not abort), but body success=false.
        assertEquals("success", result.getStatus(),
                "node-failure maps to status=success (DAG outcome, not technical fault)");
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, body.get("success"),
                "honest failure body marks success:false (not silent success)");
        assertEquals(a, body.get("failedTaskId"), "failedTaskId reported honestly");
        List<String> skipped = (List<String>) body.get("skippedTaskIds");
        assertTrue(skipped.contains(b), "B reported as skipped: " + skipped);
        // success-only keys must NOT be present on the failure body.
        assertFalse(body.containsKey("startOrder"),
                "failure body must not carry startOrder");
        assertFalse(body.containsKey("completionOrder"),
                "failure body must not carry completionOrder");
    }

    // ========================================================================
    // 7. DD#4: structural fast-failures (empty task set / unknown team /
    //    cyclic blockedBy) → errorResult.
    // ========================================================================

    @Test
    void emptyTaskSetStructuralFailureMapsToErrorResult() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-sess", "worker-agent-model");
        // No tasks created → orchestrator throws nop.ai.team.flow.no-tasks.
        AgentToolExecuteContext ctx = ctxWith(mgr, store, new RecordingAgentEngine(),
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus(),
                "structural failure (empty task set) → errorResult, not success body");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("no-tasks")
                        || result.getError().getBody().toLowerCase().contains("no tasks"),
                "errorResult body cites the structural cause: " + result.getError().getBody());
    }

    @Test
    void unknownTeamStructuralFailureMapsToErrorResult() throws Exception {
        // Drive the orchestrator's team-not-found structural branch by using a
        // manager whose getTeamBySession returns a team snapshot but whose
        // getTeam cannot find that team (simulates an inconsistent / raced
        // snapshot). The tool resolves the team from the session, hands the
        // teamId to the orchestrator, and the orchestrator's getTeam fails →
        // NopAiAgentException → tool's top-level catch → errorResult.
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        // Seed a task under a team id the manager does NOT know.
        String phantomTeamId = "phantom-team-id";
        store.createTask(phantomTeamId, "phantom", "d",
                Collections.emptyList(), "lead-session");

        // Snapshot returned by getTeamBySession carrying the phantom id.
        Team phantomSnapshot = new Team(phantomTeamId,
                new TeamSpec("phantom", "d", "lead-agent",
                        Arrays.asList(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD)),
                        0),
                Collections.emptyMap(),
                io.nop.ai.agent.team.TeamStatus.ACTIVE,
                System.currentTimeMillis());

        ITeamManager mgrOverride = new ForwardingTeamManager(mgr) {
            @Override
            public java.util.Optional<Team> getTeamBySession(String sessionId) {
                if ("worker-sess".equals(sessionId)) {
                    return java.util.Optional.of(phantomSnapshot);
                }
                return delegate.getTeamBySession(sessionId);
            }
        };

        AgentToolExecuteContext ctx = ctxWith(mgrOverride, store, new RecordingAgentEngine(),
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus(),
                "structural failure (unknown team) → errorResult, not success body");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("team-not-found")
                        || result.getError().getBody().toLowerCase().contains("unknown team"),
                "errorResult body cites the structural cause: " + result.getError().getBody());
    }

    /**
     * Forwarding {@link ITeamManager} base so tests can override a single
     * method without re-declaring all nine.
     */
    static class ForwardingTeamManager implements ITeamManager {
        final InMemoryTeamManager delegate;

        ForwardingTeamManager(InMemoryTeamManager delegate) {
            this.delegate = delegate;
        }

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
        public TeamMember addMember(String teamId, TeamMemberSpec memberSpec) {
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
        public java.util.Optional<TeamMember> getMember(String teamId, String memberName) {
            return delegate.getMember(teamId, memberName);
        }
    }

    @Test
    void cyclicBlockedByStructuralFailureMapsToErrorResult() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        Team team = createTeamWithBoundMember(mgr, "worker", "worker-sess", "worker-agent-model");
        String teamId = team.getTeamId();

        // Build two tasks P and Q in a fresh store, then wrap the store so each
        // appears mutually blockedBy the other on read — synthesizing a cycle
        // the orchestrator's graph builder (nop-task GraphStepAnalyzer) must
        // reject. The structural throw is mapped by the tool to an errorResult.
        InMemoryTeamTaskStore tightCycle = new InMemoryTeamTaskStore();
        String p = tightCycle.createTask(teamId, "P", "d", Collections.emptyList(), "lead").getTaskId();
        String q = tightCycle.createTask(teamId, "Q", "d", Collections.singletonList(p), "lead").getTaskId();

        ITeamTaskStore cyclicReadStore = cyclicBlockedByWrapper(tightCycle, p, q);
        AgentToolExecuteContext ctx = ctxWith(mgr, cyclicReadStore, new RecordingAgentEngine(),
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus(),
                "cyclic blockedBy → structural failure → errorResult");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().toLowerCase().contains("cycle")
                        || result.getError().getBody().toLowerCase().contains("cyclic"),
                "errorResult body cites the cyclic cause: " + result.getError().getBody());
    }

    /**
     * Wrap a store so the two given task ids appear mutually blockedBy each
     * other when read back, synthesizing a cycle the graph builder must reject.
     */
    private static ITeamTaskStore cyclicBlockedByWrapper(InMemoryTeamTaskStore delegate,
                                                         String p, String q) {
        return new ITeamTaskStore() {
            @Override
            public io.nop.ai.agent.team.TeamTask createTask(String teamId, String subject,
                                                             String description, List<String> blockedBy,
                                                             String createdBy) {
                return delegate.createTask(teamId, subject, description, blockedBy, createdBy);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> getTask(String taskId) {
                return delegate.getTask(taskId).map(this::withCycle);
            }

            @Override
            public List<io.nop.ai.agent.team.TeamTask> getTasksByTeam(String teamId) {
                List<io.nop.ai.agent.team.TeamTask> out = new ArrayList<>();
                for (io.nop.ai.agent.team.TeamTask t : delegate.getTasksByTeam(teamId)) {
                    out.add(withCycle(t));
                }
                return out;
            }

            private io.nop.ai.agent.team.TeamTask withCycle(io.nop.ai.agent.team.TeamTask t) {
                if (p.equals(t.getTaskId())) {
                    return new io.nop.ai.agent.team.TeamTask(
                            p, t.getTeamId(), t.getSubject(), t.getDescription(),
                            Collections.singletonList(q), t.getStatus(),
                            t.getCreatedBy(), t.getClaimedBy(), t.getCreatedAt());
                }
                if (q.equals(t.getTaskId())) {
                    return new io.nop.ai.agent.team.TeamTask(
                            q, t.getTeamId(), t.getSubject(), t.getDescription(),
                            Collections.singletonList(p), t.getStatus(),
                            t.getCreatedBy(), t.getClaimedBy(), t.getCreatedAt());
                }
                return t;
            }

            @Override
            public List<io.nop.ai.agent.team.TeamTask> getTasksByCreator(String createdBy) {
                return delegate.getTasksByCreator(createdBy);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> claimTask(String taskId, String claimedBy) {
                return delegate.claimTask(taskId, claimedBy);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> completeTask(String taskId, String completedBy) {
                return delegate.completeTask(taskId, completedBy);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> abandonTask(String taskId, String abandonedBy) {
                return delegate.abandonTask(taskId, abandonedBy);
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> reclaimTask(String taskId, String reclaimedBy) {
                return delegate.reclaimTask(taskId, reclaimedBy);
            }
        };
    }

    // ========================================================================
    // 8. DD#3 + Wiring #23: functional spawner → unbound-member node runs at
    //    run time via spawnMember (NO manual bindMemberSession), task COMPLETED.
    // ========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void functionalSpawnerUnboundMemberRunTimeSpawn() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        // No member is bound → resolveMember returns null → spawn path. The
        // session-resolution wrapper lets the tool resolve the caller's team
        // without any bindMemberSession (simulating an external
        // session→team association).
        ITeamManager mgrWithSession = sessionResolvesTo(mgr, "caller-sess", team);

        TeamExecuteFlowExecutor executor = new TeamExecuteFlowExecutor();
        CountingSpawner spawner = new CountingSpawner(engine);
        executor.setMemberSpawner(spawner);

        AgentToolExecuteContext ctx = ctxWith(mgrWithSession, store, engine,
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "caller-sess");

        AiToolCallResult result = executor.executeAsync(call(), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.TRUE, body.get("success"),
                "unbound-member node + functional spawner → DAG completes via run-time spawn");
        // Wiring #23: the injected spawner was really consulted at run time.
        assertEquals(1, spawner.invocations.get(),
                "spawner.spawnMember consulted once at run time (wire-at-consumer wired)");
        // The spawned execution reached the engine.
        assertEquals(1, engine.capturedRequests.size(),
                "IAgentEngine.execute invoked by the spawner (real spawn execution)");
        // Task reached COMPLETED with NO manual bindMemberSession.
        assertEquals(TeamTaskStatus.COMPLETED, store.getTask(a).orElseThrow().getStatus(),
                "task COMPLETED via spawn (no manual bind)");
    }

    // ========================================================================
    // 9. NoOp spawner + unbound member → honest failure body (orchestrator
    //    success=false mapped honestly, NOT silent success).
    // ========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void noOpSpawnerUnboundMemberHonestFailure() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = createTeamWithoutBoundMember(mgr, "worker", "worker-agent-model");
        String teamId = team.getTeamId();
        String a = createTask(store, teamId, "A", Collections.emptyList());

        ITeamManager mgrWithSession = sessionResolvesTo(mgr, "caller-sess", team);

        // NoOp spawner: the shipped default (wire-at-consumer, no opt-in).
        TeamExecuteFlowExecutor executor = new TeamExecuteFlowExecutor();
        // setMemberSpawner(null) is null-safe → NoOp (zero regression).
        executor.setMemberSpawner(null);

        AgentToolExecuteContext ctx = ctxWith(mgrWithSession, store, engine,
                io.nop.ai.agent.team.NoOpTeamAclChecker.noOp(),
                NoOpMemberSpawner.noOp(), "caller-sess");

        AiToolCallResult result = executor.executeAsync(call(), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        // status="success" (DAG outcome), body success=false (honest).
        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, body.get("success"),
                "NoOp spawner + unbound member = honest failure (not silent success)");
        assertEquals(a, body.get("failedTaskId"));
        // Engine never invoked (NoOp declines before any execution).
        assertEquals(0, engine.capturedRequests.size(),
                "engine NOT invoked (NoOp honestly declined)");
    }

    // ========================================================================
    // 10. Wiring #23: spawner wire-at-consumer via setter, null-safe → NoOp.
    // ========================================================================

    @Test
    void spawnerWiredViaSetterWireAtConsumer() {
        TeamExecuteFlowExecutor executor = new TeamExecuteFlowExecutor();
        // Shipped default: NoOp.
        assertTrue(executor.getMemberSpawner() instanceof NoOpMemberSpawner,
                "shipped default spawner is NoOp (zero regression)");

        // Setter wiring of a functional spawner.
        DefaultMemberSpawner functional = new DefaultMemberSpawner(new RecordingAgentEngine());
        executor.setMemberSpawner(functional);
        assertEquals(functional, executor.getMemberSpawner(),
                "functional spawner wired via setter (wire-at-consumer)");

        // null-safe reset → NoOp shipped default.
        executor.setMemberSpawner(null);
        assertTrue(executor.getMemberSpawner() instanceof NoOpMemberSpawner,
                "null setter resets to NoOp shipped default (zero regression)");
    }
}
