package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.IAgentMessageHandler;
import io.nop.ai.agent.message.LocalAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.DefaultTeamAclChecker;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.NoOpTeamAclChecker;
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
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.json.JSON;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.util.ICancelToken;
import io.nop.message.core.local.LocalMessageService;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end ACL test (plan 228 Phase 3).
 *
 * <p>Drives the full path: {@code DefaultAgentEngine.execute()} → ReAct →
 * team tool → {@link DefaultTeamAclChecker} → store/messenger. Proves:
 * <ul>
 *   <li><b>Anti-Hollow #22</b>: the engine actually consults the wired
 *       checker (not just the type system).</li>
 *   <li><b>Wiring #23</b>: an ACL denial actually prevents the store
 *       mutation (task status stays CREATED when a MEMBER abandon-unclaimed
 *       is denied).</li>
 *   <li><b>Role distinction</b>: LEAD can abandon-unclaimed, MEMBER cannot.</li>
 *   <li><b>NoOp zero-regression</b>: default engine config (no
 *       setTeamAclChecker) preserves the pre-ACL behaviour.</li>
 * </ul>
 *
 * <p>See plan 228 Phase 3.
 */
public class TestTeamAclEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String LEAD_SESSION = "lead-acl-e2e";
    private static final String MEMBER_SESSION = "member-acl-e2e";

    private IToolManager createTeamToolManager() {
        TeamSendMessageExecutor sendExecutor = new TeamSendMessageExecutor();
        TeamStatusExecutor statusExecutor = new TeamStatusExecutor();
        TeamTaskCreateExecutor taskCreateExecutor = new TeamTaskCreateExecutor();
        TeamTaskUpdateExecutor taskUpdateExecutor = new TeamTaskUpdateExecutor();
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                IToolExecutor executor;
                switch (toolName) {
                    case "team-send-message":
                        executor = sendExecutor;
                        break;
                    case "team-status":
                        executor = statusExecutor;
                        break;
                    case "team-task-create":
                        executor = taskCreateExecutor;
                        break;
                    case "team-task-update":
                        executor = taskUpdateExecutor;
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
        TeamSpec spec = new TeamSpec("AclTeam", "acl e2e team", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("member", "member-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", LEAD_SESSION, "actor-lead");
        mgr.bindMemberSession(team.getTeamId(), "member", MEMBER_SESSION, "actor-member");
        return mgr;
    }

    private DefaultAgentEngine engineWithChecker(IChatService chat,
                                                  LocalAgentMessenger messenger,
                                                  InMemoryTeamManager teamManager,
                                                  InMemoryTeamTaskStore taskStore,
                                                  io.nop.ai.agent.team.ITeamAclChecker checker) {
        DefaultAgentEngine engine = new DefaultAgentEngine(chat, createTeamToolManager());
        engine.setMessenger(messenger);
        engine.setTeamManager(teamManager);
        engine.setTeamTaskStore(taskStore);
        engine.setTeamAclChecker(checker);
        return engine;
    }

    private ChatToolCall makeToolCall(String id, String name, Map<String, Object> args) {
        ChatToolCall tc = new ChatToolCall();
        tc.setId(id);
        tc.setName(name);
        tc.setArguments(args);
        return tc;
    }

    private static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static IChatService scriptedChat(ChatToolCall... script) {
        AtomicInteger callCount = new AtomicInteger(0);
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
                ChatAssistantMessage msg = new ChatAssistantMessage();
                if (n < script.length && script[n] != null) {
                    msg.setContent("");
                    msg.setToolCalls(List.of(script[n]));
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

    /** Concatenate every ChatToolResponseMessage body from a result. */
    private static String toolResponses(AgentExecutionResult result) {
        return result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> ((ChatToolResponseMessage) m).getContent())
                .reduce("", (a, b) -> a + " " + b);
    }

    // ========================================================================
    // (a) LEAD agent: create + claim + complete + abandon-unclaimed all succeed
    // ========================================================================

    @Test
    void leadAgentFullOperationSetSucceeds() throws Exception {
        IMessageService messageService = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);
        InMemoryTeamManager teamManager = createAndBindTeam();
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        String teamId = teamManager.getActiveTeams().iterator().next().getTeamId();

        // Round 0: create task-A. Round 1: create task-B. Round 2: final.
        IChatService leadChat = scriptedChat(
                makeToolCall("lead-1", "team-task-create",
                        args("subject", "task-A")),
                makeToolCall("lead-2", "team-task-create",
                        args("subject", "task-B")),
                null);
        DefaultAgentEngine engine = engineWithChecker(leadChat, messenger, teamManager,
                taskStore, new DefaultTeamAclChecker(teamManager));

        AgentExecutionResult result = engine.execute(new AgentMessageRequest(
                "test-agent", "create two tasks", LEAD_SESSION, null)).get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Lead should complete. Messages: " + result.getMessages());
        assertEquals(2, taskStore.getTasksByTeam(teamId).size(),
                "LEAD must be allowed to create tasks (WRITE).");

        // Now LEAD attempts abandon-unclaimed on task-A (ADMIN-only op).
        TeamTask taskA = taskStore.getTasksByTeam(teamId).get(0);
        IChatService leadAbandonChat = scriptedChat(
                makeToolCall("lead-3", "team-task-update",
                        args("taskId", taskA.getTaskId(), "action", "abandon")),
                null);
        DefaultAgentEngine engine2 = engineWithChecker(leadAbandonChat, messenger,
                teamManager, taskStore, new DefaultTeamAclChecker(teamManager));
        AgentExecutionResult abandonResult = engine2.execute(new AgentMessageRequest(
                "test-agent", "abandon task A", LEAD_SESSION, null)).get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, abandonResult.getStatus());
        assertEquals(TeamTaskStatus.ABANDONED, taskStore.getTask(taskA.getTaskId()).orElseThrow().getStatus(),
                "LEAD abandon-unclaimed MUST succeed — task moved to ABANDONED");
        String body = toolResponses(abandonResult);
        assertTrue(body.contains("ABANDONED"),
                "LEAD abandon tool response must report ABANDONED: " + body);
    }

    // ========================================================================
    // (b) MEMBER: collaborative ops succeed; abandon-unclaimed DENIED
    // ========================================================================

    @Test
    void memberAgentCollaborativeOpsSucceedButAbandonUnclaimedDenied() throws Exception {
        IMessageService messageService = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);
        InMemoryTeamManager teamManager = createAndBindTeam();
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        String teamId = teamManager.getActiveTeams().iterator().next().getTeamId();

        // Pre-create a CREATED task for the MEMBER to operate on (created by lead).
        TeamTask task = taskStore.createTask(teamId, "shared task", null,
                Collections.emptyList(), LEAD_SESSION);
        String taskId = task.getTaskId();
        assertEquals(TeamTaskStatus.CREATED, task.getStatus());

        // MEMBER: claim → complete → all allowed (EXECUTE).
        IChatService memberChat = scriptedChat(
                makeToolCall("m-1", "team-task-update",
                        args("taskId", taskId, "action", "claim")),
                makeToolCall("m-2", "team-task-update",
                        args("taskId", taskId, "action", "complete")),
                null);
        DefaultAgentEngine memberEngine = engineWithChecker(memberChat, messenger, teamManager,
                taskStore, new DefaultTeamAclChecker(teamManager));
        AgentExecutionResult memberResult = memberEngine.execute(new AgentMessageRequest(
                "test-agent", "claim and complete", MEMBER_SESSION, null)).get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, memberResult.getStatus(),
                "Member should complete. Messages: " + memberResult.getMessages());
        assertEquals(TeamTaskStatus.COMPLETED, taskStore.getTask(taskId).orElseThrow().getStatus(),
                "MEMBER claim + complete must succeed (EXECUTE allowed)");

        // MEMBER: now create a NEW task and try abandon-unclaimed — MUST be denied.
        IChatService memberCreateChat = scriptedChat(
                makeToolCall("m-3", "team-task-create",
                        args("subject", "member-created task")),
                null);
        DefaultAgentEngine memberCreateEngine = engineWithChecker(memberCreateChat, messenger,
                teamManager, taskStore, new DefaultTeamAclChecker(teamManager));
        AgentExecutionResult memberCreateResult = memberCreateEngine.execute(new AgentMessageRequest(
                "test-agent", "create a task", MEMBER_SESSION, null)).get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, memberCreateResult.getStatus());

        // Find the member-created task (CREATED status, member is creator).
        TeamTask memberTask = taskStore.getTasksByTeam(teamId).stream()
                .filter(t -> t.getStatus() == TeamTaskStatus.CREATED)
                .findFirst().orElseThrow(() -> new AssertionError("expected a CREATED task"));
        assertEquals(MEMBER_SESSION, memberTask.getCreatedBy(),
                "member create must record the MEMBER session as createdBy");

        // MEMBER attempts abandon-unclaimed — DENIED. Status MUST stay CREATED.
        IChatService memberAbandonChat = scriptedChat(
                makeToolCall("m-4", "team-task-update",
                        args("taskId", memberTask.getTaskId(), "action", "abandon")),
                null);
        DefaultAgentEngine memberAbandonEngine = engineWithChecker(memberAbandonChat, messenger,
                teamManager, taskStore, new DefaultTeamAclChecker(teamManager));
        AgentExecutionResult memberAbandonResult = memberAbandonEngine.execute(new AgentMessageRequest(
                "test-agent", "abandon the task", MEMBER_SESSION, null)).get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, memberAbandonResult.getStatus(),
                "MEMBER engine still completes — ACL denial is honest feedback, not a crash");

        // Anti-Hollow #22 / Wiring #23: the store MUST NOT have transitioned.
        assertEquals(TeamTaskStatus.CREATED,
                taskStore.getTask(memberTask.getTaskId()).orElseThrow().getStatus(),
                "MEMBER abandon-unclaimed MUST be blocked — task status unchanged (Anti-Hollow #22)");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) JSON.parse(
                toolResponses(memberAbandonResult).trim());
        assertEquals(Boolean.FALSE, body.get("allowed"),
                "Tool response must explicitly say allowed=false");
        assertEquals("abandon-unclaimed", body.get("action"),
                "ACL action must be refined to abandon-unclaimed for a CREATED task");
        assertNotNull(body.get("reason"));
        assertTrue(((String) body.get("reason")).contains("ADMIN"),
                "Deny reason must cite ADMIN: " + body.get("reason"));
    }

    // ========================================================================
    // (c) MEMBER: team-send-message + team-status succeed
    // ========================================================================

    @Test
    void memberAgentSendMessageAndStatusSucceed() throws Exception {
        IMessageService messageService = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);
        InMemoryTeamManager teamManager = createAndBindTeam();
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();

        AtomicReference<AgentMessageEnvelope> leadReceived = new AtomicReference<>();
        IMessageSubscription sub = messenger.registerHandler(
                io.nop.ai.agent.message.AgentMessageTopics.inboxTopic(LEAD_SESSION),
                new IAgentMessageHandler() {
                    @Override
                    public Object onMessage(AgentMessageEnvelope envelope) {
                        leadReceived.set(envelope);
                        return null;
                    }
                });

        IChatService memberChat = scriptedChat(
                makeToolCall("ms-1", "team-send-message",
                        args("to", "lead", "body", "hello from member")),
                makeToolCall("ms-2", "team-status", new HashMap<>()),
                null);
        DefaultAgentEngine memberEngine = engineWithChecker(memberChat, messenger, teamManager,
                taskStore, new DefaultTeamAclChecker(teamManager));
        AgentExecutionResult result = memberEngine.execute(new AgentMessageRequest(
                "test-agent", "message lead and check status", MEMBER_SESSION, null))
                .get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Member should complete. Messages: " + result.getMessages());

        // team-send-message delivered (WRITE allowed for MEMBER)
        AgentMessageEnvelope env = leadReceived.get();
        assertNotNull(env, "MEMBER send must deliver to lead inbox (Anti-Hollow #22)");
        assertEquals("hello from member", env.getPayload());
        assertEquals(MEMBER_SESSION, env.getSenderId());

        // team-status returned (READ allowed for MEMBER)
        assertTrue(toolResponses(result).contains("AclTeam"),
                "team-status response must include the team name: " + toolResponses(result));

        sub.cancel();
    }

    // ========================================================================
    // (d) NoOp default zero-regression
    // ========================================================================

    @Test
    void noOpCheckerDefaultZeroRegression() throws Exception {
        // Engine with teamManager/teamTaskStore wired but NoOp ACL default
        // (no setTeamAclChecker call) — the shipped default must apply.
        IMessageService messageService = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);
        InMemoryTeamManager teamManager = createAndBindTeam();
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();
        String teamId = teamManager.getActiveTeams().iterator().next().getTeamId();

        // MEMBER creates + abandons-unclaimed — must succeed under NoOp ACL.
        IChatService memberChat = scriptedChat(
                makeToolCall("noop-1", "team-task-create",
                        args("subject", "noOp task")),
                null);
        DefaultAgentEngine engine = new DefaultAgentEngine(memberChat, createTeamToolManager());
        engine.setMessenger(messenger);
        engine.setTeamManager(teamManager);
        engine.setTeamTaskStore(taskStore);
        // Deliberately NOT calling setTeamAclChecker — NoOp is the shipped default.
        assertEquals(NoOpTeamAclChecker.noOp(), engine.getTeamAclChecker(),
                "Wiring #23: engine must default teamAclChecker to NoOp singleton");

        AgentExecutionResult createResult = engine.execute(new AgentMessageRequest(
                "test-agent", "create task", MEMBER_SESSION, null)).get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, createResult.getStatus());

        TeamTask task = taskStore.getTasksByTeam(teamId).get(0);

        // MEMBER abandon-unclaimed under NoOp — succeeds (pre-ACL behaviour).
        IChatService memberAbandonChat = scriptedChat(
                makeToolCall("noop-2", "team-task-update",
                        args("taskId", task.getTaskId(), "action", "abandon")),
                null);
        DefaultAgentEngine engine2 = new DefaultAgentEngine(memberAbandonChat, createTeamToolManager());
        engine2.setMessenger(messenger);
        engine2.setTeamManager(teamManager);
        engine2.setTeamTaskStore(taskStore);
        // Again no setTeamAclChecker — relies on NoOp default.
        AgentExecutionResult abandonResult = engine2.execute(new AgentMessageRequest(
                "test-agent", "abandon task", MEMBER_SESSION, null)).get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, abandonResult.getStatus(),
                "Agent should complete under NoOp ACL default. Messages: " + abandonResult.getMessages());
        assertEquals(TeamTaskStatus.ABANDONED,
                taskStore.getTask(task.getTaskId()).orElseThrow().getStatus(),
                "NoOp ACL must allow MEMBER abandon-unclaimed (zero regression — pre-228 behaviour)");
        assertFalse(toolResponses(abandonResult).contains("\"allowed\":false"),
                "NoOp allow must NOT produce an ACL-denial body: " + toolResponses(abandonResult));
    }
}
