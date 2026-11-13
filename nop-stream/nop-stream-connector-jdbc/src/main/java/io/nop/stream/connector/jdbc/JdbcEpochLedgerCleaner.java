/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.jdbc;

import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.stream.core.exceptions.StreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONFIG_ERROR;

/**
 * Plan 2511 C3-002 (R01 ledger-retention-policy SSOT member row R2-08 ④, face token
 * {@code epochledger}): form-A periodic retention cleanup of the JDBC epoch ledger
 * ({@code stream_epoch_ledger_v2}).
 *
 * <p>Anchor column = {@code committed_at} (written once on the successful commit path,
 * never rewritten by business semantics), predicate = {@code committed_at < now -
 * retention}. The retention window must stay &ge; the re-submittable epoch window
 * (SSOT §3.1 item 4): deleting not-yet-expired rows would break the recovery
 * idempotent-commit guard, so the default 30 days deliberately overshoots.
 *
 * <p>Lifecycle wiring (anti-hollow, SSOT ruling ② / the C7-005 precedent): the owning
 * {@link JdbcTwoPhaseCommitSink} starts this cleaner on its first
 * {@code beginTransaction()} (production-reachable: every deployed job passes there)
 * and stops it on {@code close()}. Scheduling follows the insulation contract (timer
 * only triggers, task body hops to a worker, {@link IPeriodicTaskListener} observes
 * failures): a failed round is logged ERROR + counted — never swallowed, never
 * terminal for the chain.
 *
 * <p>Config keys (SSOT §3.2, face {@code nop.stream.ledger.epochledger.*}) and their
 * defaults live in {@link EpochLedgerConfigs}; revisions go through the SSOT.
 */
public class JdbcEpochLedgerCleaner extends LifeCycleSupport {
    static final Logger LOG = LoggerFactory.getLogger(JdbcEpochLedgerCleaner.class);

    public static final String TASK_NAME = "nop-stream-epochledger-cleanup";

    /** SSOT §3.1 item 2: cleanup interval floor (below it = assembly fail-fast). */
    public static final long MIN_CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final IJdbcTemplate jdbcTemplate;
    private final String querySpace;
    private final String ledgerTableName;

    private IScheduledExecutor timer;
    private java.util.concurrent.Executor worker;
    /** Test seam: a pre-bound executor (R01 precedent: RetryEngineImpl.getExecutor). */
    private IScheduledExecutor boundExecutor;
    private final Integer retentionDays;
    private final Duration cleanupInterval;
    private final int deleteBatchSize;

    private volatile Future<?> cleanupFuture;
    private final AtomicLong cleanupFailureCount = new AtomicLong();
    /** Lazily resolved escaped identifiers (dialect resolution mirrors the sink's lazy init). */
    private transient volatile String escapedSqlNames;

    private static final String ESC_NAMESPACE = "sink_namespace";
    private static final String ESC_EPOCH = "epoch_id";
    private static final String ESC_SUBTASK = "subtask_id";
    private static final String ESC_TIMESTAMP = "committed_at";

    private String ledgerTableName() {
        String names = escapedSqlNames;
        if (names == null) {
            synchronized (this) {
                if (escapedSqlNames == null) {
                    io.nop.dao.dialect.IDialect dialect =
                            jdbcTemplate.getDialectForQuerySpace(querySpace);
                    escapedSqlNames = dialect.escapeSQLName(ledgerTableName);
                }
                names = escapedSqlNames;
            }
        }
        return names;
    }

