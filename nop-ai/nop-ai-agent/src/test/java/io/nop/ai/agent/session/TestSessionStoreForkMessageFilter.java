package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
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
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA6.5-AR-8: forkSession message filter hook. Verifies that a
 * {@link java.util.function.Predicate} applied on fork with context
 * inheritance copies only the accepted subset of parent messages, for all
 * three session stores, plus the engine→store end-to-end path and the
 * interface default fail-fast behaviour.
 */
public class TestSessionStoreForkMessageFilter {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static void seedParent(ISessionStore store, String parentId) {
        AgentSession parent = store.getOrCreate(parentId, "agent-a");
        parent.appendMessages(List.of(
                new ChatUserMessage("user message one"),
                new ChatUserMessage("user message two"),
                new ChatAssistantMessage("assistant reply")));
    }

    private static java.util.function.Predicate<ChatMessage> userMessagesOnly() {
        return m -> m instanceof ChatUserMessage;
    }

    @Test
    void inMemoryStoreFiltersInheritedMessages() {
        InMemorySessionStore store = new InMemorySessionStore();
        seedParent(store, "p-inmem");

        String childId = store.forkSession("p-inmem", true, null, userMessagesOnly());

        AgentSession child = store.get(childId);
        assertEquals(2, child.getMessageCount(), "only user messages should be inherited");
        assertTrue(child.getMessages().stream().allMatch(m -> m instanceof ChatUserMessage));
    }

    @Test
    void inMemoryStoreNullFilterKeepsFullInheritance() {
        InMemorySessionStore store = new InMemorySessionStore();
        seedParent(store, "p-inmem-null");

        String childId = store.forkSession("p-inmem-null", true, null, null);

        AgentSession child = store.get(childId);
        assertEquals(3, child.getMessageCount(), "null filter must preserve full inheritance");
    }

    @Test
    void fileBackedStoreFiltersInheritedMessages() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("fork-filter");
        FileBackedSessionStore store = new FileBackedSessionStore(tempDir);
        seedParent(store, "p-file");

        String childId = store.forkSession("p-file", true, null, userMessagesOnly());

        AgentSession child = store.get(childId);
        assertEquals(2, child.getMessageCount());
        assertTrue(child.getMessages().stream().allMatch(m -> m instanceof ChatUserMessage));
    }

    @Test
    void dbStoreFiltersInheritedMessages() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:fork-filter-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        DataSource dataSource = ds;
        DBSessionStore store = new DBSessionStore(dataSource);
        seedParent(store, "p-db");

        String childId = store.forkSession("p-db", true, null, userMessagesOnly());

        AgentSession child = store.get(childId);
        assertEquals(2, child.getMessageCount());
        assertTrue(child.getMessages().stream().allMatch(m -> m instanceof ChatUserMessage));
    }

    @Test
    void interfaceDefaultFailsFastOnNonNullFilter() {
        ISessionStore bareStore = new ISessionStore() {
            @Override
            public AgentSession getOrCreate(String sessionId, String agentName) {
                return AgentSession.create(sessionId, agentName);
            }

            @Override
            public AgentSession get(String sessionId) {
                return null;
            }

            @Override
            public void remove(String sessionId) {
            }

            @Override
            public java.util.Collection<AgentSession> getAll() {
                return Collections.emptyList();
            }
        };

        assertThrows(UnsupportedOperationException.class,
                () -> bareStore.forkSession("p", true, null, userMessagesOnly()),
                "a store without filter support must fail fast instead of silently ignoring the filter");
    }

    @Test
    void enginePassesFilterToStoreEndToEnd() {
        InMemorySessionStore store = new InMemorySessionStore();
        seedParent(store, "p-engine");

        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(new StubChatService(), new StubToolManager())
                .sessionStore(store)
                .forkMessageFilter(userMessagesOnly())
                .build();

        String childId = engine.forkSession(new AgentMessageRequest("agent-a", "fork", "p-engine", null), true)
                .toCompletableFuture().join();

        AgentSession child = store.get(childId);
        assertEquals(2, child.getMessageCount(), "engine→store filter path must be wired end to end");
        assertTrue(child.getMessages().stream().allMatch(m -> m instanceof ChatUserMessage));
    }

    @Test
    void engineWithoutFilterKeepsFullInheritance() {
        InMemorySessionStore store = new InMemorySessionStore();
        seedParent(store, "p-engine-null");

        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(new StubChatService(), new StubToolManager())
                .sessionStore(store)
                .build();

        String childId = engine.forkSession(new AgentMessageRequest("agent-a", "fork", "p-engine-null", null), true)
                .toCompletableFuture().join();

        AgentSession child = store.get(childId);
        assertEquals(3, child.getMessageCount(), "default engine must keep full inheritance");
    }

    private static class StubChatService implements IChatService {
        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            ChatAssistantMessage msg = new ChatAssistantMessage();
            msg.setContent("stub");
            return CompletableFuture.completedFuture(ChatResponse.success(msg));
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    private static class StubToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
            return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "result"));
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
            return null;
        }
    }
}
