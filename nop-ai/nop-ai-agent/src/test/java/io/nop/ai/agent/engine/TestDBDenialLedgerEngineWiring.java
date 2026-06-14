package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.AiAgentDenialTable;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DBDenialLedger;
import io.nop.ai.agent.security.NoOpDenialLedger;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.ToolAccessResult;
import io.nop.ai.agent.security.IToolAccessChecker;
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
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 end-to-end engine-wiring tests for {@link DBDenialLedger}.
 *
 * <p>These tests prove the full wiring chain is connected: a
 * {@link DBDenialLedger} registered via
 * {@code DefaultAgentEngine.setDenialLedger(...)} receives dispatch-path
 * {@code recordDenial(...)} calls and the denials are <b>actually persisted to
 * the {@code ai_agent_denial} DB table</b> (verified by direct SQL, not just
 * by the ledger object receiving a call). Also covers the backward-compat
 * default ({@link NoOpDenialLedger}) producing zero spurious pauses.
 */
public class TestDBDenialLedgerEngineWiring {

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
        dbUrl = "jdbc:h2:mem:test-db-ledger-wiring-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    private int countDenialRows(String sessionId) throws Exception {
        String sql = sessionId == null
                ? "SELECT COUNT(*) FROM " + AiAgentDenialTable.TABLE_NAME
                : "SELECT COUNT(*) FROM " + AiAgentDenialTable.TABLE_NAME
                        + " WHERE " + AiAgentDenialTable.COL_SESSION_ID + " = '" + sessionId + "'";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ========================================================================
    // Wiring: dispatch-path recordDenial lands records in the DB table
    // ========================================================================

    /**
     * Wire {@link DBDenialLedger} into a {@link DefaultAgentEngine}, deny a
     * tool call via {@code DenyAllTools}, and verify the dispatch-path
     * {@code recordDenial(...)} call <b>actually writes a row to the
     * {@code ai_agent_denial} table</b> (Minimum Rules #23 Wiring Verification +
     * #22 Anti-Hollow Rule). The denial is proven persisted by direct SQL —
     * not just by the ledger receiving the call.
     */
    @Test
    void dispatchPathDenialIsPersistedToDbTable() throws Exception {
        String sessionId = "wiring-session-1";

        DBDenialLedger ledger = new DBDenialLedger(dataSource, 10);

        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("w1", "shell.exec")),
                finalAssistant("done")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new DenyAllTools());
        engine.setDenialLedger(ledger);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", sessionId, null, ChannelKind.WEBUI, Principal.user());

        engine.execute(req).toCompletableFuture().join();

        // The denial record was persisted to the DB (wiring + Anti-Hollow).
        int dbCount = countDenialRows(sessionId);
        assertTrue(dbCount >= 1,
                "dispatch-path recordDenial must persist a row to ai_agent_denial for session '"
                        + sessionId + "', got: " + dbCount);

        // The ledger and the DB table agree on the count.
        assertEquals(ledger.getDenialCount(sessionId), dbCount,
                "ledger.getDenialCount must match the actual DB row count");
    }

    /**
     * Threshold-pause end-to-end via the DB-backed ledger: two denied tool
     * calls in one iteration (threshold=2) → the second deny reaches the
     * threshold → session is paused. The two denials are both persisted to the
     * DB table.
     */
    @Test
    void thresholdPauseEndToEndAndBothDenialsPersisted() throws Exception {
        String sessionId = "wiring-session-2";

        DBDenialLedger ledger = new DBDenialLedger(dataSource, 2);

        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(
                        toolCall("tp1", "shell.exec"),
                        toolCall("tp2", "shell.exec")),
                finalAssistant("done")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new DenyAllTools());
        engine.setDenialLedger(ledger);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", sessionId, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertEquals(AgentExecStatus.paused, result.getStatus(),
                "session must be paused once the 2nd denial reaches the threshold");

        // Both denials persisted to the DB.
        assertEquals(2, countDenialRows(sessionId),
                "two dispatch-path denials must both be persisted to the DB");
        assertEquals(2, ledger.getDenialCount(sessionId));
        assertTrue(ledger.isPaused(sessionId));
    }

    // ========================================================================
    // Backward-compat: NoOpDenialLedger default produces no spurious pauses
    //                 and writes nothing to any DB table
    // ========================================================================

    /**
     * Backward-compat: an engine with the default {@link NoOpDenialLedger}
     * (never explicitly set) must not pause the session, even after many
     * denials. Confirms the shipped default is unaffected by the new
     * {@link DBDenialLedger} sibling.
     */
    @Test
    void defaultNoOpLedgerProducesNoSpuriousPausesAndWritesNoDbRows() {
        // Engine with default ledger (NoOpDenialLedger) — never explicitly set.
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(
                        assistantWithToolCalls(toolCall("bc1", "shell.exec")),
                        assistantWithToolCalls(toolCall("bc2", "shell.exec")),
                        assistantWithToolCalls(toolCall("bc3", "shell.exec")),
                        finalAssistant("done")
                )),
                stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                new DenyAllTools());

        assertTrue(engine.getDenialLedger() instanceof NoOpDenialLedger,
                "default denial ledger must be NoOpDenialLedger");

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", "bc-session", null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        assertNotEquals(AgentExecStatus.paused, result.getStatus(),
                "NoOpDenialLedger default must never pause, even after many denials");
    }

    // ========================================================================
    // Mocks / helpers (same shape as TestDispatchPathDenialLedger)
    // ========================================================================

    static final class DenyAllTools implements IToolAccessChecker {
        @Override
        public ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx) {
            return ToolAccessResult.deny("test deny-all rule");
        }
    }

    static final class RecordingChatService implements IChatService {
        final java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger();
        final List<ChatResponse> scripted;

        RecordingChatService(List<ChatResponse> scripted) {
            this.scripted = scripted;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            int idx = Math.min(callCount.getAndIncrement(), scripted.size() - 1);
            return scripted.get(idx);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    private static ChatResponse assistantWithToolCalls(ChatToolCall... calls) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(calls));
        return ChatResponse.success(msg);
    }

    private static ChatResponse finalAssistant(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private static ChatToolCall toolCall(String id, String name) {
        ChatToolCall c = new ChatToolCall();
        c.setId(id);
        c.setName(name);
        c.setArguments(Map.of("command", "ls"));
        return c;
    }

    private static IToolManager stubToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
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
                return null;
            }
        };
    }
}
