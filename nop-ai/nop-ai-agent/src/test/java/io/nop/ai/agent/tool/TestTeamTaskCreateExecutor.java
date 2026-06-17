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
 * Focused unit tests for {@link TeamTaskCreateExecutor}: NoOp honest
 * reporting (NoOpTeamManager + NoOpTeamTaskStore), functional task creation
 * returning taskId + CREATED status, blockedBy storage, and caller-not-in-team
 * error.
 *
 * <p>See plan 225 (L4-8-team-tools) Phase 1.
 */
public class TestTeamTaskCreateExecutor {

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

    private AiToolCall createCall(String subject, String description, String blockedBy) {
        AiToolCall call = new AiToolCall();
        call.setToolName("team-task-create");
        call.setId(1);
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"subject\":\"").append(subject).append("\"");
        if (description != null) {
            sb.append(",\"description\":\"").append(description).append("\"");
        }
        if (blockedBy != null) {
            sb.append(",\"blockedBy\":\"").append(blockedBy).append("\"");
        }
        sb.append("}");
        call.setInput(sb.toString());
        return call;
    }

    private ITeamManager createTeam(String leadSessionId) {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("TaskTeam", null, "lead",
                Arrays.asList(new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", leadSessionId, "actor-lead");
        return mgr;
    }

    @Test
    void noOpTeamManagerReportsHonestly() throws Exception {
        AgentToolExecuteContext ctx = createContext(NoOpTeamManager.noOp(),
                NoOpTeamTaskStore.noOp(), "sess-1");
        AiToolCall call = createCall("Do work", null, null);

        TeamTaskCreateExecutor executor = new TeamTaskCreateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "team-task-create with NoOp teamManager should return success (honest report)");
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("not enabled"),
                "NoOp teamManager result must honestly report not-enabled: " + result.getOutput().getBody());
    }

    @Test
    void noOpTaskStoreReportsHonestly() throws Exception {
        ITeamManager teamManager = createTeam("lead-sess");
        // Functional teamManager but NoOp taskStore.
        AgentToolExecuteContext ctx = createContext(teamManager, NoOpTeamTaskStore.noOp(), "lead-sess");
        AiToolCall call = createCall("Do work", null, null);

        TeamTaskCreateExecutor executor = new TeamTaskCreateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("task store is not enabled"),
                "NoOp taskStore result must honestly report not-enabled: " + result.getOutput().getBody());
    }

    @Test
    @SuppressWarnings("unchecked")
    void functionalCreateReturnsTaskIdAndCreatedStatus() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess");
        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "lead-sess");

        AiToolCall call = createCall("Implement feature X", "Detailed description", null);
        TeamTaskCreateExecutor executor = new TeamTaskCreateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());

        Map<String, Object> json = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        assertNotNull(json.get("taskId"));
        assertEquals("CREATED", json.get("status"));

        // Verify the task was actually stored.
        String taskId = (String) json.get("taskId");
        TeamTask stored = taskStore.getTask(taskId).orElseThrow();
        assertEquals("Implement feature X", stored.getSubject());
        assertEquals("Detailed description", stored.getDescription());
        assertEquals("lead-sess", stored.getCreatedBy());
    }

    @Test
    void functionalCreateStoresBlockedBy() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess");
        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "lead-sess");

        AiToolCall call = createCall("Dependent task", null, "task-1,task-2, task-3");
        TeamTaskCreateExecutor executor = new TeamTaskCreateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        Map<String, Object> json = (Map<String, Object>) JSON.parse(result.getOutput().getBody());
        String taskId = (String) json.get("taskId");

        TeamTask stored = taskStore.getTask(taskId).orElseThrow();
        assertEquals(3, stored.getBlockedBy().size());
        assertTrue(stored.getBlockedBy().contains("task-1"));
        assertTrue(stored.getBlockedBy().contains("task-2"));
        assertTrue(stored.getBlockedBy().contains("task-3"));
    }

    @Test
    void callerNotInTeamReturnsError() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess");
        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "stranger-sess");

        AiToolCall call = createCall("Task", null, null);
        TeamTaskCreateExecutor executor = new TeamTaskCreateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("not bound to any team"));
    }

    @Test
    void missingSubjectReturnsError() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess");
        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "lead-sess");

        AiToolCall call = new AiToolCall();
        call.setToolName("team-task-create");
        call.setId(1);
        call.setInput("{\"description\":\"no subject\"}");

        TeamTaskCreateExecutor executor = new TeamTaskCreateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("'subject'"));
    }
}
