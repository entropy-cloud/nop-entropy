package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 unit tests for {@link DBDenialLedger}.
 *
 * <p>Each test uses a real H2 in-memory database — no mocks — so the full
 * persistence chain (INSERT/COUNT/DELETE) is exercised end-to-end. DB table
 * row counts are queried directly to verify records are persisted (not just
 * held in-memory).
 */
public class TestDBDenialLedger {

    private DataSource dataSource;
    private String dbUrl;

    @BeforeEach
    void setUp() {
        dbUrl = "jdbc:h2:mem:test-db-denial-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    private DenialRecord deny(String sessionId, String toolName, DenialLayerSource layer) {
        return DenialRecord.of(sessionId, toolName, layer,
                "test deny", "test-rule", System.currentTimeMillis());
    }

    // ========================================================================
    // recordDenial: count increments + thresholdExceeded flip
    // ========================================================================

    @Test
    void recordDenialPersistsToDbAndReturnsIncrementingCount() throws Exception {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 3);

        DenialRecordOutcome o1 = ledger.recordDenial(deny("sessA", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertEquals(1, o1.getCount(), "first denial -> count=1");
        assertFalse(o1.isThresholdExceeded(), "threshold=3, first deny not exceeded");

        DenialRecordOutcome o2 = ledger.recordDenial(deny("sessA", "shell.exec", DenialLayerSource.LAYER3_APPROVAL_GATE));
        assertEquals(2, o2.getCount(), "second denial -> count=2");
        assertFalse(o2.isThresholdExceeded(), "threshold=3, second deny not exceeded");

        DenialRecordOutcome o3 = ledger.recordDenial(deny("sessA", "shell.exec", DenialLayerSource.LAYER2_SECURITY_POLICY));
        assertEquals(3, o3.getCount(), "third denial -> count=3");
        assertTrue(o3.isThresholdExceeded(), "threshold=3, third deny exceeds");

        // Verify records are actually persisted to the DB table.
        assertEquals(3, countDenialRows("sessA"),
                "3 denial records must be persisted to ai_agent_denial for sessA");
    }

    @Test
    void thresholdBoundaryThreshold1PausesOnFirstDeny() {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 1);

        DenialRecordOutcome o = ledger.recordDenial(deny("sessT1", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertEquals(1, o.getCount());
        assertTrue(o.isThresholdExceeded(), "threshold=1, first deny must exceed");
        assertTrue(ledger.isPaused("sessT1"));
    }

    @Test
    void thresholdBoundaryDefaultThreshold3TriggersOnThirdDeny() {
        DBDenialLedger ledger = new DBDenialLedger(dataSource);

        assertEquals(3, ledger.getDenialThreshold(), "default threshold must be 3");

        ledger.recordDenial(deny("sessD", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertFalse(ledger.isPaused("sessD"));
        ledger.recordDenial(deny("sessD", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertFalse(ledger.isPaused("sessD"), "not paused at count=2 with threshold=3");
        ledger.recordDenial(deny("sessD", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertTrue(ledger.isPaused("sessD"), "paused at count=3 with threshold=3");
    }

    // ========================================================================
    // isPaused: threshold comparison from DB COUNT
    // ========================================================================

    @Test
    void isPausedFalseBelowThresholdTrueAtAndAboveThreshold() {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 2);

        assertFalse(ledger.isPaused("sessP"));
        ledger.recordDenial(deny("sessP", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertFalse(ledger.isPaused("sessP"), "count=1 < threshold=2, not paused");
        ledger.recordDenial(deny("sessP", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertTrue(ledger.isPaused("sessP"), "count=2 >= threshold=2, paused");
        // Extra deny beyond threshold stays paused.
        ledger.recordDenial(deny("sessP", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertTrue(ledger.isPaused("sessP"), "count=3 >= threshold=2, still paused");
    }

    // ========================================================================
    // getDenialCount: reads from DB in real time
    // ========================================================================

    @Test
    void getDenialCountReadsFromDbRealTime() throws Exception {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 5);

        assertEquals(0, ledger.getDenialCount("sessC"));
        ledger.recordDenial(deny("sessC", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertEquals(1, ledger.getDenialCount("sessC"));
        ledger.recordDenial(deny("sessC", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertEquals(2, ledger.getDenialCount("sessC"));

        // Directly verify the count matches the DB row count.
        assertEquals(countDenialRows("sessC"), ledger.getDenialCount("sessC"));
    }

    // ========================================================================
    // reset: DELETE from DB
    // ========================================================================

    @Test
    void resetDeletesSessionRecordsAndClearsPauseState() throws Exception {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 2);

        ledger.recordDenial(deny("sessR", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        ledger.recordDenial(deny("sessR", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertTrue(ledger.isPaused("sessR"));
        assertEquals(2, countDenialRows("sessR"));

        ledger.reset("sessR");

        assertEquals(0, ledger.getDenialCount("sessR"), "count must be 0 after reset");
        assertFalse(ledger.isPaused("sessR"), "not paused after reset");
        assertEquals(0, countDenialRows("sessR"), "DB rows must be deleted after reset");
    }

    @Test
    void resetOnlyTargetsSpecifiedSession() {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 5);

        ledger.recordDenial(deny("sessR1", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        ledger.recordDenial(deny("sessR2", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));

        ledger.reset("sessR1");

        assertEquals(0, ledger.getDenialCount("sessR1"), "sessR1 cleared");
        assertEquals(1, ledger.getDenialCount("sessR2"), "sessR2 must be unaffected");
    }

    // ========================================================================
    // Per-session independence
    // ========================================================================

    @Test
    void perSessionCountsAreIndependent() {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 2);

        ledger.recordDenial(deny("sessA", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        ledger.recordDenial(deny("sessA", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertTrue(ledger.isPaused("sessA"), "sessA paused");

        // sessB unaffected by sessA's denials.
        assertEquals(0, ledger.getDenialCount("sessB"));
        assertFalse(ledger.isPaused("sessB"));

        DenialRecordOutcome ob = ledger.recordDenial(deny("sessB", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
        assertEquals(1, ob.getCount(), "sessB first deny -> count=1");
        assertFalse(ob.isThresholdExceeded(), "sessB under threshold even after sessA hit threshold");
    }

    // ========================================================================
    // Anonymous (null) session: documented non-persisting behavior
    // ========================================================================

    @Test
    void anonymousSessionDenialNotPersisted() throws Exception {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 1);

        DenialRecordOutcome o = ledger.recordDenial(
                DenialRecord.of(null, "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                        "no", "rule", 1000L));
        assertEquals(0, o.getCount(), "anonymous denial -> count=0");
        assertFalse(o.isThresholdExceeded(), "anonymous denial never exceeds threshold");
        assertFalse(ledger.isPaused(null));
        assertEquals(0, ledger.getDenialCount(null));
        assertEquals(0, countDenialRows(null), "no DB rows for anonymous denial");
    }

    @Test
    void resetNullSessionIsNoOp() {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 2);
        // Must not throw and must not affect any persisted session.
        ledger.reset(null);
    }

    // ========================================================================
    // Thread safety: concurrent multi-session recordDenial
    // ========================================================================

    @Test
    void concurrentRecordDenialPerSessionCountsAreIndependent() throws Exception {
        int threshold = 5;
        final DBDenialLedger ledger = new DBDenialLedger(dataSource, threshold);
        int sessionCount = 10;
        int deniesPerSession = 8;

        ExecutorService pool = Executors.newFixedThreadPool(sessionCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(sessionCount);
        List<Throwable> errors = new ArrayList<>();
        ConcurrentHashMap<String, Throwable> errorMap = new ConcurrentHashMap<>();

        for (int i = 0; i < sessionCount; i++) {
            final String sid = "conc-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < deniesPerSession; j++) {
                        ledger.recordDenial(deny(sid, "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS));
                    }
                } catch (Throwable t) {
                    errorMap.put(sid, t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "all concurrent tasks must complete");
        pool.shutdown();

        for (Throwable t : errorMap.values()) {
            errors.add(t);
        }
        assertEquals(0, errors.size(), "no exception expected during concurrent recordDenial: " + errors);

        // Each session must have exactly deniesPerSession records, independently.
        for (int i = 0; i < sessionCount; i++) {
            String sid = "conc-" + i;
            assertEquals(deniesPerSession, ledger.getDenialCount(sid),
                    "session " + sid + " must have exactly " + deniesPerSession + " denials");
            assertTrue(ledger.isPaused(sid),
                    "session " + sid + " (count=" + deniesPerSession + " >= " + threshold + ") must be paused");
        }

        // Total DB rows = sessionCount * deniesPerSession.
        assertEquals(sessionCount * deniesPerSession, countDenialRows(null),
                "total DB rows must match all recorded denials");
    }

    // ========================================================================
    // Constructor validation + schema init
    // ========================================================================

    @Test
    void constructorRejectsNullDataSource() {
        assertThrows(NullPointerException.class, () -> new DBDenialLedger(null));
    }

    @Test
    void constructorRejectsNonPositiveThreshold() {
        assertThrows(NopAiAgentException.class, () -> new DBDenialLedger(dataSource, 0));
        assertThrows(NopAiAgentException.class, () -> new DBDenialLedger(dataSource, -1));
    }

    @Test
    void constructorInitializesSchema() throws Exception {
        // A fresh DB: table must be created by the constructor.
        new DBDenialLedger(dataSource, 3);

        // Table must exist and be usable.
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO " + AiAgentDenialTable.TABLE_NAME
                    + " (SID, SESSION_ID, TOOL_NAME, LAYER_SOURCE, REASON, MATCHED_RULE,"
                    + " DENIAL_TIMESTAMP, CREATED_AT) VALUES "
                    + "('schema-test', 'sx', 't', 'LAYER1_TOOL_ACCESS', 'r', 'm', 1, CURRENT_TIMESTAMP)");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM " + AiAgentDenialTable.TABLE_NAME)) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    @Test
    void multipleLayerSourcesAreRecordedDistinctly() {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 10);

        ledger.recordDenial(DenialRecord.of("sessL", "shell.exec",
                DenialLayerSource.LAYER1_TOOL_ACCESS, "a", "r", 1L));
        ledger.recordDenial(DenialRecord.of("sessL", "shell.exec",
                DenialLayerSource.LAYER1_PERMISSION, "b", "r", 2L));
        ledger.recordDenial(DenialRecord.of("sessL", "shell.exec",
                DenialLayerSource.LAYER3_APPROVAL_GATE, "c", "r", 3L));

        assertEquals(3, ledger.getDenialCount("sessL"),
                "all layer sources recorded under the same session");
    }

    @Test
    void uniqueSidAllowsManyRecordsPerSession() {
        DBDenialLedger ledger = new DBDenialLedger(dataSource, 100);
        // Recording the same timestamp multiple times must not collide — SID is
        // the primary key and is generated per record.
        for (int i = 0; i < 20; i++) {
            ledger.recordDenial(DenialRecord.of("sessU", "shell.exec",
                    DenialLayerSource.LAYER1_TOOL_ACCESS, "r", "rule", 1000L));
        }
        assertEquals(20, ledger.getDenialCount("sessU"));
    }
}
