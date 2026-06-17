package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.team.DefaultTeamAclChecker;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpTeamAclChecker;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.json.JSON;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused ACL tests for {@link TeamTaskCreateExecutor} (plan 228 Phase 2).
 *
 * <p>Verifies the executor consults
 * {@link io.nop.ai.agent.team.ITeamAclChecker} after team resolution and
 * before argument parsing / task creation:
 * <ul>
 *   <li>MEMBER create is allowed (WRITE) — task is actually created.</li>
 *   <li>A deny-stub checker blocks creation — store NOT touched
 *       (Wiring Verification #23).</li>
 *   <li>NoOp checker preserves zero-regression behaviour.</li>
 * </ul>
 */
public class TestTeamTaskCreateExecutorAcl {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private InMemoryTeamManager newTeam() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("CreateTeam", null, "lead",
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
                                                    io.nop.ai.agent.team.ITeamAclChecker checker,
                                                    String sessionId) {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, null, sessionId, "test-agent",
                null, null, null, null,
                mgr, store, checker);
    }

    private AiToolCall call(String subject) {
        AiToolCall c = new AiToolCall();
        c.setToolName("team-task-create");
        c.setId(1);
        c.setInput("{\"subject\":\"" + subject + "\"}");
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void memberCreateIsAllowed() throws Exception {
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), "worker-sess");

        AiToolCallResult result = new TeamTaskCreateExecutor()
                .executeAsync(call("worker task"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertNotNull(body.get("taskId"), "MEMBER create must produce a taskId");
        assertEquals("CREATED", body.get("status"));

        // Wiring verification: the task actually landed in the store.
        assertEquals(1, store.getTasksByTeam(
                mgr.getActiveTeams().iterator().next().getTeamId()).size(),
                "task must be in the shared store (Wiring #23)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void denyStubCheckerBlocksCreateAndStoreNotTouched() throws Exception {
        // Wiring Verification (#23): a recording store + deny-stub checker
        // to prove ACL denial blocks createTask entirely.
        boolean[] createCalled = {false};
        InMemoryTeamManager mgr = newTeam();
        io.nop.ai.agent.team.ITeamTaskStore recording = new io.nop.ai.agent.team.ITeamTaskStore() {
            @Override
            public TeamTask createTask(String teamId, String subject, String description, java.util.List<String> blockedBy, String createdBy) {
                createCalled[0] = true;
                return new TeamTask("stub-id", teamId, subject, description,
                        Collections.emptyList(),
                        io.nop.ai.agent.team.TeamTaskStatus.CREATED,
                        createdBy, null, 0L);
            }

            @Override
            public java.util.Optional<TeamTask> getTask(String taskId) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<TeamTask> getTasksByTeam(String teamId) {
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.List<TeamTask> getTasksByCreator(String createdBy) {
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.Optional<TeamTask> claimTask(String taskId, String claimedBy) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<TeamTask> completeTask(String taskId, String completedBy) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<TeamTask> abandonTask(String taskId, String abandonedBy) {
                throw new UnsupportedOperationException();
            }
        };
        io.nop.ai.agent.team.ITeamAclChecker denyAll = (teamId, sess, tool, action) ->
                io.nop.ai.agent.team.TeamAclDecision.deny(MemberRole.MEMBER,
                        "stub-deny: " + tool + "/" + action);

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, recording, denyAll, "worker-sess");

        AiToolCallResult result = new TeamTaskCreateExecutor()
                .executeAsync(call("blocked task"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "ACL denial returns success (honest report, not crash)");
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, body.get("allowed"));
        assertEquals("team-task-create", body.get("toolName"));
        assertEquals("create", body.get("action"));
        assertFalse(createCalled[0],
                "store.createTask MUST NOT be called when ACL denies (Wiring #23)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void noOpCheckerAllowsMemberCreateZeroRegression() throws Exception {
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                NoOpTeamAclChecker.noOp(), "worker-sess");

        AiToolCallResult result = new TeamTaskCreateExecutor()
                .executeAsync(call("noOp task"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertNotNull(body.get("taskId"),
                "NoOp checker must allow MEMBER create → task produced (zero regression)");
    }

    @Test
    void strangerSessionRejectedBeforeAclByTeamResolution() throws Exception {
        // Sanity: a session not bound to the team is rejected at
        // getTeamBySession BEFORE the ACL check runs (existing behaviour).
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), "stranger-sess");

        AiToolCallResult result = new TeamTaskCreateExecutor()
                .executeAsync(call("intruder"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("not bound to any team"));
        assertEquals(0, store.getTasksByTeam(
                mgr.getActiveTeams().iterator().next().getTeamId()).size(),
                "store must remain empty (no task created)");
    }
}
