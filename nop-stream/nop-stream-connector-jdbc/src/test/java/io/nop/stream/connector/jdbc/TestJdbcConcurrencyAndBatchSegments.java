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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Plan 358 Phase 3 focused tests (JDBC sink contract fixes):
 * <ul>
 *   <li>Fix-10: lazy initialization is race-safe — concurrent first use from the
 *       saveState (task) thread and the commit (notification) thread cannot
 *       produce an unsafe publication or duplicated init.</li>
 *   <li>Fix-11: large epochs are committed in batch segments; the result equals
 *       the whole-batch commit (same rows, single transaction, ledger recorded).</li>
 * </ul>
 */
class TestJdbcConcurrencyAndBatchSegments {

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

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "CREATE TABLE target_data (id BIGINT, name VARCHAR(100), amount BIGINT)")) {
            ps.execute();
        }
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    private JdbcTwoPhaseCommitSink<Map<String, Object>> createSink(int maxBatchSize) {
        JdbcTwoPhaseCommitSinkBuilder<Map<String, Object>> builder = JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("target_data")
                .columns("id", "name", "amount")
                .recordMapper(m -> m);
        if (maxBatchSize > 0) {
            builder.maxBatchSize(maxBatchSize);
        }
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = builder.build();
        sink.initializeLedgerTable();
        return sink;
    }

    private Map<String, Object> row(long id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", "n" + id);
        m.put("amount", id * 10);
        return m;
    }

    private int countDataRows() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM target_data");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void concurrentFirstInitializationIsSafe() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink(0);

        // Barrier-synchronized concurrent first use: before Fix-10 the unsynchronized
        // lazy init could publish partially-initialized state (NPE on dialect/SQL).
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CyclicBarrier barrier = new CyclicBarrier(threads);
            java.util.List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    sink.beginTransaction();
                    return null;
                }));
            }
            for (Future<Void> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // The sink remains fully functional after the concurrent init.
        sink.invoke(row(1));
        sink.saveState(1L);
        sink.commit(1L);
        assertEquals(1, countDataRows(), "post-concurrent-init commit must work");
    }

    @Test
    void largeEpochIsCommittedInSegmentsWithIdenticalResult() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink(2); // maxBatchSize=2

        for (long id = 1; id <= 5; id++) {
            sink.invoke(row(id));
        }
        sink.saveState(7L);
        sink.commit(7L);

        assertEquals(5, countDataRows(),
                "segmented batch execution must commit the same rows as a whole-batch commit");
        assertEquals(1, countLedgerRows(7L), "ledger entry must be recorded for the segmented commit");
    }

    private int countLedgerRows(long epochId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM stream_epoch_ledger_v2 WHERE epoch_id = ?");
        ) {
            ps.setLong(1, epochId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Plan 2511 C3-002 / C10-007 rewrite (假保护消号：the old countLedgerRows assertion
     * covered write-existence only, zero retention/cleanup coverage). Full retention
     * assertion set on the REAL commit path: multi-epoch commits → backdate old rows'
     * {@code committed_at} anchor → form-A cleanup bounds the row count (expired
     * purged / fresh retained) → repeat round is a no-op (idempotence) → the retained
     * recent epoch's idempotent commit guard still hits (no data re-write).
     */
    @Test
    void multiEpochLedgerRowsBoundedByRetention() throws Exception {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink = createSink(0);

        // multi-epoch writes over the REAL commit path (epochs 1..4)
        for (long epoch = 1; epoch <= 4; epoch++) {
            sink.invoke(row(epoch));
            sink.saveState(epoch);
            sink.commit(epoch);
        }
        assertEquals(4, totalLedgerRows());

        // age epochs 1..2 beyond the retention window (committed_at anchor backdated)
        backdateLedgerRows(1);
        backdateLedgerRows(2);

        JdbcEpochLedgerCleaner cleaner = new JdbcEpochLedgerCleaner(jdbcTemplate, "",
                "stream_epoch_ledger_v2", 30, java.time.Duration.ofHours(1), 1000);
        long deleted = cleaner.purgeExpiredLedgerRows(30L * 24L * 3600_000L, 1000);
        assertEquals(2, deleted, "expired epochs must be purged");
        assertEquals(2, totalLedgerRows(), "ledger rows must be bounded (high-water retention)");
        assertEquals(1, countLedgerRows(3), "unexpired rows must be retained");
        assertEquals(1, countLedgerRows(4), "the latest epoch must be retained");

        // idempotence: repeat round deletes nothing
        assertEquals(0, cleaner.purgeExpiredLedgerRows(30L * 24L * 3600_000L, 1000));
        assertEquals(2, totalLedgerRows());

        // recovery idempotent guard intact for a retained epoch: re-commit is a
        // ledger-guard skip, not a duplicate data write
        sink.getPendingCommits().put(3L, new java.util.ArrayList<>());
        sink.commit(3L);
        assertEquals(1, countLedgerRows(3));
        assertEquals(4, countDataRows(), "guard hit must not re-write epoch 3's data rows");
    }

    private int totalLedgerRows() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM stream_epoch_ledger_v2");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void backdateLedgerRows(long epochId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE stream_epoch_ledger_v2 SET committed_at = ? WHERE epoch_id = ?")) {
            ps.setTimestamp(1, new java.sql.Timestamp(System.currentTimeMillis() - 31L * 24L * 3600_000L));
            ps.setLong(2, epochId);
            ps.executeUpdate();
        }
    }

    @Test
    void zeroMaxBatchSizeIsRejected() {
        assertDoesNotThrow(() -> createSink(1));
        org.junit.jupiter.api.Assertions.assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                () -> JdbcTwoPhaseCommitSink.<Map<String, Object>>builder()
                        .jdbcTemplate(jdbcTemplate)
                        .tableName("target_data")
                        .columns("id", "name", "amount")
                        .recordMapper(m -> m)
                        .maxBatchSize(0)
                        .build());
    }
}
