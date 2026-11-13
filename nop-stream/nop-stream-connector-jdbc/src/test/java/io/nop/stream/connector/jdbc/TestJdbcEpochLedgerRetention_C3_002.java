/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2511 C3-002 / C10-007 (R01 ledger-retention-policy SSOT member row, face
 * {@code epochledger}): JDBC form-A retention cleanup of {@code stream_epoch_ledger_v2}.
 *
 * <p>Assertion set (SSOT §3.4 member row): expired-row cleanup on the
 * {@code committed_at} anchor, unexpired-row retention (the window must stay &ge; the
 * re-submittable epoch window — recovery idempotent guard intact), idempotence
 * (repeat rounds delete nothing), bounded batch pick (delete-batch-size), assembly
 * fail-fast on illegal config, and the anti-hollow lifecycle wiring (the scheduler
 * really invokes the cleanup body — the C7-005 precedent).
 *
 * <p>Red ④ carrier: {@link #expiredRowsPurgedAndFreshRetained} — pre-fix (no cleanup
 * entry / purge body disabled by mutation) the expired rows survive and the total row
 * count grows without bound.
 */
class TestJdbcEpochLedgerRetention_C3_002 {

    private static final long DAY_MS = 24L * 3600_000L;
    private static final String LEDGER_TABLE = "stream_epoch_ledger_v2";

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
        dataSource.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName()
                + StringHelper.generateUUID() + ";MODE=MySQL");
        jdbcTemplate = JdbcFactory.newJdbcTemplateFor(dataSource);
        JdbcTwoPhaseCommitSink<Object> ddlSource = JdbcTwoPhaseCommitSink.builder()
                .jdbcTemplate(jdbcTemplate).tableName("t").columns("id").recordMapper(m -> new java.util.LinkedHashMap<>()).build();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(ddlSource.getLedgerTableDDL())) {
            ps.execute();
        }
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    private void insertLedgerRow(String namespace, long epoch, int subtask, long committedAtMillis) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO " + LEDGER_TABLE
                             + " (sink_namespace, epoch_id, subtask_id, committed_at) VALUES (?,?,?,?)")) {
            ps.setString(1, namespace);
            ps.setLong(2, epoch);
            ps.setInt(3, subtask);
            ps.setTimestamp(4, new Timestamp(committedAtMillis));
            ps.executeUpdate();
        }
    }

    private int countLedgerRows() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + LEDGER_TABLE);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private JdbcEpochLedgerCleaner newCleaner(int retentionDays, Duration interval, int batchSize) {
        return new JdbcEpochLedgerCleaner(jdbcTemplate, "", LEDGER_TABLE,
                retentionDays, interval, batchSize);
    }

    /** Red ④ carrier (see class javadoc). */
    @Test
    void expiredRowsPurgedAndFreshRetained() throws Exception {
        long now = System.currentTimeMillis();
        insertLedgerRow("ns", 1, 0, now - 31 * DAY_MS);
        insertLedgerRow("ns", 2, 0, now - 40 * DAY_MS);
        insertLedgerRow("ns", 3, 1, now - 31 * DAY_MS);
        // fresh epochs (recoverable window): must survive
        insertLedgerRow("ns", 100, 0, now - DAY_MS);
        insertLedgerRow("ns", 101, 0, now);
        assertEquals(5, countLedgerRows());

        JdbcEpochLedgerCleaner cleaner = newCleaner(30, Duration.ofHours(1), 1000);
        long deleted = cleaner.purgeExpiredLedgerRows(30L * DAY_MS, 1000);
        assertEquals(3, deleted, "expired rows must be purged");
        assertEquals(2, countLedgerRows(), "fresh (recoverable-window) rows must be retained");

        // idempotence: a repeat round over the same state deletes nothing
        long deletedAgain = cleaner.purgeExpiredLedgerRows(30L * DAY_MS, 1000);
        assertEquals(0, deletedAgain);
        assertEquals(2, countLedgerRows());
    }

    /** SSOT §3.1 item 3: bounded pick — a small delete-batch-size converges over rounds. */
    @Test
    void boundedBatchPickConvergesOverRounds() throws Exception {
        long now = System.currentTimeMillis();
        for (long e = 1; e <= 5; e++) {
            insertLedgerRow("ns", e, 0, now - 31 * DAY_MS);
        }
        JdbcEpochLedgerCleaner cleaner = newCleaner(30, Duration.ofHours(1), 2);

        long round1 = cleaner.purgeExpiredLedgerRows(30L * DAY_MS, 2);
        assertEquals(2, round1, "one round must delete at most delete-batch-size rows");
        assertEquals(3, countLedgerRows());
        long round2 = cleaner.purgeExpiredLedgerRows(30L * DAY_MS, 2);
        assertEquals(2, round2);
        long round3 = cleaner.purgeExpiredLedgerRows(30L * DAY_MS, 2);
        assertEquals(1, round3);
        assertEquals(0, countLedgerRows(), "all expired rows must eventually converge to zero");
    }

    /** A failed DB round is counted, logged, and rethrown — never swallowed. */
    @Test
    void cleanupFailureIsCountedNotSwallowed() throws Exception {
        insertLedgerRow("ns", 1, 0, System.currentTimeMillis() - 31L * DAY_MS);
        JdbcEpochLedgerCleaner cleaner = newCleaner(30, Duration.ofHours(1), 1000);
        dataSource.close(); // force the pick to fail
        assertThrows(RuntimeException.class,
                () -> cleaner.purgeExpiredLedgerRows(30L * DAY_MS, 1000),
                "a failed round must propagate so the insulation listener observes it");
        assertTrue(cleaner.getCleanupFailureCount() >= 1,
                "the failed round must be visible via the failure counter");
    }

    /** SSOT §3.1 / §3.2: illegal config is a typed assembly-time fail-fast. */
    @Test
    void illegalConfigFailsFast() {
        assertThrows(StreamException.class,
                () -> newCleaner(-1, Duration.ofHours(1), 1000),
                "negative retention-days must fail fast");
        assertThrows(StreamException.class,
                () -> newCleaner(30, Duration.ofMillis(59_999), 1000),
                "cleanup-interval below the 60s floor must fail fast");
        assertThrows(StreamException.class,
                () -> newCleaner(30, Duration.ofHours(1), 0),
                "delete-batch-size <= 0 must fail fast");
    }

    /** retention-days = 0 is the explicit disable: doStart schedules nothing. */
    @Test
    void zeroRetentionDisablesScheduling() {
        FakeScheduledExecutor timer = new FakeScheduledExecutor();
        JdbcEpochLedgerCleaner cleaner = newCleaner(0, Duration.ofHours(1), 1000);
        cleaner.setBoundExecutor(timer);
        cleaner.start();
        assertTrue(cleaner.isActive());
        assertNull(timer.command.get(), "disabled cleaner must not schedule a cleanup task");
    }

    /**
     * Anti-hollow wiring (rule 23 / SSOT ruling ②): doStart really schedules the
     * cleanup body via the insulation executor, and running the scheduled command
     * really deletes expired rows (the C7-005 empty-shell shape would leave rows).
     */
    @Test
    void lifecycleChainReallyInvokesCleanup() throws Exception {
        long now = System.currentTimeMillis();
        insertLedgerRow("ns", 1, 0, now - 31 * DAY_MS);
        insertLedgerRow("ns", 2, 0, now);

        FakeScheduledExecutor timer = new FakeScheduledExecutor();
        JdbcEpochLedgerCleaner cleaner = newCleaner(30, Duration.ofHours(1), 1000);
        cleaner.setBoundExecutor(timer);
        cleaner.start();
        assertTrue(cleaner.isActive());
        assertNotNull(timer.command.get(), "doStart must schedule the cleanup task");
        assertEquals(Duration.ofHours(1).toMillis(), timer.delayMillis.get(),
                "schedule period must be the cleanup interval");

        timer.command.get().run();
        assertEquals(1, countLedgerRows(), "the scheduled cleanup must really purge expired rows");
    }

    /**
     * Sink wiring: beginTransaction (production-reachable chain) starts the cleaner,
     * close() stops it — the deployment teardown contract the Kafka 2PC sink uses.
     */
    @Test
    void sinkLifecycleStartsAndStopsCleaner() {
        JdbcTwoPhaseCommitSink<Object> sink = JdbcTwoPhaseCommitSink.builder()
                .jdbcTemplate(jdbcTemplate).tableName("t").columns("id").recordMapper(m -> new java.util.LinkedHashMap<>())
                .build();
        sink.beginTransaction();
        JdbcEpochLedgerCleaner cleaner = sink.getLedgerCleanerForTest();
        assertNotNull(cleaner, "beginTransaction must start the retention cleaner (form-A wiring)");
        assertTrue(cleaner.isActive());
        sink.close();
        assertFalse(cleaner.isActive(), "close must stop the cleaner's schedule chain");
    }

    /**
     * C10-007 rewrite carrier (anchored in the SSOT member row): retention preserves
     * the recovery idempotent-commit guard — a RECENT epoch's ledger row survives, so
     * a recovery re-commit still hits the guard instead of re-writing data.
     */
    @Test
    void retainedRecentEpochKeepsIdempotentGuardAlive() throws Exception {
        long now = System.currentTimeMillis();
        insertLedgerRow("job|vertex", 50, 0, now);
        JdbcEpochLedgerCleaner cleaner = newCleaner(30, Duration.ofHours(1), 1000);
        cleaner.purgeExpiredLedgerRows(30L * DAY_MS, 1000);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM " + LEDGER_TABLE
                             + " WHERE sink_namespace = ? AND epoch_id = ? AND subtask_id = ?")) {
            ps.setString(1, "job|vertex");
            ps.setLong(2, 50);
            ps.setInt(3, 0);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(),
                        "recent ledger row must survive retention (re-commit guard intact)");
            }
        }
    }

    /** Capture-only scheduled executor (wiring assertion carrier, R01 precedent). */
    static class FakeScheduledExecutor implements IScheduledExecutor {
        final AtomicReference<String> taskName = new AtomicReference<>();
        final AtomicReference<Long> delayMillis = new AtomicReference<>();
        final AtomicReference<Runnable> command = new AtomicReference<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        class ObservedFuture extends CompletableFuture<Object> {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled.set(true);
                return super.cancel(mayInterruptIfRunning);
            }
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                                TimeUnit unit, String taskName,
                                                io.nop.commons.concurrent.executor.IPeriodicTaskListener listener) {
            this.taskName.set(taskName);
            this.delayMillis.set(unit.toMillis(delay));
            this.command.set(command);
            return new ObservedFuture();
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                                TimeUnit unit) {
            this.command.set(command);
            this.delayMillis.set(unit.toMillis(delay));
            return new ObservedFuture();
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public <V> CompletableFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay,
                                                 TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                             TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> submit(java.util.concurrent.Callable<V> callable) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return new CompletableFuture<>();
        }

        @Override
        public void destroy() {
            // test double: nothing to release
        }

        @Override
        public void refreshConfig() {
            // test double: no hot-reload face
        }

        @Override
        public String getName() {
            return "fake-scheduled-executor";
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolConfig getConfig() {
            return new io.nop.commons.concurrent.executor.ThreadPoolConfig();
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolStats stats() {
            return null;
        }
    }
}
