package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.FileBackedCheckpointManager;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.AiAgentSessionTable;
import io.nop.ai.agent.session.DBSessionStore;
import io.nop.ai.agent.session.FileBackedSessionStore;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.session.ISessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
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
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
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

/**
 * Phase 3 end-to-end engine-wiring tests for {@link DBSessionStore}.
 *
 * <p>These tests prove the full wiring chain is connected: a
 * {@link DBSessionStore} registered via the {@link DefaultAgentEngine}
 * constructor receives dispatch-path {@code save} calls and the sessions are
 * <b>actually persisted to the {@code ai_agent_session} DB table</b> (verified
 * by direct SQL, not just by the store object receiving a call). Also covers
 * the core value (cross-instance survival via shared DB), the
 * {@code restoreSession} path (plan 183) and {@code restorePendingSessions}
 * path (plan 184) with DB-backed discovery, and backward compatibility
 * ({@link InMemorySessionStore} / {@link FileBackedSessionStore} defaults
 * producing zero DB rows).
 */
public class TestDBSessionStoreEngineWiring {

    private DataSource dataSource;
    private String dbUrl;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        dbUrl = "jdbc:h2:mem:test-db-session-wiring-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    @TempDir
    Path tempDir;

    // ========================================================================
    // Cross-instance survival (core value — Minimum Rules #22 Anti-Hollow)
    // ========================================================================

    /**
     * Core value: session state survives across independent store instances
     * sharing the same DB. If {@link DBSessionStore} secretly relied on
     * in-memory state instead of the DB, the new instance's {@code get} would
     * return null and this test would fail.
     */
    @Test
    void crossInstanceSurvivalViaSharedDb() {
        DBSessionStore storeA = new DBSessionStore(dataSource);

        for (AgentExecStatus status : AgentExecStatus.values()) {
            String sid = "cross-" + status.name();
            AgentSession s = AgentSession.create(sid, "agent-" + status.name());
            s.setStatus(status);
            s.appendMessages(List.of(new ChatUserMessage("msg-" + status)));
            s.addTokensUsed(500L);
            s.getMetadata().put("k", "v-" + status);
            storeA.save(s);
        }

        Collection<AgentSession> discoveredOnA = storeA.listAllSessions();
        assertEquals(AgentExecStatus.values().length, discoveredOnA.size());

        // Discard storeA entirely — new instance B shares only the DB.
        DBSessionStore storeB = new DBSessionStore(dataSource);

        for (AgentExecStatus status : AgentExecStatus.values()) {
            AgentSession restored = storeB.get("cross-" + status.name());
            assertNotNull(restored, "Cross-instance get must return the persisted session: " + status);
            assertEquals(status, restored.getStatus());
            assertEquals("agent-" + status.name(), restored.getAgentName());
            assertEquals(1, restored.getMessageCount());
            assertEquals(500L, restored.getTotalTokensUsed());
            assertEquals("v-" + status, restored.getMetadata().get("k"));
        }

        Collection<AgentSession> discoveredOnB = storeB.listAllSessions();
        assertEquals(AgentExecStatus.values().length, discoveredOnB.size());
    }

    // ========================================================================
    // restoreSession with DB-backed store (plan 183 path)
    // ========================================================================

