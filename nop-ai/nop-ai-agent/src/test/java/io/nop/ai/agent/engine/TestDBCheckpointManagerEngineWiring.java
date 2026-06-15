package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.AiAgentCheckpointTable;
import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.agent.reliability.DBCheckpointManager;
import io.nop.ai.agent.reliability.FileBackedCheckpointManager;
import io.nop.ai.agent.reliability.NoOpCheckpoint;
import io.nop.ai.agent.session.DBSessionStore;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 end-to-end engine-wiring tests for {@link DBCheckpointManager}.
 *
 * <p>These tests prove the full wiring chain is connected: a
 * {@link DBCheckpointManager} registered via
 * {@code DefaultAgentEngine.setCheckpointManager} receives dispatch-path
 * {@code saveCheckpoint} calls and checkpoints are <b>actually persisted to the
 * {@code ai_agent_checkpoint} DB table</b> (verified by direct SQL, not just by
 * the manager object receiving a call). Also covers the core value
 * (cross-instance survival via shared DB), the {@code restoreSession} path
 * (plan 183) with DB-backed checkpoint manager, and backward compatibility
 * ({@link NoOpCheckpoint} / {@link FileBackedCheckpointManager} /
 * {@link InMemorySessionStore} defaults producing zero DB rows).
 */
public class TestDBCheckpointManagerEngineWiring {

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
        dbUrl = "jdbc:h2:mem:test-db-checkpoint-wiring-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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
     * Core value: checkpoint state survives across independent manager
     * instances sharing the same DB. If {@link DBCheckpointManager} secretly
     * relied on in-memory state instead of the DB, the new instance's
     * {@code getCheckpoint} / {@code getLatestCheckpoint} would return null and
     * this test would fail.
     */
    @Test
    void crossInstanceSurvivalViaSharedDb() throws Exception {
        // Instance A: save checkpoints for 2 sessions
        DBCheckpointManager mgrA = new DBCheckpointManager(dataSource);

        for (int i = 0; i < 2; i++) {
            mgrA.saveCheckpoint(Checkpoint.of(
                    "sess-cross-1", "wm-cross-1-" + i, i, 1000L + i,
                    CheckpointType.TOOL_EXECUTION,
                    "tool", "call-1-" + i,
                    "input", "output",
                    i + 1, 100L * (i + 1)));
        }

        for (int i = 0; i < 2; i++) {
            mgrA.saveCheckpoint(Checkpoint.of(
                    "sess-cross-2", "wm-cross-2-" + i, i, 2000L + i,
                    CheckpointType.TOOL_EXECUTION,
                    "tool", "call-2-" + i,
                    "input", "output",
                    i + 1, 200L * (i + 1)));
        }

        // Verify rows landed in DB (Anti-Hollow — direct SQL)
        assertEquals(4, countAllCheckpointRows(),
                "saveCheckpoint must have written 4 rows to ai_agent_checkpoint");

        // Discard mgrA entirely — new instance B shares only the DB
        DBCheckpointManager mgrB = new DBCheckpointManager(dataSource);

        // PK lookup on new instance
        Checkpoint wm10 = mgrB.getCheckpoint("wm-cross-1-0");
        assertNotNull(wm10, "Cross-instance getCheckpoint must return the persisted checkpoint");
        assertEquals("sess-cross-1", wm10.getSessionId());
        assertEquals(0, wm10.getSeq());

        // getLatestCheckpoint on new instance — returns highest seq
        Checkpoint latest1 = mgrB.getLatestCheckpoint("sess-cross-1");
        assertNotNull(latest1);
        assertEquals(1, latest1.getSeq(), "Cross-instance getLatestCheckpoint must return highest seq");
        assertEquals("wm-cross-1-1", latest1.getWatermark());

        Checkpoint latest2 = mgrB.getLatestCheckpoint("sess-cross-2");
        assertNotNull(latest2);
        assertEquals(1, latest2.getSeq());
        assertEquals("wm-cross-2-1", latest2.getWatermark());
    }

    // ========================================================================
    // Engine wiring: execute → saveCheckpoint → DB table (Minimum Rules #23)
    // ========================================================================

