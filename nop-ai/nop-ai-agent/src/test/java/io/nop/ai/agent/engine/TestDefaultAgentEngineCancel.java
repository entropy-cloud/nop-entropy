package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultAgentEngineCancel {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private ChatResponse buildToolCallResponse(String toolName) {
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_cancel_1");
        toolCall.setName(toolName);
        toolCall.setArguments(Map.of("expr", "1+1"));

        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(toolCall));
        return ChatResponse.success(msg);
    }

    private IChatService createToolCallChatService(ChatResponse response) {
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
                return subscriber -> {
                };
            }
        };
    }

    private IChatService createBlockingChatService(ChatResponse response, CountDownLatch enteredLatch) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                enteredLatch.countDown();
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NopAiAgentException("interrupted during LLM call", e);
                }
                return response;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    private IToolManager createNoOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
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
    }

    private IToolManager createBlockingToolManager(CountDownLatch enteredLatch, CountDownLatch proceedLatch) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
                enteredLatch.countDown();
                try {
                    assertTrue(proceedLatch.await(30, TimeUnit.SECONDS),
                            "Tool should be released by the test within timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "42"));
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
    }

    @Test
    void getSessionStatusThrowsForNonExistentSession() {
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.getSessionStatus("does-not-exist"));
        assertTrue(ex.getMessage().contains("session not found"),
                "getSessionStatus should fail-fast for a non-existent session");
    }

    @Test
    void getSessionStatusReturnsPendingForNewlyCreatedSession() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null, store);
        store.getOrCreate("new-session", "test-agent");

        assertEquals(AgentExecStatus.pending, engine.getSessionStatus("new-session"),
                "A newly created session should have status pending");
    }

    @Test
    void getSessionStatusReflectsLifecycleDuringExecution() throws Exception {
        ChatResponse toolCallResponse = buildToolCallResponse("test-calculator");
        IChatService chatService = createToolCallChatService(toolCallResponse);

        CountDownLatch toolEntered = new CountDownLatch(1);
        CountDownLatch proceedWithTool = new CountDownLatch(1);
        IToolManager toolManager = createBlockingToolManager(toolEntered, proceedWithTool);

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        AgentMessageRequest request = new AgentMessageRequest(
                "test-react-agent", "compute", "lifecycle-e2e", null);
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);

        assertTrue(toolEntered.await(30, TimeUnit.SECONDS),
                "The tool call should have been entered by the executor");

        assertEquals(AgentExecStatus.running, engine.getSessionStatus("lifecycle-e2e"),
                "While execution is in progress, session status should be running");

        engine.cancelSession("lifecycle-e2e", "lifecycle-test", false);
        proceedWithTool.countDown();

        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.cancelled, result.getStatus(),
                "Result status should be cancelled after graceful cancel");
        assertEquals(AgentExecStatus.cancelled, engine.getSessionStatus("lifecycle-e2e"),
                "Session status should be cancelled after graceful cancel");
    }

    @Test
    void cancelGracefulOnNonExistentSessionThrows() {
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.cancelSession("ghost", "no-target", false));
        assertTrue(ex.getMessage().contains("session not found"),
                "cancelSession should fail-fast for a non-existent session");
    }

    @Test
    void cancelGracefulOnIdleSessionSetsCancelled() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null, store);
        store.getOrCreate("idle", "test-agent");

        java.util.List<AgentEvent> events = Collections.synchronizedList(new java.util.ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        engine.cancelSession("idle", "user-stop", false);

        assertEquals(AgentExecStatus.cancelled, engine.getSessionStatus("idle"),
                "Cancelling an idle session should mark it cancelled");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.SESSION_CANCELLED),
                "Idle-session cancel should also publish SESSION_CANCELLED");
    }

    @Test
    void cancelGracefulEndToEndStopsAfterCurrentTool() throws Exception {
        ChatResponse toolCallResponse = buildToolCallResponse("test-calculator");
        IChatService chatService = createToolCallChatService(toolCallResponse);

        CountDownLatch toolEntered = new CountDownLatch(1);
        CountDownLatch proceedWithTool = new CountDownLatch(1);
        IToolManager toolManager = createBlockingToolManager(toolEntered, proceedWithTool);

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        AgentMessageRequest request = new AgentMessageRequest(
                "test-react-agent", "compute", "graceful-e2e", null);
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);

        assertTrue(toolEntered.await(30, TimeUnit.SECONDS),
                "The tool call should have been entered by the executor");

        engine.cancelSession("graceful-e2e", "graceful-test", false);
        proceedWithTool.countDown();

        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.cancelled, result.getStatus(),
                "Graceful cancel should yield a cancelled result");
        assertEquals(AgentExecStatus.cancelled, engine.getSessionStatus("graceful-e2e"),
                "Session status should be cancelled after graceful cancel");
    }

    @Test
    void cancelForcedEndToEndInterruptsImmediately() throws Exception {
        ChatResponse toolCallResponse = buildToolCallResponse("test-calculator");
        CountDownLatch llmEntered = new CountDownLatch(1);
        IChatService chatService = createBlockingChatService(toolCallResponse, llmEntered);
        IToolManager toolManager = createNoOpToolManager();

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        AgentMessageRequest request = new AgentMessageRequest(
                "test-react-agent", "compute", "forced-e2e", null);
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);

        assertTrue(llmEntered.await(30, TimeUnit.SECONDS),
                "The LLM call should have been entered by the executor");

        long startNs = System.nanoTime();
        engine.cancelSession("forced-e2e", "forced-test", true);
        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertEquals(AgentExecStatus.cancelled, result.getStatus(),
                "Forced cancel should yield a cancelled result (not failed)");
        assertEquals(AgentExecStatus.cancelled, engine.getSessionStatus("forced-e2e"),
                "Session status should be cancelled after forced cancel");
        assertTrue(elapsedMs < 30000,
                "Forced cancel should terminate far sooner than the 30s sleep, took " + elapsedMs + "ms");
    }

    @Test
    void cancelPublishesCancelRequestedAndCancelledEvents() throws Exception {
        ChatResponse toolCallResponse = buildToolCallResponse("test-calculator");
        IChatService chatService = createToolCallChatService(toolCallResponse);

        CountDownLatch toolEntered = new CountDownLatch(1);
        CountDownLatch proceedWithTool = new CountDownLatch(1);
        IToolManager toolManager = createBlockingToolManager(toolEntered, proceedWithTool);

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);
        java.util.List<AgentEvent> events = Collections.synchronizedList(new java.util.ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest request = new AgentMessageRequest(
                "test-react-agent", "compute", "event-e2e", null);
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);

        assertTrue(toolEntered.await(30, TimeUnit.SECONDS));
        engine.cancelSession("event-e2e", "event-test", false);
        proceedWithTool.countDown();

        AgentExecutionResult result = future.get(60, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.cancelled, result.getStatus());

        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.SESSION_CANCEL_REQUESTED),
                "SESSION_CANCEL_REQUESTED event should be published during cancel");
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.SESSION_CANCELLED),
                "SESSION_CANCELLED event should be published when the loop terminates due to cancel");

        assertFalse(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED),
                "A cancelled execution should NOT emit EXECUTION_COMPLETED");
    }
}
