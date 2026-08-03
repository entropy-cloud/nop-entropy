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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 46: unit tests for {@link JdbcLeaderElector}. Two elector instances
 * contend over a single shared H2 lease table; verifies that exactly one wins
 * leadership, the lease epoch is monotonic, and a takeover on lease expiry
 * produces a strictly greater epoch (feeding the composite fencing epoch).
 *
 * <p>Runs by default (no gating) — uses an in-process H2 DB, no spawned JVMs.
 */
class TestJdbcLeaderElector {

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
        try {
            jdbcTemplate.executeUpdate(SQL.begin().sql("DROP TABLE IF EXISTS nop_stream_leader").end());
        } catch (Exception ignored) {
            // best-effort
        }
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.executeUpdate(SQL.begin().sql("DROP TABLE IF EXISTS nop_stream_leader").end());
        } catch (Exception ignored) {
            // best-effort
        }
    }

    @Test
    void testSingleNodeBecomesLeader() throws Exception {
        DefaultScheduledExecutor exec = DefaultScheduledExecutor.newSingleThreadTimer("elector-1");
        JdbcLeaderElector elector = newElector("host-1", exec);
        try {
            elector.start();
            // First check grants leadership immediately.
            LeaderEpoch epoch = elector.whenElectionCompleted().toCompletableFuture().get();
            assertNotNull(epoch);
            assertEquals("host-1", epoch.getLeaderId());
            assertTrue(epoch.getEpoch() >= 1, "first grant epoch must be >= 1");
            assertTrue(elector.isLeader(), "single node must be leader");
        } finally {
            elector.stop();
            exec.destroy();
        }
    }

    @Test
    void testTwoNodesExactlyOneLeader() throws Exception {
        DefaultScheduledExecutor exec1 = DefaultScheduledExecutor.newSingleThreadTimer("e-1");
        DefaultScheduledExecutor exec2 = DefaultScheduledExecutor.newSingleThreadTimer("e-2");
        JdbcLeaderElector a = newElector("host-A", exec1);
        JdbcLeaderElector b = newElector("host-B", exec2);
        try {
            a.start();
            LeaderEpoch aEpoch = a.whenElectionCompleted().toCompletableFuture().get();
            assertEquals("host-A", aEpoch.getLeaderId());

            b.start();
            // Poll until b observes a's lease and settles into follower state (avoids
            // fixed-sleep flakiness under scheduling jitter).
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                LeaderEpoch bView = b.getLeaderEpoch();
                if (bView != null && "host-A".equals(bView.getLeaderId())) {
                    break;
                }
                Thread.sleep(50);
            }

            // Exactly one leader: a holds the lease, b is follower.
            assertTrue(a.isLeader(), "host-A must remain leader (lease not expired)");
            assertTrue(!b.isLeader(), "host-B must be follower while host-A lease is valid");
        } finally {
            a.stop();
            b.stop();
            exec1.destroy();
            exec2.destroy();
        }
    }

    @Test
    void testTakeoverOnLeaseExpiryProducesGreaterEpoch() throws Exception {
        DefaultScheduledExecutor exec1 = DefaultScheduledExecutor.newSingleThreadTimer("e-1");
        DefaultScheduledExecutor exec2 = DefaultScheduledExecutor.newSingleThreadTimer("e-2");
        // Short lease so expiry is observable quickly once host-A stops refreshing.
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
            assertEquals("host-A", aEpoch.getLeaderId());
            long initialEpoch = aEpoch.getEpoch();

            // Fully stop host-A: stop the elector AND destroy its executor so it
            // definitively stops refreshing the lease (no scheduling races).
            a.stop();
            exec1.destroy();

            // Explicitly expire host-A's lease row so host-B observes an expired
            // lease deterministically (simulates coordinator JVM kill + lease
            // timeout), avoiding timing-dependent microsecond scheduling races.
            jdbcTemplate.executeUpdate(SQL.begin()
                    .sql("UPDATE nop_stream_leader SET expire_at = ? WHERE cluster_id = ?",
                            System.currentTimeMillis() - 1000, "test-cluster")
                    .end());

            b.start();
            // b's first checkFollower sees the expired lease -> changeLeader.
            LeaderEpoch bEpoch = null;
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                LeaderEpoch le = b.getLeaderEpoch();
                if (le != null && "host-B".equals(le.getLeaderId())) {
                    bEpoch = le;
                    break;
                }
                Thread.sleep(100);
            }
            assertNotNull(bEpoch, "host-B must take over after host-A lease expires");
            assertEquals("host-B", bEpoch.getLeaderId());
            assertTrue(bEpoch.getEpoch() > initialEpoch,
                    "takeover epoch (" + bEpoch.getEpoch() + ") must be strictly greater than initial ("
                            + initialEpoch + ")");
            assertTrue(b.isLeader(), "host-B must be leader after takeover");
        } finally {
            a.stop();
            b.stop();
            exec1.destroy();
            exec2.destroy();
        }
    }

    private JdbcLeaderElector newElector(String hostId, DefaultScheduledExecutor exec) {
        JdbcLeaderElector e = new JdbcLeaderElector(jdbcTemplate);
        e.setClusterId("test-cluster");
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
