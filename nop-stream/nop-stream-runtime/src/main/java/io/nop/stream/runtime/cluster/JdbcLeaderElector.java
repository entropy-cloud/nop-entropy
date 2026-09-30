/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import io.nop.api.core.time.CoreMetrics;
import java.sql.Timestamp;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.config.AppConfig;
import io.nop.cluster.elector.AbstractPollingLeaderElector;
import io.nop.cluster.elector.LeaderEpoch;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ATTEMPTS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_LEADER_EPOCH_ALLOC_FAILED;

/**
 * Stage 46: a production {@link io.nop.cluster.elector.ILeaderElector} backed by a JDBC
 * lease table, for nop-stream's distributed HA mode. Extends the platform
 * {@link AbstractPollingLeaderElector} contract and uses {@link IJdbcTemplate} (the same
 * raw-JDBC abstraction {@code JdbcClusterRegistry} uses), so nop-stream-runtime can run
 * HA without pulling in the nop-sys-dao ORM stack.
 *
 * <p><strong>Why a nop-stream-runtime elector?</strong> {@code SysDaoLeaderElector}
 * (nop-sys-dao) is the production elector for platform apps that already use the ORM
 * stack. nop-stream-runtime cannot depend on nop-sys-dao (the dependency direction is
 * reversed: nop-sys-dao test -&gt; nop-stream-runtime). For nop-stream's standalone
 * distributed coordinator ({@code JobCoordinatorMain} / {@code MiniStreamCluster}) a
 * JDBC lease elector that needs only {@link IJdbcTemplate} is the zero-infrastructure
 * choice (plan vision: "Phase 4 选 JDBC，零基建").
 *
 * <p><strong>Lease semantics</strong> (mirror {@code SysDaoLeaderElector}):
 * <ul>
 *   <li>Single lease row keyed by {@code cluster_id}; the row holder is the leader.</li>
 *   <li>Leader periodically refreshes {@code expire_at = now + leaseMs}.</li>
 *   <li>A follower that observes {@code expire_at &lt; now} attempts an epoch-bumping
 *       takeover ({@code UPDATE ... WHERE leader_epoch = oldEpoch}); optimistic
 *       concurrency — a 0-rows-affected update means another node raced and won.</li>
 *   <li>On takeover the new leader increments {@code leader_epoch} (cluster-wide
 *       monotonic), which feeds the composite fencing epoch
 *       ({@code leaderEpochValue * EPOCH_SCALE + recoveryGen}).</li>
 * </ul>
 *
 * <p><strong>Epoch monotonicity across lease-row loss</strong> (plan 369 Phase 4,
 * R5-CC-15): the epoch granted when the lease row is MISSING is allocated from a
 * dedicated persistent counter row (table {@code <leaseTable>_epoch_counter},
 * keyed by {@code cluster_id}) that is <em>independent of the lease row and never
 * cleaned up</em> — deleting the lease row (external ops, a wiped table) can no
 * longer roll the fencing epoch back to a constant. The grant is a
 * read-and-increment inside a transaction with an optimistic compare-and-set
 * on the counter value, so concurrent takeovers serialize: each successful
 * grant consumes a distinct, strictly increasing value. The lease-row-exists
 * paths (takeover / restartElection) keep their original optimistic
 * {@code leader_epoch = old + 1} UPDATE mechanism and, after winning, advance
 * the counter row past the granted epoch so the counter never lags behind any
 * epoch ever written to the lease row.
 *
 * <p>Timestamps are stored as {@code BIGINT} epoch-millis (not {@code TIMESTAMP}) to
 * stay dialect-portable across H2/MySQL/Postgres and to avoid per-dialect timestamp
 * arithmetic.
 */
