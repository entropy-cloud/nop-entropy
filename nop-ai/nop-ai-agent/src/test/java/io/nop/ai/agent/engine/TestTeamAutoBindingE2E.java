package io.nop.ai.agent.engine;

import io.nop.ai.agent.message.NoOpAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.agent.team.TeamStatus;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.agent.tool.TeamStatusExecutor;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 231 Phase 3 end-to-end test (Anti-Hollow #22 + Wiring #23): proves a
 * complete declarative team — lead declared via {@code <team>} + two members
 * declared via {@code <team-member>} — materialises purely from
 * {@code .agent.xml} configuration, with no integrator code calling
 * {@code createTeam}/{@code bindMemberSession}. The resulting team is then
 * transparently consumed by the existing {@code team-status} tool (drop-in
 * semantics).
 *
 * <p>Full path: engine.execute(lead) → loadAgentModel → precheck → async
 * block → createActor → auto-bind (createTeam + bindMemberSession lead)
 * → executor completes. Then engine.execute(memberA/B) → auto-bind member
 * sessions. Finally TeamStatusExecutor reads the declaratively-materialised
 * team and returns the complete team + 3 members.
 */
public class TestTeamAutoBindingE2E {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    @SuppressWarnings("unchecked")
    void declarativeTeamMaterialisesAndIsConsumedByTeamStatusTool() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        DefaultAgentEngine engine = newEngine(mgr, taskStore);

        String leadSession = "e2e-lead-sess";
        String memberASession = "e2e-member-a-sess";
        String memberBSession = "e2e-member-b-sess";

        // 1. Execute the lead agent — auto-bind creates the team + binds lead.
        AgentExecutionResult leadResult = engine.execute(
                new AgentMessageRequest("test-team-lead", "lead starts the team", leadSession, null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, leadResult.getStatus(),
                "lead should complete. Messages: " + leadResult.getMessages());

        Team team = mgr.getTeamBySession(leadSession).orElse(null);
        assertNotNull(team, "team must exist after lead execution");
        assertEquals(TeamStatus.ACTIVE, team.getStatus(), "team must be ACTIVE");
        String teamId = team.getTeamId();

        // 2. Execute both member agents — auto-bind binds each member session.
        AgentExecutionResult memberAResult = engine.execute(
                new AgentMessageRequest("test-team-member-a", "member A joins", memberASession, null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, memberAResult.getStatus(),
                "member A should complete. Messages: " + memberAResult.getMessages());

        AgentExecutionResult memberBResult = engine.execute(
                new AgentMessageRequest("test-team-member-b", "member B joins", memberBSession, null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, memberBResult.getStatus(),
                "member B should complete. Messages: " + memberBResult.getMessages());

        // 3. Assert all three members are bound in the manager.
        TeamMember lead = mgr.getMember(teamId, "test-team-lead").orElse(null);
        TeamMember memberA = mgr.getMember(teamId, "member-a").orElse(null);
        TeamMember memberB = mgr.getMember(teamId, "member-b").orElse(null);
        assertNotNull(lead);
        assertNotNull(memberA);
        assertNotNull(memberB);
        assertTrue(lead.isBound() && memberA.isBound() && memberB.isBound(),
                "lead + both members must be bound");
        assertEquals(leadSession, lead.getSessionId());
        assertEquals(memberASession, memberA.getSessionId());
        assertEquals(memberBSession, memberB.getSessionId());
        assertEquals(MemberRole.LEAD, lead.getRole());
        assertEquals(MemberRole.MEMBER, memberA.getRole());
        assertEquals(MemberRole.MEMBER, memberB.getRole());

        // 4. Anti-Hollow / drop-in: the existing team-status tool, invoked with
        // the lead's session, transparently returns the complete team + 3
        // members. No special "declarative team" branch exists in the tool.
        AgentToolExecuteContext statusCtx = new AgentToolExecuteContext(
                new File("."), Collections.emptyMap(), 0L, null, null, null,
                null, NoOpAgentMessenger.noOp(), leadSession, "test-team-lead",
                null, null, null, null,
                mgr, taskStore);
        AiToolCall call = new AiToolCall();
        call.setToolName("team-status");
        call.setId(1);

        TeamStatusExecutor statusExecutor = new TeamStatusExecutor();
        AiToolCallResult statusResult = statusExecutor.executeAsync(call, statusCtx)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals("success", statusResult.getStatus());
        Map<String, Object> status = (Map<String, Object>) JSON.parse(statusResult.getOutput().getBody());
        assertEquals("test-team", status.get("teamName"));
        assertEquals("ACTIVE", status.get("status"));

        List<Map<String, Object>> members = (List<Map<String, Object>>) status.get("members");
        assertEquals(3, members.size(), "team-status must report all 3 members (lead + 2)");
        boolean foundLead = false, foundA = false, foundB = false;
        for (Map<String, Object> m : members) {
            String name = (String) m.get("memberName");
            switch (name) {
                case "test-team-lead":
                    assertEquals("LEAD", m.get("role"));
                    assertEquals(Boolean.TRUE, m.get("bound"));
                    foundLead = true;
                    break;
                case "member-a":
                    assertEquals("MEMBER", m.get("role"));
                    assertEquals(Boolean.TRUE, m.get("bound"));
                    foundA = true;
                    break;
                case "member-b":
                    assertEquals("MEMBER", m.get("role"));
                    assertEquals(Boolean.TRUE, m.get("bound"));
                    foundB = true;
                    break;
                default:
                    break;
            }
        }
        assertTrue(foundLead && foundA && foundB,
                "team-status must enumerate lead + member-a + member-b: " + members);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private DefaultAgentEngine newEngine(InMemoryTeamManager mgr, InMemoryTeamTaskStore taskStore) {
        IChatService chat = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(finalResponse());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return finalResponse();
            }

            private ChatResponse finalResponse() {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done");
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
        IToolManager tools = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
            }

            @Override
            public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                return model;
            }
        };
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, tools);
        engine.setTeamManager(mgr);
        engine.setTeamTaskStore(taskStore);
        return engine;
    }
}
