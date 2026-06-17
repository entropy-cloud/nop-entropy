package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.runtime.lock.AiAgentSessionLockTable;
import io.nop.ai.agent.session.AiAgentSessionTable;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.commons.lang.IDestroyable;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 222 Phase 2 focused tests for {@link ScheduledRecoveryManager}.
 * Each scanOnce semantic (stale-lock cleanup / orphan detection /
 * active-lock preserved / terminal session excluded) and the
 * start/stop scheduling wiring are verified against a real H2 DB (not a
 * mock), satisfying Minimum Rules #22 (Anti-Hollow) and #23 (Wiring
 * Verification).
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #staleLocksAreCleanedUp} — DELETE expired lock rows + count</li>
 *   <li>{@link #activeLocksArePreserved} — non-expired locks survive</li>
 *   <li>{@link #orphanSessionsAreDetected} — running/pending + no active lock</li>
 *   <li>{@link #terminalSessionsAreExcluded} — completed session not orphan</li>
 *   <li>{@link #sessionWithActiveLockIsNotOrphan} — running + active lock = not orphan</li>
 *   <li>{@link #sessionWithExpiredLockIsOrphan} — expired lock cleaned then orphan</li>
 *   <li>{@link #endToEndStaleLockPlusOrphan} — full scanOnce result fields</li>
 *   <li>{@link #scanResultFieldsPopulated} — scannedAt/duration observable</li>
 *   <li>{@link #startRegistersPeriodicTaskOnScheduler} — start wiring</li>
 *   <li>{@link #startUsesConfiguredScanInterval} — custom interval wiring</li>
 *   <li>{@link #stopCancelsRegisteredHandle} — stop wiring</li>
 *   <li>{@link #startAndStopAreIdempotent} — lifecycle idempotency</li>
 *   <li>{@link #stopBeforeStartIsNoOp} — lifecycle safety</li>
 *   <li>{@link #scheduledTaskRunsScanOnce} — Anti-Hollow wiring</li>
 *   <li>{@link #noOpDefaultReturnsAllZeroResult} — NoOp default semantic</li>
 *   <li>{@link #constructorValidatesArguments} — input contract</li>
 * </ul>
 */
public class TestScheduledRecoveryManager {

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
        dbUrl = "jdbc:h2:mem:test-recovery-mgr-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    // Helper: insert a session row with a given status.
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

    // Helper: insert a lock row directly with explicit expiry.
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

    private int countAllLockRows() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentSessionLockTable.TABLE_NAME)) {
            rs.next();
            return rs.getInt(1);
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

    private ScheduledRecoveryManager newManager() {
        return new ScheduledRecoveryManager(dataSource, new RecordingScheduler());
    }

    // ========================================================================
    // scanOnce — stale lock cleanup (idempotent DELETE)
    // ========================================================================

    @Test
    void staleLocksAreCleanedUp() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        long now = System.currentTimeMillis();
        // Two stale (expired) lock rows + one active lock row.
        insertLockRow("stale-1", "owner-A", now - 1000L);
        insertLockRow("stale-2", "owner-B", now - 1L);
        insertLockRow("active-1", "owner-C", now + 60_000L);
        assertEquals(3, countAllLockRows(), "precondition: 3 lock rows inserted");

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(2, result.getStaleLocksCleaned(),
                "staleLocksCleaned must count the 2 expired rows deleted");
        assertEquals(1, countAllLockRows(),
                "only the active lock row must remain after cleanup");
    }

    @Test
    void activeLocksArePreserved() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        long now = System.currentTimeMillis();
        insertLockRow("active-future", "owner-A", now + 60_000L);

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(0, result.getStaleLocksCleaned(),
                "no expired locks → nothing cleaned");
        assertEquals(1, countAllLockRows(),
                "active (non-expired) lock row must NOT be deleted");
    }

    // ========================================================================
    // scanOnce — orphan session detection
    // ========================================================================

    @Test
    void orphanSessionsAreDetected() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        // running session with NO active lock → orphan.
        insertSession("orphan-running", "running");
        // pending session with NO active lock → orphan.
        insertSession("orphan-pending", "pending");

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(2, result.getOrphanSessionsDetected(),
                "both running and pending sessions without a lock are orphans");
        assertTrue(result.getOrphanSessionIds().contains("orphan-running"),
                "orphanSessionIds must contain the running orphan");
        assertTrue(result.getOrphanSessionIds().contains("orphan-pending"),
                "orphanSessionIds must contain the pending orphan");
    }

    @Test
    void terminalSessionsAreExcluded() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        insertSession("completed-session", "completed");
        insertSession("failed-session", "failed");
        insertSession("cancelled-session", "cancelled");

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(0, result.getOrphanSessionsDetected(),
                "terminal sessions are never orphans");
        assertTrue(result.getOrphanSessionIds().isEmpty(),
                "no orphan ids for terminal-only sessions");
    }

    @Test
    void sessionWithActiveLockIsNotOrphan() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        long now = System.currentTimeMillis();
        insertSession("running-locked", "running");
        // An active (non-expired) lock protects the session → not an orphan.
        insertLockRow("running-locked", "owner-A", now + 60_000L);

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(0, result.getOrphanSessionsDetected(),
                "a running session with an active lock is not an orphan");
    }

    @Test
    void sessionWithExpiredLockIsOrphan() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        long now = System.currentTimeMillis();
        insertSession("running-expired-lock", "running");
        // Only an expired lock exists → after cleanup it has no active lock.
        insertLockRow("running-expired-lock", "owner-A", now - 1000L);

        RecoveryScanResult result = mgr.scanOnce();

        // The expired lock is cleaned up first, then orphan detection runs
        // against the (now lock-free) session.
        assertEquals(1, result.getStaleLocksCleaned(),
                "expired lock is cleaned up");
        assertEquals(1, result.getOrphanSessionsDetected(),
                "after cleanup, the session has no active lock → orphan");
        assertEquals("running-expired-lock", result.getOrphanSessionIds().get(0));
    }

    // ========================================================================
    // scanOnce — result fields / E2E
    // ========================================================================

    @Test
    void scanResultFieldsPopulated() {
        ScheduledRecoveryManager mgr = newManager();
        long before = System.currentTimeMillis();
        RecoveryScanResult result = mgr.scanOnce();
        long after = System.currentTimeMillis();

        assertNotNull(result, "scanOnce must return a non-null result");
        assertTrue(result.getScannedAt() >= before && result.getScannedAt() <= after,
                "scannedAt must be a current epoch ms timestamp");
        assertTrue(result.getScanDurationMs() >= 0,
                "scanDurationMs must be non-negative");
        // Empty DB → all-zero counts but a real timestamp.
        assertEquals(0, result.getStaleLocksCleaned());
        assertEquals(0, result.getOrphanSessionsDetected());
        assertTrue(result.getOrphanSessionIds().isEmpty());
    }

    @Test
    void endToEndStaleLockPlusOrphan() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        long now = System.currentTimeMillis();
        // Stale lock (orphaned by a crashed holder).
        insertLockRow("crashed-holder", "dead-owner", now - 5_000L);
        // Orphan session: running, no lock.
        insertSession("orphan-1", "running");
        // Active, healthy session+lock — must be untouched.
        insertSession("healthy", "running");
        insertLockRow("healthy", "alive-owner", now + 60_000L);
        // Terminal session — excluded.
        insertSession("done", "completed");

        RecoveryScanResult result = mgr.scanOnce();

        // End-to-end: stale lock deleted, orphan detected, active lock preserved,
        // terminal session excluded.
        assertEquals(1, result.getStaleLocksCleaned(),
                "the single stale lock row is cleaned");
        assertEquals(1, result.getOrphanSessionsDetected(),
                "only the lock-free running session is an orphan (healthy is locked, done is terminal)");
        assertEquals("orphan-1", result.getOrphanSessionIds().get(0));
        assertEquals(1, countAllLockRows(),
                "only the healthy active lock row remains");
        // Observable duration + timestamp.
        assertTrue(result.getScanDurationMs() >= 0);
        assertTrue(result.getScannedAt() > 0);
    }

    // ========================================================================
    // start / stop — scheduling wiring (Wiring Verification #23)
    // ========================================================================

    @Test
    void startRegistersPeriodicTaskOnScheduler() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);

        mgr.start();

        assertNotNull(scheduler.lastCommand.get(),
                "start() must register a task via scheduleWithFixedDelay");
        assertEquals(ScheduledRecoveryManager.DEFAULT_SCAN_INTERVAL_SEC,
                scheduler.lastDelay.get().longValue(),
                "start() must use the default 60s delay");
        assertEquals(TimeUnit.SECONDS, scheduler.lastUnit.get(),
                "delay unit must be SECONDS");
        assertFalse(scheduler.cancelled.get(),
                "the registered handle must NOT be cancelled right after start");
    }

    @Test
    void startUsesConfiguredScanInterval() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler, 15L);

        mgr.start();

        assertEquals(15L, scheduler.lastDelay.get().longValue(),
                "start() must use the configured 15s delay");
    }

    @Test
    void stopCancelsRegisteredHandle() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);
        mgr.start();
        assertFalse(scheduler.cancelled.get(), "precondition: handle not yet cancelled");

        mgr.stop();

        assertTrue(scheduler.cancelled.get(),
                "stop() must cancel the registered Future handle");
        assertFalse(scheduler.mayInterruptIfRunning,
                "cancel must use mayInterruptIfRunning=false (best-effort)");
    }

    @Test
    void startAndStopAreIdempotent() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);

        // Double start → exactly one scheduleWithFixedDelay call.
        mgr.start();
        mgr.start();
        assertEquals(1, scheduler.scheduleCount.get(),
                "idempotent start: repeated start must not register a second task");

        // Double stop → handle cancelled, no exception.
        mgr.stop();
        mgr.stop();
        assertTrue(scheduler.cancelled.get(),
                "idempotent stop: handle is cancelled");

        // Restart after stop → registers again.
        scheduler.scheduleCount.set(0);
        scheduler.cancelled.set(false);
        mgr.start();
        assertEquals(1, scheduler.scheduleCount.get(),
                "start after stop re-registers the task");
        mgr.stop();
    }

    @Test
    void stopBeforeStartIsNoOp() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);
        // stop() without a prior start() must not throw and must not cancel anything.
        mgr.stop();
        assertFalse(scheduler.cancelled.get(),
                "stop before start is a no-op (no handle to cancel)");
    }

    // ========================================================================
    // scheduled task actually invokes scanOnce at runtime (Anti-Hollow #22)
    // ========================================================================

    @Test
    void scheduledTaskRunsScanOnce() throws Exception {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);
        insertLockRow("stale-x", "owner", System.currentTimeMillis() - 1000L);

        mgr.start();
        // The wiring is verified by executing the registered Runnable and
        // checking it actually performed the cleanup (not just that a
        // command object was registered).
        Runnable registered = scheduler.lastCommand.get();
        assertNotNull(registered);
        registered.run();

        assertEquals(0, countAllLockRows(),
                "the registered periodic task must actually run scanOnce and clean stale locks");
        mgr.stop();
    }

    // ========================================================================
    // NoOp default semantic (Minimum Rules #24 — explicit, not silent)
    // ========================================================================

    @Test
    void noOpDefaultReturnsAllZeroResult() {
        NoOpRecoveryManager noop = NoOpRecoveryManager.noOp();
        // start/stop are no-ops (must not throw).
        noop.start();
        noop.stop();
        RecoveryScanResult result = noop.scanOnce();
        assertNotNull(result, "NoOp scanOnce must return a non-null result");
        assertEquals(0, result.getStaleLocksCleaned());
        assertEquals(0, result.getOrphanSessionsDetected());
        assertTrue(result.getOrphanSessionIds().isEmpty());
        assertEquals(0L, result.getScanDurationMs());
        assertEquals(0L, result.getScannedAt());
    }

    @Test
    void noOpIsSingleton() {
        assertSame(NoOpRecoveryManager.noOp(), NoOpRecoveryManager.noOp(),
                "NoOpRecoveryManager.noOp() is a singleton");
    }

    // ========================================================================
    // Constructor argument validation (fail-fast, no silent no-op)
    // ========================================================================

    @Test
    void constructorValidatesArguments() {
        RecordingScheduler scheduler = new RecordingScheduler();
        assertThrows(NopAiAgentException.class,
                () -> new ScheduledRecoveryManager(null, scheduler),
                "null dataSource must fail-fast");
        assertThrows(NopAiAgentException.class,
                () -> new ScheduledRecoveryManager(dataSource, null),
                "null scheduledExecutor must fail-fast");
        assertThrows(NopAiAgentException.class,
                () -> new ScheduledRecoveryManager(dataSource, scheduler, 0L),
                "scanIntervalSec=0 must fail-fast");
        assertThrows(NopAiAgentException.class,
                () -> new ScheduledRecoveryManager(dataSource, scheduler, -1L),
                "negative scanIntervalSec must fail-fast");
    }

    // ========================================================================
    // Orphan recovery handler integration (plan 226 / L4-8-P4-RecoveryStrategy)
    // ========================================================================

    @Test
    void defaultOrphanHandlerIsNoOp() {
        ScheduledRecoveryManager mgr = newManager();
        // The shipped default handler must be NoOpOrphanRecoveryHandler
        // (SKIP mode, zero regression with plan 222).
        assertTrue(mgr.getOrphanRecoveryHandler() instanceof NoOpOrphanRecoveryHandler,
                "shipped default orphan handler must be NoOpOrphanRecoveryHandler");
    }

    @Test
    void setOrphanRecoveryHandlerRejectsNull() {
        ScheduledRecoveryManager mgr = newManager();
        assertThrows(NopAiAgentException.class,
                () -> mgr.setOrphanRecoveryHandler(null),
                "setOrphanRecoveryHandler(null) must fail-fast (no silent fallback to default)");
    }

    @Test
    void setOrphanRecoveryHandlerInjectsNonNoOpHandler() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        RecordingHandler handler = new RecordingHandler();
        mgr.setOrphanRecoveryHandler(handler);

        insertSession("orphan-inject", "running");
        RecoveryScanResult result = mgr.scanOnce();

        // Wiring Verification (#23): the injected handler must be called
        // at runtime by scanOnce (not just assigned as a field).
        assertEquals(1, handler.callCount.get(),
                "scanOnce must invoke the injected handler for the single orphan");
        assertEquals("orphan-inject", handler.lastSessionId.get(),
                "handler must receive the orphan session ID");
        assertFalse(mgr.getOrphanRecoveryHandler() instanceof NoOpOrphanRecoveryHandler,
                "after injection, the handler must be the non-NoOp instance");
        assertEquals(1, result.getRecoveryActions().size(),
                "recoveryActions must contain one outcome per orphan");
    }

    @Test
    void noOpDefaultScanRecordsSkipOutcomes() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        insertSession("orphan-noop-1", "running");
        insertSession("orphan-noop-2", "pending");

        RecoveryScanResult result = mgr.scanOnce();

        // Shipped default (NoOp/SKIP): each orphan gets a SKIP outcome.
        assertEquals(2, result.getOrphanSessionsDetected());
        assertEquals(2, result.getRecoveryActions().size(),
                "recoveryActions must have one outcome per detected orphan");
        for (RecoveryOutcome outcome : result.getRecoveryActions()) {
            assertEquals(RecoveryMode.SKIP, outcome.getMode(),
                    "NoOp default must produce SKIP outcomes");
            assertTrue(outcome.isSucceeded(),
                    "SKIP is an observation-only success");
        }
    }

    @Test
    void noOpManagerEmptyResultHasEmptyRecoveryActions() {
        RecoveryScanResult empty = RecoveryScanResult.empty();
        assertNotNull(empty.getRecoveryActions(),
                "empty() recoveryActions must be non-null");
        assertTrue(empty.getRecoveryActions().isEmpty(),
                "empty() recoveryActions must be an empty list");
    }

    // ========================================================================
    // E2E: daemon + ABORT handler → orphan session aborted (plan 226)
    // ========================================================================

    @Test
    void endToEndAbortHandlerAbortsOrphanSession() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        // Inject a functional ABORT handler (no engine needed for ABORT).
        DefaultOrphanRecoveryHandler abortHandler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.ABORT, null, dataSource);
        mgr.setOrphanRecoveryHandler(abortHandler);

        insertSession("orphan-e2e", "running");
        // A healthy locked session — must be untouched by ABORT.
        insertSession("healthy-e2e", "running");
        insertLockRow("healthy-e2e", "alive-owner", System.currentTimeMillis() + 60_000L);
        // A terminal session — excluded from orphan detection entirely.
        insertSession("done-e2e", "completed");

        RecoveryScanResult result = mgr.scanOnce();

        // End-to-end: exactly one orphan (running, no lock) was detected and aborted.
        assertEquals(1, result.getOrphanSessionsDetected(),
                "only the lock-free running session is an orphan");
        assertEquals("orphan-e2e", result.getOrphanSessionIds().get(0));
        assertEquals(1, result.getRecoveryActions().size());
        RecoveryOutcome outcome = result.getRecoveryActions().get(0);
        assertEquals(RecoveryMode.ABORT, outcome.getMode());
        assertTrue(outcome.isSucceeded(),
                "ABORT of the orphan must succeed");

        // The orphan session's DB status must now be 'failed'.
        assertEquals("failed", getSessionStatus("orphan-e2e"),
                "E2E: orphan session status must be 'failed' after ABORT scan");
        // The healthy session must be untouched (still running, lock preserved).
        assertEquals("running", getSessionStatus("healthy-e2e"),
                "E2E: healthy locked session must be untouched");
        // The terminal session must be untouched.
        assertEquals("completed", getSessionStatus("done-e2e"),
                "E2E: terminal session must be untouched");
    }

    @Test
    void daemonIntegrationAbortHandlerPopulatesRecoveryActions() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        DefaultOrphanRecoveryHandler abortHandler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.ABORT, null, dataSource);
        mgr.setOrphanRecoveryHandler(abortHandler);

        insertSession("orphan-diag", "pending");

        RecoveryScanResult result = mgr.scanOnce();

        // Daemon integration: scanOnce detected the orphan and the handler
        // recorded a succeeded=true ABORT outcome in recoveryActions.
        assertEquals(1, result.getRecoveryActions().size());
        assertTrue(result.getRecoveryActions().get(0).isSucceeded());
        assertEquals(RecoveryMode.ABORT, result.getRecoveryActions().get(0).getMode());
        assertEquals("failed", getSessionStatus("orphan-diag"),
                "daemon integration: pending orphan status must be 'failed' after scan");
    }

    // ========================================================================
    // Session timeout handler integration (plan 229 / L4-8-P4-TimeoutAbort)
    // ========================================================================

    @Test
    void defaultTimeoutHandlerIsNoOp() {
        ScheduledRecoveryManager mgr = newManager();
        // The shipped default timeout handler must be NoOpSessionTimeoutHandler
        // (SKIPPED action, zero regression with plan 226).
        assertTrue(mgr.getSessionTimeoutHandler() instanceof NoOpSessionTimeoutHandler,
                "shipped default timeout handler must be NoOpSessionTimeoutHandler");
    }

    @Test
    void defaultTimeoutSecondsIs30Minutes() {
        ScheduledRecoveryManager mgr = newManager();
        assertEquals(30L * 60L, mgr.getTimeoutSeconds(),
                "default timeoutSeconds must be 30 minutes (1800s, matching default lockLeaseMs)");
    }

    @Test
    void setTimeoutSecondsRejectsNonPositive() {
        ScheduledRecoveryManager mgr = newManager();
        assertThrows(NopAiAgentException.class,
                () -> mgr.setTimeoutSeconds(0L),
                "setTimeoutSeconds(0) must fail-fast");
        assertThrows(NopAiAgentException.class,
                () -> mgr.setTimeoutSeconds(-1L),
                "setTimeoutSeconds(-1) must fail-fast");
        // Positive value must succeed.
        mgr.setTimeoutSeconds(120L);
        assertEquals(120L, mgr.getTimeoutSeconds());
    }

    @Test
    void setSessionTimeoutHandlerRejectsNull() {
        ScheduledRecoveryManager mgr = newManager();
        assertThrows(NopAiAgentException.class,
                () -> mgr.setSessionTimeoutHandler(null),
                "setSessionTimeoutHandler(null) must fail-fast (no silent fallback to default)");
    }

    @Test
    void setSessionTimeoutHandlerInjectsNonNoOpHandler() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        RecordingTimeoutHandler handler = new RecordingTimeoutHandler();
        mgr.setSessionTimeoutHandler(handler);

        insertSession("timeout-inject", "running");
        RecoveryScanResult result = mgr.scanOnce();

        // Wiring Verification (#23): the injected handler must be called
        // at runtime by scanOnce (not just assigned as a field).
        assertEquals(1, handler.callCount.get(),
                "scanOnce must invoke the injected timeout handler for the single timed-out session");
        assertEquals("timeout-inject", handler.lastSessionId.get(),
                "handler must receive the timed-out session ID");
        assertFalse(mgr.getSessionTimeoutHandler() instanceof NoOpSessionTimeoutHandler,
                "after injection, the handler must be the non-NoOp instance");
        assertEquals(1, result.getTimeoutActions().size(),
                "timeoutActions must contain one outcome per timed-out session");
    }

    @Test
    void noOpTimeoutHandlerDefaultProducesSkippedOutcomes() throws Exception {
        // Shipped default (NoOp SKIPPED): timed-out sessions produce SKIPPED
        // outcomes, no DB mutation, zero behaviour regression with plan 226.
        ScheduledRecoveryManager mgr = newManager();
        // UPDATED_AT=0 → always timed-out (0 < now - threshold).
        insertSession("timeout-noop-1", "running");
        insertSession("timeout-noop-2", "pending");

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(2, result.getTimeoutActions().size(),
                "NoOp default must produce one SKIPPED outcome per timed-out session");
        for (TimeoutOutcome outcome : result.getTimeoutActions()) {
            assertEquals(TimeoutAction.SKIPPED, outcome.getAction(),
                    "NoOp default must produce SKIPPED outcomes");
            assertTrue(outcome.isSucceeded(),
                    "SKIPPED is an observation-only success");
        }
        // NoOp must NOT mutate DB status (zero regression).
        assertEquals("running", getSessionStatus("timeout-noop-1"),
                "NoOp timeout handler must NOT mutate DB status");
        assertEquals("pending", getSessionStatus("timeout-noop-2"),
                "NoOp timeout handler must NOT mutate DB status");
    }

    @Test
    void noOpManagerEmptyResultHasEmptyTimeoutActions() {
        RecoveryScanResult empty = RecoveryScanResult.empty();
        assertNotNull(empty.getTimeoutActions(),
                "empty() timeoutActions must be non-null");
        assertTrue(empty.getTimeoutActions().isEmpty(),
                "empty() timeoutActions must be an empty list");
    }

    @Test
    void nonTimedOutSessionIsNotTimeoutDetected() throws Exception {
        // A session with a RECENT UPDATED_AT (within the threshold) must
        // NOT be flagged as timed-out.
        ScheduledRecoveryManager mgr = newManager();
        mgr.setTimeoutSeconds(60L); // 1-minute threshold
        long now = System.currentTimeMillis();
        // UPDATED_AT = now → within the 60s threshold → NOT timed-out.
        insertSessionWithUpdatedAt("fresh-session", "running", now);

        RecoveryScanResult result = mgr.scanOnce();

        assertTrue(result.getTimeoutActions().isEmpty(),
                "a session with a recent UPDATED_AT must NOT be flagged as timed-out");
    }

    @Test
    void daemonTimeoutDetectionForceFailsOrphanedTimedOutSession() throws Exception {
        // Daemon integration: scanOnce detects a timed-out orphan session
        // and the DefaultSessionTimeoutHandler FORCE_FAILED branch marks
        // it 'failed' (terminal).
        ScheduledRecoveryManager mgr = newManager();
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, new StubEngine(), dataSource, "this-instance");
        mgr.setSessionTimeoutHandler(handler);

        // UPDATED_AT=0 → timed-out; no lock → orphaned → FORCE_FAILED.
        insertSession("timeout-orphan", "running");

        RecoveryScanResult result = mgr.scanOnce();

        // Daemon integration: scanOnce detected the timed-out session and
        // the handler recorded a succeeded=true FORCE_FAILED outcome.
        assertEquals(1, result.getTimeoutActions().size(),
                "one timed-out session → one timeout outcome");
        TimeoutOutcome outcome = result.getTimeoutActions().get(0);
        assertEquals(TimeoutAction.FORCE_FAILED, outcome.getAction());
        assertTrue(outcome.isSucceeded(),
                "FORCE_FAILED of a running orphan must succeed");
        assertEquals("failed", getSessionStatus("timeout-orphan"),
                "daemon integration: timed-out orphan status must be 'failed' after scan");
    }

    @Test
    void timeoutBeforeOrphanOrderingAvoidsConflict() throws Exception {
        // Design 裁定 3: timeout detection runs BEFORE orphan detection.
        // A timed-out orphan session force-marked 'failed' by the timeout
        // handler must NOT be subsequently detected as an orphan (it is
        // now terminal), avoiding double-handling.
        ScheduledRecoveryManager mgr = newManager();
        DefaultSessionTimeoutHandler timeoutHandler = new DefaultSessionTimeoutHandler(
                60L, new StubEngine(), dataSource, "this-instance");
        mgr.setSessionTimeoutHandler(timeoutHandler);
        // Inject a RESUME orphan handler to prove the timeout path took
        // precedence (if orphan detection ran on this session, the RESUME
        // handler would be called — but it must NOT be, because the
        // timeout handler already terminalised the session).
        RecordingHandler orphanHandler = new RecordingHandler();
        mgr.setOrphanRecoveryHandler(orphanHandler);

        // UPDATED_AT=0 → timed-out; no lock → would be orphan too.
        insertSession("conflict-1", "running");

        RecoveryScanResult result = mgr.scanOnce();

        // The timeout handler force-failed the session FIRST.
        assertEquals(1, result.getTimeoutActions().size());
        assertTrue(result.getTimeoutActions().get(0).isSucceeded());
        assertEquals(TimeoutAction.FORCE_FAILED, result.getTimeoutActions().get(0).getAction());
        assertEquals("failed", getSessionStatus("conflict-1"),
                "timeout handler must have force-failed the session");

        // Orphan detection subsequently excludes the session (now terminal).
        assertEquals(0, result.getOrphanSessionsDetected(),
                "after timeout force-fail, the session is terminal and must NOT be an orphan");
        assertTrue(result.getOrphanSessionIds().isEmpty());
        assertEquals(0, orphanHandler.callCount.get(),
                "the orphan handler must NOT be called (session was terminalised by timeout first)");
    }

    @Test
    void endToEndTimeoutForceFailedCompleteScanResult() throws Exception {
        // End-to-end (Minimum Rules #22): full scanOnce path with a
        // DefaultSessionTimeoutHandler. A timed-out orphan session is
        // force-failed; a healthy session with a fresh UPDATED_AT is NOT
        // timed-out; a terminal session is excluded.
        ScheduledRecoveryManager mgr = newManager();
        mgr.setTimeoutSeconds(60L);
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, new StubEngine(), dataSource, "this-instance");
        mgr.setSessionTimeoutHandler(handler);

        long now = System.currentTimeMillis();
        // Timed-out orphan: UPDATED_AT=0, no lock, running → FORCE_FAILED.
        insertSession("e2e-timeout", "running");
        // Healthy session: recent UPDATED_AT, active lock → NOT timed-out,
        // NOT orphan.
        insertSessionWithUpdatedAt("e2e-healthy", "running", now);
        insertLockRow("e2e-healthy", "alive-owner", now + 60_000L);
        // Terminal session: excluded from timeout and orphan detection.
        insertSession("e2e-done", "completed");

        RecoveryScanResult result = mgr.scanOnce();

        // Timeout path: only the timed-out orphan is detected and force-failed.
        assertEquals(1, result.getTimeoutActions().size(),
                "only the timed-out session (UPDATED_AT=0) is in timeoutActions");
        TimeoutOutcome timeoutOutcome = result.getTimeoutActions().get(0);
        assertEquals("e2e-timeout", timeoutOutcome.getSessionId());
        assertEquals(TimeoutAction.FORCE_FAILED, timeoutOutcome.getAction());
        assertTrue(timeoutOutcome.isSucceeded());

        // The timed-out session is now terminal → excluded from orphan detection.
        assertEquals(0, result.getOrphanSessionsDetected(),
                "the force-failed session is terminal → not detected as orphan");
        assertEquals("failed", getSessionStatus("e2e-timeout"),
                "E2E: timed-out session status must be 'failed'");
        // The healthy session is untouched.
        assertEquals("running", getSessionStatus("e2e-healthy"),
                "E2E: healthy session with fresh UPDATED_AT must be untouched");
        // The terminal session is untouched.
        assertEquals("completed", getSessionStatus("e2e-done"),
                "E2E: terminal session must be untouched");
        // Observable duration + timestamp.
        assertTrue(result.getScanDurationMs() >= 0);
        assertTrue(result.getScannedAt() > 0);
    }

    @Test
    void daemonTimeoutLocalCancelledWiringWithMockEngine() throws Exception {
        // Daemon integration for LOCAL_CANCELLED: a timed-out session with
        // an active local lock → the DefaultSessionTimeoutHandler must
        // delegate to engine.cancelSession(forced=true). The DB status is
        // NOT mutated by the handler (the cancelSession path owns the
        // terminal transition).
        ScheduledRecoveryManager mgr = newManager();
        StubEngine engine = new StubEngine();
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, engine, dataSource, StubEngine.INSTANCE_ID);
        mgr.setSessionTimeoutHandler(handler);

        // Timed-out session (UPDATED_AT=0) with an active lock owned by
        // this instance → LOCAL_CANCELLED branch.
        insertSession("timeout-local", "running");
        insertLockRow("timeout-local", StubEngine.INSTANCE_ID,
                System.currentTimeMillis() + 60_000L);

        RecoveryScanResult result = mgr.scanOnce();

        // Wiring Verification (#23): engine.cancelSession(forced=true) was
        // actually invoked at runtime by the daemon scanOnce path.
        assertEquals(1, engine.cancelCount.get(),
                "LOCAL_CANCELLED via daemon must call engine.cancelSession");
        assertEquals("timeout-local", engine.lastCancelSessionId.get());
        assertTrue(engine.lastCancelForced.get(),
                "cancelSession must be forced=true");
        // The outcome records LOCAL_CANCELLED.
        assertEquals(1, result.getTimeoutActions().size());
        TimeoutOutcome outcome = result.getTimeoutActions().get(0);
        assertEquals(TimeoutAction.LOCAL_CANCELLED, outcome.getAction());
        assertTrue(outcome.isSucceeded());
        // The session is still running/pending for orphan detection
        // purposes (the handler did not mutate status). However, the
        // session has an ACTIVE lock → it is NOT an orphan either.
        assertEquals(0, result.getOrphanSessionsDetected(),
                "the local-locked session is not an orphan (active lock protects it)");
    }

    // Helper: insert a session row with a specific UPDATED_AT timestamp.
    private void insertSessionWithUpdatedAt(String sessionId, String status, long updatedAt) throws SQLException {
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
                    + "', '{}', 0, " + updatedAt + ")");
        }
    }

    // ========================================================================
    // Minimal recording ISessionTimeoutHandler for setter-injection tests.
    // Records handleTimeout calls so wiring can be verified (#23).
    // ========================================================================
    static final class RecordingTimeoutHandler implements ISessionTimeoutHandler {
        final AtomicInteger callCount = new AtomicInteger(0);
        final AtomicReference<String> lastSessionId = new AtomicReference<>();

        @Override
        public TimeoutOutcome handleTimeout(String sessionId) {
            callCount.incrementAndGet();
            lastSessionId.set(sessionId);
            return new TimeoutOutcome(sessionId, TimeoutAction.SKIPPED, true, "recording-timeout-handler");
        }
    }

    // ========================================================================
    // Minimal stub IAgentEngine for daemon-integration tests that exercise
    // the LOCAL_CANCELLED branch via DefaultSessionTimeoutHandler. Records
    // cancelSession invocations; all other methods throw UOE.
    // ========================================================================
    static final class StubEngine implements IAgentEngine {
        static final String INSTANCE_ID = "test-instance-id";
        final AtomicInteger cancelCount = new AtomicInteger(0);
        final AtomicReference<String> lastCancelSessionId = new AtomicReference<>();
        final AtomicReference<String> lastCancelReason = new AtomicReference<>();
        final AtomicReference<Boolean> lastCancelForced = new AtomicReference<>();

        @Override
        public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
            cancelCount.incrementAndGet();
            lastCancelSessionId.set(sessionId);
            lastCancelReason.set(reason);
            lastCancelForced.set(forced);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used in timeout daemon tests");
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            throw new UnsupportedOperationException("not used in timeout daemon tests");
        }
    }

    // ========================================================================
    // Minimal recording IOrphanRecoveryHandler for setter-injection tests.
    // Records handleOrphan calls so wiring can be verified (#23).
    // ========================================================================
    static final class RecordingHandler implements IOrphanRecoveryHandler {
        final AtomicInteger callCount = new AtomicInteger(0);
        final AtomicReference<String> lastSessionId = new AtomicReference<>();

        @Override
        public RecoveryOutcome handleOrphan(String sessionId) {
            callCount.incrementAndGet();
            lastSessionId.set(sessionId);
            return new RecoveryOutcome(sessionId, RecoveryMode.SKIP, true, "recording-handler");
        }
    }

    // ========================================================================
    // Minimal recording IScheduledExecutor stub for scheduling-wiring tests.
    // Records the scheduleWithFixedDelay arguments and exposes a
    // cancel-recording Future. Does NOT actually schedule anything — tests
    // invoke the recorded Runnable manually.
    // ========================================================================
    static final class RecordingScheduler implements IScheduledExecutor, IDestroyable {
        final AtomicInteger scheduleCount = new AtomicInteger(0);
        final AtomicReference<Runnable> lastCommand = new AtomicReference<>();
        final AtomicReference<Long> lastInitialDelay = new AtomicReference<>();
        final AtomicReference<Long> lastDelay = new AtomicReference<>();
        final AtomicReference<TimeUnit> lastUnit = new AtomicReference<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        boolean mayInterruptIfRunning;
        final AtomicBoolean destroyed = new AtomicBoolean(false);

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            scheduleCount.incrementAndGet();
            lastCommand.set(command);
            lastInitialDelay.set(initialDelay);
            lastDelay.set(delay);
            lastUnit.set(unit);
            // Return a cancel-recording Future so stop()'s cancel(false) is
            // observable (CompletableFuture.cancel does not expose whether
            // it was called by the caller vs completed).
            return new Future<Object>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    cancelled.set(true);
                    RecordingScheduler.this.mayInterruptIfRunning = mayInterruptIfRunning;
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get();
                }

                @Override
                public boolean isDone() {
                    return cancelled.get();
                }

                @Override
                public Object get() {
                    return null;
                }

                @Override
                public Object get(long timeout, TimeUnit u) {
                    return null;
                }
            };
        }

        @Override
        public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public void execute(Runnable command) {
        }

        @Override
        public void destroy() {
            destroyed.set(true);
        }

        @Override
        public boolean isDestroyed() {
            return destroyed.get();
        }

        @Override
        public String getName() {
            return "recording-scheduler";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public ThreadPoolStats stats() {
            return null;
        }

        @Override
        public <V> CompletableFuture<V> submit(Callable<V> callable) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void refreshConfig() {
        }
    }
}
