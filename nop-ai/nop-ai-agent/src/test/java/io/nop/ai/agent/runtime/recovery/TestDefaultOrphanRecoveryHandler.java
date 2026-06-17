package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.session.AiAgentSessionTable;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 226 Phase 2 focused tests for {@link DefaultOrphanRecoveryHandler}.
 * Each recovery mode (RESUME / RESUME-conflict / ABORT / ABORT-transitioned /
 * SKIP) and the constructor fail-fast contract are verified against a
 * recording engine stub (RESUME) and a real H2 DB (ABORT), satisfying
 * Minimum Rules #22 (Anti-Hollow) and #24 (No Silent No-Op).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #resumeModeTriggersRestoreSession} — RESUME delegates to engine</li>
 *   <li>{@link #resumeModeConflictReturnsFailedOutcome} — RESUME sync exception → failed outcome</li>
 *   <li>{@link #abortModeSetsStatusFailed} — ABORT raw JDBC UPDATE status=failed</li>
 *   <li>{@link #abortModeAlreadyTransitioned} — ABORT affected rows=0 → failed outcome</li>
 *   <li>{@link #skipModeLogsAndReturnsSkipOutcome} — SKIP LOG.warn + SKIP outcome</li>
 *   <li>{@link #resumeModeRequiresEngine} — constructor fail-fast RESUME+null engine</li>
 *   <li>{@link #abortModeRequiresDataSource} — constructor fail-fast ABORT+null ds</li>
 * </ul>
 */
public class TestDefaultOrphanRecoveryHandler {

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
        dbUrl = "jdbc:h2:mem:test-orphan-handler-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
        createSessionTable();
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

    private void createSessionTable() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create ai_agent_session table", e);
        }
    }

    private void insertSession(String sessionId, String status) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("MERGE INTO " + AiAgentSessionTable.TABLE_NAME
                    + " (" + AiAgentSessionTable.COL_SESSION_ID
                    + ", " + AiAgentSessionTable.COL_AGENT_NAME
                    + ", " + AiAgentSessionTable.COL_STATUS
                    + ", " + AiAgentSessionTable.COL_SESSION_DATA
                    + ", " + AiAgentSessionTable.COL_CREATED_AT
                    + ", " + AiAgentSessionTable.COL_UPDATED_AT
                    + ") KEY (" + AiAgentSessionTable.COL_SESSION_ID
                    + ") VALUES ('" + sessionId + "', 'test-agent', '" + status
                    + "', '{}', 0, 0)");
        }
    }

    private String getSessionStatus(String sessionId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT " + AiAgentSessionTable.COL_STATUS
                             + " FROM " + AiAgentSessionTable.TABLE_NAME
                             + " WHERE " + AiAgentSessionTable.COL_SESSION_ID
                             + " = '" + sessionId + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    // ========================================================================
    // RESUME mode
    // ========================================================================

    @Test
    void resumeModeTriggersRestoreSession() {
        RecordingAgentEngine engine = new RecordingAgentEngine(sid ->
                CompletableFuture.completedFuture(null));
        DefaultOrphanRecoveryHandler handler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.RESUME, engine, null);

        RecoveryOutcome outcome = handler.handleOrphan("orphan-resume");

        assertEquals(1, engine.restoreCount.get(),
                "RESUME mode must call engine.restoreSession exactly once");
        assertEquals("orphan-resume", engine.lastSessionId.get(),
                "restoreSession must receive the orphan session ID");
        assertEquals("recovery-daemon", engine.lastApprover.get(),
                "restoreSession approver must be the recovery daemon identity");
        assertEquals(RecoveryMode.RESUME, outcome.getMode());
        assertTrue(outcome.isSucceeded(),
                "RESUME fire-and-forget must return succeeded=true (no exception)");
        assertEquals("orphan-resume", outcome.getSessionId());
    }

    @Test
    void resumeModeConflictReturnsFailedOutcome() {
        // Simulate a takeover-lock conflict: restoreSession throws
        // synchronously (tryAcquire failed / session not found / terminal).
        RecordingAgentEngine engine = new RecordingAgentEngine(sid -> {
            throw new NopAiAgentException(
                    "takeover lock conflict: session already held by another instance");
        });
        DefaultOrphanRecoveryHandler handler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.RESUME, engine, null);

        RecoveryOutcome outcome = handler.handleOrphan("orphan-conflict");

        assertEquals(1, engine.restoreCount.get(),
                "restoreSession must have been invoked (and threw)");
        assertFalse(outcome.isSucceeded(),
                "RESUME conflict must return succeeded=false (non-silent)");
        assertEquals(RecoveryMode.RESUME, outcome.getMode());
        assertNotNull(outcome.getMessage(),
                "failed outcome must carry a descriptive message");
        assertTrue(outcome.getMessage().contains("takeover"),
                "message must contain the exception detail ('takeover')");
    }

    // ========================================================================
    // ABORT mode
    // ========================================================================

    @Test
    void abortModeSetsStatusFailed() throws Exception {
        insertSession("orphan-abort", "running");
        DefaultOrphanRecoveryHandler handler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.ABORT, null, dataSource);

        RecoveryOutcome outcome = handler.handleOrphan("orphan-abort");

        assertTrue(outcome.isSucceeded(),
                "ABORT of a running session must return succeeded=true");
        assertEquals(RecoveryMode.ABORT, outcome.getMode());
        assertEquals("failed", getSessionStatus("orphan-abort"),
                "DB status must be updated to 'failed'");
    }

    @Test
    void abortModeAlreadyTransitioned() throws Exception {
        // A completed session: the conditional WHERE (STATUS IN
        // running,pending) matches zero rows → affected rows=0.
        insertSession("orphan-done", "completed");
        DefaultOrphanRecoveryHandler handler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.ABORT, null, dataSource);

        RecoveryOutcome outcome = handler.handleOrphan("orphan-done");

        assertFalse(outcome.isSucceeded(),
                "ABORT of an already-transitioned session must return succeeded=false (non-silent)");
        assertEquals(RecoveryMode.ABORT, outcome.getMode());
        assertNotNull(outcome.getMessage());
        assertTrue(outcome.getMessage().contains("transitioned"),
                "message must explain the session already transitioned");
        assertEquals("completed", getSessionStatus("orphan-done"),
                "DB status must remain 'completed' (untouched by conditional WHERE)");
    }

    // ========================================================================
    // SKIP mode
    // ========================================================================

    @Test
    void skipModeLogsAndReturnsSkipOutcome() {
        DefaultOrphanRecoveryHandler handler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.SKIP, null, null);

        RecoveryOutcome outcome = handler.handleOrphan("orphan-skip");

        assertEquals(RecoveryMode.SKIP, outcome.getMode());
        assertTrue(outcome.isSucceeded(),
                "SKIP is an observation-only success (not a recovery failure)");
        assertEquals("orphan-skip", outcome.getSessionId());
        assertNotNull(outcome.getMessage());
    }

    // ========================================================================
    // Constructor fail-fast (Minimum Rules #24)
    // ========================================================================

    @Test
    void resumeModeRequiresEngine() {
        assertThrows(NullPointerException.class,
                () -> new DefaultOrphanRecoveryHandler(RecoveryMode.RESUME, null, null),
                "RESUME mode with null engine must fail-fast (NPE)");
    }

    @Test
    void abortModeRequiresDataSource() {
        assertThrows(NullPointerException.class,
                () -> new DefaultOrphanRecoveryHandler(RecoveryMode.ABORT, null, null),
                "ABORT mode with null dataSource must fail-fast (NPE)");
    }

    @Test
    void abortModeAllowsNullEngine() {
        // ABORT does not need an engine; constructing with null engine + a
        // real dataSource must succeed (engine is only required for RESUME).
        DefaultOrphanRecoveryHandler handler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.ABORT, null, dataSource);
        assertEquals(RecoveryMode.ABORT, handler.getMode());
    }

    @Test
    void resumeModeAllowsNullDataSource() {
        // RESUME does not need a dataSource; constructing with null ds + a
        // real engine must succeed.
        DefaultOrphanRecoveryHandler handler = new DefaultOrphanRecoveryHandler(
                RecoveryMode.RESUME, new RecordingAgentEngine(sid -> new CompletableFuture<>()), null);
        assertEquals(RecoveryMode.RESUME, handler.getMode());
    }

    // ========================================================================
    // Minimal recording IAgentEngine stub for RESUME-wiring tests.
    // Records the restoreSession arguments; the restoreBehaviour function
    // decides whether to return a future or throw synchronously.
    // ========================================================================
    static final class RecordingAgentEngine implements IAgentEngine {
        final AtomicInteger restoreCount = new AtomicInteger(0);
        final AtomicReference<String> lastSessionId = new AtomicReference<>();
        final AtomicReference<String> lastApprover = new AtomicReference<>();
        final AtomicReference<String> lastReason = new AtomicReference<>();

        private final Function<String, CompletableFuture<AgentExecutionResult>> restoreBehaviour;

        RecordingAgentEngine(Function<String, CompletableFuture<AgentExecutionResult>> restoreBehaviour) {
            this.restoreBehaviour = restoreBehaviour;
        }

        @Override
        public CompletableFuture<AgentExecutionResult> restoreSession(
                String sessionId, String approver, String reason) {
            restoreCount.incrementAndGet();
            lastSessionId.set(sessionId);
            lastApprover.set(approver);
            lastReason.set(reason);
            return restoreBehaviour.apply(sessionId);
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used in recovery tests");
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used in recovery tests");
        }
    }
}