@Internal
public class JdbcLeaderElector extends AbstractPollingLeaderElector {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcLeaderElector.class);

    private static final String DEFAULT_QUERY_SPACE = "default";

    /**
     * Bounded retry budget for the optimistic epoch-counter allocation. Each
     * attempt is a short transaction; exhaustion means an implausible number of
     * concurrent takeovers on one cluster id, surfaced as a typed error rather
     * than an unbounded loop.
     */
    private static final int EPOCH_ALLOC_MAX_ATTEMPTS = 32;

    private final IJdbcTemplate jdbcTemplate;
    private final String querySpace;
    private final String leaseTable;
    private final String epochCounterTable;

    private volatile boolean tableInitialized = false;
    private volatile boolean counterTableInitialized = false;

    public JdbcLeaderElector(IJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DEFAULT_QUERY_SPACE, "nop_stream_leader");
    }

    public JdbcLeaderElector(IJdbcTemplate jdbcTemplate, String querySpace, String leaseTable) {
        this.jdbcTemplate = jdbcTemplate;
        this.querySpace = querySpace != null ? querySpace : DEFAULT_QUERY_SPACE;
        this.leaseTable = leaseTable != null ? leaseTable : "nop_stream_leader";
        this.epochCounterTable = this.leaseTable + "_epoch_counter";
    }

    // ==================== Lifecycle ====================

    @Override
    protected Void checkElection() {
        try {
            ensureLeaseTable();
            int loopCount = 0;
            // Pre-check isStopping() before each iteration body: AbstractLeaderElector.doStop()
            // nulls leaderEpoch, which would make isLeader() return false and route a stopped
            // leader into the checkFollower self-takeover branch (re-acquiring leadership after
            // stop). Checking isStopping() at the loop head prevents a stopped elector from
            // touching the lease row.
            while (!isStopping()) {
                if (loopCount > 20) {
                    break;
                }
                boolean isLeader = isLeader();
                LeaseRow row = readLeaseRow();
                if (isLeader) {
                    if (checkLeader(row)) {
                        break;
                    }
                } else {
                    if (checkFollower(row)) {
                        break;
                    }
                }
                loopCount++;
            }
        } catch (Exception e) {
            LOG.info("nop.stream.leader-elector.check-fail: clusterId={}", getClusterId(), e);
            onException(e);
            onBecomeFollower(null);
            onRestartElection();
        }
        if (!isStopping()) {
            scheduleCheck();
        }
        return null;
    }

    @Override
    public void restartElection() {
        try {
            ensureLeaseTable();
            for (int i = 0; i < 10; i++) {
                LeaseRow row = readLeaseRow();
                if (row == null) {
                    return;
                }
                long nextEpoch = row.leaderEpoch + 1;
                SQL sql = SQL.begin().name("restartElection").querySpace(querySpace)
                        .sql("UPDATE " + leaseTable + " SET leader_epoch = ?, expire_at = ? "
                                        + "WHERE cluster_id = ? AND leader_epoch = ?",
                                nextEpoch, CoreMetrics.currentTimeMillis(), getClusterId(), row.leaderEpoch)
                        .end();
                long affected = jdbcTemplate.executeUpdate(sql);
                if (affected > 0) {
                    LOG.info("nop.stream.leader-elector.restart: clusterId={} epoch {} -> {}",
                            getClusterId(), row.leaderEpoch, nextEpoch);
                    // CC-15: keep the counter row ahead of every epoch written to
                    // the lease row so a later lease-row loss cannot re-grant it.
                    advanceCounterPast(nextEpoch);
                    return;
                }
            }
        } catch (Exception e) {
            LOG.warn("nop.stream.leader-elector.restart-fail: clusterId={}", getClusterId(), e);
        }
    }

    // ==================== Election state machine ====================

    private boolean checkLeader(LeaseRow row) {
        long now = CoreMetrics.currentTimeMillis();

        if (row == null || !getHostId().equals(row.leaderId)) {
            // DB says someone else (or no one) is leader — we lost it.
            onBecomeFollower(toLeaderEpoch(row));
            onRestartElection();
            return false;
        }

        LeaderEpoch current = getLeaderEpoch();
        if (current != null && row.leaderEpoch > current.getEpoch()) {
            // Epoch advanced beyond us — another leader took over.
            onBecomeFollower(toLeaderEpoch(row));
            onRestartElection();
            return false;
        }

        // We hold the lease. Refresh it.
        long newExpireAt = now + getLeaseMs();
        SQL sql = SQL.begin().name("refreshLeader").querySpace(querySpace)
                .sql("UPDATE " + leaseTable + " SET refresh_at = ?, expire_at = ? "
                                + "WHERE cluster_id = ? AND leader_id = ?",
                        now, newExpireAt, getClusterId(), getHostId())
                .end();
        long affected = jdbcTemplate.executeUpdate(sql);
        if (affected == 0) {
            // Lost the lease between read and refresh.
            onBecomeFollower(null);
            onRestartElection();
            return false;
        }
        return true;
    }

    private boolean checkFollower(LeaseRow row) {
        // Guard: once stop is initiated, never re-acquire leadership. AbstractLeaderElector.doStop()
        // nulls leaderEpoch, which makes isLeader() return false; without this guard a racing
        // checkElection iteration would route into the self-takeover branch and re-grant
        // leadership to a stopping node.
        if (isStopping()) {
            return true;
        }
        long now = CoreMetrics.currentTimeMillis();

        if (row == null) {
            // No leader yet — try to become one.
            tryBecomeLeader();
            return true;
        }

        if (getHostId().equals(row.leaderId)) {
            // DB thinks we're leader but our state says follower — take over.
            if (changeLeader(row, now)) {
                LOG.info("nop.stream.leader-elector.takeover-self: clusterId={}", getClusterId());
            }
            return false;
        }

        if (now < row.expireAt - getLeaseSafeGap()) {
            // Lease still valid, someone else is leader.
            if (!getHostId().equals(row.leaderId)) {
                onBecomeFollower(toLeaderEpoch(row));
                onElectionCompleted(toLeaderEpoch(row));
            }
            return true;
        }

        // Lease expired — attempt takeover.
        if (changeLeader(row, now)) {
            LOG.info("nop.stream.leader-elector.takeover-expired: clusterId={}", getClusterId());
        }
        return false;
    }

    private void tryBecomeLeader() {
        if (isStopping()) {
            return;
        }
        long now = CoreMetrics.currentTimeMillis();
        long expireAt = now + getLeaseMs();
        // CC-15 (plan 369 Phase 4): the epoch comes from the dedicated counter
        // row, NOT a hardcoded constant — deleting the lease row can never roll
        // fencing back. An allocation failure (DB down, retry budget exhausted)
        // propagates to checkElection's containment: logged + retried on the
        // next poll; it is never swallowed as "another node raced and won".
        long epoch = allocateNextEpoch();
        SQL sql = SQL.begin().name("tryBecomeLeader").querySpace(querySpace)
                .sql("INSERT INTO " + leaseTable + " (cluster_id, leader_id, leader_addr, leader_epoch, "
                                + "expire_at, refresh_at, elect_at, app_name) VALUES (?,?,?,?,?,?,?,?)",
                        getClusterId(), getHostId(), getLeaderAddr(), epoch,
                        expireAt, now, now, AppConfig.appName())
                .end();
        try {
            jdbcTemplate.executeUpdate(sql);
            LeaderEpoch leaderEpoch = new LeaderEpoch(getHostId(), epoch, new Timestamp(expireAt));
            onBecomeLeader(leaderEpoch);
            onElectionCompleted(leaderEpoch);
        } catch (Exception e) {
            // Duplicate key or constraint violation — another node raced and won.
            // Safe to ignore; the next poll re-evaluates. The consumed counter
            // value is simply skipped (gaps preserve monotonicity).
            LOG.debug("nop.stream.leader-elector.become-leader-fail: clusterId={}", getClusterId(), e);
        }
    }

    /**
     * Allocates the next strictly-monotonic leader epoch from the persistent
     * counter row — the CC-15 monotonic source that survives lease-row loss.
     *
     * <p>Each grant is a read-and-increment transaction with an optimistic CAS
     * ({@code UPDATE ... SET next_epoch = current + 1 WHERE next_epoch = current}):
     * under any isolation level ≥ READ COMMITTED, a racing contender's CAS
     * affects 0 rows and retries against the refreshed value, so every
     * <em>successful</em> grant consumes a distinct, strictly increasing value.
     * A missing counter row (fresh table, or legacy deployment that never ran
     * this version) is seeded at 1, matching the historical first-grant epoch.
     *
     * <p>Throws a typed {@link StreamException} when the bounded retry budget
     * is exhausted — never falls back to a constant epoch (that would be the
     * CC-15 rollback this method exists to prevent).
     */
    private long allocateNextEpoch() {
        ensureEpochCounterTable();
        for (int attempt = 1; attempt <= EPOCH_ALLOC_MAX_ATTEMPTS; attempt++) {
            Long allocated = jdbcTemplate.txn()
                    .runInTransaction(querySpace, TransactionPropagation.REQUIRED, txn -> {
                        Long current = readEpochCounter();
                        if (current == null) {
                            // Seed the counter row. If another elector seeded it
                            // first, the duplicate-key failure retries the outer
                            // loop against the winner's row.
                            SQL seed = SQL.begin().name("seedEpochCounter").querySpace(querySpace)
                                    .sql("INSERT INTO " + epochCounterTable
                                                    + " (cluster_id, next_epoch) VALUES (?,?)",
                                            getClusterId(), 1L)
                                    .end();
                            try {
                                jdbcTemplate.executeUpdate(seed);
                            } catch (Exception e) {
                                LOG.debug("nop.stream.leader-elector.epoch-counter-seed-raced: clusterId={}",
                                        getClusterId(), e);
                                return null;
                            }
                            return 1L;
                        }
                        long next = current + 1;
                        SQL cas = SQL.begin().name("bumpEpochCounter").querySpace(querySpace)
                                .sql("UPDATE " + epochCounterTable + " SET next_epoch = ? "
                                                + "WHERE cluster_id = ? AND next_epoch = ?",
                                        next, getClusterId(), current)
                                .end();
                        if (jdbcTemplate.executeUpdate(cas) == 0) {
                            // Another takeover consumed this counter value first.
                            return null;
                        }
                        return next;
                    });
            if (allocated != null) {
                return allocated;
            }
        }
        throw new StreamException(ERR_STREAM_LEADER_EPOCH_ALLOC_FAILED)
                .param(ARG_ATTEMPTS, EPOCH_ALLOC_MAX_ATTEMPTS)
                .param(ARG_DETAIL, "clusterId=" + getClusterId());
    }

    /**
     * Advances the counter row past {@code grantedEpoch} (counter becomes
     * {@code grantedEpoch + 1}) without ever decreasing it. Used by the
     * lease-row-exists grant paths (whose epoch mechanism stays
     * {@code old + 1} on the lease row) so the counter never lags behind an
     * epoch already written to the lease row — otherwise a later lease-row
     * deletion could still re-grant a historical value (CC-15). Single-statement
     * and idempotent; safe to run outside an explicit transaction.
     */
    private void advanceCounterPast(long grantedEpoch) {
        try {
            ensureEpochCounterTable();
            SQL sql = SQL.begin().name("advanceEpochCounter").querySpace(querySpace)
                    .sql("UPDATE " + epochCounterTable + " SET next_epoch = ? "
                                    + "WHERE cluster_id = ? AND next_epoch < ?",
                            grantedEpoch + 1, getClusterId(), grantedEpoch + 1)
                    .end();
            jdbcTemplate.executeUpdate(sql);
        } catch (Exception e) {
            // Observable WARN, not fatal: the lease row was already won. A missed
            // sync only matters if the lease row is later deleted while the
            // counter still lags, and the next successful grant re-syncs.
            LOG.warn("nop.stream.leader-elector.advance-counter-fail: clusterId={} grantedEpoch={}",
                    getClusterId(), grantedEpoch, e);
        }
    }

    private Long readEpochCounter() {
        SQL sql = SQL.begin().name("readEpochCounter").querySpace(querySpace)
                .sql("SELECT next_epoch FROM " + epochCounterTable + " WHERE cluster_id = ?", getClusterId())
                .end();
        Long value = jdbcTemplate.executeQuery(sql, dataSet -> {
            for (IDataRow row : dataSet) {
                return getLong(row, 0);
            }
            return null;
        });
        return value;
    }

    private boolean changeLeader(LeaseRow row, long now) {
        long nextEpoch = row.leaderEpoch + 1;
        long expireAt = now + getLeaseMs();
        // Optimistic concurrency: only succeed if the epoch hasn't changed under us.
        SQL sql = SQL.begin().name("changeLeader").querySpace(querySpace)
                .sql("UPDATE " + leaseTable + " SET leader_id = ?, leader_addr = ?, leader_epoch = ?, "
                                + "expire_at = ?, refresh_at = ?, elect_at = ?, app_name = ? "
                                + "WHERE cluster_id = ? AND leader_epoch = ?",
                        getHostId(), getLeaderAddr(), nextEpoch,
                        expireAt, now, now, AppConfig.appName(),
                        getClusterId(), row.leaderEpoch)
                .end();
        long affected = jdbcTemplate.executeUpdate(sql);
        if (affected == 0) {
            LOG.debug("nop.stream.leader-elector.change-leader-fail: clusterId={} epoch={}->{}",
                    getClusterId(), row.leaderEpoch, nextEpoch);
            return false;
        }
        // CC-15: the takeover grant stays the optimistic old+1 lease-row UPDATE,
        // but the counter row must not lag behind the granted epoch — otherwise
        // a later lease-row deletion could re-grant this value from the counter.
        advanceCounterPast(nextEpoch);
        LeaderEpoch leaderEpoch = new LeaderEpoch(getHostId(), nextEpoch, new Timestamp(expireAt));
        onBecomeLeader(leaderEpoch);
        onElectionCompleted(leaderEpoch);
        return true;
    }

    // ==================== Storage ====================

    private void ensureLeaseTable() {
        if (tableInitialized) {
            ensureEpochCounterTable();
            return;
        }
        String ddl = "CREATE TABLE IF NOT EXISTS " + leaseTable + " (" +
                "cluster_id VARCHAR(200) NOT NULL, " +
                "leader_id VARCHAR(100) NOT NULL, " +
                "leader_addr VARCHAR(100) NOT NULL, " +
                "leader_epoch BIGINT NOT NULL, " +
                "expire_at BIGINT NOT NULL, " +
                "refresh_at BIGINT NOT NULL, " +
                "elect_at BIGINT NOT NULL, " +
                "app_name VARCHAR(100) NOT NULL, " +
                "PRIMARY KEY (cluster_id)" +
                ")";
        try {
            SQL sql = SQL.begin().name("createLeaseTable").querySpace(querySpace).sql(ddl).end();
            jdbcTemplate.executeUpdate(sql);
            tableInitialized = true;
            LOG.info("Created leader lease table {}", leaseTable);
        } catch (Exception e) {
            // Best-effort: a concurrent JVM may have created it. Mark initialized so we
            // don't retry DDL every poll; the next query will surface a real problem.
            tableInitialized = true;
            LOG.debug("Leader lease table creation skipped (likely already exists): {}", leaseTable, e);
        }
        ensureEpochCounterTable();
    }

    /**
     * Creates the CC-15 epoch-counter table — the persistent monotonic epoch
     * source that is independent of the lease row. The row is NEVER cleaned up
     * (no delete path in this class): wiping it would reintroduce the fencing
     * rollback CC-15 exists to prevent. Best-effort DDL with the same
     * concurrent-creation tolerance as {@link #ensureLeaseTable()}.
     */
    private void ensureEpochCounterTable() {
        if (counterTableInitialized) {
            return;
        }
        String ddl = "CREATE TABLE IF NOT EXISTS " + epochCounterTable + " (" +
                "cluster_id VARCHAR(200) NOT NULL, " +
                "next_epoch BIGINT NOT NULL, " +
                "PRIMARY KEY (cluster_id)" +
                ")";
        try {
            SQL sql = SQL.begin().name("createEpochCounterTable").querySpace(querySpace).sql(ddl).end();
            jdbcTemplate.executeUpdate(sql);
            counterTableInitialized = true;
            LOG.info("Created leader epoch counter table {}", epochCounterTable);
        } catch (Exception e) {
            counterTableInitialized = true;
            LOG.debug("Leader epoch counter table creation skipped (likely already exists): {}",
                    epochCounterTable, e);
        }
    }

    LeaseRow readLeaseRow() {
        SQL sql = SQL.begin().name("readLeaseRow").querySpace(querySpace)
                .sql("SELECT leader_id, leader_addr, leader_epoch, expire_at, refresh_at FROM " + leaseTable
                        + " WHERE cluster_id = ?", getClusterId())
                .end();
        return queryFirst(sql, this::mapLeaseRow);
    }

    private LeaseRow mapLeaseRow(IDataSet dataSet) {
        for (IDataRow row : dataSet) {
            return new LeaseRow(
                    row.getString(0),
                    row.getString(1),
                    getLong(row, 2),
                    getLong(row, 3),
                    getLong(row, 4));
        }
        return null;
    }

    private LeaderEpoch toLeaderEpoch(LeaseRow row) {
        if (row == null) {
            return null;
        }
        return new LeaderEpoch(row.leaderId, row.leaderEpoch, new Timestamp(row.expireAt));
    }

    private <T> T queryFirst(SQL sql, Function<IDataSet, T> mapper) {
        return jdbcTemplate.executeQuery(sql, mapper);
    }

    private static long getLong(IDataRow row, int col) {
        Object v = row.getObject(col);
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return Long.parseLong(v.toString());
    }

    static final class LeaseRow {
        final String leaderId;
        final String leaderAddr;
        final long leaderEpoch;
        final long expireAt;
        final long refreshAt;

        LeaseRow(String leaderId, String leaderAddr, long leaderEpoch, long expireAt, long refreshAt) {
            this.leaderId = leaderId;
            this.leaderAddr = leaderAddr;
            this.leaderEpoch = leaderEpoch;
            this.expireAt = expireAt;
            this.refreshAt = refreshAt;
        }
    }
}
