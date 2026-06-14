package io.nop.ai.agent.engine;

import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;import io.nop.api.core.util.ICancelToken;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDefaultAgentEngineFork {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private IChatService createTextChatService(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        ChatResponse response = ChatResponse.success(msg);
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

    @Test
    void forkSessionInheritsMessagesEndToEnd() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-e2e-1", "test-agent");
        parent.appendMessages(List.of(
                new ChatUserMessage("hello"),
                new ChatUserMessage("world")));

        DefaultAgentEngine engine = new DefaultAgentEngine(null, null, store);
        AgentMessageRequest forkRequest = new AgentMessageRequest(
                "test-agent", "", "parent-e2e-1", null);

        CompletableFuture<String> future = engine.forkSession(forkRequest, true);
        String childId = future.join();

        assertNotNull(childId);
        assertNotEquals("parent-e2e-1", childId);

        AgentSession child = store.get(childId);
        assertNotNull(child, "Child session must appear in the store after fork");
        assertEquals("parent-e2e-1", child.getParentSessionId());
        assertEquals(2, child.getMessageCount());
        assertEquals(parent.getMessageCount(), child.getMessageCount());
    }

    @Test
    void forkSessionEmptyMessagesWhenInheritContextFalse() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-e2e-2", "test-agent");
        parent.appendMessages(List.of(new ChatUserMessage("hello")));

        DefaultAgentEngine engine = new DefaultAgentEngine(null, null, store);
        AgentMessageRequest forkRequest = new AgentMessageRequest(
                "test-agent", "", "parent-e2e-2", null);

        String childId = engine.forkSession(forkRequest, false).join();

        AgentSession child = store.get(childId);
        assertNotNull(child);
        assertEquals(0, child.getMessageCount());
    }

    @Test
    void forkSessionThenExecuteChildDoesNotAffectParent() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = new DefaultAgentEngine(
                createTextChatService("reply from LLM"), createNoOpToolManager(), store);

        engine.execute(new AgentMessageRequest("test-agent", "hello", "parent-exec-1", null))
                .get(30, TimeUnit.SECONDS);

        AgentSession parent = store.get("parent-exec-1");
        int parentCountBeforeFork = parent.getMessageCount();
        assertTrue(parentCountBeforeFork > 0, "Parent should have messages after execution");

        AgentMessageRequest forkRequest = new AgentMessageRequest(
                "test-agent", "", "parent-exec-1", null);
        String childId = engine.forkSession(forkRequest, true).join();
        AgentSession child = store.get(childId);
        int childCountAfterFork = child.getMessageCount();
        assertEquals(parentCountBeforeFork, childCountAfterFork,
                "Child should inherit parent messages after fork");

        engine.execute(new AgentMessageRequest("test-agent", "follow-up on child", childId, null))
                .get(30, TimeUnit.SECONDS);

        AgentSession childAfterExec = store.get(childId);
        assertTrue(childAfterExec.getMessageCount() > childCountAfterFork,
                "Child message count should grow after executing on child");

        assertEquals(parentCountBeforeFork, store.get("parent-exec-1").getMessageCount(),
                "Parent message count must not change after executing on the forked child");
    }

    @Test
    void forkSessionPublishesSessionForkedEvent() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("parent-e2e-event", "test-agent");

        DefaultAgentEngine engine = new DefaultAgentEngine(null, null, store);
        List<AgentEvent> events = Collections.synchronizedList(new ArrayList<>());
        engine.getEventPublisher().addSubscriber(events::add);

        AgentMessageRequest forkRequest = new AgentMessageRequest(
                "test-agent", "", "parent-e2e-event", null);
        String childId = engine.forkSession(forkRequest, true).join();

        boolean hasForked = events.stream()
                .anyMatch(e -> e.getEventType() == AgentEventType.SESSION_FORKED);
        assertTrue(hasForked, "SESSION_FORKED event should be published during fork");

        AgentEvent forkEvent = events.stream()
                .filter(e -> e.getEventType() == AgentEventType.SESSION_FORKED)
                .findFirst()
                .orElseThrow();
        assertEquals(childId, forkEvent.getSessionId(),
                "SESSION_FORKED event sessionId should be the child session id");
        assertEquals("parent-e2e-event", forkEvent.getPayload().get("parentSessionId"));
        assertEquals(childId, forkEvent.getPayload().get("childSessionId"));
        assertEquals(true, forkEvent.getPayload().get("inheritContext"));
    }

    @Test
    void forkSessionThrowsForNonExistentParent() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null, store);

        AgentMessageRequest forkRequest = new AgentMessageRequest(
                "test-agent", "", "ghost-parent", null);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.forkSession(forkRequest, true));
        assertTrue(ex.getMessage().contains("session not found"),
                "forkSession should fail-fast for a non-existent parent session");
    }

    @Test
    void forkSessionThrowsForNullSessionId() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null, store);

        AgentMessageRequest forkRequest = new AgentMessageRequest("test-agent", "hello");

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.forkSession(forkRequest, true));
        assertTrue(ex.getMessage().contains("sessionId"),
                "forkSession should fail-fast when request.sessionId is null");
    }

    @Test
    void forkSessionMetadataFromRequestMergedIntoChild() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("parent-meta", "test-agent");

        DefaultAgentEngine engine = new DefaultAgentEngine(null, null, store);
        Map<String, Object> metadata = Map.of("source", "fork-request", "priority", 3);
        AgentMessageRequest forkRequest = new AgentMessageRequest(
                "child-agent", "", "parent-meta", metadata);

        String childId = engine.forkSession(forkRequest, true).join();
        AgentSession child = store.get(childId);

        assertEquals("fork-request", child.getMetadata().get("source"));
        assertEquals(3, child.getMetadata().get("priority"));
        assertEquals("child-agent", child.getAgentName(),
                "Child agentName should be overridden by request.agentName");
    }
}