    public JdbcEpochLedgerCleaner(IJdbcTemplate jdbcTemplate, String querySpace,
                                  String ledgerTableName, int retentionDays,
                                  Duration cleanupInterval, int deleteBatchSize) {
        if (jdbcTemplate == null) {
            throw new StreamException(ERR_STREAM_CONFIG_ERROR).param(ARG_ARG_NAME, "jdbcTemplate");
        }
        if (ledgerTableName == null || ledgerTableName.isEmpty()) {
            throw new StreamException(ERR_STREAM_CONFIG_ERROR).param(ARG_ARG_NAME, "ledgerTableName");
        }
        // SSOT §3.1: negative retention = assembly fail-fast; 0 = explicit disable
        if (retentionDays < 0) {
            throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                    .param(ARG_ARG_NAME, EpochLedgerConfigs.CFG_LEDGER_RETENTION_DAYS.getName())
                    .param(ARG_DETAIL, "retention-days must be >= 0 (0 disables cleanup), got: "
                            + retentionDays);
        }
        if (cleanupInterval == null || cleanupInterval.toMillis() < MIN_CLEANUP_INTERVAL_MILLIS) {
            throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                    .param(ARG_ARG_NAME, EpochLedgerConfigs.CFG_LEDGER_CLEANUP_INTERVAL.getName())
                    .param(ARG_DETAIL, "cleanup-interval must be >= 60s, got: " + cleanupInterval);
        }
        if (deleteBatchSize <= 0) {
            throw new StreamException(ERR_STREAM_CONFIG_ERROR)
                    .param(ARG_ARG_NAME, EpochLedgerConfigs.CFG_LEDGER_DELETE_BATCH_SIZE.getName())
                    .param(ARG_DETAIL, "delete-batch-size must be positive, got: " + deleteBatchSize);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace != null ? querySpace : "";
        this.ledgerTableName = ledgerTableName;
        this.retentionDays = retentionDays;
        this.cleanupInterval = cleanupInterval;
        this.deleteBatchSize = deleteBatchSize;
    }

    /**
     * Assembly projection of the {@code nop.stream.ledger.epochledger.*} keys (SSOT
     * §3.2). Deployments that do not override the keys get exactly the SSOT defaults.
     */
    public static JdbcEpochLedgerCleaner forConfig(IJdbcTemplate jdbcTemplate, String querySpace,
                                                   String ledgerTableName) {
        return new JdbcEpochLedgerCleaner(jdbcTemplate, querySpace, ledgerTableName,
                EpochLedgerConfigs.CFG_LEDGER_RETENTION_DAYS.get(),
                EpochLedgerConfigs.CFG_LEDGER_CLEANUP_INTERVAL.get(),
                EpochLedgerConfigs.CFG_LEDGER_DELETE_BATCH_SIZE.get());
    }

    public void setTimer(IScheduledExecutor timer) {
        this.timer = timer;
    }

    public void setWorker(java.util.concurrent.Executor worker) {
        this.worker = worker;
    }

    /** Test seam: inject a pre-bound executor to observe the 6-param schedule call. */
    public void setBoundExecutor(IScheduledExecutor executor) {
        this.boundExecutor = executor;
    }

    public long getCleanupFailureCount() {
        return cleanupFailureCount.get();
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    @Override
    protected void doStart() {
        if (retentionDays == 0) {
            LOG.info("nop.stream.ledger.epochledger.cleanup-disabled: retention-days=0 (explicit opt-out)");
            return;
        }
        if (timer == null) {
            timer = GlobalExecutors.globalTimer();
        }
        if (worker == null) {
            worker = GlobalExecutors.globalWorker();
        }
        IScheduledExecutor executor = boundExecutor != null
                ? boundExecutor
                : timer.executeOn(worker);
        long intervalMillis = cleanupInterval.toMillis();
        long retentionMillis = retentionDays * 24L * 3600_000L;
        cleanupFuture = executor.scheduleWithFixedDelay(
                () -> purgeExpiredLedgerRows(retentionMillis, deleteBatchSize),
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS,
                TASK_NAME, new IPeriodicTaskListener() {
                    @Override
                    public void onRunFailure(Throwable e, long consecutiveFailures) {
                        cleanupFailureCount.incrementAndGet();
                        LOG.error("nop.stream.ledger.epochledger.cleanup-fail:table={},"
                                + "consecutiveFailures={}", ledgerTableName, consecutiveFailures, e);
                    }

                    @Override
                    public void onChainRejected(Throwable e) {
                        cleanupFailureCount.incrementAndGet();
                        LOG.error("nop.stream.ledger.epochledger.cleanup-chain-rejected: cleanup chain"
                                + " terminated by executor rejection, will not resurrect", e);
                    }
                });
        LOG.info("nop.stream.ledger.epochledger.cleanup-started:table={},retentionDays={},"
                + "intervalMillis={},batchSize={}", ledgerTableName, retentionDays,
                intervalMillis, deleteBatchSize);
    }

    @Override
    protected void doStop() {
        Future<?> future = cleanupFuture;
        if (future != null) {
            future.cancel(false);
            cleanupFuture = null;
            LOG.info("nop.stream.ledger.epochledger.cleanup-stopped:table={}", ledgerTableName);
        }
    }

    /**
     * One bounded cleanup round: pick up to {@code deleteBatchSize} primary-key tuples
     * older than the retention cutoff on the {@code committed_at} anchor, delete them
     * by primary key inside one transaction. Idempotent: a repeated round over the
     * same state deletes nothing new. Failures propagate to the insulation listener
     * (ERROR + failure counter), they are never swallowed here.
     *
     * @return the number of ledger rows deleted in this round
     */
    public long purgeExpiredLedgerRows(long retentionMillis, int batchSize) {
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - retentionMillis);
        List<Object[]> keys = pickExpiredKeys(cutoff, batchSize);
        if (keys.isEmpty()) {
            return 0L;
        }
        long deleted = 0L;
        Connection connection = jdbcTemplate.openConnection(querySpace);
        boolean committed = false;
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + ledgerTableName() + " WHERE " + ESC_NAMESPACE + " = ?"
                            + " AND " + ESC_EPOCH + " = ? AND " + ESC_SUBTASK + " = ?")) {
                for (Object[] key : keys) {
                    ps.setString(1, (String) key[0]);
                    ps.setLong(2, (Long) key[1]);
                    ps.setInt(3, (Integer) key[2]);
                    deleted += ps.executeUpdate();
                }
            }
            connection.commit();
            committed = true;
            if (deleted > 0) {
                LOG.debug("nop.stream.ledger.epochledger.cleanup-round:table={},deleted={}",
                        ledgerTableName, deleted);
            }
            return deleted;
        } catch (SQLException | RuntimeException e) {
            // SSOT §3.1 item 5: a failed round is ERROR-visible + counted, never
            // swallowed silently. The failure must also reach the insulation
            // listener (onRunFailure), so it is rethrown after logging/counting —
            // the insulation layer (PeriodicTaskHelper) keeps the periodic chain
            // alive across failed rounds.
            LOG.error("nop.stream.ledger.epochledger.cleanup-fail:table={}", ledgerTableName, e);
            cleanupFailureCount.incrementAndGet();
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new StreamException("jdbc epoch ledger cleanup round failed: " + e.getMessage(), e);
        } finally {
            if (!committed) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackErr) {
                    LOG.warn("nop.stream.ledger.epochledger.cleanup-rollback-fail", rollbackErr);
                }
            }
            try {
                connection.setAutoCommit(true);
            } catch (SQLException autoCommitErr) {
                LOG.warn("nop.stream.ledger.epochledger.cleanup-autocommit-restore-fail", autoCommitErr);
            }
            try {
                connection.close();
            } catch (SQLException closeErr) {
                LOG.warn("nop.stream.ledger.epochledger.cleanup-close-fail", closeErr);
            }
        }
    }

    /**
     * Bounded pick on the {@code committed_at} anchor (SSOT §3.1 item 3: never a
     * full-table delete; the anchor column is explicit — no implicit selection).
     */
    private List<Object[]> pickExpiredKeys(Timestamp cutoff, int batchSize) {
        List<Object[]> keys = new ArrayList<>();
        String sql = "SELECT " + ESC_NAMESPACE + ", " + ESC_EPOCH + ", " + ESC_SUBTASK + " FROM "
                + ledgerTableName() + " WHERE " + ESC_TIMESTAMP + " < ? ORDER BY " + ESC_EPOCH + " LIMIT ?";
        try (Connection connection = jdbcTemplate.openConnection(querySpace);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, cutoff);
            ps.setInt(2, batchSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(new Object[]{rs.getString(1), rs.getLong(2), rs.getInt(3)});
                }
            }
            return keys;
        } catch (SQLException | RuntimeException e) {
            // SSOT §3.1 item 5: log + count, then rethrow so the failure reaches
            // the insulation listener instead of masquerading as an empty round
            LOG.error("nop.stream.ledger.epochledger.cleanup-fail:table={}", ledgerTableName, e);
            cleanupFailureCount.incrementAndGet();
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new StreamException("jdbc epoch ledger cleanup pick failed: " + e.getMessage(), e);
        }
    }
}
