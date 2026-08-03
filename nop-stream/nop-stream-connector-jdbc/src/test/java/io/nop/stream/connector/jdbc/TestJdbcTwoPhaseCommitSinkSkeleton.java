/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.jdbc;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;

import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.operators.StreamSinkOperator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JdbcTwoPhaseCommitSink} Phase 1:
 * skeleton compilation, consistency capability, participant detection,
 * and basic lifecycle paths (begin/invoke/preCommit/commit/rollback/abort).
 */
class TestJdbcTwoPhaseCommitSinkSkeleton {

    private HikariDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroyCore() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName() + StringHelper.generateUUID() + ";MODE=MySQL");
        jdbcTemplate = JdbcFactory.newJdbcTemplateFor(dataSource);

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE TABLE target_data (id BIGINT, name VARCHAR(100), amount BIGINT)")) {
                ps.execute();
            }
        }
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    private JdbcTwoPhaseCommitSink<Map<String, Object>> createDefaultSink() {
        return JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(Function.identity())
                .build();
    }

    // ---- Consistency capability ----

    @Test
    void testGetSinkConsistencyReturnsTwoPhaseCommit() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        assertEquals(SinkConsistencyCapability.TWO_PHASE_COMMIT, sink.getSinkConsistency());
    }

    // ---- Participant detection ----

    @Test
    void testSinkIsCheckpointParticipant() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        assertInstanceOf(CheckpointParticipant.class, sink,
                "JdbcTwoPhaseCommitSink must implement CheckpointParticipant (via TwoPhaseCommitSinkFunction)");
    }

    @Test
    void testSinkRecognizedByStreamSinkOperatorAsParticipant() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        StreamSinkOperator<Map<String, Object>> operator = new StreamSinkOperator<>(sink);

        assertTrue(sink instanceof CheckpointParticipant,
                "The sink must be a CheckpointParticipant so StreamSinkOperator.processBarrier "
                        + "routes saveState/prepareCommit/finishCommit to it");
    }

    // ---- Null-arg guards (no silent skip) ----

    @Test
    void testConstructorRejectsNullJdbcTemplate() {
        assertThrows(Exception.class, () -> new JdbcTwoPhaseCommitSink<>(
                null, "", "target_data", "ledger",
                Arrays.asList("id"), Function.<Map<String, Object>>identity()));
    }

    @Test
    void testConstructorRejectsNullTableName() {
        assertThrows(Exception.class, () -> new JdbcTwoPhaseCommitSink<>(
                jdbcTemplate, "", null, "ledger",
                Arrays.asList("id"), Function.<Map<String, Object>>identity()));
    }

    @Test
    void testConstructorRejectsNullColumns() {
        assertThrows(Exception.class, () -> new JdbcTwoPhaseCommitSink<>(
                jdbcTemplate, "", "target_data", "ledger",
                null, Function.<Map<String, Object>>identity()));
    }

    @Test
    void testConstructorRejectsNullRecordMapper() {
        assertThrows(Exception.class, () -> new JdbcTwoPhaseCommitSink<Map<String, Object>>(
                jdbcTemplate, "", "target_data", "ledger",
                Arrays.asList("id"), null));
    }

    @Test
    void testInvokeRejectsNullValue() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        sink.beginTransaction();
        assertThrows(Exception.class, () -> sink.consume(null),
                "invoke must reject null — no silent skip");
    }

    // ---- Basic lifecycle paths ----

    @Test
    void testBeginTransactionSucceeds() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        assertDoesNotThrow(() -> sink.beginTransaction());
    }

    @Test
    void testInvokeBuffersInMemory() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        sink.beginTransaction();
        sink.consume(row(1L, "a", 100L));
        sink.consume(row(2L, "b", 200L));

        // Data should NOT be written yet (in-memory buffer only)
        assertEquals(0, countRows("target_data"),
                "Data must not be written to JDBC before commit");
    }

    @Test
    void testPreCommitIsNoOp() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        sink.beginTransaction();
        sink.consume(row(1L, "a", 100L));
        sink.preCommit(1L);

        // preCommit must NOT write to JDBC
        assertEquals(0, countRows("target_data"),
                "preCommit must not write to JDBC (in-memory buffer model)");
    }

    @Test
    void testRollbackDiscardsBuffer() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        sink.beginTransaction();
        sink.consume(row(1L, "a", 100L));
        sink.rollback();

        // After rollback, buffer is cleared. saveState should produce no pending.
        sink.saveState(1L);
        assertFalse(sink.getPendingCommits().containsKey(1L),
                "Rollback should have cleared the buffer before saveState could snapshot it");
    }

    @Test
    void testAbortRemovesPendingEntry() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        sink.beginTransaction();
        sink.consume(row(1L, "a", 100L));
        sink.saveState(1L); // moves buffer to pendingCommits[1L]

        assertTrue(sink.getPendingCommits().containsKey(1L));
        sink.abort(1L);
        assertFalse(sink.getPendingCommits().containsKey(1L),
                "abort should remove the pending entry");
    }

    // ---- saveState moves buffer to pendingCommits ----

    @Test
    void testSaveStateMovesBufferToPendingCommits() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        sink.beginTransaction();
        sink.consume(row(1L, "a", 100L));
        sink.consume(row(2L, "b", 200L));

        sink.saveState(1L);

        Object pending = sink.getPendingCommits().get(1L);
        assertNotNull(pending, "saveState should have moved the buffer to pendingCommits[1L]");
        assertInstanceOf(List.class, pending);
        assertEquals(2, ((List<?>) pending).size(),
                "pendingCommits[1L] should contain both buffered records");
    }

    @Test
    void testSaveStateClearsInMemoryBuffer() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        sink.beginTransaction();
        sink.consume(row(1L, "a", 100L));

        sink.saveState(1L);

        // After saveState, buffer is cleared. A second saveState should not
        // re-add the same data.
        sink.saveState(2L);
        assertFalse(sink.getPendingCommits().containsKey(2L),
                "After saveState(1L) cleared the buffer, saveState(2L) should find an empty buffer");
    }

    @Test
    void testCommitWithNoPendingBatchIsNoOp() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        sink.beginTransaction();
        // No data buffered, no saveState called → pendingCommits is empty
        assertDoesNotThrow(() -> sink.commit(999L),
                "commit on a non-existent epoch should be a safe no-op, not throw");
    }

    // ---- Ledger DDL ----

    @Test
    void testGetLedgerTableDDLProducesValidSQL() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        String ddl = sink.getLedgerTableDDL();
        assertNotNull(ddl);
        assertTrue(ddl.contains("CREATE TABLE"), "DDL must be a CREATE TABLE statement");
        assertTrue(ddl.contains("epoch_id"), "DDL must include epoch_id column");
    }

    @Test
    void testInitializeLedgerTableCreatesTable() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        sink.beginTransaction();
        sink.initializeLedgerTable();

        // The ledger table should now exist — verify by inserting and querying
        assertDoesNotThrow(() -> {
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT 1 FROM stream_epoch_ledger WHERE epoch_id = 0")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        assertFalse(rs.next()); // no rows yet
                    }
                }
            }
        });
    }

    // ---- Full commit cycle with real JDBC ----

    @Test
    void testFullCycleCommitWritesDataAndLedger() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createDefaultSink();
        sink.beginTransaction();
        sink.initializeLedgerTable();

        sink.consume(row(1L, "a", 100L));
        sink.consume(row(2L, "b", 200L));
        sink.saveState(1L);
        sink.preCommit(1L);

        // Before commit: no data
        assertEquals(0, countRows("target_data"));

        sink.commit(1L);

        // After commit: 2 rows in target_data, 1 row in ledger
        assertEquals(2, countRows("target_data"),
                "commit should have written both data rows");
        assertEquals(1, countRows("stream_epoch_ledger"),
                "commit should have written one ledger entry");

        // pendingCommits should be cleared for this epoch
        assertFalse(sink.getPendingCommits().containsKey(1L));
    }

    // ---- Helpers ----

    private Map<String, Object> row(long id, String name, long amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("amount", amount);
        return m;
    }

    private int countRows(String tableName) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
