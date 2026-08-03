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

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.operators.StreamSinkOperator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 deep unit tests: saveState ordering, idempotent commit guard,
 * restore path (durable re-commit + non-durable abort), and pendingCommits
 * serialization round-trip.
 */
class TestJdbcTwoPhaseCommitSinkDeep {

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

    @SuppressWarnings("unchecked")
    private JdbcTwoPhaseCommitSink<Map<String, Object>> createSink() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(Function.identity())
                .build();
        sink.beginTransaction();
        sink.initializeLedgerTable();
        return sink;
    }

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

    private int countDataRowsById(long id) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM target_data WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ---- saveState ordering: epoch N batch captured in THIS checkpoint ----

    @Test
    void testSaveStateCapturesEpochNInThisCheckpoint() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();

        sink.consume(row(1L, "a", 100L));
        sink.consume(row(2L, "b", 200L));

        // CRITICAL: saveState(N) must move the batch to pendingCommits[N]
        // BEFORE super.saveState serializes it — otherwise the batch lags by
        // one epoch and is lost on restore.
        TaskStateSnapshot snapshot = sink.saveState(5L);

        assertNotNull(snapshot);
        assertNotNull(sink.getPendingCommits().get(5L),
                "pendingCommits[5L] must contain the batch after saveState(5L) — NOT after preCommit");

        Object raw = snapshot.getOperatorState(TwoPhaseCommitSinkFunction.PENDING_COMMITS_KEY);
        assertNotNull(raw);
        assertInstanceOf(Map.class, raw);
        @SuppressWarnings("unchecked")
        Map<Long, Object> serialized = (Map<Long, Object>) raw;
        assertNotNull(serialized.get(5L),
                "The serialized snapshot must contain epoch 5L (proving saveState captured it BEFORE serialization)");

        // The buffer should be cleared
        sink.preCommit(5L);
        // preCommit should NOT add anything new (buffer already cleared by saveState)
        assertNull(sink.getPendingCommits().get(6L),
                "preCommit must not add new entries — saveState already cleared the buffer");
    }

    @Test
    void testSaveStateLagByOneEpochIsAvoided() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();

        // Epoch 1: buffer records, saveState, preCommit
        sink.consume(row(1L, "a", 100L));
        sink.saveState(1L);
        sink.preCommit(1L);

        // The batch for epoch 1 MUST be in pendingCommits[1L] (not missing or in [0L])
        assertNotNull(sink.getPendingCommits().get(1L),
                "Epoch 1 batch must be in pendingCommits[1L] — not lagging");

        // Epoch 2: buffer new records, saveState, preCommit
        sink.consume(row(2L, "b", 200L));
        sink.saveState(2L);
        sink.preCommit(2L);

        assertNotNull(sink.getPendingCommits().get(2L),
                "Epoch 2 batch must be in pendingCommits[2L]");
        assertNotNull(sink.getPendingCommits().get(1L),
                "Epoch 1 batch must still be there (not consumed by commit yet)");
    }

    // ---- Idempotent commit: repeat commit same epoch → no duplicates ----

    @Test
    void testIdempotentCommitNoDuplicates() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();

        sink.consume(row(1L, "a", 100L));
        sink.consume(row(2L, "b", 200L));
        sink.saveState(1L);

        // First commit: writes 2 rows + ledger
        sink.commit(1L);
        assertEquals(2, countRows("target_data"));
        assertEquals(1, countRows("stream_epoch_ledger"));
        assertFalse(sink.getPendingCommits().containsKey(1L));

        // Simulate recovery: put the same batch back and re-commit
        // (this is what restoreFromEpoch does for durable-but-uncommitted epochs)
        List<Map<String, Object>> batch = new ArrayList<>();
        batch.add(row(1L, "a", 100L));
        batch.add(row(2L, "b", 200L));
        sink.getPendingCommits().put(1L, batch);

        // Second commit (idempotent re-commit): ledger exists → skip data write
        sink.commit(1L);

        assertEquals(2, countRows("target_data"),
                "Idempotent re-commit must NOT produce duplicate data rows");
        assertEquals(1, countRows("stream_epoch_ledger"),
                "Idempotent re-commit must NOT produce duplicate ledger rows (PK guard)");
    }

    @Test
    void testIdempotentCommitGuardAcrossMultipleEpochs() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();

        // Epoch 1: commit successfully
        sink.consume(row(1L, "a", 100L));
        sink.saveState(1L);
        sink.commit(1L);

        // Epoch 2: commit successfully
        sink.consume(row(2L, "b", 200L));
        sink.saveState(2L);
        sink.commit(2L);

        assertEquals(2, countRows("target_data"));

        // Simulate recovery: re-commit epoch 1 and 2
        sink.getPendingCommits().put(1L, Collections.singletonList(row(1L, "a", 100L)));
        sink.getPendingCommits().put(2L, Collections.singletonList(row(2L, "b", 200L)));

        sink.finishCommit(2L, true); // subsuming commit for epochs <= 2

        assertEquals(2, countRows("target_data"),
                "Re-commit of epochs 1 and 2 must not produce duplicates (ledger guard)");
    }

    // ---- abort cleanup ----

    @Test
    void testAbortClearsPendingEntry() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();
        sink.consume(row(1L, "a", 100L));
        sink.saveState(1L);

        assertTrue(sink.getPendingCommits().containsKey(1L));

        sink.abort(1L);

        assertFalse(sink.getPendingCommits().containsKey(1L),
                "abort must remove the pending entry");
        assertEquals(0, countRows("target_data"),
                "abort must not write any data (commit was never called)");
        assertEquals(0, countRows("stream_epoch_ledger"),
                "abort must not write a ledger entry");
    }

    @Test
    void testAbortOnNonExistentEpochIsSafe() {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();
        assertDoesNotThrow(() -> sink.abort(999L),
                "abort on a non-existent epoch should be a safe no-op");
    }

    // ---- pendingCommits serialization round-trip ----

    @Test
    void testPendingCommitsSurviveSerializationRoundTrip() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();
        sink.consume(row(1L, "a", 100L));
        sink.consume(row(2L, "b", 200L));
        sink.saveState(1L);

        TaskStateSnapshot snapshot = sink.saveState(1L);

        // Simulate the save → restore path that StreamSinkOperator uses:
        // saveState → snapshot → restoreState rebuilds pendingCommits
        OperatorSnapshotResult snapshotResult = new OperatorSnapshotResult();
        for (Map.Entry<String, Object> entry : snapshot.getOperatorStates().entrySet()) {
            snapshotResult.putOperatorState("participant-" + entry.getKey(), entry.getValue());
        }

        JdbcTwoPhaseCommitSink<Map<String, Object>> restoredSink = createSink();
        assertTrue(restoredSink.getPendingCommits().isEmpty());

        StreamSinkOperator<Map<String, Object>> operator = new StreamSinkOperator<>(restoredSink);
        operator.restoreState(snapshotResult);

        assertEquals(1, restoredSink.getPendingCommits().size(),
                "Pending commits must be rebuilt from durable snapshot");
        assertNotNull(restoredSink.getPendingCommits().get(1L));

        // The restored batch should be committable
        restoredSink.commit(1L);
        assertEquals(2, countRows("target_data"),
                "Restored batch must be committable after serialization round-trip");
        assertEquals(1, countRows("stream_epoch_ledger"));
    }

    // ---- Restore path: durable re-commit + non-durable abort ----

    @Test
    void testRestoreFromEpochDurableReCommit() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();

        // Epoch 1 already committed (ledger has entry)
        sink.consume(row(1L, "a", 100L));
        sink.saveState(1L);
        sink.commit(1L);
        assertEquals(1, countRows("target_data"));
        assertEquals(1, countRows("stream_epoch_ledger"));

        // Epoch 2 durable but not committed (in pendingCommits, no ledger entry)
        sink.getPendingCommits().put(2L, Collections.singletonList(row(2L, "b", 200L)));

        // Restore at epoch 5: epoch 2 is durable (2 <= 5) → re-commit
        sink.restoreFromEpoch(5L, null);

        assertEquals(2, countRows("target_data"),
                "Durable pending epoch 2 must be re-committed on restore");
        assertTrue(sink.getPendingCommits().isEmpty(),
                "All pending must be cleared after restore");
    }

    @Test
    void testRestoreFromEpochIdempotentForAlreadyCommittedEpoch() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();

        // Epoch 1 already committed
        sink.consume(row(1L, "a", 100L));
        sink.saveState(1L);
        sink.commit(1L);
        assertEquals(1, countRows("target_data"));
        assertEquals(1, countRows("stream_epoch_ledger"));

        // Simulate crash-recovery: pendingCommits still has epoch 1
        // (because finishCommit was never called to remove it)
        sink.getPendingCommits().put(1L, Collections.singletonList(row(1L, "a", 100L)));

        // Restore at epoch 1: epoch 1 is durable (1 <= 1) → re-commit
        // But ledger already has epoch 1 → idempotent skip
        sink.restoreFromEpoch(1L, null);

        assertEquals(1, countRows("target_data"),
                "Re-commit of already-committed epoch must not produce duplicates");
        assertEquals(1, countRows("stream_epoch_ledger"),
                "Ledger must not have duplicate entries (PK guard)");
    }

    @Test
    void testRestoreFromEpochNonDurableAbort() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();

        // Epoch 8 is non-durable (epochId > restore epoch)
        sink.getPendingCommits().put(8L, Collections.singletonList(row(8L, "h", 800L)));

        sink.restoreFromEpoch(3L, null);

        assertEquals(0, countRows("target_data"),
                "Non-durable pending epoch 8 must be aborted, NOT committed");
        assertEquals(0, countRows("stream_epoch_ledger"),
                "No ledger entry for aborted epoch");
        assertTrue(sink.getPendingCommits().isEmpty(),
                "All pending must be cleared after restore");
    }

    @Test
    void testRestoreFromEpochMixedDurableAndNonDurable() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();

        // Durable epochs (will be committed)
        sink.getPendingCommits().put(1L, Collections.singletonList(row(1L, "a", 100L)));
        sink.getPendingCommits().put(2L, Collections.singletonList(row(2L, "b", 200L)));
        // Non-durable epochs (will be aborted)
        sink.getPendingCommits().put(8L, Collections.singletonList(row(8L, "h", 800L)));
        sink.getPendingCommits().put(9L, Collections.singletonList(row(9L, "i", 900L)));

        sink.restoreFromEpoch(3L, null);

        assertEquals(2, countRows("target_data"),
                "Durable epochs 1 and 2 must be committed; non-durable 8 and 9 must be aborted");
        assertEquals(2, countRows("stream_epoch_ledger"),
                "Two ledger entries for committed epochs 1 and 2");
        assertTrue(sink.getPendingCommits().isEmpty());

        // Verify the right rows were written
        assertEquals(1, countDataRowsById(1L));
        assertEquals(1, countDataRowsById(2L));
        assertEquals(0, countDataRowsById(8L));
        assertEquals(0, countDataRowsById(9L));
    }

    // ---- Subsuming commit: finishCommit commits all epochs <= M ----

    @Test
    void testFinishCommitSubsumesMultiplePendingEpochs() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();

        sink.getPendingCommits().put(1L, Collections.singletonList(row(1L, "a", 100L)));
        sink.getPendingCommits().put(2L, Collections.singletonList(row(2L, "b", 200L)));
        sink.getPendingCommits().put(3L, Collections.singletonList(row(3L, "c", 300L)));

        // finishCommit(3, true) commits all epochs <= 3
        sink.finishCommit(3L, true);

        assertEquals(3, countRows("target_data"),
                "All three epochs must be committed by subsuming finishCommit");
        assertEquals(3, countRows("stream_epoch_ledger"));
        assertTrue(sink.getPendingCommits().isEmpty(),
                "All pending must be cleared after subsuming commit");
    }

    @Test
    void testEachCommitUsesIndependentTransaction() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink();

        // This test verifies that multiple epoch commits don't interfere.
        // If they shared a connection, one failure would corrupt all.
        sink.getPendingCommits().put(1L, Collections.singletonList(row(1L, "a", 100L)));
        sink.getPendingCommits().put(2L, Collections.singletonList(row(2L, "b", 200L)));

        // Commit epoch 1 first
        sink.commit(1L);
        assertEquals(1, countRows("target_data"));

        // Commit epoch 2 independently
        sink.commit(2L);
        assertEquals(2, countRows("target_data"));

        // Both ledger entries exist
        assertEquals(2, countRows("stream_epoch_ledger"));
    }
}
