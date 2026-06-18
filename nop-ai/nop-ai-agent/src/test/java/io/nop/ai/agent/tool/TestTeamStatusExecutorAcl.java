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
 * Focused ACL tests for {@link TeamStatusExecutor} (plan 228 Phase 2).
 *
 * <p>Verifies the executor consults
 * {@link io.nop.ai.agent.team.ITeamAclChecker} after team resolution and
 * before the taskStore query / status build:
 * <ul>
 *   <li>MEMBER view is allowed (READ) — status JSON returned with taskCount.</li>
 *   <li>A deny-stub checker blocks MEMBER view — taskStore NOT queried
 *       (Wiring Verification #23).</li>
 *   <li>NoOp checker preserves zero-regression behaviour.</li>
 * </ul>
 */
public class TestTeamStatusExecutorAcl {

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
        TeamSpec spec = new TeamSpec("StatusTeam", null, "lead",
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

    private AiToolCall call() {
        AiToolCall c = new AiToolCall();
        c.setToolName("team-status");
        c.setId(1);
        c.setInput("{}");
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void memberViewIsAllowedAndReturnsStatusJson() throws Exception {
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        // Create one task in the team so taskCount is meaningful.
        store.createTask(mgr.getActiveTeams().iterator().next().getTeamId(),
                "some task", null, Collections.emptyList(), "lead-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), "worker-sess");

        AiToolCallResult result = new TeamStatusExecutor()
                .executeAsync(call(), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("StatusTeam", body.get("teamName"));
        assertEquals(1, ((Number) body.get("taskCount")).intValue(),
                "taskCount must reflect the store content (READ allowed for MEMBER)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void denyStubCheckerBlocksMemberViewAndStoreNotQueried() throws Exception {
        // Wiring Verification (#23): use a recording store + deny-stub
        // checker to prove ACL denial blocks the taskStore query.
        boolean[] storeQueried = {false};
        InMemoryTeamManager mgr = newTeam();
        io.nop.ai.agent.team.ITeamTaskStore recording = new io.nop.ai.agent.team.ITeamTaskStore() {
            @Override
            public io.nop.ai.agent.team.TeamTask createTask(String teamId, String subject, String description, java.util.List<String> blockedBy, String createdBy) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> getTask(String taskId) {
                storeQueried[0] = true;
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<io.nop.ai.agent.team.TeamTask> getTasksByTeam(String teamId) {
                storeQueried[0] = true;
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.List<io.nop.ai.agent.team.TeamTask> getTasksByCreator(String createdBy) {
                storeQueried[0] = true;
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> claimTask(String taskId, String claimedBy) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> completeTask(String taskId, String completedBy) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> abandonTask(String taskId, String abandonedBy) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<io.nop.ai.agent.team.TeamTask> reclaimTask(String taskId, String reclaimedBy) {
                throw new UnsupportedOperationException();
            }
        };
        io.nop.ai.agent.team.ITeamAclChecker denyAll = (teamId, sess, tool, action) ->
                io.nop.ai.agent.team.TeamAclDecision.deny(MemberRole.MEMBER,
                        "stub-deny: " + tool + "/" + action);

        AgentToolExecuteContext ctxWithStore = ctxWithChecker(mgr, recording, denyAll, "worker-sess");

        AiToolCallResult result = new TeamStatusExecutor()
                .executeAsync(call(), ctxWithStore)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "ACL denial returns success (honest report, not crash)");
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, body.get("allowed"),
                "body must explicitly say allowed=false");
        assertEquals("team-status", body.get("toolName"));
        assertEquals("view", body.get("action"));
        assertFalse(storeQueried[0],
                "taskStore MUST NOT be queried when ACL denies (Wiring #23)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void noOpCheckerAllowsMemberViewZeroRegression() throws Exception {
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                NoOpTeamAclChecker.noOp(), "worker-sess");

        AiToolCallResult result = new TeamStatusExecutor()
                .executeAsync(call(), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("StatusTeam", body.get("teamName"),
                "NoOp checker must allow MEMBER view → status returned (zero regression)");
        assertNotNull(body.get("members"));
    }
}
