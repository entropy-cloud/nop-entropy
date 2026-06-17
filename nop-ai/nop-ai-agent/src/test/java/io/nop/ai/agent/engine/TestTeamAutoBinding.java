package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.runtime.AgentActor;
import io.nop.ai.agent.runtime.IActorRuntime;
import io.nop.ai.agent.runtime.NoOpActorRuntime;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.TeamStatus;
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 231 Phase 2: focused integration tests for the engine's declarative
 * team auto-bind at the three execution entry points. Verifies lead idempotent
 * team creation + binding, member binding, the fail-fast branches (NoOp
 * conflict / no ACTIVE team / bindMemberSession false-return), the actorId
 * Actor-vs-sessionId paths, and zero regression for agents without team
 * declarations.
 *
 * <p>The mock chat service returns a final assistant message immediately (no
 * tool calls), so the ReAct loop terminates after one iteration; the auto-bind
 * runs before the executor, so team state is observable regardless of the
 * executor's behaviour.
 */
public class TestTeamAutoBinding {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void leadAutoBindCreatesTeamAndBindsLeadSession() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr);

        String leadSession = "lead-bind-sess";
        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-team-lead", "hi", leadSession, null))
                .get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "lead execution should complete. Messages: " + result.getMessages());

        // Team created + lead's first binding transitions CREATED -> ACTIVE.
        Optional<Team> teamOpt = mgr.getTeamBySession(leadSession);
        assertTrue(teamOpt.isPresent(), "lead session should be bound to a team");
        Team team = teamOpt.get();
        assertEquals(TeamStatus.ACTIVE, team.getStatus(), "team must be ACTIVE after lead binds");
        assertEquals("test-team", team.getSpec().getTeamName());

        TeamMember lead = mgr.getMember(team.getTeamId(), "test-team-lead").orElse(null);
        assertTrue(lead != null && lead.isBound(), "lead member must be bound");
        assertEquals(leadSession, lead.getSessionId());
        assertEquals(MemberRole.LEAD, lead.getRole());
    }

    @Test
    void leadAutoBindIsIdempotentAcrossExecutions() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr);

        String leadSession = "lead-idem-sess";
        engine.execute(new AgentMessageRequest("test-team-lead", "first", leadSession, null))
                .get(60, TimeUnit.SECONDS);
        String teamIdAfterFirst = mgr.getTeamBySession(leadSession).orElseThrow().getTeamId();
        int teamCountAfterFirst = mgr.getActiveTeams().size();

        // Second execution (new turn) must reuse the existing team, not rebuild.
        engine.execute(new AgentMessageRequest("test-team-lead", "second", leadSession, null))
                .get(60, TimeUnit.SECONDS);
        String teamIdAfterSecond = mgr.getTeamBySession(leadSession).orElseThrow().getTeamId();
        int teamCountAfterSecond = mgr.getActiveTeams().size();

        assertEquals(teamIdAfterFirst, teamIdAfterSecond,
                "second lead execution must reuse the same teamId (idempotent)");
        assertEquals(teamCountAfterFirst, teamCountAfterSecond,
                "team count must not grow on re-execution");
    }

    @Test
    void memberAutoBindBindsToActivatedTeam() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr);

        // Lead must execute first to create + activate the team.
        String leadSession = "lead-then-member-sess";
        engine.execute(new AgentMessageRequest("test-team-lead", "lead first", leadSession, null))
                .get(60, TimeUnit.SECONDS);
        String teamId = mgr.getTeamBySession(leadSession).orElseThrow().getTeamId();

        // Now execute a member agent; its <team-member> should auto-bind.
        String memberSession = "member-a-bind-sess";
        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-team-member-a", "member reporting", memberSession, null))
                .get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "member execution should complete. Messages: " + result.getMessages());
        TeamMember member = mgr.getMember(teamId, "member-a").orElse(null);
        assertTrue(member != null && member.isBound(), "member-a must be bound after execution");
        assertEquals(memberSession, member.getSessionId());
    }

    @Test
    void memberAutoBindFailsFastWhenNoActiveTeamExists() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr);

        // Member executes before the lead -> no ACTIVE team -> fail-fast.
        CompletionException ce = assertThrows(CompletionException.class, () ->
                engine.execute(new AgentMessageRequest("test-team-member-a", "too early", "early-member-sess", null))
                        .join());
        Throwable cause = unwrap(ce);
        assertTrue(cause instanceof NopAiAgentException, "expect NopAiAgentException, got " + cause);
        assertTrue(cause.getMessage().contains("no ACTIVE team"),
                "error should mention no ACTIVE team: " + cause.getMessage());
    }

    @Test
    void memberAutoBindFailsFastWhenTeamOnlyCreatedNotActive() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr);

        // Manually pre-create a CREATED team (lead not yet bound) so that
        // getActiveTeams() returns it but it is NOT ACTIVE. The member
        // auto-bind must fail-fast instead of binding to an unactivated team.
        TeamSpec createdOnly = new TeamSpec("test-team", null, "test-team-lead",
                Collections.singletonList(
                        new TeamMemberSpec("member-a", "test-team-member-a", MemberRole.MEMBER)),
                0);
        mgr.createTeam(createdOnly);

        CompletionException ce = assertThrows(CompletionException.class, () ->
                engine.execute(new AgentMessageRequest("test-team-member-a", "too early",
                        "created-only-member-sess", null))
                        .join());
        Throwable cause = unwrap(ce);
        assertTrue(cause instanceof NopAiAgentException);
        assertTrue(cause.getMessage().contains("no ACTIVE team"),
                "member must fail-fast on a CREATED-only team: " + cause.getMessage());
    }

    @Test
    void memberAutoBindFailsFastWhenMemberNotInRoster() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr);

        // Lead executes -> team activated with roster [lead, member-a, member-b].
        String leadSession = "lead-ghost-sess";
        engine.execute(new AgentMessageRequest("test-team-lead", "lead first", leadSession, null))
                .get(60, TimeUnit.SECONDS);

        // Ghost member declares memberName=ghost, which is NOT in the roster.
        CompletionException ce = assertThrows(CompletionException.class, () ->
                engine.execute(new AgentMessageRequest("test-team-member-ghost", "intruder",
                        "ghost-sess", null))
                        .join());
        Throwable cause = unwrap(ce);
        assertTrue(cause instanceof NopAiAgentException);
        assertTrue(cause.getMessage().contains("not in the lead's team roster"),
                "error should mention roster mismatch: " + cause.getMessage());
    }

    @Test
    void noOpManagerWithTeamDeclarationFailsFastSynchronously() {
        // Shipped default teamManager = NoOpTeamManager; a <team> declaration
        // must fail-fast in the synchronous precheck (before supplyAsync), so
        // execute() itself throws rather than failing the future.
        DefaultAgentEngine engine = newEngine(NoOpTeamManager.noOp());
        // The engine keeps NoOp when null/noOp is set.
        assertEquals(NoOpTeamManager.noOp().getClass(), engine.getTeamManager().getClass());

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () ->
                engine.execute(new AgentMessageRequest("test-team-lead", "noop", "noop-lead-sess", null)));
        assertTrue(ex.getMessage().contains("no functional ITeamManager"),
                "precheck error should mention missing functional manager: " + ex.getMessage());
    }

    @Test
    void noTeamDeclarationTouchesNoTeamManager() throws Exception {
        // A plain agent without <team>/<team-member> must leave the
        // teamManager empty (zero regression).
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr);

        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-react-agent", "hello", "plain-sess", null))
                .get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "plain agent should complete. Messages: " + result.getMessages());
        assertTrue(mgr.getActiveTeams().isEmpty(),
                "no team should be created for an agent without <team>/<team-member>");
    }

    @Test
    void actorIdFallsBackToSessionIdWhenActorRuntimeIsNoOp() throws Exception {
        // Shipped default actorRuntime = NoOpActorRuntime (isEnabled()=false),
        // so resolveActorId falls back to sessionId. The bound member's
        // actorId must equal the sessionId.
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr);
        assertTrue(engine.getActorRuntime() instanceof NoOpActorRuntime,
                "precondition: default actorRuntime is NoOp");

        String leadSession = "actorid-sessionid-sess";
        engine.execute(new AgentMessageRequest("test-team-lead", "lead", leadSession, null))
                .get(60, TimeUnit.SECONDS);
        Team team = mgr.getTeamBySession(leadSession).orElseThrow();
        TeamMember lead = mgr.getMember(team.getTeamId(), "test-team-lead").orElseThrow();
        assertEquals(leadSession, lead.getActorId(),
                "with NoOp actorRuntime, actorId must fall back to sessionId");
    }

    @Test
    void actorIdUsesActorIdWhenActorRuntimeIsEnabled() throws Exception {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        DefaultAgentEngine engine = newEngine(mgr);
        // Wire a minimal functional actorRuntime so createActor produces an
        // Actor whose UUID actorId is used for binding.
        engine.setActorRuntime(new SimpleFunctionalActorRuntime());

        String leadSession = "actorid-actor-sess";
        engine.execute(new AgentMessageRequest("test-team-lead", "lead", leadSession, null))
                .get(60, TimeUnit.SECONDS);
        Team team = mgr.getTeamBySession(leadSession).orElseThrow();
        TeamMember lead = mgr.getMember(team.getTeamId(), "test-team-lead").orElseThrow();
        assertNotEquals(leadSession, lead.getActorId(),
                "with a functional actorRuntime, actorId must be the Actor's UUID, not sessionId");
        // Actor's actorId is a non-empty UUID-like string.
        assertTrue(lead.getActorId().startsWith("actor-"),
                "actorId should come from the Actor: " + lead.getActorId());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static Throwable unwrap(CompletionException ce) {
        Throwable c = ce.getCause();
        return c != null ? c : ce;
    }

    /**
     * Minimal mock chat service: returns a final assistant message (content
     * set, no tool calls) so the ReAct loop terminates after one iteration.
     * The team auto-bind runs before the executor, so it is observable
     * regardless of chat output.
     */
    private DefaultAgentEngine newEngine(io.nop.ai.agent.team.ITeamManager mgr) {
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
        return engine;
    }

    /**
     * Minimal functional {@link IActorRuntime} for testing the Actor-based
     * actorId path. createActor registers a synthetic AgentActor; the engine
     * calls getActorBySession to resolve the actorId for team binding.
     */
    private static class SimpleFunctionalActorRuntime implements IActorRuntime {
        private final java.util.concurrent.ConcurrentHashMap<String, AgentActor> bySession =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public AgentActor createActor(String sessionId, String agentName) {
            return bySession.computeIfAbsent(sessionId, sid -> {
                String actorId = "actor-" + counter.incrementAndGet() + "-" + sid;
                return new AgentActor(actorId, sid, agentName, System.currentTimeMillis(), null);
            });
        }

        @Override
        public Optional<AgentActor> getActor(String actorId) {
            return bySession.values().stream().filter(a -> a.getActorId().equals(actorId)).findFirst();
        }

        @Override
        public Optional<AgentActor> getActorBySession(String sessionId) {
            return Optional.ofNullable(bySession.get(sessionId));
        }

        @Override
        public Collection<AgentActor> getActiveActors() {
            return bySession.values();
        }

        @Override
        public boolean destroyActor(String actorId) {
            return bySession.values().removeIf(a -> a.getActorId().equals(actorId));
        }

        @Override
        public int destroyAll() {
            int n = bySession.size();
            bySession.clear();
            return n;
        }
    }
}
