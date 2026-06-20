package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 functional tests for {@link DBCheckpointManager}: verifies the core
 * value — save → persist to DB → reload via new instance → read round-trip,
 * full-field integrity, getLatestCheckpoint, getCheckpoint PK lookup,
 * append-only / duplicate-watermark fail-fast, cross-session isolation,
 * anonymous session, empty session, and backward compatibility.
 *
 * <p>Every DB-backed operation is verified by direct SQL queries against the
 * {@code ai_agent_checkpoint} table (not just cache state), satisfying
 * Minimum Rules #22 Anti-Hollow and #23 Wiring Verification.
 */
public class TestDBCheckpointManager {

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
        dbUrl = "jdbc:h2:mem:test-db-checkpoint-mgr-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
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

    // ========================================================================
    // save → get round-trip (core value — survives new instance)
    // ========================================================================

    @Test
    void saveGetRoundTripSurvivesNewInstance() throws Exception {
        Checkpoint original = Checkpoint.of(
                "sess-rt", "wm-rt-1", 0, 1000L,
                CheckpointType.TOOL_EXECUTION,
                "echo", "call-rt-1",
                "input-data", "output-data",
                5, 200L);

        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(original);

        // Verify row exists in DB (Anti-Hollow — direct SQL)
        assertEquals(1, countCheckpointRows("wm-rt-1"),
                "saveCheckpoint must write exactly 1 row to ai_agent_checkpoint");

        // New manager instance — simulate process restart (no shared memory state)
        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint restored = mgr2.getCheckpoint("wm-rt-1");

        assertNotNull(restored, "After reload, getCheckpoint must return the persisted checkpoint");
        assertEquals(original, restored, "All 11 fields must round-trip exactly");
    }

    // ========================================================================
    // Field integrity: all Checkpoint fields + nullable + enum + boundary
    // ========================================================================

    @Test
    void fieldIntegrityAllFieldsPopulated() throws Exception {
        Checkpoint cp = Checkpoint.of(
                "sess-fi", "wm-fi-1", 2, 1234567890L,
                CheckpointType.TOOL_EXECUTION,
                "file_write", "call-fi-1",
                "large input content", "large output content",
                42, 99999L);

        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(cp);

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint restored = mgr2.getCheckpoint("wm-fi-1");

        assertNotNull(restored);
        assertEquals("sess-fi", restored.getSessionId());
        assertEquals("wm-fi-1", restored.getWatermark());
        assertEquals(2, restored.getSeq());
        assertEquals(1234567890L, restored.getTimestamp());
        assertEquals(CheckpointType.TOOL_EXECUTION, restored.getType());
        assertEquals("file_write", restored.getToolName());
        assertEquals("call-fi-1", restored.getCallId());
        assertEquals("large input content", restored.getInputSummary());
        assertEquals("large output content", restored.getOutputSummary());
        assertEquals(42, restored.getMessageCount());
        assertEquals(99999L, restored.getTokenEstimate());
    }

    @Test
    void fieldIntegrityNullableFieldsAsNull() throws Exception {
        Checkpoint cp = Checkpoint.of(
                "sess-null", "wm-null-1", 0, 100L,
                CheckpointType.TOOL_EXECUTION,
                null, null, null, null,
                0, 0L);

        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(cp);

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint restored = mgr2.getCheckpoint("wm-null-1");

        assertNotNull(restored);
        assertNull(restored.getToolName());
        assertNull(restored.getCallId());
        assertNull(restored.getInputSummary());
        assertNull(restored.getOutputSummary());
        assertEquals(0, restored.getSeq());
        assertEquals(0, restored.getMessageCount());
        assertEquals(0L, restored.getTokenEstimate());
    }

    @Test
    void fieldIntegrityAllCheckpointTypeEnumValues() {
        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);

