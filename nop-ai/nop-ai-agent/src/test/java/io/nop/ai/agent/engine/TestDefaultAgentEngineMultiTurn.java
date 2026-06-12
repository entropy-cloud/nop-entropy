package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultAgentEngineMultiTurn {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private ChatResponse buildTextResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private IChatService createSequentialChatService(List<ChatResponse> responses) {
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

    private IToolManager createNoOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                  IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "mock-result"));
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
    void testMultiTurnHistoryPreserved() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();

        IChatService chatService = createSequentialChatService(List.of(
                buildTextResponse("Hi there"),
                buildTextResponse("You said hello")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, createNoOpToolManager(), store);

        CompletableFuture<AgentExecutionResult> r1 = engine.execute(
                new AgentMessageRequest("test-agent", "hello", "session-1", null));
        AgentExecutionResult result1 = r1.get(10, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result1.getStatus());

        CompletableFuture<AgentExecutionResult> r2 = engine.execute(
                new AgentMessageRequest("test-agent", "what did I say?", "session-1", null));
        AgentExecutionResult result2 = r2.get(10, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result2.getStatus());

        AgentSession session = store.get("session-1");
        assertNotNull(session);
        assertTrue(session.getMessageCount() > 0);

        List<ChatMessage> history = session.getMessages();
        boolean hasFirstUser = history.stream().anyMatch(m ->
                m instanceof ChatUserMessage && m.getContent() != null && m.getContent().contains("hello"));
        boolean hasFirstAssistant = history.stream().anyMatch(m ->
                m instanceof ChatAssistantMessage && m.getContent() != null && m.getContent().contains("Hi there"));
        boolean hasSecondUser = history.stream().anyMatch(m ->
                m instanceof ChatUserMessage && m.getContent() != null && m.getContent().contains("what did I say?"));

        assertTrue(hasFirstUser, "History should contain first user message 'hello'");
        assertTrue(hasFirstAssistant, "History should contain first assistant response 'Hi there'");
        assertTrue(hasSecondUser, "History should contain second user message 'what did I say?'");
    }

    @Test
    void testMultiTurnWithToolCallsHistoryPreserved() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("test-calculator");
        toolCall.setArguments(Map.of("expr", "2+2"));

        ChatAssistantMessage toolMsg = new ChatAssistantMessage();
        toolMsg.setContent("");
        toolMsg.setToolCalls(List.of(toolCall));

        ChatAssistantMessage finalMsg1 = new ChatAssistantMessage();
        finalMsg1.setContent("The result of 2+2 is 4.");

        ChatAssistantMessage finalMsg2 = new ChatAssistantMessage();
        finalMsg2.setContent("The previous result was 4.");

        AtomicInteger callIdx = new AtomicInteger(0);
        List<ChatResponse> responses = List.of(
                ChatResponse.success(toolMsg),
                ChatResponse.success(finalMsg1),
                ChatResponse.success(finalMsg2)
        );

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(responses.get(callIdx.getAndIncrement()));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return responses.get(callIdx.getAndIncrement());
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, createNoOpToolManager(), store);

        CompletableFuture<AgentExecutionResult> r1 = engine.execute(
                new AgentMessageRequest("test-react-agent", "What is 2+2?", "session-tools", null));
        AgentExecutionResult result1 = r1.get(10, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result1.getStatus());

        CompletableFuture<AgentExecutionResult> r2 = engine.execute(
                new AgentMessageRequest("test-react-agent", "What was the previous result?", "session-tools", null));
        AgentExecutionResult result2 = r2.get(10, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.completed, result2.getStatus());

        AgentSession session = store.get("session-tools");
        assertNotNull(session);

        List<ChatMessage> history = session.getMessages();
        boolean hasToolResponse = history.stream().anyMatch(m -> m instanceof ChatToolResponseMessage);
        assertTrue(hasToolResponse, "History should contain tool response message from first turn");

        boolean hasFirstUserMsg = history.stream().anyMatch(m ->
                m instanceof ChatUserMessage && m.getContent().contains("2+2"));
        boolean hasSecondUserMsg = history.stream().anyMatch(m ->
                m instanceof ChatUserMessage && m.getContent().contains("previous result"));
        assertTrue(hasFirstUserMsg, "History should contain first user message");
        assertTrue(hasSecondUserMsg, "History should contain second user message");
    }

    @Test
    void testDifferentSessionsAreIsolated() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();

        IChatService chatService = createSequentialChatService(List.of(
                buildTextResponse("Response for A"),
                buildTextResponse("Response for B"),
                buildTextResponse("Second response for A")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, createNoOpToolManager(), store);

        engine.execute(new AgentMessageRequest("test-agent", "hello A", "session-A", null))
                .get(10, TimeUnit.SECONDS);
        engine.execute(new AgentMessageRequest("test-agent", "hello B", "session-B", null))
                .get(10, TimeUnit.SECONDS);
        engine.execute(new AgentMessageRequest("test-agent", "follow-up A", "session-A", null))
                .get(10, TimeUnit.SECONDS);

        AgentSession sessionA = store.get("session-A");
        AgentSession sessionB = store.get("session-B");

        assertNotNull(sessionA);
        assertNotNull(sessionB);

        boolean aHasBMessage = sessionA.getMessages().stream().anyMatch(m ->
                m.getContent() != null && m.getContent().contains("hello B"));
        boolean bHasAMessage = sessionB.getMessages().stream().anyMatch(m ->
                m.getContent() != null && m.getContent().contains("hello A"));

        assertTrue(!aHasBMessage, "Session A should not contain session B messages");
        assertTrue(!bHasAMessage, "Session B should not contain session A messages");

        boolean aHasFollowUp = sessionA.getMessages().stream().anyMatch(m ->
                m instanceof ChatUserMessage && m.getContent() != null && m.getContent().contains("follow-up A"));
        assertTrue(aHasFollowUp, "Session A should contain its follow-up message");
    }

    @Test
    void testSessionCreatedAndLoadedEvents() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();

        IChatService chatService = createSequentialChatService(List.of(
                buildTextResponse("First response"),
                buildTextResponse("Second response")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, createNoOpToolManager(), store);
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        engine.execute(new AgentMessageRequest("test-agent", "first", "session-events", null))
                .get(10, TimeUnit.SECONDS);

        boolean hasCreated = events.stream().anyMatch(e ->
                e.getEventType() == AgentEventType.SESSION_CREATED
                        && "session-events".equals(e.getSessionId()));
        assertTrue(hasCreated, "First call should emit SESSION_CREATED event");

        events.clear();

        engine.execute(new AgentMessageRequest("test-agent", "second", "session-events", null))
                .get(10, TimeUnit.SECONDS);

        boolean hasLoaded = events.stream().anyMatch(e ->
                e.getEventType() == AgentEventType.SESSION_LOADED
                        && "session-events".equals(e.getSessionId()));
        assertTrue(hasLoaded, "Second call should emit SESSION_LOADED event");
    }

    @Test
    void testSessionStatsAccumulateAcrossRequests() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();

        IChatService chatService = createSequentialChatService(List.of(
                buildTextResponse("Response 1"),
                buildTextResponse("Response 2")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, createNoOpToolManager(), store);

        engine.execute(new AgentMessageRequest("test-agent", "first", "session-stats", null))
                .get(10, TimeUnit.SECONDS);
        AgentSession session = store.get("session-stats");
        long tokensAfterFirst = session.getTotalTokensUsed();
        int iterationsAfterFirst = session.getTotalIterations();

        engine.execute(new AgentMessageRequest("test-agent", "second", "session-stats", null))
                .get(10, TimeUnit.SECONDS);

        session = store.get("session-stats");
        assertTrue(session.getTotalTokensUsed() >= tokensAfterFirst,
                "Tokens should accumulate across requests");
        assertTrue(session.getTotalIterations() >= iterationsAfterFirst,
                "Iterations should accumulate across requests");
    }
}
