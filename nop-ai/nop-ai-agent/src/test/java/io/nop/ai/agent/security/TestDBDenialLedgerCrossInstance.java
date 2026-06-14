package io.nop.ai.agent.security;

import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 cross-instance end-to-end tests for {@link DBDenialLedger}.
 *
 * <p>These tests prove the <b>core value</b> of DB persistence: the per-session
 * denial count and the paused state survive ledger-instance reconstruction.
 * Two independent {@code DBDenialLedger} instances — separate object
 * identities, no shared in-memory state — share the same H2 database. The
 * second instance reads the count and pause state that the first instance
 * persisted.
 *
 * <p><b>Anti-Hollow Check</b>: if {@code DBDenialLedger} secretly used
 * in-memory state (e.g. a {@code ConcurrentHashMap}), the second instance's
 * count would be zero and {@code isPaused} would be false — these tests would
 * fail. They only pass because the count is genuinely read from the DB.
 */
public class TestDBDenialLedgerCrossInstance {

    private DataSource dataSource;
    private String dbUrl;

    @BeforeEach
    void setUp() {
        dbUrl = "jdbc:h2:mem:test-denial-cross-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    private DenialRecord deny(String sessionId) {
        return DenialRecord.of(sessionId, "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "test deny", "test-rule", System.currentTimeMillis());
    }

    /**
     * The persistence cross-instance survival scenario: create instance A,
     * record denials until the session is paused, then <b>drop all references
     * to instance A</b> and create a brand-new instance B against the same DB.
     * Instance B must observe the same count and the same paused state.
     */
    @Test
    void persistenceSurvivesLedgerInstanceReconstruction() {
        String sessionId = "survival-session";

        // Phase A: instance A records 2 denials (threshold=2) → paused.
        DBDenialLedger instanceA = new DBDenialLedger(dataSource, 2);
        instanceA.recordDenial(deny(sessionId));
        DenialRecordOutcome o2 = instanceA.recordDenial(deny(sessionId));
        assertEquals(2, o2.getCount());
        assertTrue(o2.isThresholdExceeded());
        assertTrue(instanceA.isPaused(sessionId));

        // Drop all references to instance A. It is eligible for GC.
        instanceA = null;

        // Phase B: a brand-new instance B shares the same DB. It has no
        // in-memory link to instance A.
        DBDenialLedger instanceB = new DBDenialLedger(dataSource, 2);

        assertNotSame(instanceA, instanceB,
                "sanity: instanceB is a distinct object (instanceA is nulled)");

        // The count and paused state must survive because they live in the DB.
        assertEquals(2, instanceB.getDenialCount(sessionId),
                "instanceB must read the count persisted by instance A from the DB");
        assertTrue(instanceB.isPaused(sessionId),
                "instanceB must observe the paused state persisted by instance A");
    }

    /**
     * A new instance can record additional denials that stack on top of the
     * count persisted by a previous (now-gone) instance.
     */
    @Test
    void newInstanceContinuesAccumulatingAcrossInstances() throws Exception {
        String sessionId = "stack-session";

        DBDenialLedger instanceA = new DBDenialLedger(dataSource, 5);
        instanceA.recordDenial(deny(sessionId));
        instanceA.recordDenial(deny(sessionId));
        assertEquals(2, instanceA.getDenialCount(sessionId));
        instanceA = null;

        DBDenialLedger instanceB = new DBDenialLedger(dataSource, 5);
        assertEquals(2, instanceB.getDenialCount(sessionId),
                "instanceB reads the count from the DB");

        // One more denial from instance B → count becomes 3.
        DenialRecordOutcome o = instanceB.recordDenial(deny(sessionId));
        assertEquals(3, o.getCount(), "instance B's recordDenial stacks on the DB count");

        assertEquals(3, countDenialRows(sessionId), "DB holds all 3 rows");
    }

    /**
     * reset() issued from one instance is observable by another instance
     * (because reset is a DELETE against the shared DB).
     */
    @Test
    void resetFromOneInstanceIsObservableByAnother() {
        String sessionId = "reset-session";

        DBDenialLedger instanceA = new DBDenialLedger(dataSource, 2);
        instanceA.recordDenial(deny(sessionId));
        instanceA.recordDenial(deny(sessionId));
        assertTrue(instanceA.isPaused(sessionId));

        DBDenialLedger instanceB = new DBDenialLedger(dataSource, 2);
        assertTrue(instanceB.isPaused(sessionId),
                "instanceB observes the paused state before reset");

        instanceB.reset(sessionId);

        instanceA.getDenialCount(sessionId);
        assertFalse(instanceA.isPaused(sessionId),
                "instanceA observes the cleared state after instanceB.reset()");
        assertEquals(0, instanceA.getDenialCount(sessionId),
                "instanceA observes count=0 after instanceB.reset()");
    }

    /**
     * Schema re-initialization is idempotent: a second instance's constructor
     * re-runs the CREATE TABLE IF NOT EXISTS against an already-populated
     * table without losing any rows.
     */
    @Test
    void secondConstructorKeepsExistingRows() throws Exception {
        String sessionId = "idem-session";

        DBDenialLedger instanceA = new DBDenialLedger(dataSource, 5);
        instanceA.recordDenial(deny(sessionId));
        instanceA.recordDenial(deny(sessionId));
        assertEquals(2, countDenialRows(sessionId));

        // Constructor re-runs DDL with IF NOT EXISTS.
        DBDenialLedger instanceB = new DBDenialLedger(dataSource, 5);
        assertEquals(2, countDenialRows(sessionId),
                "second constructor's schema init must not wipe existing rows");
        assertEquals(2, instanceB.getDenialCount(sessionId));
    }
}
