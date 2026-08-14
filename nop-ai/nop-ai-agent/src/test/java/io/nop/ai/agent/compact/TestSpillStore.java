package io.nop.ai.agent.compact;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentToolExecuteContext;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.tool.ReadSpillExecutor;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.nop.ai.agent.support.ChatResponseFixtures;

/**
 * Phase 3 tests for the spill store (design §3.3): unit behaviour of
 * {@link InMemorySpillStore}, the {@link AgentSession} host, the dispatcher
 * write side (spill decision precedes truncation), degradation semantics
 * (put-failure warn vs null-session silent), and the {@code read-spill} tool
 * read side.
 */
public class TestSpillStore {

    private static final int THRESHOLD = ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentModel agentModel;
    private InMemorySessionStore sessionStore;

    @BeforeEach
    void setUp() {
        agentModel = new AgentModel();
        agentModel.setName("test-agent");
        agentModel.setTools(Set.of("bash", "ask-oracle"));
        sessionStore = new InMemorySessionStore();
    }

    // ------------------------------------------------------------------
    // InMemorySpillStore unit behaviour
    // ------------------------------------------------------------------

    @Test
    void putReturnsOpaqueIdAndGetReadsBackFullContent() {
        ISpillStore store = new InMemorySpillStore("sess-1");
        String content = "F".repeat(20000);
        String id = store.put(content);
        assertNotNull(id);
        assertEquals(content, store.get(id));
    }

    @Test
    void unknownIdReturnsNull() {
        ISpillStore store = new InMemorySpillStore("sess-1");
        assertNull(store.get("no-such-id"));
        assertNull(store.get(null));
    }

    @Test
    void deleteRemovesContentAndIsIdempotent() {
        ISpillStore store = new InMemorySpillStore("sess-1");
        String id = store.put("content");
        store.delete(id);
        assertNull(store.get(id));
        store.delete(id);
        store.delete(null);
    }

    @Test
    void nullContentRejected() {
        ISpillStore store = new InMemorySpillStore("sess-1");
        assertThrows(NullPointerException.class, () -> store.put(null));
    }

    @Test
    void multiplePutsYieldDistinctIds() {
        ISpillStore store = new InMemorySpillStore("sess-1");
        String id1 = store.put("alpha");
        String id2 = store.put("beta");
        assertTrue(!id1.equals(id2));
        assertEquals("alpha", store.get(id1));
        assertEquals("beta", store.get(id2));
        assertEquals(2, ((InMemorySpillStore) store).size());
    }

    @Test
    void spillIdEmbedsSessionPrefix() {
        ISpillStore store = new InMemorySpillStore("sess-42");
        String id = store.put("content");
        assertTrue(id.startsWith("sess-42-spill-"), "spill id should embed session prefix: " + id);
    }

    // ------------------------------------------------------------------
    // AgentSession host
    // ------------------------------------------------------------------

    @Test
    void spillStoreLazilyInitialisedOnFirstAccess() {
        AgentSession session = AgentSession.create("sess-1", "test-agent");
        assertNull(session.getSpillStore(), "store must not materialise before first access");
        ISpillStore store = session.getOrCreateSpillStore();
        assertNotNull(store);
        assertSame(store, session.getSpillStore(), "same instance exposed after materialisation");
    }

    @Test
    void sessionHostsSingleSharedStoreInstance() {
        AgentSession session = AgentSession.create("sess-1", "test-agent");
        assertSame(session.getOrCreateSpillStore(), session.getOrCreateSpillStore());
    }

    // ------------------------------------------------------------------
    // Dispatcher write side (engine path — session registered)
    // ------------------------------------------------------------------

