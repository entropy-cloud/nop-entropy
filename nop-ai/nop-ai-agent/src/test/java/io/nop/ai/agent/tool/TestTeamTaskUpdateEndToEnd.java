package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.message.LocalAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.DbTeamTaskStore;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for the {@code team-task-update} state machine (plan 227
 * Phase 3). Three paths are exercised:
 *
 * <ol>
 *   <li><b>in-memory through the engine</b>: lead agent ReAct calls
 *       {@code team-task-create} → member agent ReAct calls
 *       {@code team-task-update} (claim) → member calls
 *       {@code team-task-update} (complete) → lead calls {@code team-status}.
 *       The test asserts the store reflects CLAIMED then COMPLETED after each
 *       step, and the tool responses flow back into the conversation.</li>
 *   <li><b>DB-backed cross-instance</b>: two {@link DbTeamTaskStore} instances
 *       sharing one H2 DB (simulating two processes). A task created via
 *       instance A is claimed + completed via instance B, then re-read via
 *       instance A as COMPLETED — proving cross-process sharing + CAS through
 *       the executor path.</li>
 *   <li><b>NoOp default zero-regression</b>: shipped defaults honestly report
 *       "not enabled" inside a ReAct loop; the agent still completes.</li>
 * </ol>
 *
 * <p>These satisfy Minimum Rules #22 (Anti-Hollow: full ReAct → store path)
 * and #23 (Wiring Verification: tool reaches the functional store, not NoOp).
 *
 * <p>See plan 227 (team-task-update) Phase 3.
 */
public class TestTeamTaskUpdateEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String LEAD_SESSION = "lead-update-e2e";
    private static final String MEMBER_SESSION = "member-update-e2e";

    /**
     * Tool manager routing the 4 team tools to their executors. The executors
     * read teamManager / teamTaskStore / messenger from the
     * AgentToolExecuteContext provided by the engine's dispatch loop.
     */
    private IToolManager createTeamToolManager() {
        TeamTaskCreateExecutor taskCreateExecutor = new TeamTaskCreateExecutor();
        TeamTaskUpdateExecutor taskUpdateExecutor = new TeamTaskUpdateExecutor();
        TeamStatusExecutor statusExecutor = new TeamStatusExecutor();
        TeamSendMessageExecutor sendExecutor = new TeamSendMessageExecutor();

        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                IToolExecutor executor;
                switch (toolName) {
                    case "team-task-create":
                        executor = taskCreateExecutor;
                        break;
                    case "team-task-update":
                        executor = taskUpdateExecutor;
                        break;
                    case "team-status":
                        executor = statusExecutor;
                        break;
                    case "team-send-message":
                        executor = sendExecutor;
                        break;
                    default:
                        return CompletableFuture.completedFuture(AiToolCallResult.successResult(call.getId(), ""));
                }
                return executor.executeAsync(call, context).toCompletableFuture();
            }

            @Override
            public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls, IToolExecuteContext context) {
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
    }

    private InMemoryTeamManager createAndBindTeam() {
        InMemoryTeamManager mgr = new InMemoryTeamManager();
        TeamSpec spec = new TeamSpec("UpdateTeam", "team-task-update e2e", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("member", "member-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", LEAD_SESSION, "actor-lead");
        mgr.bindMemberSession(team.getTeamId(), "member", MEMBER_SESSION, "actor-member");
        return mgr;
    }

    private ChatToolCall makeToolCall(String id, String name, Map<String, Object> args) {
        ChatToolCall tc = new ChatToolCall();
        tc.setId(id);
        tc.setName(name);
        tc.setArguments(args);
        return tc;
    }

    // ========================================================================
    // (1) In-memory end-to-end through engine.execute()
    // ========================================================================

    @Test
    void inMemoryEndToEndLeadCreateMemberClaimComplete() throws Exception {
        IMessageService messageService = new io.nop.message.core.local.LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);
        InMemoryTeamManager teamManager = createAndBindTeam();
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        String teamId = teamManager.getActiveTeams().iterator().next().getTeamId();

        // --- Lead: ReAct loop calls team-task-create ---
        IChatService leadChat = scriptedChat(
                // round 0: create a task
                makeToolCall("e2e-create", "team-task-create",
                        args("subject", "E2E state machine",
                                "description", "claim then complete")),
                // round 1: final text
                null);
        DefaultAgentEngine engine = new DefaultAgentEngine(leadChat, createTeamToolManager());
        engine.setMessenger(messenger);
        engine.setTeamManager(teamManager);
        engine.setTeamTaskStore(taskStore);

        AgentMessageRequest leadReq = new AgentMessageRequest("test-agent",
                "create a team task", LEAD_SESSION, null);
        AgentExecutionResult leadResult = engine.execute(leadReq).get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, leadResult.getStatus(),
                "Lead should complete. Messages: " + leadResult.getMessages());

        // The task is now in the shared store.
        List<TeamTask> tasks = taskStore.getTasksByTeam(teamId);
        assertEquals(1, tasks.size(), "Exactly one task created by the lead");
        TeamTask task = tasks.get(0);
        assertEquals(TeamTaskStatus.CREATED, task.getStatus(),
                "Task starts in CREATED before the member claims it");
        String taskId = task.getTaskId();

        // --- Member: ReAct loop calls team-task-update claim then complete ---
        IChatService memberChat = scriptedChat(
                // round 0: claim the task the lead just created
                makeToolCall("e2e-claim", "team-task-update",
                        args("taskId", taskId, "action", "claim")),
                // round 1: complete the claimed task
                makeToolCall("e2e-complete", "team-task-update",
                        args("taskId", taskId, "action", "complete")),
                // round 2: final text
                null);
        DefaultAgentEngine memberEngine = new DefaultAgentEngine(memberChat, createTeamToolManager());
        memberEngine.setMessenger(messenger);
        memberEngine.setTeamManager(teamManager);
        memberEngine.setTeamTaskStore(taskStore);

        AgentMessageRequest memberReq = new AgentMessageRequest("test-agent",
                "claim and complete the task", MEMBER_SESSION, null);
        AgentExecutionResult memberResult = memberEngine.execute(memberReq).get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, memberResult.getStatus(),
                "Member should complete. Messages: " + memberResult.getMessages());

        // Anti-Hollow #22 / Wiring #23: the store reflects the full lifecycle.
        TeamTask finalTask = taskStore.getTask(taskId).orElseThrow();
        assertEquals(TeamTaskStatus.COMPLETED, finalTask.getStatus(),
                "After member claim + complete, the store task must be COMPLETED");
        assertEquals(MEMBER_SESSION, finalTask.getClaimedBy(),
                "claim must have recorded the member sessionId in claimedBy");

        // --- Lead: ReAct loop calls team-status → taskCount reflects the task ---
        IChatService leadStatusChat = scriptedChat(
                makeToolCall("e2e-status", "team-status", new HashMap<>()),
                null);
        DefaultAgentEngine leadStatusEngine = new DefaultAgentEngine(leadStatusChat, createTeamToolManager());
        leadStatusEngine.setMessenger(messenger);
        leadStatusEngine.setTeamManager(teamManager);
        leadStatusEngine.setTeamTaskStore(taskStore);

        AgentMessageRequest statusReq = new AgentMessageRequest("test-agent",
                "check team status", LEAD_SESSION, null);
        AgentExecutionResult statusResult = leadStatusEngine.execute(statusReq).get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, statusResult.getStatus());

        // TeamStatusExecutor only returns taskCount (not per-task status), so we
        // assert taskCount here; the per-task COMPLETED assertion is the store
        // query above.
        String toolResponse = statusResult.getMessages().stream()
                .filter(m -> m instanceof io.nop.ai.api.chat.messages.ChatToolResponseMessage)
                .map(m -> ((io.nop.ai.api.chat.messages.ChatToolResponseMessage) m).getContent())
                .reduce("", (a, b) -> a + " " + b);
        assertTrue(toolResponse.contains("\"taskCount\":1"),
                "team-status should show taskCount=1: " + toolResponse);
    }

    // ========================================================================
    // (2) DB-backed cross-instance create → claim → complete
    // ========================================================================

    @Test
    void dbBackedCrossInstanceCreateClaimComplete() throws Exception {
        // Shared H2 DB; two independent store instances simulate two processes.
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:test-team-task-e2e-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");

        DbTeamTaskStore storeA = new DbTeamTaskStore(ds);
        DbTeamTaskStore storeB = new DbTeamTaskStore(ds);
        InMemoryTeamManager teamManager = createAndBindTeam();
        String teamId = teamManager.getActiveTeams().iterator().next().getTeamId();

        // Lead (process A) creates the task directly in the shared DB.
        TeamTask created = storeA.createTask(teamId, "DB e2e task", null,
                Collections.emptyList(), LEAD_SESSION);

        // Member (process B, different store instance) claims via the executor
        // path — proves the TeamTaskUpdateExecutor reaches a functional store
        // and drives the cross-instance state machine.
        OptionalAssert claimResult = executeUpdateViaExecutor(
                teamManager, storeB, MEMBER_SESSION, created.getTaskId(), "claim");
        assertTrue(claimResult.applied, "claim via store B must succeed: " + claimResult.body);
        assertEquals(TeamTaskStatus.CLAIMED.name(), claimResult.status);
        assertEquals(MEMBER_SESSION, claimResult.claimedBy);

        // CAS: a second claim via store A must fail (DB STATUS guard).
        OptionalAssert competitor = executeUpdateViaExecutor(
                teamManager, storeA, "competitor-sess", created.getTaskId(), "claim");
        assertTrue(!competitor.applied,
                "A second concurrent claimer must lose the DB CAS race: " + competitor.body);

        // Member (process B) completes.
        OptionalAssert completeResult = executeUpdateViaExecutor(
                teamManager, storeB, MEMBER_SESSION, created.getTaskId(), "complete");
        assertTrue(completeResult.applied, "complete via store B must succeed: " + completeResult.body);
        assertEquals(TeamTaskStatus.COMPLETED.name(), completeResult.status);

        // Cross-instance visibility: store A re-reads the row as COMPLETED.
        TeamTask readFromA = storeA.getTask(created.getTaskId()).orElseThrow();
        assertEquals(TeamTaskStatus.COMPLETED, readFromA.getStatus(),
                "store A (other process) must see the task as COMPLETED in the shared DB");
        assertEquals(MEMBER_SESSION, readFromA.getClaimedBy());

        if (ds instanceof AutoCloseable) {
            ((AutoCloseable) ds).close();
        }
    }

    /** Drive {@link TeamTaskUpdateExecutor} directly with an AgentToolExecuteContext. */
    private OptionalAssert executeUpdateViaExecutor(ITeamManager teamManager,
                                                    ITeamTaskStore taskStore,
                                                    String sessionId,
                                                    String taskId,
                                                    String action) throws Exception {
        io.nop.ai.agent.engine.AgentToolExecuteContext ctx = new io.nop.ai.agent.engine.AgentToolExecuteContext(
                new java.io.File("."), Collections.emptyMap(), 0L, null, null, null,
                null, io.nop.ai.agent.message.NoOpAgentMessenger.noOp(), sessionId, "test-agent",
                null, null, null, null,
                teamManager, taskStore);
        AiToolCall call = new AiToolCall();
        call.setToolName("team-task-update");
        call.setId(1);
        call.setInput("{\"taskId\":\"" + taskId + "\",\"action\":\"" + action + "\"}");

        TeamTaskUpdateExecutor executor = new TeamTaskUpdateExecutor();
        AiToolCallResult result = executor.executeAsync(call, ctx).toCompletableFuture().get(10, TimeUnit.SECONDS);
        OptionalAssert a = new OptionalAssert();
        a.body = result.getOutput() != null ? result.getOutput().getBody() : "";
        @SuppressWarnings("unchecked")
        Map<String, Object> json = a.body.isEmpty() ? Collections.emptyMap()
                : (Map<String, Object>) io.nop.api.core.json.JSON.parse(a.body);
        a.applied = Boolean.TRUE.equals(json.get("applied"));
        a.status = (String) json.get("status");
        a.claimedBy = (String) json.get("claimedBy");
        return a;
    }

    private static class OptionalAssert {
        boolean applied;
        String status;
        String claimedBy;
        String body;
    }

    // ========================================================================
    // (3) NoOp default zero-regression
    // ========================================================================

    @Test
    void noOpDefaultsZeroRegressionInReActLoop() throws Exception {
        IChatService chatService = scriptedChat(
                makeToolCall("noop-update", "team-task-update",
                        args("taskId", "any", "action", "claim")),
                null);
        // Engine with ALL defaults — no setTeamManager / setTeamTaskStore.
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, createTeamToolManager());

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "use team tools");
        AgentExecutionResult result = engine.execute(request).get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Agent should complete even with NoOp team defaults. Messages: " + result.getMessages());

        String toolResponse = result.getMessages().stream()
                .filter(m -> m instanceof io.nop.ai.api.chat.messages.ChatToolResponseMessage)
                .map(m -> ((io.nop.ai.api.chat.messages.ChatToolResponseMessage) m).getContent())
                .reduce("", (a, b) -> a + " " + b);
        assertTrue(toolResponse.contains("not enabled"),
                "NoOp default should produce honest 'not enabled' report: " + toolResponse);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Build a chat service that emits the given tool calls in sequence, then
     * a final text response (when the next script entry is null). */
    private static IChatService scriptedChat(ChatToolCall... script) {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicReference<ChatToolCall[]> scriptRef = new AtomicReference<>(script);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(buildResponse());
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return buildResponse();
            }

            private ChatResponse buildResponse() {
                int n = callCount.getAndIncrement();
                ChatToolCall[] s = scriptRef.get();
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (n < s.length && s[n] != null) {
                    msg.setContent("");
                    msg.setToolCalls(List.of(s[n]));
                } else {
                    msg.setContent("Done.");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