    /**
     * Engine-wiring + Anti-Hollow: execute → intra-execution save writes to
     * the DB table → simulate crash (new engine + new store sharing DB) →
     * {@code restoreSession} loads from DB and resumes the ReAct loop.
     * Verified by direct SQL on the {@code ai_agent_session} table.
     */
    @Test
    void restoreSessionUsesDbBackedStoreAndPersistsToTable() throws Exception {
        String sessionId = "db-restore-1";

        DBSessionStore store1 = new DBSessionStore(dataSource);
        FileBackedCheckpointManager ckptMgr1 = new FileBackedCheckpointManager(tempDir.resolve("db-restore-ckpt"));

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call-db-r1");
        toolCall.setName("test-calculator");
        toolCall.setArguments(Map.of("expr", "2+2"));

        ScriptedChatService chat1 = new ScriptedChatService(List.of(
                toolCallResponse("call-db-r1", "test-calculator", Map.of("expr", "2+2")),
                finalResponse("The result is 4.")));

        DefaultAgentEngine engine1 = new DefaultAgentEngine(chat1, toolManagerReturning("tool-out"), store1);
        engine1.setCheckpointManager(ckptMgr1);

        engine1.execute(new AgentMessageRequest("test-react-agent", "What is 2+2?", sessionId, null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        // Anti-Hollow: verify the session was actually persisted to the DB table
        assertTrue(countSessionRows(sessionId) >= 1,
                "execute must have persisted the session to ai_agent_session via intra-execution save");

        // Simulate crash: discard engine1 + store1 + ckptMgr1 entirely.
        // New engine2 + store2 + ckptMgr2 share the same DB + checkpoint dir.
        DBSessionStore store2 = new DBSessionStore(dataSource);
        FileBackedCheckpointManager ckptMgr2 = new FileBackedCheckpointManager(tempDir.resolve("db-restore-ckpt"));

        // Verify cross-instance read works (the session is in the DB, not memory)
        AgentSession persisted = store2.get(sessionId);
        assertNotNull(persisted, "Session must survive across instances (DB-persisted)");
        assertTrue(persisted.getMessageCount() > 0);

        // Simulate crash-mid-execution by reverting status to running
        persisted.setStatus(AgentExecStatus.running);
        store2.save(persisted);

        ScriptedChatService chat2 = new ScriptedChatService(List.of(
                finalResponse("Resumed after crash: the answer is still 4.")));

        DefaultAgentEngine engine2 = new DefaultAgentEngine(chat2, toolManagerReturning("ok"), store2);
        engine2.setCheckpointManager(ckptMgr2);

        AgentExecutionResult result2 = engine2.restoreSession(sessionId, "operator", "crash recovery")
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result2.getStatus(),
                "Restored execution must complete from DB-backed store");

        // Final state is persisted
        AgentSession restored = store2.get(sessionId);
        assertEquals(AgentExecStatus.completed, restored.getStatus());
    }

    // ========================================================================
    // restorePendingSessions with DB-backed discovery (plan 184 path)
    // ========================================================================

    /**
     * Engine-wiring for {@code restorePendingSessions}: two sessions executed
     * by engine A → crash simulated → engine B (new store sharing DB) calls
     * {@code restorePendingSessions} → {@code listAllSessions} discovers all
     * sessions from the DB → both are restored. Proves plan 184's auto-restore
     * works on a DB-backed store without any engine code change.
     */
    @Test
    void restorePendingSessionsUsesDbBackedDiscovery() throws Exception {
        DBSessionStore storeA = new DBSessionStore(dataSource);
        FileBackedCheckpointManager ckptA = new FileBackedCheckpointManager(tempDir.resolve("pending-ckpt"));

        ChatToolCall call1 = new ChatToolCall();
        call1.setId("call-pend-1");
        call1.setName("test-calculator");
        call1.setArguments(Map.of("expr", "1+1"));

        ScriptedChatService chatA1 = new ScriptedChatService(List.of(
                toolCallResponse("call-pend-1", "test-calculator", Map.of("expr", "1+1")),
                finalResponse("pending-1-done")));

        DefaultAgentEngine engineA1 = new DefaultAgentEngine(chatA1, toolManagerReturning("out"), storeA);
        engineA1.setCheckpointManager(ckptA);
        engineA1.execute(new AgentMessageRequest("test-react-agent", "What is 1+1?", "pending-1", null))
                .get(30, TimeUnit.SECONDS);

        ChatToolCall call2 = new ChatToolCall();
        call2.setId("call-pend-2");
        call2.setName("test-calculator");
        call2.setArguments(Map.of("expr", "2+2"));

        ScriptedChatService chatA2 = new ScriptedChatService(List.of(
                toolCallResponse("call-pend-2", "test-calculator", Map.of("expr", "2+2")),
                finalResponse("pending-2-done")));

        DefaultAgentEngine engineA2 = new DefaultAgentEngine(chatA2, toolManagerReturning("out"), storeA);
        engineA2.setCheckpointManager(ckptA);
        engineA2.execute(new AgentMessageRequest("test-react-agent", "What is 2+2?", "pending-2", null))
                .get(30, TimeUnit.SECONDS);

        // Simulate crash: revert both completed sessions to running
        DBSessionStore storeCrashed = new DBSessionStore(dataSource);
        AgentSession s1 = storeCrashed.get("pending-1");
        s1.setStatus(AgentExecStatus.running);
        storeCrashed.save(s1);
        AgentSession s2 = storeCrashed.get("pending-2");
        s2.setStatus(AgentExecStatus.running);
        storeCrashed.save(s2);

        // Engine B: fresh engine + fresh store, same DB + checkpoint dir
        DBSessionStore storeB = new DBSessionStore(dataSource);
        FileBackedCheckpointManager ckptB = new FileBackedCheckpointManager(tempDir.resolve("pending-ckpt"));

        ScriptedChatService chatB = new ScriptedChatService(List.of(
                finalResponse("pending-1-restored"),
                finalResponse("pending-2-restored")));

        DefaultAgentEngine engineB = new DefaultAgentEngine(chatB, toolManagerReturning("ok"), storeB);
        engineB.setCheckpointManager(ckptB);

        SessionRestoreSummary summary = engineB.restorePendingSessions("operator", "crash recovery");

        assertEquals(2, summary.getRestoredCount(),
                "Both running sessions must be auto-restored from DB. Summary=" + summary);
        assertEquals(0, summary.getFailedCount());

        // Both sessions end up completed
        AgentSession r1 = storeB.get("pending-1");
        AgentSession r2 = storeB.get("pending-2");
        assertEquals(AgentExecStatus.completed, r1.getStatus());
        assertEquals(AgentExecStatus.completed, r2.getStatus());
    }

    // ========================================================================
    // Backward compatibility: InMemory / FileBacked defaults unchanged
    // ========================================================================

    /**
     * Backward-compat: an engine with {@link InMemorySessionStore} (default)
     * executes normally and writes nothing to the {@code ai_agent_session}
     * table — the DB-backed store is opt-in, never automatic.
     */
    @Test
    void inMemoryStoreDefaultWritesNoDbRows() throws Exception {
        // Initialize the schema so the table exists (the InMemorySessionStore
        // would never create it). We discard this store immediately — the
        // engine below uses an InMemorySessionStore.
        new DBSessionStore(dataSource);

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(List.of(finalResponse("im-done"))),
                toolManagerReturning("ok"),
                new InMemorySessionStore());

        engine.execute(new AgentMessageRequest("test-react-agent", "hi", "im-session", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(0, countAllSessionRows(),
                "InMemorySessionStore must never write to ai_agent_session");
    }

    /**
     * Backward-compat: an engine with {@link FileBackedSessionStore} executes
     * normally and writes nothing to the {@code ai_agent_session} table.
     */
    @Test
    void fileBackedStoreWritesNoDbRows() throws Exception {
        // Initialize the schema so the table exists for the verification query.
        new DBSessionStore(dataSource);

        FileBackedSessionStore store = new FileBackedSessionStore(tempDir.resolve("fb-compat"));
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(List.of(finalResponse("fb-done"))),
                toolManagerReturning("ok"),
                store);

        engine.execute(new AgentMessageRequest("test-react-agent", "hi", "fb-session", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(0, countAllSessionRows(),
                "FileBackedSessionStore must never write to ai_agent_session");
    }

    // ========================================================================
    // DB-backed engine writes to the table (wiring + Anti-Hollow)
    // ========================================================================

    /**
     * Wiring verification (Minimum Rules #23): a {@link DBSessionStore}
     * injected into the engine via the constructor causes session state to
     * actually land in the {@code ai_agent_session} table — verified by direct
     * SQL, not just by the store object receiving a call.
     */
    @Test
    void dbBackedEngineExecutionPersistsSessionToTable() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(List.of(finalResponse("db-exec-done"))),
                toolManagerReturning("ok"),
                store);

        engine.execute(new AgentMessageRequest("test-react-agent", "hi", "db-exec-1", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertTrue(countSessionRows("db-exec-1") >= 1,
                "DB-backed engine execution must persist the session to ai_agent_session");
    }

    // ========================================================================
    // Mocks / helpers
    // ========================================================================

    static final class ScriptedChatService implements IChatService {
        final List<ChatResponse> scripted;
        final AtomicInteger idx = new AtomicInteger(0);

        ScriptedChatService(List<ChatResponse> scripted) {
            this.scripted = scripted;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            int i = idx.getAndIncrement();
            if (i >= scripted.size()) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("(no more scripted responses — auto-final)");
                return ChatResponse.success(msg);
            }
            return scripted.get(i);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    static IToolManager toolManagerReturning(String output) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, output));
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

    static ChatResponse toolCallResponse(String callId, String toolName, Map<String, Object> args) {
        ChatToolCall call = new ChatToolCall();
        call.setId(callId);
        call.setName(toolName);
        call.setArguments(args);
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(call));
        return ChatResponse.success(msg);
    }

    static ChatResponse finalResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private int countSessionRows(String sessionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentSessionTable.TABLE_NAME
                             + " WHERE " + AiAgentSessionTable.COL_SESSION_ID + " = '" + sessionId + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countAllSessionRows() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentSessionTable.TABLE_NAME)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