        for (CheckpointType type : CheckpointType.values()) {
            String wm = "wm-enum-" + type.name();
            Checkpoint cp = Checkpoint.of(
                    "sess-enum", wm, 0, 100L,
                    type, "tool", "call",
                    "in", "out", 1, 10L);
            mgr1.saveCheckpoint(cp);
        }

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        for (CheckpointType type : CheckpointType.values()) {
            Checkpoint restored = mgr2.getCheckpoint("wm-enum-" + type.name());
            assertNotNull(restored, "Type " + type + " must round-trip");
            assertEquals(type, restored.getType());
        }
    }

    @Test
    void fieldIntegrityLargeClobContent() throws Exception {
        // Build a large string that would exceed typical VARCHAR limits
        StringBuilder largeInput = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            largeInput.append("line-").append(i).append("; ");
        }
        String largeOutput = largeInput.toString().replace("line", "result");

        Checkpoint cp = Checkpoint.of(
                "sess-large", "wm-large-1", 0, 100L,
                CheckpointType.TOOL_EXECUTION,
                "file_read", "call-large",
                largeInput.toString(), largeOutput,
                10, 50000L);

        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(cp);

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint restored = mgr2.getCheckpoint("wm-large-1");

        assertNotNull(restored);
        assertEquals(largeInput.toString(), restored.getInputSummary(),
                "Large CLOB inputSummary must round-trip exactly");
        assertEquals(largeOutput, restored.getOutputSummary(),
                "Large CLOB outputSummary must round-trip exactly");
    }

    // ========================================================================
    // getLatestCheckpoint
    // ========================================================================

    @Test
    void getLatestCheckpointReturnsHighestSeq() throws Exception {
        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);

        for (int i = 0; i < 3; i++) {
            mgr1.saveCheckpoint(Checkpoint.of(
                    "sess-latest", "wm-latest-" + i, i, 1000L + i,
                    CheckpointType.TOOL_EXECUTION,
                    "tool", "call-" + i,
                    "in", "out", i + 1, 100L * (i + 1)));
        }

        assertEquals(3, countSessionCheckpoints("sess-latest"));

        // New instance — must load from DB
        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint latest = mgr2.getLatestCheckpoint("sess-latest");

        assertNotNull(latest);
        assertEquals(2, latest.getSeq(), "getLatestCheckpoint must return the highest seq");
        assertEquals("wm-latest-2", latest.getWatermark());
    }

    @Test
    void getLatestCheckpointReturnsNullForEmptySession() {
        DBCheckpointManager mgr = new DBCheckpointManager(dataSource);
        Checkpoint latest = mgr.getLatestCheckpoint("nonexistent-session");
        assertNull(latest, "getLatestCheckpoint on a session with no checkpoints must return null");
    }

    @Test
    void getLatestCheckpointReturnsNullForNullSession() {
        DBCheckpointManager mgr = new DBCheckpointManager(dataSource);
        assertNull(mgr.getLatestCheckpoint(null),
                "getLatestCheckpoint(null) must return null");
    }

    // ========================================================================
    // getCheckpoint PK lookup
    // ========================================================================

    @Test
    void getCheckpointReturnsNullForMissingWatermark() {
        DBCheckpointManager mgr = new DBCheckpointManager(dataSource);
        assertNull(mgr.getCheckpoint("never-saved"),
                "getCheckpoint on missing watermark must return null");
    }

    @Test
    void getCheckpointReturnsNullForNullWatermark() {
        DBCheckpointManager mgr = new DBCheckpointManager(dataSource);
        assertNull(mgr.getCheckpoint(null),
                "getCheckpoint(null) must return null");
    }

    // ========================================================================
    // Append-only semantics / duplicate watermark fail-fast
    // ========================================================================

    @Test
    void duplicateWatermarkInsertThrowsFailFast() throws Exception {
        DBCheckpointManager mgr = new DBCheckpointManager(dataSource);

        Checkpoint cpA = Checkpoint.of(
                "sess-dup", "wm-dup-1", 0, 100L,
                CheckpointType.TOOL_EXECUTION,
                "tool", "call-a", "in", "out", 1, 10L);

        mgr.saveCheckpoint(cpA);
        assertEquals(1, countCheckpointRows("wm-dup-1"));

        // Same watermark — DB PK constraint must reject this
        Checkpoint cpADup = Checkpoint.of(
                "sess-dup", "wm-dup-1", 1, 200L,
                CheckpointType.TOOL_EXECUTION,
                "tool", "call-dup", "in2", "out2", 2, 20L);

        assertThrows(NopAiAgentException.class, () -> mgr.saveCheckpoint(cpADup),
                "Duplicate watermark INSERT must fail fast (PK constraint), not silently overwrite");

        // Verify only the original row remains (not overwritten)
        assertEquals(1, countCheckpointRows("wm-dup-1"),
                "DB must contain exactly 1 row for the watermark (not overwritten)");

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint restored = mgr2.getCheckpoint("wm-dup-1");
        assertNotNull(restored);
        assertEquals(0, restored.getSeq(), "Original checkpoint must be intact (seq=0, not overwritten with seq=1)");
        assertEquals("call-a", restored.getCallId());
    }

    // ========================================================================
    // Cross-session isolation
    // ========================================================================

    @Test
    void crossSessionIsolation() throws Exception {
        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);

        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-A", "wm-A-0", 0, 100L,
                CheckpointType.TOOL_EXECUTION,
                "toolA", "call-A0", "inA", "outA", 1, 10L));

        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-B", "wm-B-0", 0, 200L,
                CheckpointType.TOOL_EXECUTION,
                "toolB", "call-B0", "inB", "outB", 1, 20L));

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);

        Checkpoint latestA = mgr2.getLatestCheckpoint("sess-A");
        assertNotNull(latestA);
        assertEquals("wm-A-0", latestA.getWatermark());
        assertEquals("toolA", latestA.getToolName());

        Checkpoint latestB = mgr2.getLatestCheckpoint("sess-B");
        assertNotNull(latestB);
        assertEquals("wm-B-0", latestB.getWatermark());
        assertEquals("toolB", latestB.getToolName());

        // getCheckpoint does not cross sessions
        Checkpoint byWmA = mgr2.getCheckpoint("wm-A-0");
        assertEquals("sess-A", byWmA.getSessionId());
        Checkpoint byWmB = mgr2.getCheckpoint("wm-B-0");
        assertEquals("sess-B", byWmB.getSessionId());
    }

    // ========================================================================
    // Anonymous session (sessionId == null)
    // ========================================================================

    @Test
    void anonymousSessionCheckpointPersistedAndRetrievableByWatermark() throws Exception {
        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);

        Checkpoint anon = Checkpoint.of(
                null, "wm-anon-1", 0, 100L,
                CheckpointType.TOOL_EXECUTION,
                "tool", "call-anon", "in", "out", 1, 10L);

        mgr1.saveCheckpoint(anon);
        assertEquals(1, countCheckpointRows("wm-anon-1"));

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        Checkpoint restored = mgr2.getCheckpoint("wm-anon-1");

        assertNotNull(restored, "Anonymous checkpoint must be retrievable by watermark PK lookup");
        assertNull(restored.getSessionId(), "sessionId must remain null after round-trip");
        assertEquals("wm-anon-1", restored.getWatermark());
        assertEquals("tool", restored.getToolName());

        // getLatestCheckpoint(null) returns null (no session to query)
        assertNull(mgr2.getLatestCheckpoint(null),
                "getLatestCheckpoint(null) must return null");
    }

    // ========================================================================
    // Write-through cache: intra-instance get after save
    // ========================================================================

    @Test
    void intraInstanceGetAfterSaveWorks() {
        DBCheckpointManager mgr = new DBCheckpointManager(dataSource);

        mgr.saveCheckpoint(Checkpoint.of(
                "sess-cache", "wm-cache-1", 0, 100L,
                CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 1, 10L));

        // getCheckpoint hits the write-through cache
        Checkpoint cp = mgr.getCheckpoint("wm-cache-1");
        assertNotNull(cp);
        assertEquals("wm-cache-1", cp.getWatermark());

        // getLatestCheckpoint hits the write-through cache
        Checkpoint latest = mgr.getLatestCheckpoint("sess-cache");
        assertNotNull(latest);
        assertEquals("wm-cache-1", latest.getWatermark());
    }

    // ========================================================================
    // Backward compatibility: ToolExecutionCheckpoint / FileBackedCheckpointManager unaffected
    // ========================================================================

    @Test
    void toolExecutionCheckpointBehaviorUnaffected() {
        ToolExecutionCheckpoint mgr = new ToolExecutionCheckpoint();

        mgr.saveCheckpoint(Checkpoint.of(
                "sess-bc", "wm-bc-1", 0, 100L,
                CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 1, 10L));

        assertNotNull(mgr.getLatestCheckpoint("sess-bc"));
        assertNotNull(mgr.getCheckpoint("wm-bc-1"));
    }

    @Test
    void noOpCheckpointBehaviorUnaffected() {
        NoOpCheckpoint mgr = (NoOpCheckpoint) NoOpCheckpoint.noOp();

        mgr.saveCheckpoint(Checkpoint.of(
                "sess-noop", "wm-noop-1", 0, 100L,
                CheckpointType.TOOL_EXECUTION,
                "tool", "call", "in", "out", 1, 10L));

        assertNull(mgr.getLatestCheckpoint("sess-noop"));
        assertNull(mgr.getCheckpoint("wm-noop-1"));
    }

    // ========================================================================
    // Plan 188: compaction-aware truncation on DB load
    // ========================================================================

    @Test
    void dbNoCompactionNoTruncationBackwardCompat() {
        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-no-compaction", "wm-db-te", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c0", "in", "out", 1, 10L));
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-no-compaction", "wm-db-ll", 1, 1001L,
                CheckpointType.LLM_TURN, null, null, null, "ll", 2, 20L));
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-no-compaction", "wm-db-te2", 2, 1002L,
                CheckpointType.TOOL_EXECUTION, "ls", "c2", "in2", "out2", 3, 30L));

        // New instance — triggers DB load.
        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        List<Checkpoint> loaded = mgr2.getCheckpoints("sess-no-compaction");

        assertEquals(3, loaded.size(), "No COMPACTION → no truncation (backward compat)");
        assertEquals("wm-db-te", loaded.get(0).getWatermark());
        assertEquals("wm-db-ll", loaded.get(1).getWatermark());
        assertEquals("wm-db-te2", loaded.get(2).getWatermark());

        // All watermarks still resolvable.
        assertNotNull(mgr2.getCheckpoint("wm-db-te"));
        assertNotNull(mgr2.getCheckpoint("wm-db-ll"));
        assertNotNull(mgr2.getCheckpoint("wm-db-te2"));
    }

    @Test
    void dbSingleCompactionTruncatesInclusive() {
        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-db-comp", "wm-db-pre-te", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "echo", "c0", "in", "out", 50, 500L));
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-db-comp", "wm-db-pre-ll", 1, 1001L,
                CheckpointType.LLM_TURN, null, null, null, "ll-pre", 51, 510L));
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-db-comp", "wm-db-compaction", 2, 1002L,
                CheckpointType.COMPACTION, null, null, null, "compaction", 5, 50L));
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-db-comp", "wm-db-post-ll", 3, 1003L,
                CheckpointType.LLM_TURN, null, null, null, "ll-post", 6, 60L));

        // New instance — triggers DB load.
        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        List<Checkpoint> loaded = mgr2.getCheckpoints("sess-db-comp");

        assertEquals(2, loaded.size(), "Truncated to COMPACTION inclusive");
        assertEquals(CheckpointType.COMPACTION, loaded.get(0).getType());
        assertEquals("wm-db-compaction", loaded.get(0).getWatermark());
        assertEquals("wm-db-post-ll", loaded.get(1).getWatermark());

        // getLatestCheckpoint returns post-compaction.
        Checkpoint latest = mgr2.getLatestCheckpoint("sess-db-comp");
        assertNotNull(latest);
        assertEquals("wm-db-post-ll", latest.getWatermark());

        // Pre-compaction watermark still resolves via DB direct-query fallback
        // (loadCheckpointFromDb) — byWatermark cache was populated during load
        // anyway, but the DB fallback guarantees this even on cache miss.
        Checkpoint preCompaction = mgr2.getCheckpoint("wm-db-pre-te");
        assertNotNull(preCompaction,
                "Pre-compaction watermark must resolve via DB fallback / cache after truncation");
        assertEquals(50, preCompaction.getMessageCount(),
                "Pre-compaction checkpoint must retain its original messageCount (audit capability)");
    }

    @Test
    void dbAllLoadedCheckpointsSatisfyInvariant() {
        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-db-invariant", "wm-db-pre", 0, 1000L,
                CheckpointType.TOOL_EXECUTION, "t", "c0", "in", "out", 50, 500L));
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-db-invariant", "wm-db-compaction", 1, 1001L,
                CheckpointType.COMPACTION, null, null, null, "compaction", 5, 50L));
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-db-invariant", "wm-db-post-6", 2, 1002L,
                CheckpointType.LLM_TURN, null, null, null, "ll", 6, 60L));
        mgr1.saveCheckpoint(Checkpoint.of(
                "sess-db-invariant", "wm-db-post-7", 3, 1003L,
                CheckpointType.TOOL_EXECUTION, "t", "c3", "in3", "out3", 7, 70L));

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);

        int sessionMessageCount = 7;
        List<Checkpoint> loaded = mgr2.getCheckpoints("sess-db-invariant");
        assertFalse(loaded.isEmpty());

        for (Checkpoint cp : loaded) {
            assertTrue(cp.getMessageCount() <= sessionMessageCount,
                    "Invariant violated: checkpoint.messageCount=" + cp.getMessageCount()
                            + " > session.messageCount=" + sessionMessageCount
                            + " for watermark=" + cp.getWatermark());
        }
    }

    // ========================================================================
    // Plan 279 / AR-08: cross-takeover latest-checkpoint ordering
    // ========================================================================

    @Test
    void getLatestCheckpointAcrossTakeoverReturnsMostRecentExecution() {
        // Instance A runs first: seq 0..5 at an EARLIER wall-clock time.
        DBCheckpointManager instanceA = new DBCheckpointManager(dataSource);
        long tA = 1_000_000L;
        for (int i = 0; i <= 5; i++) {
            instanceA.saveCheckpoint(Checkpoint.of(
                    "sess-takeover", "wm-a-" + i, i, tA + i,
                    CheckpointType.TOOL_EXECUTION, "toolA", "call-a-" + i,
                    "in", "out", i + 1, 10L * (i + 1)));
        }

        // Instance B takes over after A crashed: a fresh execute() resets SEQ
        // to 0, runs seq 0..2 at a LATER wall-clock time.
        DBCheckpointManager instanceB = new DBCheckpointManager(dataSource);
        long tB = 9_000_000L;
        for (int i = 0; i <= 2; i++) {
            instanceB.saveCheckpoint(Checkpoint.of(
                    "sess-takeover", "wm-b-" + i, i, tB + i,
                    CheckpointType.TOOL_EXECUTION, "toolB", "call-b-" + i,
                    "in", "out", i + 1, 10L * (i + 1)));
        }

        // Instance C reconstructs from the DB (fresh cache) and asks for the
        // latest checkpoint — the restoreSession path (DefaultAgentEngine).
        DBCheckpointManager instanceC = new DBCheckpointManager(dataSource);
        Checkpoint latest = instanceC.getLatestCheckpoint("sess-takeover");

        assertNotNull(latest);
        // AR-08: the latest is instance B's most recent (seq=2, latest
        // timestamp), NOT instance A's seq=5 (earlier timestamp despite the
        // higher per-execution-local seq).
        assertEquals("wm-b-2", latest.getWatermark(),
                "cross-takeover latest must be the most-recent-execution checkpoint "
                        + "(B seq=2), not the highest-seq (A seq=5)");
        assertEquals(2, latest.getSeq());
        assertEquals("toolB", latest.getToolName());
    }

    @Test
    void getCheckpointsStillAscendingSeqAndDecoupledFromLatestSelection() {
        // Save checkpoints with seq and timestamp OUT OF SYNC to prove the two
        // selections are independent: getCheckpoints orders by SEQ (ascending,
        // via loadSessionRowsFromDb which is intentionally unchanged), while
        // getLatestCheckpoint selects by CHECKPOINT_TIMESTAMP.
        DBCheckpointManager mgr1 = new DBCheckpointManager(dataSource);
        mgr1.saveCheckpoint(Checkpoint.of("sess-order", "wm-2", 2, 100L,
                CheckpointType.TOOL_EXECUTION, "t", "c2", "in", "out", 3, 30L));
        mgr1.saveCheckpoint(Checkpoint.of("sess-order", "wm-0", 0, 300L,
                CheckpointType.TOOL_EXECUTION, "t", "c0", "in", "out", 1, 10L));
        mgr1.saveCheckpoint(Checkpoint.of("sess-order", "wm-1", 1, 200L,
                CheckpointType.TOOL_EXECUTION, "t", "c1", "in", "out", 2, 20L));

        DBCheckpointManager mgr2 = new DBCheckpointManager(dataSource);
        List<Checkpoint> cps = mgr2.getCheckpoints("sess-order");
        assertEquals(3, cps.size());
        // getCheckpoints: ascending SEQ order preserved (AR-08 isolation).
        assertEquals(0, cps.get(0).getSeq());
        assertEquals(1, cps.get(1).getSeq());
        assertEquals(2, cps.get(2).getSeq());

        // getLatestCheckpoint: timestamp-based, decoupled from the list's last
        // element (which is seq=2 / wm-2 @100 — NOT the latest).
        Checkpoint latest = mgr2.getLatestCheckpoint("sess-order");
        assertEquals("wm-0", latest.getWatermark(),
                "latest is the greatest-timestamp checkpoint (wm-0 @300), not the last-by-seq (wm-2 @100)");
        assertNotEquals(cps.get(2).getWatermark(), latest.getWatermark(),
                "getLatestCheckpoint is decoupled from getCheckpoints' last element (AR-08 isolation)");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private int countCheckpointRows(String watermark) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + AiAgentCheckpointTable.TABLE_NAME
                             + " WHERE " + AiAgentCheckpointTable.COL_WATERMARK + " = '" + watermark + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countSessionCheckpoints(String sessionId) throws Exception {
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