    private IToolManager toolManagerReturning(String content) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, content));
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                m.setDescription("Test tool");
                return m;
            }
        };
    }

    private IChatService chatServiceWithOneToolCallThenDone(String toolName) {
        AtomicInteger calls = new AtomicInteger();
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                if (calls.getAndIncrement() == 0) {
                    ChatToolCall call = new ChatToolCall();
                    call.setId("tc-1");
                    call.setName(toolName);
                    return CompletableFuture.completedFuture(ChatResponseFixtures.assistantWithToolCalls("use tool", call));
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private ReActAgentExecutor buildExecutor(IChatService chatService, IToolManager toolManager) {
        return ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .sessionStore(sessionStore)
                .toolAccessChecker(new io.nop.ai.agent.security.AllowAllToolAccessChecker())
                .build();
    }

    private AgentExecutionContext executeViaExecutor(ReActAgentExecutor executor) {
        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, "test-session");
        ctx.addMessage(new ChatUserMessage("hello"));
        executor.execute(ctx).toCompletableFuture().join();
        return ctx;
    }

    private String toolResponseText(AgentExecutionContext ctx) {
        for (ChatMessage m : ctx.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                return m.getContent();
            }
        }
        return null;
    }

    private String extractSpillId(String inline) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\[SPILL_REF id=([^\\]]+)\\]").matcher(inline);
        assertTrue(m.find(), "inline should contain SPILL_REF marker: " + inline);
        return m.group(1);
    }

    @Test
    void oversizedToolResultSpilledToSessionStore() {
        sessionStore.getOrCreate("test-session", "test-agent");
        ReActAgentExecutor executor = buildExecutor(
                chatServiceWithOneToolCallThenDone("bash"),
                toolManagerReturning("B".repeat(20000)));
        AgentExecutionContext ctx = executeViaExecutor(executor);

        String inline = toolResponseText(ctx);
        assertNotNull(inline);
        assertTrue(inline.contains("[SPILL_REF id="), "inline should carry SPILL_REF marker: " + inline);
        assertTrue(!inline.contains("B".repeat(THRESHOLD)), "full content must not stay inline");

        AgentSession session = sessionStore.get("test-session");
        assertNotNull(session);
        ISpillStore store = session.getSpillStore();
        assertNotNull(store);
        assertEquals(1, ((InMemorySpillStore) store).size());

        String spillId = extractSpillId(inline);
        assertEquals("B".repeat(20000), store.get(spillId), "full content readable back from the store");
    }

    @Test
    void belowThresholdResultStaysInline() {
        sessionStore.getOrCreate("test-session", "test-agent");
        ReActAgentExecutor executor = buildExecutor(
                chatServiceWithOneToolCallThenDone("bash"),
                toolManagerReturning("short result"));
        AgentExecutionContext ctx = executeViaExecutor(executor);
        assertEquals("short result", toolResponseText(ctx));
        AgentSession session = sessionStore.get("test-session");
        assertNull(session.getSpillStore(), "no spill store materialised for inline results");
    }

    @Test
    void nonTruncatableToolExemptFromSpill() {
        sessionStore.getOrCreate("test-session", "test-agent");
        ReActAgentExecutor executor = buildExecutor(
                chatServiceWithOneToolCallThenDone("ask-oracle"),
                toolManagerReturning("A".repeat(20000)));
        AgentExecutionContext ctx = executeViaExecutor(executor);
        assertEquals("A".repeat(20000), toolResponseText(ctx),
                "ask-oracle result must stay full inline (spill-exempt)");
        AgentSession session = sessionStore.get("test-session");
        assertNull(session.getSpillStore(), "exempt tool must not materialise the spill store");
    }

    @Test
    void putFailureDegradesToTruncation() {
        ISpillStore throwingStore = new ISpillStore() {
            @Override
            public String put(String content) {
                throw new IllegalStateException("store full");
            }

            @Override
            public String get(String spillId) {
                return null;
            }

            @Override
            public void delete(String spillId) {
            }
        };
        ReActAgentExecutor executor = buildExecutor(
                chatServiceWithOneToolCallThenDone("bash"),
                toolManagerReturning("B".repeat(20000)));
        sessionStore.getOrCreate("test-session", "test-agent").setSpillStore(throwingStore);
        AgentExecutionContext ctx = executeViaExecutor(executor);

        String inline = toolResponseText(ctx);
        assertTrue(inline.contains("TRUNCATED"), "put failure must degrade to truncation: " + inline);
        assertTrue(!inline.contains("[SPILL_REF"), "no SPILL_REF marker on degraded path");
    }

    // ------------------------------------------------------------------
    // null-session path (builder-direct executor, no sessionStore wiring)
    // ------------------------------------------------------------------

    @Test
    void nullSessionSilentlyTruncates() {
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceWithOneToolCallThenDone("bash"))
                .toolManager(toolManagerReturning("B".repeat(20000)))
                .toolAccessChecker(new io.nop.ai.agent.security.AllowAllToolAccessChecker())
                .build();
        AgentExecutionContext ctx = AgentExecutionContext.create(agentModel, "test-session");
        ctx.addMessage(new ChatUserMessage("hello"));
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());

        String inline = toolResponseText(ctx);
        assertTrue(inline.contains("TRUNCATED"), "null-session path must fall back to truncation: " + inline);
        assertTrue(!inline.contains("[SPILL_REF"), "no SPILL_REF marker on null-session path");
    }

    // ------------------------------------------------------------------
    // read-spill tool (read side)
    // ------------------------------------------------------------------

    private AgentToolExecuteContext contextWithSession(AgentSession session) {
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), null, 0L, null, null, null,
                null, null, session.getSessionId(), "test-agent");
        ctx.setSession(session);
        return ctx;
    }

    private AiToolCallResult executeReadSpill(AgentToolExecuteContext ctx, String spillId) {
        AiToolCall call = new AiToolCall();
        call.setId(1);
        call.setInput("{\"id\": \"" + spillId + "\"}");
        return new ReadSpillExecutor().executeAsync(call, ctx).toCompletableFuture().join();
    }

    @Test
    void readSpillReturnsFullContent() {
        sessionStore.getOrCreate("test-session", "test-agent");
        ReActAgentExecutor executor = buildExecutor(
                chatServiceWithOneToolCallThenDone("bash"),
                toolManagerReturning("B".repeat(20000)));
        AgentExecutionContext ctx = executeViaExecutor(executor);
        String inline = toolResponseText(ctx);
        String spillId = extractSpillId(inline);

        AgentSession session = sessionStore.get("test-session");
        AiToolCallResult result = executeReadSpill(contextWithSession(session), spillId);
        assertEquals("success", result.getStatus());
        assertEquals("B".repeat(20000), result.getOutput().getBody());
    }

    @Test
    void readSpillUnknownIdReturnsExplicitError() {
        AgentSession session = AgentSession.create("sess-1", "test-agent");
        AiToolCallResult result = executeReadSpill(contextWithSession(session), "no-such-id");
        assertTrue(!"success".equals(result.getStatus()), "unknown id must fail, not silently succeed");
        assertNotNull(result.getError());
    }

    @Test
    void readSpillWithoutSessionReturnsError() {
        AgentToolExecuteContext ctx = new AgentToolExecuteContext(
                new File("."), null, 0L, null, null, null,
                null, null, "sess-1", "test-agent");
        AiToolCallResult result = executeReadSpill(ctx, "whatever");
        assertTrue(!"success".equals(result.getStatus()));
        assertNotNull(result.getError());
    }
}