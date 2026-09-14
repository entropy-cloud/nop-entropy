package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSingleTurnExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private ChatResponse buildSuccessResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private ChatResponse buildSuccessResponseWithUsage(String content, int promptTokens, int completionTokens) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        ChatResponse response = ChatResponse.success(msg);
        response.setUsage(new ChatUsage(promptTokens, completionTokens));
        return response;
    }

    private IChatService createMockChatService(ChatResponse response) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return response;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private AgentModel createAgentModel(String name, String mode) {
        AgentModel model = new AgentModel();
        model.setName(name);
        model.setMode(mode);
        return model;
    }

    @Test
    void testSingleCallReturnsResult() throws Exception {
        IChatService chatService = createMockChatService(buildSuccessResponse("Hello!"));
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        SingleTurnExecutor executor = new SingleTurnExecutor(chatService, publisher);

        AgentModel model = createAgentModel("test", "single-turn");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("Hi"));

        CompletableFuture<AgentExecutionResult> future = executor.execute(ctx)
                .toCompletableFuture();
        AgentExecutionResult result = future.get(5, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertNotNull(result.getSessionId());
        assertEquals("session-1", result.getSessionId());
    }

    @Test
    void testCtxStatusLifecycle() throws Exception {
        IChatService chatService = createMockChatService(buildSuccessResponse("Response"));
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        SingleTurnExecutor executor = new SingleTurnExecutor(chatService, publisher);

        AgentModel model = createAgentModel("test", "single-turn");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("Hi"));

        assertEquals(AgentExecStatus.pending, ctx.getStatus());

        CompletableFuture<AgentExecutionResult> future = executor.execute(ctx)
                .toCompletableFuture();
        future.get(5, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, ctx.getStatus());
    }

    @Test
    void testTokenTracking() throws Exception {
        IChatService chatService = createMockChatService(
                buildSuccessResponseWithUsage("Response", 100, 50));
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        SingleTurnExecutor executor = new SingleTurnExecutor(chatService, publisher);

        AgentModel model = createAgentModel("test", "single-turn");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("Hi"));

        CompletableFuture<AgentExecutionResult> future = executor.execute(ctx)
                .toCompletableFuture();
        AgentExecutionResult result = future.get(5, TimeUnit.SECONDS);

        assertEquals(150, result.getTotalTokensUsed());
        assertEquals(150, ctx.getTokensUsed());
    }

    @Test
    void testToolCallOnlyResponseDoesNotNpe() throws Exception {
        // P2: tool-call-only responses (real provider form) carry no
        // ChatAssistantMessage. extractAssistantMessage returns null — the
        // executor must not NPE and must record a consistent history.
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("c1");
        toolCall.setName("test-tool");
        ChatToolCallMessage toolCallMsg = ChatToolCallMessage.fromChatToolCall(toolCall);
        ChatResponse toolOnly = ChatResponse.success(List.of(toolCallMsg));

        IChatService chatService = createMockChatService(toolOnly);
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        SingleTurnExecutor executor = new SingleTurnExecutor(chatService, publisher);

        AgentModel model = createAgentModel("test", "single-turn");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("Hi"));

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "tool-call-only response must complete without NPE");
        // History is consistent: the assistant turn is recorded as an empty
        // text message (no NPE, no gap in the message sequence).
        List<io.nop.ai.api.chat.messages.ChatMessage> history = ctx.getMessages();
        assertEquals(2, history.size(), "history = [user, empty assistant turn]");
        assertTrue(history.get(1) instanceof ChatAssistantMessage,
                "the recorded turn must be an assistant message");
        assertTrue(history.get(1).getContent() == null || history.get(1).getContent().isEmpty(),
                "tool-call-only turn records empty assistant text");
    }

    @Test
    void testEventPublication() throws Exception {
        IChatService chatService = createMockChatService(buildSuccessResponse("Response"));
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        SingleTurnExecutor executor = new SingleTurnExecutor(chatService, publisher);

        AgentModel model = createAgentModel("test", "single-turn");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("Hi"));

        executor.execute(ctx).toCompletableFuture().get(5, TimeUnit.SECONDS);

        List<AgentEventType> eventTypes = events.stream()
                .map(AgentEvent::getEventType).collect(Collectors.toList());
        assertTrue(eventTypes.contains(AgentEventType.EXECUTION_STARTED),
                "Should contain EXECUTION_STARTED");
        assertTrue(eventTypes.contains(AgentEventType.EXECUTION_COMPLETED),
                "Should contain EXECUTION_COMPLETED");
    }

    @Test
    void testErrorResponseHandling() throws Exception {
        ChatResponse errorResponse = ChatResponse.error("ERR_001", "Model unavailable");
        IChatService chatService = createMockChatService(errorResponse);
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        SingleTurnExecutor executor = new SingleTurnExecutor(chatService, publisher);

        AgentModel model = createAgentModel("test", "single-turn");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("Hi"));

        CompletableFuture<AgentExecutionResult> future = executor.execute(ctx)
                .toCompletableFuture();
        AgentExecutionResult result = future.get(5, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.failed, result.getStatus());
        assertEquals(AgentExecStatus.failed, ctx.getStatus());
        assertNotNull(ctx.getLastError());

        List<AgentEventType> eventTypes = events.stream()
                .map(AgentEvent::getEventType).collect(Collectors.toList());
        assertTrue(eventTypes.contains(AgentEventType.EXECUTION_STARTED));
        assertTrue(eventTypes.contains(AgentEventType.EXECUTION_FAILED));
    }

    @Test
    void testExceptionHandling() throws Exception {
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.failedFuture(new NopAiAgentException("Connection failed"));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                throw new NopAiAgentException("Connection failed");
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        SingleTurnExecutor executor = new SingleTurnExecutor(chatService, publisher);

        AgentModel model = createAgentModel("test", "single-turn");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("Hi"));

        CompletableFuture<AgentExecutionResult> future = executor.execute(ctx)
                .toCompletableFuture();
        AgentExecutionResult result = future.get(5, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.failed, result.getStatus());
        assertEquals(AgentExecStatus.failed, ctx.getStatus());
        assertNotNull(ctx.getLastError());

        List<AgentEventType> eventTypes = events.stream()
                .map(AgentEvent::getEventType).collect(Collectors.toList());
        assertTrue(eventTypes.contains(AgentEventType.EXECUTION_FAILED));
    }

    @Test
    void testNoToolInvocation() throws Exception {
        AtomicInteger toolCallCount = new AtomicInteger(0);
        IToolManager toolManager = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                toolCallCount.incrementAndGet();
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
        };

        IChatService chatService = createMockChatService(buildSuccessResponse("Response"));
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        SingleTurnExecutor executor = new SingleTurnExecutor(chatService, publisher);

        AgentModel model = createAgentModel("test", "single-turn");
        model.setTools(java.util.Set.of("some-tool"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("Hi"));

        executor.execute(ctx).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, toolCallCount.get(), "SingleTurnExecutor should not invoke any tools");
    }

    @Test
    void testNoIterationIncrement() throws Exception {
        IChatService chatService = createMockChatService(buildSuccessResponse("Response"));
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        SingleTurnExecutor executor = new SingleTurnExecutor(chatService, publisher);

        AgentModel model = createAgentModel("test", "single-turn");
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
        ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("Hi"));

        assertEquals(0, ctx.getCurrentIteration());

        executor.execute(ctx).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, ctx.getCurrentIteration(),
                "SingleTurnExecutor should not increment iteration counter");
    }

    /**
     * I3 gate-2/SingleTurnExecutor fix — behavior-level timeout verification:
     * a hanging {@code chatService.call} must NOT block the calling thread
     * indefinitely. With a 100ms {@code llmTimeoutMs} and a chat service that
     * never answers, {@code execute} must complete within a bounded window
     * with an honest failed result (status=failed, error mentions the
     * timeout) and emit EXECUTION_FAILED — proving the timeout mechanism is
     * wired into the failure path, not just declared.
     */
    @Test
    void testHangingChatServiceTimesOutAndFails() throws Exception {
        CountDownLatch hang = new CountDownLatch(1);
        IChatService hanging = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                // SingleTurnExecutor invokes the blocking `call` — never settle
                // until the test releases the latch.
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        hang.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return buildSuccessResponse("late");
                });
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                try {
                    hang.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NopAiAgentException("hanging chat service interrupted", e);
                }
                return buildSuccessResponse("late");
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = new ArrayList<>();
        publisher.addSubscriber(events::add);

        ExecutorService timeoutExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            SingleTurnExecutor executor = new SingleTurnExecutor(hanging, publisher, 100L, timeoutExecutor);

            AgentModel model = createAgentModel("test", "single-turn");
            AgentExecutionContext ctx = AgentExecutionContext.create(model, "session-1");
            ctx.addMessage(new io.nop.ai.api.chat.messages.ChatUserMessage("Hi"));

            long start = System.currentTimeMillis();
            CompletableFuture<AgentExecutionResult> future = executor.execute(ctx).toCompletableFuture();
            // must complete well before the hanging call would return (30s)
            AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - start;

            assertEquals(AgentExecStatus.failed, result.getStatus(),
                    "a timed-out LLM call must produce a failed execution result");
            assertEquals(AgentExecStatus.failed, ctx.getStatus());
            assertNotNull(ctx.getLastError());
            assertTrue(ctx.getLastError().toLowerCase().contains("timeout")
                            || ctx.getLastError().contains("TimeoutException"),
                    "the failure must report the timeout, got: " + ctx.getLastError());
            assertTrue(elapsedMs < 5000,
                    "execute must return bounded by the timeout (not wait for the hanging call), took "
                            + elapsedMs + "ms");

            List<AgentEventType> eventTypes = events.stream()
                    .map(AgentEvent::getEventType).collect(Collectors.toList());
            assertTrue(eventTypes.contains(AgentEventType.EXECUTION_STARTED));
            assertTrue(eventTypes.contains(AgentEventType.EXECUTION_FAILED),
                    "a timed-out execution must emit EXECUTION_FAILED");
        } finally {
            hang.countDown();
            timeoutExecutor.shutdownNow();
        }
    }
}