    /**
     * Engine-wiring + Anti-Hollow: execute → intra-execution saveCheckpoint
     * writes to the {@code ai_agent_checkpoint} table → verified by direct SQL.
     * Also verifies cross-instance reload: new manager (sharing DB) retrieves
     * the checkpoint.
     */
    @Test
    void dbBackedEngineExecutionPersistsCheckpointToTable() throws Exception {
        DBSessionStore store = new DBSessionStore(dataSource);
        DBCheckpointManager ckptMgr = new DBCheckpointManager(dataSource);

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(List.of(
                        toolCallResponse("call-db-ckpt", "test-tool", Map.of("x", "y")),
                        finalResponse("done-ckpt"))),
                toolManagerReturning("tool-output"),
                store);
        engine.setCheckpointManager(ckptMgr);

        AgentExecutionResult result = engine.execute(
                        new AgentMessageRequest("test-react-agent", "run", "db-ckpt-session", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus());

        // Anti-Hollow: verify checkpoint rows actually landed in the DB table
        assertTrue(countCheckpointRowsForSession("db-ckpt-session") >= 1,
                "execute must have persisted at least 1 checkpoint to ai_agent_checkpoint via dispatch-loop saveCheckpoint");

        // The checkpoints are retrievable from the manager. With plan 187,
        // LLM_TURN checkpoints are emitted too, so find the TOOL_EXECUTION one.
        List<Checkpoint> all = ckptMgr.getCheckpoints("db-ckpt-session");
        assertFalse(all.isEmpty());
        Checkpoint toolCp = all.stream()
                .filter(c -> c.getType() == CheckpointType.TOOL_EXECUTION)
                .findFirst().orElse(null);
        assertNotNull(toolCp);
        assertEquals("test-tool", toolCp.getToolName());
        assertEquals("call-db-ckpt", toolCp.getCallId());

        // Cross-instance: new manager sharing DB retrieves the same checkpoint
        DBCheckpointManager mgrB = new DBCheckpointManager(dataSource);
        Checkpoint reloaded = mgrB.getCheckpoint(toolCp.getWatermark());
        assertNotNull(reloaded, "Cross-instance reload must find the checkpoint in the DB");
        assertEquals("call-db-ckpt", reloaded.getCallId());
        assertEquals("tool-output", reloaded.getOutputSummary());
    }

    // ========================================================================
    // restoreSession with DB-backed checkpoint (plan 183 path)
    // ========================================================================

