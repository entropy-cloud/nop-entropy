package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.NoOpTeamTaskStore;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for {@link TeamTaskUpdateExecutor}: NoOp honest
 * reporting, claim/complete/abandon success paths, CAS-failure honest report,
 * cross-team rejection, caller-not-in-team error, and case-insensitive action.
 *
 * <p>See plan 227 (team-task-update) Phase 1.
 */
public class TestTeamTaskUpdateExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentToolExecuteContext createContext(ITeamManager teamManager,
                                                   ITeamTaskStore taskStore,
                                                   String sessionId) {
        return new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, NoOpAgentMessenger.noOp(), sessionId, "test-agent",
                null, null, null, null,
                teamManager, taskStore);
    }

    private AiToolCall createCall(String taskId, String action) {
        AiToolCall call = new AiToolCall();
        call.setToolName("team-task-update");
        call.setId(1);
        call.setInput("{\"taskId\":\"" + taskId + "\",\"action\":\"" + action + "\"}");
        return call;
    }

    /**
     * Build a team with a lead + worker, bound to the given sessions. Returns
     * the manager (the team can be retrieved via getActiveTeams()).
     */
    private InMemoryTeamManager createTeam(String leadSession, String workerSession) {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("TaskTeam", null, "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", leadSession, "actor-lead");
        mgr.bindMemberSession(team.getTeamId(), "worker", workerSession, "actor-worker");
        return mgr;
    }

    @Test
    void noOpTeamManagerReportsHonestly() throws Exception {
        AgentToolExecuteContext ctx = createContext(NoOpTeamManager.noOp(),
                NoOpTeamTaskStore.noOp(), "sess-1");
        AiToolCall call = createCall("any-task", "claim");

        TeamTaskUpdateExecutor executor = new TeamTaskUpdateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "team-task-update with NoOp teamManager should return success (honest report)");
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("not enabled"),
                "NoOp teamManager result must honestly report not-enabled: " + result.getOutput().getBody());
    }

    @Test
    void noOpTaskStoreReportsHonestly() throws Exception {
        ITeamManager teamManager = createTeam("lead-sess", "worker-sess");
        AgentToolExecuteContext ctx = createContext(teamManager, NoOpTeamTaskStore.noOp(), "lead-sess");
        AiToolCall call = createCall("any-task", "claim");

        TeamTaskUpdateExecutor executor = new TeamTaskUpdateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("task store is not enabled"),
                "NoOp taskStore result must honestly report not-enabled: " + result.getOutput().getBody());
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimReturnsClaimedStatusAndClaimedBy() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess", "worker-sess");
        // Lead creates a task directly in the store.
        TeamTask task = taskStore.createTask(
                teamManager.getActiveTeams().iterator().next().getTeamId(),
                "Do work", null, Collections.emptyList(), "lead-sess");

        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "worker-sess");
        AiToolCall call = createCall(task.getTaskId(), "claim");

        TeamTaskUpdateExecutor executor = new TeamTaskUpdateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> json = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("CLAIMED", json.get("status"));
        assertEquals("worker-sess", json.get("claimedBy"),
                "claim must record the caller sessionId as claimedBy");
        assertEquals(Boolean.TRUE, json.get("applied"));

        // The store reflects the transition.
        TeamTask stored = taskStore.getTask(task.getTaskId()).orElseThrow();
        assertEquals("CLAIMED", stored.getStatus().name());
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeReturnsCompletedStatus() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess", "worker-sess");
        TeamTask task = taskStore.createTask(
                teamManager.getActiveTeams().iterator().next().getTeamId(),
                "Do work", null, Collections.emptyList(), "lead-sess");
        // Worker claims first (complete requires CLAIMED).
        taskStore.claimTask(task.getTaskId(), "worker-sess");

        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "worker-sess");
        AiToolCall call = createCall(task.getTaskId(), "complete");

        TeamTaskUpdateExecutor executor = new TeamTaskUpdateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> json = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("COMPLETED", json.get("status"));
        assertEquals("worker-sess", json.get("claimedBy"),
                "complete must preserve the recorded claimedBy (design 裁定 6)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void abandonReturnsAbandonedStatus() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess", "worker-sess");
        TeamTask task = taskStore.createTask(
                teamManager.getActiveTeams().iterator().next().getTeamId(),
                "Do work", null, Collections.emptyList(), "lead-sess");

        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "lead-sess");
        AiToolCall call = createCall(task.getTaskId(), "abandon");

        TeamTaskUpdateExecutor executor = new TeamTaskUpdateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> json = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("ABANDONED", json.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void casFailureReportsCurrentStatusHonestly() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess", "worker-sess");
        TeamTask task = taskStore.createTask(
                teamManager.getActiveTeams().iterator().next().getTeamId(),
                "Do work", null, Collections.emptyList(), "lead-sess");
        // Worker claims first.
        taskStore.claimTask(task.getTaskId(), "worker-sess");

        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "worker-sess");
        // Second claim must fail (CAS) and report the current CLAIMED status.
        AiToolCall call = createCall(task.getTaskId(), "claim");

        TeamTaskUpdateExecutor executor = new TeamTaskUpdateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "CAS failure returns success (honest report), not failure");
        Map<String, Object> json = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals(Boolean.FALSE, json.get("applied"),
                "applied=false on CAS failure");
        assertEquals("CLAIMED", json.get("currentStatus"),
                "honest report must include the current status");
        assertTrue(((String) json.get("message")).contains("CLAIMED"),
                "message must explain the disallowed transition");
    }

    @Test
    void crossTeamTaskIdReturnsError() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        // Two separate teams.
        InMemoryTeamManager teamA = createTeam("leadA-sess", "workerA-sess");
        InMemoryTeamManager teamB = createTeam("leadB-sess", "workerB-sess");

        String teamAId = teamA.getActiveTeams().iterator().next().getTeamId();
        TeamTask taskInTeamA = taskStore.createTask(teamAId, "team A task",
                null, Collections.emptyList(), "leadA-sess");

        // A member of team B tries to update team A's task.
        AgentToolExecuteContext ctx = createContext(teamB, taskStore, "workerB-sess");
        AiToolCall call = createCall(taskInTeamA.getTaskId(), "claim");

        TeamTaskUpdateExecutor executor = new TeamTaskUpdateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus(),
                "cross-team task update must be rejected");
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("does not belong"),
                "error must explain the task does not belong to the caller's team: "
                        + result.getError().getBody());
    }

    @Test
    void callerNotInTeamReturnsError() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess", "worker-sess");
        // A stranger session not bound to any team.
        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "stranger-sess");
        AiToolCall call = createCall("any-task", "claim");

        TeamTaskUpdateExecutor executor = new TeamTaskUpdateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("not bound to any team"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void actionIsCaseInsensitive() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess", "worker-sess");
        TeamTask task = taskStore.createTask(
                teamManager.getActiveTeams().iterator().next().getTeamId(),
                "Do work", null, Collections.emptyList(), "lead-sess");

        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "worker-sess");
        AiToolCall call = createCall(task.getTaskId(), "CLAIM");

        TeamTaskUpdateExecutor executor = new TeamTaskUpdateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> json = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertEquals("CLAIMED", json.get("status"),
                "action 'CLAIM' (uppercase) must be accepted case-insensitively");
    }
}
