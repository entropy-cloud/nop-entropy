package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.ai.agent.reliability.GoalAssessment;
import io.nop.ai.agent.reliability.IGoalTracker;
import io.nop.ai.agent.reliability.IterationSnapshot;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReActAgentExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentExecutionContext buildContext(int maxIterations) {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(maxIterations);
        return ctx;
    }

    private AgentExecutionContext buildContextWithTools(int maxIterations, Set<String> tools) {
        AgentModel model = new AgentModel();
        model.setTools(tools);
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(maxIterations);
        return ctx;
    }

    private ChatResponse buildSuccessResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private ChatResponse buildSuccessResponseWithToolCalls(List<ChatToolCall> toolCalls) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(toolCalls);
        return ChatResponse.success(msg);
    }

    private ChatResponse buildErrorResponse(String errorMessage) {
        return ChatResponse.error("ERROR", errorMessage);
    }

    @Test
    void testNoToolCallImmediateReturn() {
        AgentExecutionContext ctx = buildContext(10);

        IChatService chatService = new StubChatService(buildSuccessResponse("Hello, I can help you."));
        IToolManager toolManager = new NoOpToolManager();

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(0, result.getTotalIterations());
        assertTrue(result.getMessages().stream()
                .anyMatch(m -> m instanceof ChatAssistantMessage));
        assertNull(result.getError());
    }

    @Test
    void testSingleToolCallLoop() {
        AgentExecutionContext ctx = buildContextWithTools(10, Collections.singleton("calculator"));

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_123");
        toolCall.setName("calculator");
        Map<String, Object> args = new HashMap<>();
        args.put("expression", "2+2");
        toolCall.setArguments(args);

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    resp = buildSuccessResponseWithToolCalls(List.of(toolCall));
                } else {
                    resp = buildSuccessResponse("The result is 4.");
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "4"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, result.getTotalIterations());

        List<ChatMessage> messages = result.getMessages();
        boolean hasToolResponse = messages.stream()
                .anyMatch(m -> m instanceof ChatToolResponseMessage);
        assertTrue(hasToolResponse);

        assertEquals(2, callCount.get());
    }

    @Test
    void testMultipleToolCalls() {
        AgentExecutionContext ctx = buildContextWithTools(10, Set.of("tool_a", "tool_b"));

        ChatToolCall toolCallA = new ChatToolCall();
        toolCallA.setId("call_a");
        toolCallA.setName("tool_a");
        toolCallA.setArguments(Map.of("x", 1));

        ChatToolCall toolCallB = new ChatToolCall();
        toolCallB.setId("call_b");
        toolCallB.setName("tool_b");
        toolCallB.setArguments(Map.of("y", 2));

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    resp = buildSuccessResponseWithToolCalls(List.of(toolCallA, toolCallB));
                } else {
                    resp = buildSuccessResponse("Done.");
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                if ("tool_a".equals(toolName)) {
                    return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "result_a"));
                }
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(1, "result_b"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, result.getTotalIterations());

        long toolResponseCount = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .count();
        assertEquals(2, toolResponseCount);
    }

    @Test
    void testMaxIterationsReached() {
        AgentExecutionContext ctx = buildContextWithTools(2, Collections.singleton("tool_a"));

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("tool_a");
        toolCall.setArguments(Map.of("x", 1));

        IChatService chatService = new StubChatService(
                buildSuccessResponseWithToolCalls(List.of(toolCall)));

        IToolManager toolManager = new NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        // AR-14 (plan 277): hitting max-iterations is now reported as
        // "truncated" (not "completed"), so downstream consumers can
        // distinguish a budget-truncated session from a successful completion.
        assertEquals(AgentExecStatus.truncated, result.getStatus());
        assertEquals(2, result.getTotalIterations());
    }

    /**
     * AR-07 (plan 277): handleGoalStuck must publish SESSION_ESCALATED (not
     * SESSION_PAUSED). Wiring proof: a functional goal tracker that returns
     * STUCK causes the loop to abort with escalated status + the new event.
     */
    @Test
    void goalStuckPublishesSessionEscalatedNotPaused() {
        AgentExecutionContext ctx = buildContext(10);

        IGoalTracker stuckTracker = new IGoalTracker() {
            @Override
            public void recordIteration(String sessionId, IterationSnapshot snapshot) {
            }

            @Override
            public GoalAssessment assessGoal(String sessionId) {
                return GoalAssessment.STUCK;
            }
        };

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        IChatService chatService = new StubChatService(buildSuccessResponse("thinking"));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new NoOpToolManager())
                .goalTracker(stuckTracker)
                .eventPublisher(publisher)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.escalated, result.getStatus(),
                "Goal tracker STUCK must set escalated status");

        boolean hasEscalated = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.SESSION_ESCALATED);
        assertTrue(hasEscalated, "SESSION_ESCALATED event must be published on goal stuck");

        // AR-07: SESSION_PAUSED must NOT be published for an escalation
        boolean hasPaused = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.SESSION_PAUSED);
        assertFalse(hasPaused, "SESSION_PAUSED must NOT be published for goal stuck (AR-07)");
    }

    /**
     * AR-14-b (plan 277): a truncated session must NOT publish
     * EXECUTION_COMPLETED or run POST_CALL hooks. Wiring proof via event
     * observation.
     */
    @Test
    void truncatedSessionDoesNotPublishExecutionCompleted() {
        AgentExecutionContext ctx = buildContextWithTools(1, Collections.singleton("tool_a"));

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("tool_a");
        toolCall.setArguments(Map.of("x", 1));

        IChatService chatService = new StubChatService(
                buildSuccessResponseWithToolCalls(List.of(toolCall)));

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new NoOpToolManager())
                .eventPublisher(publisher)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.truncated, result.getStatus());

        // AR-14-b: truncated must be excluded from the post-loop gate
        boolean hasCompleted = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED);
        assertFalse(hasCompleted,
                "A truncated session must NOT publish EXECUTION_COMPLETED (AR-14-b)");
    }

    @Test
    void testLlmCallFailure() {
        AgentExecutionContext ctx = buildContext(10);

        IChatService chatService = new StubChatService(buildErrorResponse("rate_limit_exceeded"));
        IToolManager toolManager = new NoOpToolManager();

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.failed, result.getStatus());
        assertNotNull(result.getError());
        assertEquals("rate_limit_exceeded", result.getError());
    }

    @Test
    void testToolExecutionError() {
        AgentExecutionContext ctx = buildContextWithTools(10, Collections.singleton("failing_tool"));

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_err_1");
        toolCall.setName("failing_tool");
        toolCall.setArguments(Map.of("input", "test"));

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    resp = buildSuccessResponseWithToolCalls(List.of(toolCall));
                } else {
                    resp = buildSuccessResponse("I see the tool failed.");
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.errorResult(0, "tool crashed"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());

        List<ChatMessage> messages = result.getMessages();
        ChatToolResponseMessage toolResponse = messages.stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst()
                .orElse(null);
        assertNotNull(toolResponse);
        assertEquals("call_err_1", toolResponse.getToolCallId());
        assertTrue(toolResponse.getContent().contains("tool crashed"));

        assertEquals(2, callCount.get());
    }

    static class StubChatService implements IChatService {
        private final ChatResponse response;

        StubChatService(ChatResponse response) {
            this.response = response;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    static class NoOpToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
            return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
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
            model.setDescription("Mock tool: " + toolName);
            return model;
        }
    }

    /**
     * AR-03/AR-11 (plan 277) end-to-end: a chat provider stub that validates
     * tool_call_id pairing on EVERY call. If the message list contains an
     * orphan tool response (id not matching any preceding assistant
     * tool_call), the stub returns an error response — simulating an
     * HTTP 400 from OpenAI/Anthropic/Gemini. This proves the full path
     * (multi tool_call → fan-out → result processing → next buildChatRequest)
     * maintains pairing integrity.
     */
    static class PairingValidatingChatService implements IChatService {
        private final ChatResponse firstResponse;
        private final ChatResponse subsequentResponse;
        int callCount = 0;

        PairingValidatingChatService(ChatResponse firstResponse, ChatResponse subsequentResponse) {
            this.firstResponse = firstResponse;
            this.subsequentResponse = subsequentResponse;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            callCount++;
            // Validate: every role:"tool" message's tool_call_id must match a
            // tool_call_id in a preceding assistant message.
            Set<String> announcedIds = new HashSet<>();
            for (ChatMessage msg : request.getMessages()) {
                if (msg instanceof ChatAssistantMessage) {
                    ChatAssistantMessage am = (ChatAssistantMessage) msg;
                    if (am.getToolCalls() != null) {
                        for (ChatToolCall tc : am.getToolCalls()) {
                            announcedIds.add(tc.getId());
                        }
                    }
                }
                if (msg instanceof ChatToolResponseMessage) {
                    String respId = ((ChatToolResponseMessage) msg).getToolCallId();
                    if (!announcedIds.contains(respId)) {
                        return CompletableFuture.completedFuture(
                                ChatResponse.error("ERROR",
                                        "tool_call_id mismatch: orphan tool response id=" + respId));
                    }
                }
            }
            ChatResponse resp = (callCount == 1) ? firstResponse : subsequentResponse;
            return CompletableFuture.completedFuture(resp);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    /**
     * AR-03 (plan 277): multi-tool batch end-to-end with a pairing-validating
     * provider stub. The agent returns 2 tool_calls, both get dispatched and
     * their results processed, then the next LLM call must see a fully paired
     * message list (no orphan tool responses → no simulated HTTP 400).
     */
    @Test
    void multiToolBatchMaintainsPairingEndToEnd() {
        AgentExecutionContext ctx = buildContextWithTools(10, Set.of("tool_a", "tool_b"));

        ChatToolCall tc1 = new ChatToolCall();
        tc1.setId("call_a");
        tc1.setName("tool_a");
        tc1.setArguments(Map.of("x", 1));
        ChatToolCall tc2 = new ChatToolCall();
        tc2.setId("call_b");
        tc2.setName("tool_b");
        tc2.setArguments(Map.of("y", 2));

        ChatResponse firstResp = buildSuccessResponseWithToolCalls(List.of(tc1, tc2));
        ChatResponse doneResp = buildSuccessResponse("all done");

        PairingValidatingChatService chatService = new PairingValidatingChatService(firstResp, doneResp);

        IToolManager toolManager = new NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "result-for-" + toolName));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution must complete without pairing failure. If this fails, the message list has orphan tool responses.");

        // Both tool_call_ids must have matching tool responses
        List<String> toolResponseIds = new ArrayList<>();
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                toolResponseIds.add(((ChatToolResponseMessage) m).getToolCallId());
            }
        }
        assertTrue(toolResponseIds.contains("call_a"), "tool_a response must be present");
        assertTrue(toolResponseIds.contains("call_b"), "tool_b response must be present");
    }
}
