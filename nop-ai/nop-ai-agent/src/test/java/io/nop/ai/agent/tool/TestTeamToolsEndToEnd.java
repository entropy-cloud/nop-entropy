package io.nop.ai.agent.tool;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageKind;
import io.nop.ai.agent.message.AgentMessageTopics;
import io.nop.ai.agent.message.IAgentMessageHandler;
import io.nop.ai.agent.message.LocalAgentMessenger;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.InMemoryTeamManager;
import io.nop.ai.agent.team.InMemoryTeamTaskStore;
import io.nop.ai.agent.team.MemberRole;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for the 3 team communication tools (plan 225 Phase 2).
 *
 * <p>Full path: lead agent's ReAct loop calls team-send-message → messenger
 * → member inbox → lead calls team-task-create → task stored → lead calls
 * team-status → JSON with task + members. Proves the wiring is connected
 * end-to-end (Anti-Hollow #22, Wiring #23).
 */
public class TestTeamToolsEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String LEAD_SESSION = "lead-e2e-sess";
    private static final String WORKER_SESSION = "worker-e2e-sess";

    /**
     * Tool manager that routes the 3 team tools to their executors. The
     * executors read teamManager / teamTaskStore / messenger from the
     * AgentToolExecuteContext provided by the engine's dispatch loop.
     */
    private IToolManager createTeamToolManager() {
        TeamSendMessageExecutor sendExecutor = new TeamSendMessageExecutor();
        TeamStatusExecutor statusExecutor = new TeamStatusExecutor();
        TeamTaskCreateExecutor taskCreateExecutor = new TeamTaskCreateExecutor();

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
        TeamSpec spec = new TeamSpec("E2ETeam", "end-to-end test team", "lead",
                Arrays.asList(
                        new TeamMemberSpec("lead", "lead-agent", MemberRole.LEAD),
                        new TeamMemberSpec("worker", "worker-agent", MemberRole.MEMBER)),
                0);
        Team team = mgr.createTeam(spec);
        mgr.bindMemberSession(team.getTeamId(), "lead", LEAD_SESSION, "actor-lead");
        mgr.bindMemberSession(team.getTeamId(), "worker", WORKER_SESSION, "actor-worker");
        return mgr;
    }

    private ChatToolCall makeToolCall(String id, String name, Map<String, Object> args) {
        ChatToolCall tc = new ChatToolCall();
        tc.setId(id);
        tc.setName(name);
        tc.setArguments(args);
        return tc;
    }

    @Test
    void fullTeamToolsEndToEnd() throws Exception {
        IMessageService messageService = new LocalMessageService();
        LocalAgentMessenger messenger = new LocalAgentMessenger(messageService);
        InMemoryTeamManager teamManager = createAndBindTeam();
        InMemoryTeamTaskStore taskStore = new InMemoryTeamTaskStore();

        // Register inbox handler on the worker's inbox.
        AtomicReference<AgentMessageEnvelope> workerReceived = new AtomicReference<>();
        IMessageSubscription sub = messenger.registerHandler(
                AgentMessageTopics.inboxTopic(WORKER_SESSION),
                new IAgentMessageHandler() {
                    @Override
                    public Object onMessage(AgentMessageEnvelope envelope) {
                        workerReceived.set(envelope);
                        return null;
                    }
                });

        // Mock LLM: emit 3 tool calls in sequence, then a final text response.
        IChatService chatService = new IChatService() {
            final AtomicInteger callCount = new AtomicInteger(0);

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
                switch (n) {
                    case 0:
                        msg.setContent("");
                        Map<String, Object> sendArgs = new HashMap<>();
                        sendArgs.put("to", "worker");
                        sendArgs.put("body", "hello from lead");
                        msg.setToolCalls(List.of(makeToolCall("e2e-1", "team-send-message", sendArgs)));
                        break;
                    case 1:
                        msg.setContent("");
                        Map<String, Object> taskArgs = new HashMap<>();
                        taskArgs.put("subject", "E2E task");
                        taskArgs.put("description", "test task from e2e");
                        msg.setToolCalls(List.of(makeToolCall("e2e-2", "team-task-create", taskArgs)));
                        break;
                    case 2:
                        msg.setContent("");
                        msg.setToolCalls(List.of(makeToolCall("e2e-3", "team-status", new HashMap<>())));
                        break;
                    default:
                        msg.setContent("All team operations completed.");
                        break;
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = createTeamToolManager();
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        engine.setMessenger(messenger);
        engine.setTeamManager(teamManager);
        engine.setTeamTaskStore(taskStore);

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "use team tools", LEAD_SESSION, null);
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Agent should complete. Messages: " + result.getMessages());

        // --- team-send-message: worker inbox received the message ---
        AgentMessageEnvelope envelope = workerReceived.get();
        assertNotNull(envelope, "Worker inbox should have received the team message");
        assertEquals("hello from lead", envelope.getPayload());
        assertEquals(LEAD_SESSION, envelope.getSenderId());
        assertEquals(AgentMessageTopics.inboxTopic(WORKER_SESSION), envelope.getTargetTopic());
        assertEquals(AgentMessageKind.ASYNC, envelope.getKind());

        // --- team-task-create: task stored in the shared store ---
        List<TeamTask> tasks = taskStore.getTasksByTeam(teamManager.getActiveTeams().iterator().next().getTeamId());
        assertEquals(1, tasks.size(), "Task store should have exactly 1 task");
        assertEquals("E2E task", tasks.get(0).getSubject());
        assertEquals("test task from e2e", tasks.get(0).getDescription());
        assertEquals(LEAD_SESSION, tasks.get(0).getCreatedBy());

        // --- Verify tool responses flowed back into the conversation ---
        List<ChatMessage> messages = result.getMessages();
        String allToolResponses = messages.stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> ((ChatToolResponseMessage) m).getContent())
                .reduce("", (a, b) -> a + " " + b);

        // team-send-message response
        assertTrue(allToolResponses.contains("agent." + WORKER_SESSION + ".inbox"),
                "Tool response should mention worker inbox topic: " + allToolResponses);

        // team-task-create response (contains taskId + CREATED)
        assertTrue(allToolResponses.contains("CREATED"),
                "Tool response should contain CREATED status: " + allToolResponses);

        // team-status response (contains taskCount=1)
        assertTrue(allToolResponses.contains("\"taskCount\":1"),
                "team-status response should show taskCount=1: " + allToolResponses);

        sub.cancel();
    }

    /**
     * NoOp default zero-regression: shipped defaults (NoOpTeamManager +
     * NoOpTeamTaskStore + NoOpAgentMessenger) → 3 team tools honestly report
     * "not enabled" when called in a ReAct loop. The agent still completes
     * normally.
     */
    @Test
    void noOpDefaultsZeroRegressionInReActLoop() throws Exception {
        IChatService chatService = new IChatService() {
            final AtomicInteger callCount = new AtomicInteger(0);

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
                if (n == 0) {
                    msg.setContent("");
                    Map<String, Object> args = new HashMap<>();
                    args.put("to", "nobody");
                    args.put("body", "hello");
                    msg.setToolCalls(List.of(makeToolCall("noop-1", "team-send-message", args)));
                } else {
                    msg.setContent("Team tools not available, proceeding without.");
                }
                return ChatResponse.success(msg);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        IToolManager toolManager = createTeamToolManager();
        // Engine with ALL defaults — no setTeamManager / setTeamTaskStore / setMessenger calls.
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "use team tools");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Agent should complete even with NoOp team defaults. Messages: " + result.getMessages());

        // The tool response should honestly report "not enabled".
        String toolResponse = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> ((ChatToolResponseMessage) m).getContent())
                .reduce("", (a, b) -> a + " " + b);
        assertTrue(toolResponse.contains("not enabled"),
                "NoOp default should produce honest 'not enabled' report: " + toolResponse);
    }
}
