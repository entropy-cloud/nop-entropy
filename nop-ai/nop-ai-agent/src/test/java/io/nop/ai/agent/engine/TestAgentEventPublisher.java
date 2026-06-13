package io.nop.ai.agent.engine;

import io.nop.ai.api.exceptions.NopAiException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
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

import java.util.ArrayList;
import java.util.Collections;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAgentEventPublisher {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private IChatService createChatService(ChatResponse response) {
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

    private IChatService createMultiResponseChatService(List<ChatResponse> responses) {
        AtomicInteger idx = new AtomicInteger(0);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(responses.get(idx.getAndIncrement()));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return responses.get(idx.getAndIncrement());
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    @Test
    void testPublishNotifiesSubscribers() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        AtomicReference<AgentEvent> received = new AtomicReference<>();

        publisher.addSubscriber(received::set);

        AgentEvent event = AgentEvent.create(AgentEventType.EXECUTION_STARTED, "s1", "agent1", Map.of());
        publisher.publish(event);

        assertNotNull(received.get());
        assertEquals(AgentEventType.EXECUTION_STARTED, received.get().getEventType());
        assertEquals("s1", received.get().getSessionId());
        assertEquals("agent1", received.get().getAgentName());
    }

    @Test
    void testMultipleSubscribers() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        AtomicReference<AgentEvent> received1 = new AtomicReference<>();
        AtomicReference<AgentEvent> received2 = new AtomicReference<>();

        publisher.addSubscriber(received1::set);
        publisher.addSubscriber(received2::set);

        AgentEvent event = AgentEvent.create(AgentEventType.LLM_RESPONSE_RECEIVED, "s2", "agent2", Map.of());
        publisher.publish(event);

        assertNotNull(received1.get());
        assertNotNull(received2.get());
        assertEquals(event, received1.get());
        assertEquals(event, received2.get());
    }

    @Test
    void testRemoveSubscriber() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        AtomicReference<AgentEvent> received = new AtomicReference<>();

        IAgentEventSubscriber subscriber = received::set;
        publisher.addSubscriber(subscriber);
        publisher.removeSubscriber(subscriber);

        publisher.publish(AgentEvent.create(AgentEventType.EXECUTION_COMPLETED, "s3", "agent3", Map.of()));

        assertNull(received.get());
    }

    @Test
    void testSubscriberExceptionDoesNotBlockOthers() {
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        AtomicReference<AgentEvent> received = new AtomicReference<>();

        publisher.addSubscriber(e -> {
            throw new NopAiException("subscriber A failed");
        });
        publisher.addSubscriber(received::set);

        publisher.publish(AgentEvent.create(AgentEventType.TOOL_CALL_STARTED, "s4", "agent4", Map.of()));

        assertNotNull(received.get());
        assertEquals(AgentEventType.TOOL_CALL_STARTED, received.get().getEventType());
    }

    @Test
    void testExecutorPublishesEvents() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("Hello");
        ChatResponse response = ChatResponse.success(msg);

        IChatService chatService = createChatService(response);
        IToolManager toolManager = new NoOpToolManager();

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).eventPublisher(publisher).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());

        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_STARTED));
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.LLM_RESPONSE_RECEIVED));
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED));

        int startedIdx = indexOf(events, AgentEventType.EXECUTION_STARTED);
        int llmIdx = indexOf(events, AgentEventType.LLM_RESPONSE_RECEIVED);
        int completedIdx = indexOf(events, AgentEventType.EXECUTION_COMPLETED);
        assertTrue(startedIdx < llmIdx);
        assertTrue(llmIdx < completedIdx);
    }

    @Test
    void testExecutorWithToolCallsPublishesEvents() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("calculator"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("calculator");
        toolCall.setArguments(Map.of("expr", "1+1"));

        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));
        ChatResponse toolResponse = ChatResponse.success(toolMsg);

        ChatAssistantMessage finalMsg = new ChatAssistantMessage();
        finalMsg.setContent("Result is 2");
        ChatResponse finalResponse = ChatResponse.success(finalMsg);

        IChatService chatService = createMultiResponseChatService(List.of(toolResponse, finalResponse));
        IToolManager toolManager = new NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "2"));
            }
        };

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).eventPublisher(publisher).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());

        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_STARTED));
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.TOOL_CALL_COMPLETED));

        int toolStartedIdx = indexOf(events, AgentEventType.TOOL_CALL_STARTED);
        int toolCompletedIdx = indexOf(events, AgentEventType.TOOL_CALL_COMPLETED);
        assertTrue(toolStartedIdx < toolCompletedIdx);
    }

    @Test
    void testEngineIntegration() throws Exception {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("Hi there");
        ChatResponse response = ChatResponse.success(msg);
        IChatService chatService = createChatService(response);
        IToolManager toolManager = new NoOpToolManager();

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, toolManager);

        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest request = new AgentMessageRequest("test-agent", "Hello");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_STARTED));
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_COMPLETED));
    }

    @Test
    void testExecutorWithNullPublisherDoesNotThrow() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("Hello");
        ChatResponse response = ChatResponse.success(msg);

        IChatService chatService = createChatService(response);
        IToolManager toolManager = new NoOpToolManager();

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertNull(result.getError());
    }

    @Test
    void testExecutorPublishesFailedEventOnError() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatResponse errorResponse = ChatResponse.error("RATE_LIMIT", "rate limit exceeded");
        IChatService chatService = createChatService(errorResponse);
        IToolManager toolManager = new NoOpToolManager();

        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        publisher.addSubscriber(events::add);

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).eventPublisher(publisher).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.failed, result.getStatus());

        assertTrue(events.stream().anyMatch(e -> e.getEventType() == AgentEventType.EXECUTION_STARTED));

        AgentEvent failedEvent = events.stream()
                .filter(e -> e.getEventType() == AgentEventType.EXECUTION_FAILED)
                .findFirst()
                .orElse(null);
        assertNotNull(failedEvent);
        assertNotNull(failedEvent.getError());
        assertFalse(failedEvent.getError().isEmpty());

        int startedIdx = indexOf(events, AgentEventType.EXECUTION_STARTED);
        int failedIdx = indexOf(events, AgentEventType.EXECUTION_FAILED);
        assertTrue(startedIdx < failedIdx);
    }

    private int indexOf(List<AgentEvent> events, AgentEventType type) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getEventType() == type) {
                return i;
            }
        }
        return -1;
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
}
