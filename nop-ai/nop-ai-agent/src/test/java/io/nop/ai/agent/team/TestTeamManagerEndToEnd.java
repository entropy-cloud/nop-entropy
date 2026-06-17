package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.runtime.AgentActor;
import io.nop.ai.agent.runtime.IActorRuntime;
import io.nop.ai.agent.runtime.InMemoryActorRuntime;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for the TeamManager + Actor Runtime + engine integration
 * (plan 223 Phase 2, Minimum Rules #22 Anti-Hollow).
 *
 * <p>Full E2E path: a real {@link DefaultAgentEngine} with
 * {@link InMemoryTeamManager} + {@link InMemoryActorRuntime} drives:
 * <pre>
 *   1. Programmatically create a team (InMemoryTeamManager.createTeam)
 *   2. engine.execute(lead-agent) → Actor auto-registers in ActorRegistry
 *      (Actor Runtime wiring verified — the Actor is created/destroyed by
 *       the engine on the execution path)
 *   3. Programmatically bind the lead session to the team
 *      (TeamManager.bindMemberSession)
 *   4. Query team status: status ACTIVE + member bound + reverse-lookup by
 *      session yields the team
 *   5. Disband the team → status DISBANDED + active set excludes it
 * </pre>
 *
 * <p>This verifies the team lifecycle registry is wireable alongside the
 * Actor Runtime in a live engine and that the team tracks the session that
 * the engine executed. The foundational slice does NOT auto-bind teams on
 * the engine path (that is a successor), so the bind step is programmatic —
 * matching the documented opt-in contract.
 */
public class TestTeamManagerEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void teamLifecycleWithLiveEngineExecution() throws Exception {
        DefaultAgentEngine engine = newEngine();
        InMemoryTeamManager teamManager = new InMemoryTeamManager();
        engine.setTeamManager(teamManager);

        // Wire an Actor Runtime too, so we can verify the Actor created by
        // the engine is the one bound to the team member. Use a capturing
        // wrapper to grab the created Actor's id.
        final InMemoryActorRuntime inner = new InMemoryActorRuntime(
                engine::getSessionMailbox, 50L, 5000L);
        final AtomicReference<String> capturedActorId = new AtomicReference<>();
        IActorRuntime capturing = new IActorRuntime() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public AgentActor createActor(String sessionId, String agentName) {
                AgentActor actor = inner.createActor(sessionId, agentName);
                capturedActorId.set(actor.getActorId());
                return actor;
            }

            @Override
            public Optional<AgentActor> getActor(String actorId) {
                return inner.getActor(actorId);
            }

            @Override
            public Optional<AgentActor> getActorBySession(String sessionId) {
                return inner.getActorBySession(sessionId);
            }

            @Override
            public java.util.Collection<AgentActor> getActiveActors() {
                return inner.getActiveActors();
            }

            @Override
            public boolean destroyActor(String actorId) {
                return inner.destroyActor(actorId);
            }

            @Override
            public int destroyAll() {
                return inner.destroyAll();
            }
        };
        engine.setActorRuntime(capturing);

        // --- Step 1: programmatically create a team ---
        TeamSpec spec = new TeamSpec("e2e-team", "desc", "lead",
                List.of(new TeamMemberSpec("lead", "test-agent", MemberRole.LEAD)), 0);
        Team team = teamManager.createTeam(spec);
        assertNotNull(team.getTeamId());
        assertEquals(TeamStatus.CREATED, team.getStatus());
        assertEquals(1, team.getMembers().size());

        // --- Step 2: execute the lead agent via the engine ---
        // The engine creates an Actor (captured) and destroys it in finally.
        String sessionId = "team-e2e-sess";
        AgentMessageRequest request = new AgentMessageRequest(
                "test-agent", "complete this task", sessionId, null);
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "agent must complete normally; messages: " + result.getMessages());

        // The Actor was created during execution and captured.
        String leadActorId = capturedActorId.get();
        assertNotNull(leadActorId, "Actor must have been created during execution");

        // --- Step 3: programmatically bind the lead session to the team ---
        // The foundational slice does NOT auto-bind; integrators do this.
        boolean bound = teamManager.bindMemberSession(
                team.getTeamId(), "lead", sessionId, leadActorId);
        assertTrue(bound, "binding the lead session must succeed");

        // --- Step 4: query team status ---
        // First binding transitions CREATED → ACTIVE
        Team active = teamManager.getTeam(team.getTeamId()).get();
        assertEquals(TeamStatus.ACTIVE, active.getStatus(),
                "team must be ACTIVE after first member binds a session");

        TeamMember leadMember = teamManager.getMember(team.getTeamId(), "lead").get();
        assertEquals(sessionId, leadMember.getSessionId());
        assertEquals(leadActorId, leadMember.getActorId());
        assertTrue(leadMember.isBound());

        // Reverse-lookup by session yields the team
        Team bySession = teamManager.getTeamBySession(sessionId).get();
        assertEquals(team.getTeamId(), bySession.getTeamId());

        // Team is in the active set
        assertEquals(1, teamManager.getActiveTeams().size());

        // --- Step 5: disband the team ---
        Team disbanded = teamManager.disbandTeam(team.getTeamId());
        assertEquals(TeamStatus.DISBANDED, disbanded.getStatus());
        assertTrue(disbanded.getDisbandedAt() > 0);
        assertTrue(teamManager.getActiveTeams().isEmpty(),
                "disbanded team must not appear in the active set");

        // Disbanded team remains queryable (history/audit)
        assertTrue(teamManager.getTeam(team.getTeamId()).isPresent());

        // The actor was destroyed by the engine in the finally block
        assertFalse(inner.getActorBySession(sessionId).isPresent(),
                "Actor must be unregistered from registry after execution completes");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private DefaultAgentEngine newEngine() {
        IChatService chat = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildResponse());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return buildResponse();
            }

            private ChatResponse buildResponse() {
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
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(call.getId(), ""));
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
        return new DefaultAgentEngine(chat, tools);
    }
}
