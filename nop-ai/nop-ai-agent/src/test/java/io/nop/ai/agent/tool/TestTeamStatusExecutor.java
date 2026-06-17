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
 * Focused unit tests for {@link TeamStatusExecutor}: NoOp honest reporting,
 * functional JSON output with team + members + taskCount, and caller-not-in-team
 * error.
 *
 * <p>See plan 225 (L4-8-team-tools) Phase 1.
 */
public class TestTeamStatusExecutor {

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

    private AiToolCall createCall() {
        AiToolCall call = new AiToolCall();
        call.setToolName("team-status");
        call.setId(1);
        return call;
    }

    private ITeamManager createTeam(String leadSessionId) {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("StatusTeam", "a test team", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", leadSessionId, "actor-lead");
        return mgr;
    }

    @Test
    void noOpTeamManagerReportsHonestly() throws Exception {
        AgentToolExecuteContext ctx = createContext(NoOpTeamManager.noOp(),
                NoOpTeamTaskStore.noOp(), "sess-1");

        TeamStatusExecutor executor = new TeamStatusExecutor();
        AiToolCallResult result = executor.executeAsync(createCall(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus(),
                "team-status with NoOp teamManager should return success (honest report)");
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("not enabled"),
                "NoOp teamManager result must honestly report not-enabled: " + result.getOutput().getBody());
    }

    @Test
    void nullTeamManagerReportsHonestly() throws Exception {
        AgentToolExecuteContext ctx = createContext(null, NoOpTeamTaskStore.noOp(), "sess-1");

        TeamStatusExecutor executor = new TeamStatusExecutor();
        AiToolCallResult result = executor.executeAsync(createCall(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("not enabled"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void functionalStatusReturnsStructuredJson() throws Exception {
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        ITeamManager teamManager = createTeam("lead-sess");

        // Create a task so taskCount > 0.
        taskStore.createTask(teamManager.getActiveTeams().iterator().next().getTeamId(),
                "A task", null, Collections.emptyList(), "lead-sess");

        AgentToolExecuteContext ctx = createContext(teamManager, taskStore, "lead-sess");

        TeamStatusExecutor executor = new TeamStatusExecutor();
        AiToolCallResult result = executor.executeAsync(createCall(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        String json = result.getOutput().getBody();

        Map<String, Object> status = (Map<String, Object>) JSON.parse(json);
        assertNotNull(status.get("teamId"));
        assertEquals("StatusTeam", status.get("teamName"));
        assertEquals("ACTIVE", status.get("status"));
        assertEquals(1, status.get("taskCount"), "taskCount should be 1 after creating one task");

        // members array
        Object membersObj = status.get("members");
        assertTrue(membersObj instanceof java.util.List, "members should be a list");
        java.util.List<Map<String, Object>> members = (java.util.List<Map<String, Object>>) membersObj;
        assertEquals(2, members.size());

        boolean foundLead = false;
        boolean foundWorker = false;
        for (Map<String, Object> m : members) {
            if ("lead".equals(m.get("memberName"))) {
                assertEquals("LEAD", m.get("role"));
                assertEquals("lead-sess", m.get("sessionId"));
                assertEquals(Boolean.TRUE, m.get("bound"));
                foundLead = true;
            }
            if ("worker".equals(m.get("memberName"))) {
                assertEquals("MEMBER", m.get("role"));
                assertEquals(Boolean.FALSE, m.get("bound"));
                foundWorker = true;
            }
        }
        assertTrue(foundLead, "JSON should contain lead member");
        assertTrue(foundWorker, "JSON should contain worker member");
    }

    @Test
    void functionalStatusWithNoOpTaskStoreReturnsZeroTaskCount() throws Exception {
        ITeamManager teamManager = createTeam("lead-sess");
        // NoOp task store — taskCount should be 0 (team-status does NOT short-circuit on NoOp taskStore).
        AgentToolExecuteContext ctx = createContext(teamManager, NoOpTeamTaskStore.noOp(), "lead-sess");

        TeamStatusExecutor executor = new TeamStatusExecutor();
        AiToolCallResult result = executor.executeAsync(createCall(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", result.getStatus());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().getBody().contains("\"taskCount\":0"),
                "NoOp taskStore should yield taskCount=0: " + result.getOutput().getBody());
    }

    @Test
    void callerNotInTeamReturnsError() throws Exception {
        ITeamManager teamManager = createTeam("lead-sess");
        AgentToolExecuteContext ctx = createContext(teamManager, NoOpTeamTaskStore.noOp(), "stranger-sess");

        TeamStatusExecutor executor = new TeamStatusExecutor();
        AiToolCallResult result = executor.executeAsync(createCall(), ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("not bound to any team"));
    }
}
