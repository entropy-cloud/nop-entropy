package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.runtime.lock.AiAgentSessionLockTable;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 229 Phase 2 focused tests for {@link DefaultSessionTimeoutHandler}.
 * Each three-way classification branch (LOCAL_CANCELLED /
 * LOCAL_CANCELLED-exception / FORCE_FAILED / FORCE_FAILED-transitioned /
 * SKIPPED_REMOTE / expired-lock-to-FORCE_FAILED) and the constructor
 * fail-fast contract are verified against a recording engine stub
 * (LOCAL_CANCELLED) and a real H2 DB (FORCE_FAILED / classification
 * SELECT), satisfying Minimum Rules #22 (Anti-Hollow), #23 (Wiring
 * Verification), and #24 (No Silent No-Op).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #localCancelledDelegatesToCancelSessionForced} — LOCAL_CANCELLED via active local lock</li>
 *   <li>{@link #localCancelledExceptionReturnsFailedOutcome} — cancelSession sync exception → failed outcome</li>
 *   <li>{@link #forceFailedUpdatesStatusForOrphanedSession} — FORCE_FAILED via no lock row</li>
 *   <li>{@link #forceFailedAlreadyTransitionedReturnsFailedOutcome} — affected rows=0 → failed outcome</li>
 *   <li>{@link #skippedRemoteLeavesDbUnchanged} — SKIPPED_REMOTE via remote owner</li>
 *   <li>{@link #expiredLockRoutesToForceFailed} — expired lock treated as no active lock</li>
 *   <li>{@link #constructorValidatesArguments} — fail-fast on null deps / non-positive timeoutSeconds</li>
 * </ul>
 */
public class TestDefaultSessionTimeoutHandler {

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
        dbUrl = "jdbc:h2:mem:test-timeout-handler-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
        createTables();
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

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);
            stmt.execute(AiAgentSessionLockTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create test tables", e);
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

    private void insertLockRow(String sessionId, String owner, long expiresAt) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO " + AiAgentSessionLockTable.TABLE_NAME
                    + " (" + AiAgentSessionLockTable.COL_SESSION_ID
                    + ", " + AiAgentSessionLockTable.COL_LOCK_OWNER
                    + ", " + AiAgentSessionLockTable.COL_LOCK_ACQUIRED_AT
                    + ", " + AiAgentSessionLockTable.COL_LOCK_EXPIRES_AT
                    + ") VALUES ('" + sessionId + "', '" + owner + "', 0, " + expiresAt + ")");
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
    // LOCAL_CANCELLED
    // ========================================================================

    @Test
    void localCancelledDelegatesToCancelSessionForced() throws Exception {
        String instanceId = "this-instance";
        RecordingAgentEngine engine = new RecordingAgentEngine(
                sid -> CompletableFuture.completedFuture(null));
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, engine, dataSource, instanceId);

        insertSession("local-1", "running");
        // Active lock owned by this instance → LOCAL_CANCELLED branch.
        insertLockRow("local-1", instanceId, System.currentTimeMillis() + 60_000L);

        TimeoutOutcome outcome = handler.handleTimeout("local-1");

        // Wiring Verification (#23): the engine.cancelSession must be
        // called at runtime with forced=true (verified by the recording
        // engine capturing the args).
        assertEquals(1, engine.cancelCount.get(),
                "LOCAL_CANCELLED must invoke engine.cancelSession exactly once");
        assertEquals("local-1", engine.lastCancelSessionId.get());
        assertEquals("timeout", engine.lastCancelReason.get());
        assertTrue(engine.lastCancelForced.get(),
                "cancelSession must be called with forced=true (graceful + thread interrupt)");
        assertEquals(TimeoutAction.LOCAL_CANCELLED, outcome.getAction());
        assertTrue(outcome.isSucceeded(),
                "LOCAL_CANCELLED without exception must return succeeded=true");
        assertEquals("local-1", outcome.getSessionId());
        // The DB session status is NOT mutated by the handler directly —
        // the cancelSession path (plan 197) is responsible for the
        // cancelled/forced_stopped transition. We assert the handler did
        // not touch the session status here.
        assertEquals("running", getSessionStatus("local-1"),
                "LOCAL_CANCELLED handler must NOT mutate DB status directly "
                        + "(the cancelSession path owns the terminal transition)");
    }

    @Test
    void localCancelledExceptionReturnsFailedOutcome() throws Exception {
        String instanceId = "this-instance";
        // cancelSession throws synchronously (e.g. session not found /
        // already terminal / engine error).
        RecordingAgentEngine engine = new RecordingAgentEngine(sid -> {
            throw new NopAiAgentException("cancelSession failed: session not found");
        });
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, engine, dataSource, instanceId);

        insertSession("local-err", "running");
        insertLockRow("local-err", instanceId, System.currentTimeMillis() + 60_000L);

        TimeoutOutcome outcome = handler.handleTimeout("local-err");

        assertEquals(1, engine.cancelCount.get(),
                "cancelSession must have been invoked (and threw)");
        assertFalse(outcome.isSucceeded(),
                "LOCAL_CANCELLED with a sync exception must return succeeded=false (non-silent)");
        assertEquals(TimeoutAction.LOCAL_CANCELLED, outcome.getAction());
        assertNotNull(outcome.getMessage());
        assertTrue(outcome.getMessage().contains("cancelSession failed"),
                "message must contain the exception detail");
    }

    // ========================================================================
    // FORCE_FAILED
    // ========================================================================

    @Test
    void forceFailedUpdatesStatusForOrphanedSession() throws Exception {
        String instanceId = "this-instance";
        RecordingAgentEngine engine = new RecordingAgentEngine(
                sid -> CompletableFuture.completedFuture(null));
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, engine, dataSource, instanceId);

        // Running session with NO lock row → orphaned → FORCE_FAILED branch.
        insertSession("orphan-force", "running");

        TimeoutOutcome outcome = handler.handleTimeout("orphan-force");

        assertEquals(TimeoutAction.FORCE_FAILED, outcome.getAction());
        assertTrue(outcome.isSucceeded(),
                "FORCE_FAILED of a running session must return succeeded=true");
        // Wiring Verification (#23): the DB status must actually be
        // updated to 'failed' (raw JDBC conditional UPDATE).
        assertEquals("failed", getSessionStatus("orphan-force"),
                "DB status must be updated to 'failed' by FORCE_FAILED");
        // The engine.cancelSession must NOT have been called (this is the
        // FORCE_FAILED branch, not LOCAL_CANCELLED).
        assertEquals(0, engine.cancelCount.get(),
                "FORCE_FAILED must NOT call engine.cancelSession");
    }

    @Test
    void forceFailedAlreadyTransitionedReturnsFailedOutcome() throws Exception {
        String instanceId = "this-instance";
        RecordingAgentEngine engine = new RecordingAgentEngine(
                sid -> CompletableFuture.completedFuture(null));
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, engine, dataSource, instanceId);

        // A completed session: the conditional WHERE (STATUS IN
        // running,pending) matches zero rows → affected rows=0.
        insertSession("already-done", "completed");

        TimeoutOutcome outcome = handler.handleTimeout("already-done");

        assertFalse(outcome.isSucceeded(),
                "FORCE_FAILED of an already-transitioned session must return succeeded=false (non-silent)");
        assertEquals(TimeoutAction.FORCE_FAILED, outcome.getAction());
        assertNotNull(outcome.getMessage());
        assertTrue(outcome.getMessage().contains("transitioned"),
                "message must explain the session already transitioned");
        assertEquals("completed", getSessionStatus("already-done"),
                "DB status must remain 'completed' (untouched by conditional WHERE)");
    }

    // ========================================================================
    // SKIPPED_REMOTE
    // ========================================================================

    @Test
    void skippedRemoteLeavesDbUnchanged() throws Exception {
        String instanceId = "this-instance";
        RecordingAgentEngine engine = new RecordingAgentEngine(
                sid -> CompletableFuture.completedFuture(null));
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, engine, dataSource, instanceId);

        insertSession("remote-1", "running");
        // Active lock owned by a DIFFERENT instance → SKIPPED_REMOTE branch.
        insertLockRow("remote-1", "other-instance", System.currentTimeMillis() + 60_000L);

        TimeoutOutcome outcome = handler.handleTimeout("remote-1");

        assertEquals(TimeoutAction.SKIPPED_REMOTE, outcome.getAction());
        assertTrue(outcome.isSucceeded(),
                "SKIPPED_REMOTE is a deliberate non-intervention decision (succeeded=true)");
        assertNotNull(outcome.getMessage());
        assertTrue(outcome.getMessage().contains("other-instance"),
                "message must contain the remote LOCK_OWNER for observability");
        // Wiring Verification (#23): the DB status must be UNCHANGED
        // (SKIPPED_REMOTE does not intervene).
        assertEquals("running", getSessionStatus("remote-1"),
                "SKIPPED_REMOTE must NOT mutate DB status (no intervention)");
        assertEquals(0, engine.cancelCount.get(),
                "SKIPPED_REMOTE must NOT call engine.cancelSession");
    }

    // ========================================================================
    // Expired lock → FORCE_FAILED
    // ========================================================================

    @Test
    void expiredLockRoutesToForceFailed() throws Exception {
        String instanceId = "this-instance";
        RecordingAgentEngine engine = new RecordingAgentEngine(
                sid -> CompletableFuture.completedFuture(null));
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, engine, dataSource, instanceId);

        insertSession("expired-lock", "running");
        // An expired lock row (LOCK_EXPIRES_AT <= now) — treated as
        // equivalent to no active lock → FORCE_FAILED branch.
        insertLockRow("expired-lock", instanceId, System.currentTimeMillis() - 1_000L);

        TimeoutOutcome outcome = handler.handleTimeout("expired-lock");

        assertEquals(TimeoutAction.FORCE_FAILED, outcome.getAction(),
                "An expired lock row must route to FORCE_FAILED (treated as no active lock)");
        assertTrue(outcome.isSucceeded(),
                "FORCE_FAILED must succeed against a running orphan with an expired lock");
        assertEquals("failed", getSessionStatus("expired-lock"),
                "DB status must be updated to 'failed'");
        assertEquals(0, engine.cancelCount.get(),
                "FORCE_FAILED must NOT call engine.cancelSession");
    }

    // ========================================================================
    // Constructor fail-fast (Minimum Rules #24)
    // ========================================================================

    @Test
    void constructorValidatesArguments() {
        RecordingAgentEngine engine = new RecordingAgentEngine(
                sid -> CompletableFuture.completedFuture(null));
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultSessionTimeoutHandler(0L, engine, dataSource, "id"),
                "timeoutSeconds=0 must fail-fast");
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultSessionTimeoutHandler(-1L, engine, dataSource, "id"),
                "negative timeoutSeconds must fail-fast");
        assertThrows(NullPointerException.class,
                () -> new DefaultSessionTimeoutHandler(60L, null, dataSource, "id"),
                "null engine must fail-fast (NPE)");
        assertThrows(NullPointerException.class,
                () -> new DefaultSessionTimeoutHandler(60L, engine, null, "id"),
                "null dataSource must fail-fast (NPE)");
        assertThrows(NullPointerException.class,
                () -> new DefaultSessionTimeoutHandler(60L, engine, dataSource, null),
                "null instanceId must fail-fast (NPE)");
    }

    // ========================================================================
    // Minimal recording IAgentEngine stub for LOCAL_CANCELLED-wiring tests.
    // Records the cancelSession arguments; the cancelBehaviour consumer
    // decides whether to return normally or throw synchronously.
    // ========================================================================
    static final class RecordingAgentEngine implements IAgentEngine {
        final AtomicInteger cancelCount = new AtomicInteger(0);
        final AtomicReference<String> lastCancelSessionId = new AtomicReference<>();
        final AtomicReference<String> lastCancelReason = new AtomicReference<>();
        final AtomicReference<Boolean> lastCancelForced = new AtomicReference<>();

        private final Consumer<String> cancelBehaviour;

        RecordingAgentEngine(Consumer<String> cancelBehaviour) {
            this.cancelBehaviour = cancelBehaviour;
        }

        @Override
        public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
            cancelCount.incrementAndGet();
            lastCancelSessionId.set(sessionId);
            lastCancelReason.set(reason);
            lastCancelForced.set(forced);
            cancelBehaviour.accept(sessionId);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used in timeout tests");
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used in timeout tests");
        }
    }
}
