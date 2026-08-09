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
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
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

    @Test
    void testPollingCadenceIsMillisecondsNotMicroseconds() throws Exception {
        // Regression guard for the AbstractPollingLeaderElector.scheduleCheck() time-unit
        // bug: scheduleCheck must use TimeUnit.MILLISECONDS (not MICROSECONDS). With
        // MICROSECONDS, checkIntervalMs=200 schedules every 200us (0.2ms), causing
        // ~5000 leader refreshes/sec — the root cause of the nop-stream T2 multi-JVM
        // coordinator-failover defect (DB flooding + concurrent state corruption).
        // With MILLISECONDS, checkIntervalMs=200 yields ~5 refreshes/sec.
        //
        // This test counts how many times the leader's refresh_at column changes over
        // a fixed window. With MILLISECONDS + 200ms we expect < 20 changes; with
        // MICROSECONDS we would see thousands — the upper bound catches the regression.
        DefaultScheduledExecutor exec = DefaultScheduledExecutor.newSingleThreadTimer("cadence-test");
        JdbcLeaderElector elector = newElector("host-cadence", exec);
        elector.setCheckIntervalMs(200);
        elector.setLeaseMs(60_000);
        try {
            elector.start();
            elector.whenElectionCompleted().toCompletableFuture().get();

            // Poll refresh_at every 20ms for ~1.5s; count how many times it changes.
            long prev = readRefreshAt();
            assertNotNull(prev, "lease row must exist after becoming leader");
            int changes = 0;
            long windowEnd = System.currentTimeMillis() + 1_500L;
            while (System.currentTimeMillis() < windowEnd) {
                Thread.sleep(20L);
                long current = readRefreshAt();
                if (current != prev) {
                    changes++;
                    prev = current;
                }
            }
            // With MILLISECONDS + checkIntervalMs=200: ~7 refreshes in 1.5s.
            // With MICROSECONDS + 0.2ms: thousands of refreshes.
            // Upper bound 50 is generous (allows scheduling jitter) but catches a
            // 1000x regression decisively.
            assertTrue(changes < 50,
                    "leader refresh count in 1.5s window must be < 50 (MILLISECONDS cadence), got "
                            + changes + " — scheduleCheck may be using MICROSECONDS instead of MILLISECONDS");
        } finally {
            elector.stop();
            exec.destroy();
        }
    }

    @Test
    void testMultiThreadedExecutorTakeoverGuardsConcurrentCheckElection() throws Exception {
        // Regression guard for the cross-JVM coordinator-failover defect (T2 capability
        // gap). The production code path (JobCoordinatorMain) uses GlobalExecutors which
        // is a multi-threaded pool. This test proves standby→leader takeover works under
        // a multi-threaded scheduled executor (corePoolSize=4), the condition under which
        // the MICROSECONDS bug caused concurrent overlapping checkElection() calls.
        DefaultScheduledExecutor exec1 = newMultiThreadedExecutor("mt-a");
        DefaultScheduledExecutor exec2 = newMultiThreadedExecutor("mt-b");

        JdbcLeaderElector a = newElector("host-A-mt", exec1);
        a.setLeaseMs(1000);
        a.setCheckIntervalMs(300);
        a.setLeaseSafeGap(100);
        JdbcLeaderElector b = newElector("host-B-mt", exec2);
        b.setLeaseMs(2000);
        b.setCheckIntervalMs(300);
        b.setLeaseSafeGap(100);
        try {
            a.start();
            LeaderEpoch aEpoch = a.whenElectionCompleted().toCompletableFuture().get();
            assertEquals("host-A-mt", aEpoch.getLeaderId());
            long initialEpoch = aEpoch.getEpoch();

            // Fully stop host-A (simulates coordinator JVM kill): stop elector + destroy
            // executor so it definitively stops refreshing the lease.
            a.stop();
            exec1.destroy();

            // Expire host-A's lease so host-B observes an expired lease deterministically.
            jdbcTemplate.executeUpdate(SQL.begin()
                    .sql("UPDATE nop_stream_leader SET expire_at = ? WHERE cluster_id = ?",
                            System.currentTimeMillis() - 1000, "test-cluster")
                    .end());

            b.start();
            // B's first checkFollower sees the expired lease -> changeLeader.
            LeaderEpoch bEpoch = null;
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                LeaderEpoch le = b.getLeaderEpoch();
                if (le != null && "host-B-mt".equals(le.getLeaderId())) {
                    bEpoch = le;
                    break;
                }
                Thread.sleep(100);
            }
            assertNotNull(bEpoch, "host-B must take over after host-A is killed (multi-threaded executor)");
            assertTrue(bEpoch.getEpoch() > initialEpoch,
                    "takeover epoch (" + bEpoch.getEpoch() + ") must be strictly greater than initial ("
                            + initialEpoch + ") — fencing invariant");
            assertTrue(b.isLeader(), "host-B must be leader after takeover");
        } finally {
            a.stop();
            b.stop();
            exec1.destroy();
            exec2.destroy();
        }
    }

    private DefaultScheduledExecutor newMultiThreadedExecutor(String name) {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setCorePoolSize(4);
        config.setMaxPoolSize(4);
        config.setName(name);
        DefaultScheduledExecutor exec = new DefaultScheduledExecutor();
        exec.setConfig(config);
        exec.init();
        return exec;
    }

    private Long readRefreshAt() {
        try {
            return jdbcTemplate.executeQuery(SQL.begin()
                    .sql("SELECT refresh_at FROM nop_stream_leader WHERE cluster_id = ?", "test-cluster")
                    .end(), dataSet -> {
                if (!dataSet.hasNext()) {
                    return null;
                }
                return dataSet.next().getLong(0);
            });
        } catch (Exception e) {
            return null;
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
