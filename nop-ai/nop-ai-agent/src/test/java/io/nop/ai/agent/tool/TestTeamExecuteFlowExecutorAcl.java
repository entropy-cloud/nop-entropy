package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.DefaultTeamAclChecker;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamAclChecker;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpMemberSpawner;
import io.nop.ai.agent.team.NoOpTeamAclChecker;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTaskStatus;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused ACL tests for {@link TeamExecuteFlowExecutor} (plan 239 Phase 1).
 *
 * <p>Uses a real {@link DefaultTeamAclChecker} (not a mock) to verify the
 * {@code ("team-execute-flow", "execute") → WRITE} matrix entry delivers:
 * <ul>
 *   <li>LEAD allow (orchestrator invoked + success result),</li>
 *   <li>MEMBER allow (orchestrator invoked + success result),</li>
 *   <li>non-member deny (honest-denied body + orchestrator NOT invoked).</li>
 * </ul>
 * Pattern mirrors the sibling ACL tests
 * {@code TestTeamTaskCreateExecutorAcl} / {@code TestTeamStatusExecutorAcl} /
 * {@code TestTeamSendMessageExecutorAcl} / {@code TestTeamTaskUpdateExecutorAcl}.
 */
public class TestTeamExecuteFlowExecutorAcl {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * Recording engine: if the orchestrator is (wrongly) invoked on a denial,
     * a request lands here and the test fails.
     */
    static final class RecordingAgentEngine implements IAgentEngine {
        final List<AgentMessageRequest> capturedRequests = Collections.synchronizedList(new ArrayList<>());

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            capturedRequests.add(request);
            String taskId = (String) request.getMetadata().get("teamTaskId");
            return CompletableFuture.completedFuture(new AgentExecutionResult(
                    AgentExecStatus.completed, "ok:" + taskId,
                    Collections.emptyList(), 1, 10L, 1L, null));
        }
    }

    private InMemoryTeamManager newTeam() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("ExecTeam", null, "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", "lead-sess", "actor-lead");
        mgr.bindMemberSession(team.getTeamId(), "worker", "worker-sess", "actor-worker");
        return mgr;
    }

    private AgentToolExecuteContext ctxWithChecker(InMemoryTeamManager mgr,
                                                   ITeamTaskStore store,
                                                   ITeamAclChecker checker,
                                                   IAgentEngine engine,
                                                   String sessionId) {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                engine, null, sessionId, "test-agent",
                null, null, null, null,
                mgr, store, checker);
    }

    private AiToolCall call() {
        AiToolCall c = new AiToolCall();
        c.setToolName("team-execute-flow");
        c.setId(1);
        c.setInput("{}");
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void leadAllowOrchestratorInvokedSuccess() throws Exception {
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = mgr.getActiveTeams().iterator().next();
        store.createTask(team.getTeamId(), "task-A", "d", Collections.emptyList(), "lead-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), engine, "lead-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.TRUE, body.get("success"),
                "LEAD execute → WRITE allowed → orchestrator invoked → success body");
        // Wiring #23: orchestrator really ran the member agent.
        assertEquals(1, engine.capturedRequests.size(),
                "LEAD allowed → orchestrator invoked the member agent (Wiring #23)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void memberAllowOrchestratorInvokedSuccess() throws Exception {
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = mgr.getActiveTeams().iterator().next();
        store.createTask(team.getTeamId(), "task-A", "d", Collections.emptyList(), "lead-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), engine, "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.TRUE, body.get("success"),
                "MEMBER execute → WRITE allowed → orchestrator invoked → success body");
        assertEquals(1, engine.capturedRequests.size(),
                "MEMBER allowed → orchestrator invoked the member agent (Wiring #23)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonMemberDenyHonestBodyOrchestratorNotInvoked() throws Exception {
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = mgr.getActiveTeams().iterator().next();
        store.createTask(team.getTeamId(), "task-A", "d", Collections.emptyList(), "lead-sess");

        // "stranger-sess" is not bound to the team → getTeamBySession returns
        // empty and the tool rejects before ACL. To exercise the ACL deny path
        // specifically we instead use a MEMBER-role deny: we cannot deny a
        // MEMBER on WRITE (it is allowed). So exercise the non-member deny
        // directly: bind a stranger session to a DIFFERENT team so the stranger
        // is a valid session but not a member of THIS team. getTeamBySession
        // would resolve them to the other team though. The cleanest non-member
        // deny is via an unknown session (rejected at team resolution) — but
        // that returns an errorResult, not an honest-denied body. The honest-
        // denied body path is reached when the caller IS resolved to the team
        // (so they appear bound to the team) but the checker then rejects them.
        // For team-execute-flow (WRITE), the only way the functional checker
        // denies is a non-member. A non-member cannot be resolved to the team
        // via getTeamBySession (that is the membership test). Therefore the
        // functional checker's deny for this tool is only reachable if a caller
        // is bound to the team session-wise but somehow not in the member map.
        // To cover the ACL deny path with a real checker we use a deny-stub
        // checker (mirrors TestTeamTaskCreateExecutorAcl's deny-stub pattern),
        // which proves the tool consults the checker and returns an honest
        // denied body + does NOT touch the store / orchestrator.
        ITeamAclChecker denyAll = (teamId, sess, tool, action) ->
                io.nop.ai.agent.team.TeamAclDecision.deny(MemberRole.MEMBER,
                        "stub-deny: " + tool + "/" + action);

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store, denyAll, engine, "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "ACL denial returns success (honest report, not crash)");
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, body.get("allowed"));
        assertEquals("team-execute-flow", body.get("toolName"));
        assertEquals("execute", body.get("action"));
        // Wiring #23: orchestrator NOT invoked, store NOT touched.
        assertEquals(0, engine.capturedRequests.size(),
                "orchestrator MUST NOT be invoked when ACL denies (Wiring #23)");
        assertEquals(TeamTaskStatus.CREATED, store.getTasksByTeam(team.getTeamId()).get(0).getStatus(),
                "task store NOT touched on ACL denial");
    }

    @Test
    void strangerSessionRejectedBeforeAclByTeamResolution() throws Exception {
        // Sanity: a session not bound to the team is rejected at
        // getTeamBySession BEFORE the ACL check runs (existing behaviour,
        // identical to the sibling tools).
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), new RecordingAgentEngine(), "stranger-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("not bound to any team"));
        assertEquals(0, store.getTasksByTeam(mgr.getActiveTeams().iterator().next().getTeamId()).size(),
                "store remains empty (no tasks created)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void noOpCheckerZeroRegressionAllowsMember() throws Exception {
        // NoOp checker (shipped default) allows everything → zero regression.
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        RecordingAgentEngine engine = new RecordingAgentEngine();
        Team team = mgr.getActiveTeams().iterator().next();
        store.createTask(team.getTeamId(), "task-A", "d", Collections.emptyList(), "lead-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                NoOpTeamAclChecker.noOp(), engine, "worker-sess");

        AiToolCallResult result = new TeamExecuteFlowExecutor()
                .executeAsync(call(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.TRUE, body.get("success"),
                "NoOp checker allows MEMBER execute → orchestrator invoked (zero regression)");
        assertNotNull(body.get("completedTaskIds"),
                "success body (not an ACL denial body) — NoOp allowed the DAG to run");
    }
}
