/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import com.zaxxer.hikari.HikariDataSource;

import io.nop.cluster.elector.LeaderEpoch;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 369 Phase 4 (R5-CC-15): the leader epoch granted when the lease row is
 * MISSING must be strictly greater than every epoch ever written to the lease
 * row — deleting the lease row (external ops / a wiped table) must never roll
 * the fencing epoch back.
 *
 * <p>Two focused scenarios:
 * <ul>
 *   <li>{@link #testLeaseRowDeletionEpochNeverRollsBack}: after an expired-lease
 *       takeover raised the historical max to {@code E}, deleting the lease row
 *       and re-electing must grant {@code > E} (pre-fix the INSERT branch
 *       hardcoded {@code epoch = 1} — fencing rolled back).</li>
 *   <li>{@link #testExistingLeaseRowTakeoverPathUnchanged}: when the lease row
 *       EXISTS, the takeover keeps the original optimistic
 *       {@code leader_epoch = old + 1} UPDATE mechanism (exact increment, not a
 *       counter reallocation).</li>
 * </ul>
 *
 * <p>Runs by default (no gating) — in-process H2, no spawned JVMs.
 */
class TestJdbcLeaderElectorEpochMonotonicity {

    private static final String CLUSTER = "test-cluster";
    private static final String LEASE_TABLE = "nop_stream_leader";
    private static final String COUNTER_TABLE = LEASE_TABLE + "_epoch_counter";

    private static HikariDataSource dataSource;
    private IJdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initAll() {
        CoreInitialization.initialize();
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
    }

    @AfterAll
    static void destroyAll() {
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        JdbcFactory factory = new JdbcFactory();
        jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
        dropTables();
    }

    @AfterEach
    void tearDown() {
        dropTables();
    }

    private void dropTables() {
        for (String table : new String[]{LEASE_TABLE, COUNTER_TABLE}) {
            try {
                jdbcTemplate.executeUpdate(SQL.begin().sql("DROP TABLE IF EXISTS " + table).end());
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    /**
     * CC-15 core scenario: lease row externally deleted after the historical max
     * epoch reached {@code E} → the next granted epoch must be {@code > E}
     * (fencing never rolls back).
     */
    @Test
    void testLeaseRowDeletionEpochNeverRollsBack() throws Exception {
        DefaultScheduledExecutor exec1 = newTimer("ep-1");
        DefaultScheduledExecutor exec2 = newTimer("ep-2");
        DefaultScheduledExecutor exec3 = newTimer("ep-3");
        JdbcLeaderElector a = newElector("host-A", exec1);
        a.setLeaseMs(300);
        a.setCheckIntervalMs(1000);
        a.setLeaseSafeGap(50);
        JdbcLeaderElector b = newElector("host-B", exec2);
        b.setLeaseMs(2000);
        b.setCheckIntervalMs(1000);
        b.setLeaseSafeGap(50);
        try {
            // 1. host-A becomes leader (first grant).
            a.start();
            LeaderEpoch aEpoch = a.whenElectionCompleted().toCompletableFuture().get();
            assertEquals("host-A", aEpoch.getLeaderId());

            // 2. Kill host-A and expire its lease so host-B takes over through the
            //    lease-row-exists path (changeLeader: optimistic old+1).
            a.stop();
            exec1.destroy();
            expireLease();
            b.start();
            LeaderEpoch bEpoch = awaitLeadership(b, "host-B", 5000);
            assertTrue(bEpoch.getEpoch() > aEpoch.getEpoch(),
                    "expired-lease takeover must raise the epoch (got " + bEpoch.getEpoch()
                            + ", previous " + aEpoch.getEpoch() + ")");
            long historicalMax = bEpoch.getEpoch();

            // 3. External ops wipe the lease row entirely (the CC-15 trigger).
            b.stop();
            exec2.destroy();
            jdbcTemplate.executeUpdate(SQL.begin()
                    .sql("DELETE FROM " + LEASE_TABLE + " WHERE cluster_id = ?", CLUSTER)
                    .end());
            assertEquals(0L, countLeaseRows(), "lease row must be gone");

            // 4. A fresh elector re-elects from an empty lease table.
            JdbcLeaderElector c = newElector("host-C", exec3);
            try {
                c.start();
                LeaderEpoch cEpoch = awaitLeadership(c, "host-C", 5000);
                assertTrue(cEpoch.getEpoch() > historicalMax,
                        "epoch granted after lease-row deletion (" + cEpoch.getEpoch()
                                + ") must be strictly greater than the historical max (" + historicalMax
                                + ") — fencing must never roll back (R5-CC-15)");
            } finally {
                c.stop();
            }
        } finally {
            b.stop();
            exec2.destroy();
            exec3.destroy();
        }
    }

    /**
     * The lease-row-EXISTS takeover path keeps its original mechanism: the new
     * epoch is exactly {@code old + 1} via the optimistic conditional UPDATE
     * (not reallocated from the counter source).
     */
    @Test
    void testExistingLeaseRowTakeoverPathUnchanged() throws Exception {
        DefaultScheduledExecutor exec1 = newTimer("ep-u1");
        DefaultScheduledExecutor exec2 = newTimer("ep-u2");
        JdbcLeaderElector a = newElector("host-A", exec1);
        a.setLeaseMs(300);
        a.setCheckIntervalMs(1000);
        a.setLeaseSafeGap(50);
        JdbcLeaderElector b = newElector("host-B", exec2);
        b.setLeaseMs(2000);
        b.setCheckIntervalMs(1000);
        b.setLeaseSafeGap(50);
        try {
            a.start();
            LeaderEpoch aEpoch = a.whenElectionCompleted().toCompletableFuture().get();

            a.stop();
            exec1.destroy();
            expireLease();
            b.start();
            LeaderEpoch bEpoch = awaitLeadership(b, "host-B", 5000);

            assertEquals(aEpoch.getEpoch() + 1, bEpoch.getEpoch(),
                    "lease-row takeover must keep the optimistic old+1 increment mechanism");
        } finally {
            b.stop();
            exec2.destroy();
        }
    }

    // ==================== helpers ====================

    private void expireLease() {
        jdbcTemplate.executeUpdate(SQL.begin()
                .sql("UPDATE " + LEASE_TABLE + " SET expire_at = ? WHERE cluster_id = ?",
                        System.currentTimeMillis() - 1000, CLUSTER)
                .end());
    }

    private long countLeaseRows() {
        Long count = jdbcTemplate.executeQuery(SQL.begin()
                .sql("SELECT COUNT(*) FROM " + LEASE_TABLE + " WHERE cluster_id = ?", CLUSTER)
                .end(), dataSet -> {
            if (!dataSet.hasNext()) {
                return 0L;
            }
            return dataSet.next().getLong(0);
        });
        return count == null ? 0L : count;
    }

    private LeaderEpoch awaitLeadership(JdbcLeaderElector elector, String hostId, long timeoutMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            LeaderEpoch le = elector.getLeaderEpoch();
            if (le != null && hostId.equals(le.getLeaderId()) && elector.isLeader()) {
                return le;
            }
            Thread.sleep(50);
        }
        fail(hostId + " must attain leadership within " + timeoutMs + "ms");
        return null; // unreachable
    }

    private DefaultScheduledExecutor newTimer(String name) {
        return DefaultScheduledExecutor.newSingleThreadTimer(name);
    }

    private JdbcLeaderElector newElector(String hostId, DefaultScheduledExecutor exec) {
        JdbcLeaderElector e = new JdbcLeaderElector(jdbcTemplate);
        e.setClusterId(CLUSTER);
        e.setHostId(hostId);
        e.setScheduledExecutor(exec);
        e.setLeaseMs(2000);
        e.setCheckIntervalMs(200);
        e.setLeaseSafeGap(200);
        e.setAddr("localhost");
        e.setPort(0);
        return e;
    }
}