    /**
     * End-to-end Anti-Hollow: engine.execute → checkpoint persists to DB →
     * simulate crash (new engine + new store + new checkpoint manager sharing
     * same DB) → restoreSession loads session from DB + getLatestCheckpoint
     * loads resume-point from DB → ReAct loop resumes. Verified by direct SQL
     * on the {@code ai_agent_checkpoint} table.
     */
    @Test
    void restoreSessionUsesDbBackedCheckpointAndPersistsToTable() throws Exception {
        String sessionId = "db-restore-ckpt-1";

        DBSessionStore store1 = new DBSessionStore(dataSource);
        DBCheckpointManager ckptMgr1 = new DBCheckpointManager(dataSource);

        ScriptedChatService chat1 = new ScriptedChatService(List.of(
                toolCallResponse("call-restore-ckpt", "test-calculator", Map.of("expr", "2+2")),
                finalResponse("The result is 4.")));

        DefaultAgentEngine engine1 = new DefaultAgentEngine(chat1, toolManagerReturning("4"), store1);
        engine1.setCheckpointManager(ckptMgr1);

        engine1.execute(new AgentMessageRequest("test-react-agent", "What is 2+2?", sessionId, null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        // Verify checkpoint landed in DB (Anti-Hollow)
        assertTrue(countCheckpointRowsForSession(sessionId) >= 1,
                "execute must have persisted checkpoint to ai_agent_checkpoint");

        // Simulate crash: discard engine1 + store1 + ckptMgr1 entirely.
        // New engine2 + store2 + ckptMgr2 share the same DB.
        DBSessionStore store2 = new DBSessionStore(dataSource);
        DBCheckpointManager ckptMgr2 = new DBCheckpointManager(dataSource);

        // Verify cross-instance checkpoint read (the checkpoint is in DB, not memory).
        // With plan 187, LLM_TURN checkpoints are also emitted, so the latest
        // may be an LLM_TURN; find the TOOL_EXECUTION one to verify the
        // calculator tool-call payload persisted across instances.
        List<Checkpoint> persistedAll = ckptMgr2.getCheckpoints(sessionId);
        assertFalse(persistedAll.isEmpty(), "Checkpoint must survive across instances (DB-persisted)");
        Checkpoint persistedTool = persistedAll.stream()
                .filter(c -> c.getType() == CheckpointType.TOOL_EXECUTION)
                .findFirst().orElse(null);
        assertNotNull(persistedTool, "A TOOL_EXECUTION checkpoint must survive across instances");
        assertEquals("test-calculator", persistedTool.getToolName());

        // Revert status to running to simulate crash-mid-execution
        io.nop.ai.agent.session.AgentSession crashedSession = store2.get(sessionId);
        assertNotNull(crashedSession);
        crashedSession.setStatus(AgentExecStatus.running);
        store2.save(crashedSession);

        ScriptedChatService chat2 = new ScriptedChatService(List.of(
                finalResponse("Resumed after crash: the answer is still 4.")));

        DefaultAgentEngine engine2 = new DefaultAgentEngine(chat2, toolManagerReturning("ok"), store2);
        engine2.setCheckpointManager(ckptMgr2);

        AgentExecutionResult result2 = engine2.restoreSession(sessionId, "operator", "crash recovery")
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result2.getStatus(),
                "Restored execution must complete from DB-backed checkpoint + session store");
    }

    // ========================================================================
    // Backward compatibility: NoOp / FileBacked / InMemory defaults unchanged
    // ========================================================================

    /**
     * Backward-compat: an engine with {@link NoOpCheckpoint} (shipped default)
     * executes normally and writes nothing to the {@code ai_agent_checkpoint}
     * table — the DB-backed manager is opt-in, never automatic.
     */
    @Test
    void noOpDefaultWritesNoCheckpointRows() throws Exception {
        // Initialize both schemas so the tables exist for verification queries
        new DBCheckpointManager(dataSource);

        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(List.of(
                        toolCallResponse("call-noop-ckpt", "echo", Map.of("x", "y")),
                        finalResponse("noop-done"))),
                toolManagerReturning("ok"),
                new InMemorySessionStore());

        assertTrue(engine.getCheckpointManager() instanceof NoOpCheckpoint,
                "Default must remain NoOpCheckpoint");

        engine.execute(new AgentMessageRequest("test-react-agent", "hi", "noop-ckpt-session", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(0, countAllCheckpointRows(),
                "NoOpCheckpoint default must never write to ai_agent_checkpoint");
    }

    /**
     * Backward-compat: an engine with {@link FileBackedCheckpointManager}
     * executes normally and writes nothing to the {@code ai_agent_checkpoint}
     * table — each manager backend is independent.
     */
    @Test
    void fileBackedManagerWritesNoDbRows() throws Exception {
        new DBCheckpointManager(dataSource);

        FileBackedCheckpointManager fileMgr = new FileBackedCheckpointManager(tempDir.resolve("fb-ckpt"));
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new ScriptedChatService(List.of(
                        toolCallResponse("call-fb-ckpt", "echo", Map.of("x", "y")),
                        finalResponse("fb-done"))),
                toolManagerReturning("ok"),
                new InMemorySessionStore());
        engine.setCheckpointManager(fileMgr);

        engine.execute(new AgentMessageRequest("test-react-agent", "hi", "fb-ckpt-session", null))
                .toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertEquals(0, countAllCheckpointRows(),
                "FileBackedCheckpointManager must never write to ai_agent_checkpoint");

        // But the file-backed manager DID save (to files, not DB)
        assertNotNull(fileMgr.getLatestCheckpoint("fb-ckpt-session"));
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

    private int countAllCheckpointRows() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentCheckpointTable.TABLE_NAME)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countCheckpointRowsForSession(String sessionId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentCheckpointTable.TABLE_NAME
                             + " WHERE " + AiAgentCheckpointTable.COL_SESSION_ID + " = '" + sessionId + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
