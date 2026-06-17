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
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused ACL tests for {@link TeamTaskUpdateExecutor} (plan 228 Phase 2).
 *
 * <p>This is the heart of the §5.1 role distinction: the only operation a
 * MEMBER is denied is {@code abandon-unclaimed} (ADMIN-only). Verifies:
 * <ul>
 *   <li>MEMBER claim allowed (EXECUTE) — task moves to CLAIMED.</li>
 *   <li>MEMBER complete allowed (EXECUTE) — task moves to COMPLETED.</li>
 *   <li>MEMBER abandon CLAIMED task allowed (abandon-claimed, EXECUTE)
 *       — task moves to ABANDONED.</li>
 *   <li>MEMBER abandon CREATED task DENIED (abandon-unclaimed, ADMIN)
 *       — task status remains CREATED (operation blocked, Anti-Hollow #22).</li>
 *   <li>LEAD abandon CREATED task allowed — task moves to ABANDONED.</li>
 *   <li>Non-member denied at ACL layer after team resolution.</li>
 *   <li>NoOp checker allows all operations (zero regression).</li>
 * </ul>
 */
public class TestTeamTaskUpdateExecutorAcl {

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
        TeamSpec spec = new TeamSpec("UpdateTeam", null, "lead",
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

    private AiToolCall call(String taskId, String action) {
        AiToolCall c = new AiToolCall();
        c.setToolName("team-task-update");
        c.setId(1);
        c.setInput("{\"taskId\":\"" + taskId + "\",\"action\":\"" + action + "\"}");
        return c;
    }

    private String teamIdOf(InMemoryTeamManager mgr) {
        return mgr.getActiveTeams().iterator().next().getTeamId();
    }

    private String createTask(InMemoryTeamTaskStore store, String teamId, String creator) {
        return store.createTask(teamId, "task", null, Collections.emptyList(), creator).getTaskId();
    }

    // ----- MEMBER allowed operations -----

    @Test
    @SuppressWarnings("unchecked")
    void memberClaimIsAllowed() throws Exception {
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        String taskId = createTask(store, teamIdOf(mgr), "lead-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), "worker-sess");

        AiToolCallResult result = new TeamTaskUpdateExecutor()
                .executeAsync(call(taskId, "claim"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("CLAIMED", body.get("status"));
        assertEquals(Boolean.TRUE, body.get("applied"));
        assertEquals(TeamTaskStatus.CLAIMED, store.getTask(taskId).orElseThrow().getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void memberCompleteIsAllowed() throws Exception {
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        String taskId = createTask(store, teamIdOf(mgr), "lead-sess");
        store.claimTask(taskId, "worker-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), "worker-sess");

        AiToolCallResult result = new TeamTaskUpdateExecutor()
                .executeAsync(call(taskId, "complete"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("COMPLETED", body.get("status"));
        assertEquals(Boolean.TRUE, body.get("applied"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void memberAbandonClaimedTaskIsAllowed() throws Exception {
        // abandon on a CLAIMED task = abandon-claimed (EXECUTE) → MEMBER allowed.
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        String taskId = createTask(store, teamIdOf(mgr), "lead-sess");
        store.claimTask(taskId, "worker-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), "worker-sess");

        AiToolCallResult result = new TeamTaskUpdateExecutor()
                .executeAsync(call(taskId, "abandon"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("ABANDONED", body.get("status"));
        assertEquals(Boolean.TRUE, body.get("applied"));
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(taskId).orElseThrow().getStatus(),
                "task must have moved to ABANDONED (MEMBER abandon-claimed allowed)");
    }

    // ----- MEMBER denied: abandon-unclaimed (the §5.1 ADMIN distinction) -----

    @Test
    @SuppressWarnings("unchecked")
    void memberAbandonUnclaimedTaskIsDeniedAndStatusUnchanged() throws Exception {
        // abandon on a CREATED (unclaimed) task = abandon-unclaimed (ADMIN)
        // → MEMBER denied. The task MUST remain CREATED (Anti-Hollow #22).
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        String taskId = createTask(store, teamIdOf(mgr), "lead-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), "worker-sess");

        AiToolCallResult result = new TeamTaskUpdateExecutor()
                .executeAsync(call(taskId, "abandon"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        // Denial is an honest "success" report — ReAct loop does not abort.
        assertEquals("success", result.getStatus(),
                "ACL denial returns success (honest report, not crash)");
        assertNotNull(result.getOutput());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, body.get("allowed"),
                "body must explicitly say allowed=false");
        assertEquals("team-task-update", body.get("toolName"));
        assertEquals("abandon-unclaimed", body.get("action"),
                "action must be refined to abandon-unclaimed for a CREATED task");
        assertEquals("MEMBER", body.get("resolvedRole"));
        String reason = (String) body.get("reason");
        assertNotNull(reason);
        assertTrue(reason.contains("ADMIN"),
                "deny reason must cite ADMIN requirement: " + reason);

        // CRITICAL (Anti-Hollow #22): the store MUST NOT have transitioned.
        TeamTask stored = store.getTask(taskId).orElseThrow();
        assertEquals(TeamTaskStatus.CREATED, stored.getStatus(),
                "MEMBER abandon-unclaimed must be BLOCKED — task status unchanged");
    }

    // ----- LEAD allowed: abandon-unclaimed -----

    @Test
    @SuppressWarnings("unchecked")
    void leadAbandonUnclaimedTaskIsAllowed() throws Exception {
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        String taskId = createTask(store, teamIdOf(mgr), "lead-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), "lead-sess");

        AiToolCallResult result = new TeamTaskUpdateExecutor()
                .executeAsync(call(taskId, "abandon"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("ABANDONED", body.get("status"));
        assertEquals(Boolean.TRUE, body.get("applied"));
        assertEquals(TeamTaskStatus.ABANDONED, store.getTask(taskId).orElseThrow().getStatus(),
                "LEAD abandon-unclaimed must succeed — task moved to ABANDONED");
    }

    // ----- Non-member denied -----

    @Test
    @SuppressWarnings("unchecked")
    void strangerSessionRejectedBeforeAclByTeamResolution() throws Exception {
        // A session not bound to any team is rejected by getTeamBySession
        // BEFORE the ACL check runs (existing behaviour, unchanged).
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        String taskId = createTask(store, teamIdOf(mgr), "lead-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                new DefaultTeamAclChecker(mgr), "stranger-sess");

        AiToolCallResult result = new TeamTaskUpdateExecutor()
                .executeAsync(call(taskId, "claim"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("not bound to any team"));
        assertEquals(TeamTaskStatus.CREATED, store.getTask(taskId).orElseThrow().getStatus(),
                "store unchanged for stranger");
    }

    // ----- NoOp zero-regression -----

    @Test
    @SuppressWarnings("unchecked")
    void noOpCheckerAllowsMemberAbandonUnclaimedZeroRegression() throws Exception {
        // The crucial NoOp zero-regression check: with NoOpTeamAclChecker,
        // MEMBER abandon of an unclaimed task still succeeds (pre-ACL behaviour).
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore store = new InMemoryTeamTaskStore();
        String taskId = createTask(store, teamIdOf(mgr), "lead-sess");

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, store,
                NoOpTeamAclChecker.noOp(), "worker-sess");

        AiToolCallResult result = new TeamTaskUpdateExecutor()
                .executeAsync(call(taskId, "abandon"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("ABANDONED", body.get("status"),
                "NoOp checker must allow MEMBER abandon-unclaimed (zero regression)");
        assertEquals(Boolean.TRUE, body.get("applied"));
    }

    // ----- Wiring verification: deny-stub blocks the store -----

    @Test
    @SuppressWarnings("unchecked")
    void denyStubCheckerBlocksClaimAndStoreUntouched() throws Exception {
        // Wiring Verification (#23): prove the executor consults the checker
        // BEFORE the action switch / store transition. A deny-stub checker
        // + recording store confirms claim is never invoked.
        boolean[] claimCalled = {false};
        InMemoryTeamManager mgr = newTeam();
        InMemoryTeamTaskStore realStore = new InMemoryTeamTaskStore();
        String taskId = createTask(realStore, teamIdOf(mgr), "lead-sess");

        // Wrap a recording store around the real one so we can detect calls.
        io.nop.ai.agent.team.ITeamTaskStore recording = new io.nop.ai.agent.team.ITeamTaskStore() {
            @Override
            public TeamTask createTask(String t, String s, String d, java.util.List<String> b, String c) {
                return realStore.createTask(t, s, d, b, c);
            }

            @Override
            public java.util.Optional<TeamTask> getTask(String id) {
                return realStore.getTask(id);
            }

            @Override
            public java.util.List<TeamTask> getTasksByTeam(String id) {
                return realStore.getTasksByTeam(id);
            }

            @Override
            public java.util.List<TeamTask> getTasksByCreator(String c) {
                return realStore.getTasksByCreator(c);
            }

            @Override
            public java.util.Optional<TeamTask> claimTask(String id, String by) {
                claimCalled[0] = true;
                return realStore.claimTask(id, by);
            }

            @Override
            public java.util.Optional<TeamTask> completeTask(String id, String by) {
                return realStore.completeTask(id, by);
            }

            @Override
            public java.util.Optional<TeamTask> abandonTask(String id, String by) {
                return realStore.abandonTask(id, by);
            }
        };
        io.nop.ai.agent.team.ITeamAclChecker denyAll = (teamId, sess, tool, action) ->
                io.nop.ai.agent.team.TeamAclDecision.deny(MemberRole.MEMBER,
                        "stub-deny: " + tool + "/" + action);

        AgentToolExecuteContext ctx = ctxWithChecker(mgr, recording, denyAll, "worker-sess");

        AiToolCallResult result = new TeamTaskUpdateExecutor()
                .executeAsync(call(taskId, "claim"), ctx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> body = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, body.get("allowed"));
        assertEquals("claim", body.get("action"));
        assertFalse(claimCalled[0],
                "store.claimTask MUST NOT be called when ACL denies (Wiring #23)");
        assertEquals(TeamTaskStatus.CREATED, realStore.getTask(taskId).orElseThrow().getStatus(),
                "store status unchanged");
    }
}
