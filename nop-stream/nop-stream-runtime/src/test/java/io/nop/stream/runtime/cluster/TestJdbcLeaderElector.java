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
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.stream.core.testsupport.TestAwait;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

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
 *
 * <p>时间确定化（plan 2510）：选举/接管等待全部改为对外部可观测状态（LeaderEpoch 视图）
 * 的条件等待（{@link TestAwait#until}），上限仅挂死保护；租约到期由直接 SQL 写入过期
 * 时刻确定性构造。节流单位回归守卫（testPollingCadenceIsMillisecondsNotMicroseconds）
 * 从「真实 1.5s 窗口内计数 refresh_at 变化」改为结构性判据：记录型 IScheduledExecutor
 * 捕获 scheduleCheck 实际传入的 (delay, unit) —— 节流单位错误即红，与机器负载无关。
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
            // 条件等待 b 观测到 a 的租约并进入 follower 稳态（外部可观测状态），
            // 上限仅挂死保护。
            TestAwait.until("host-B must observe host-A's lease and settle into follower state",
                    () -> {
                        LeaderEpoch bView = b.getLeaderEpoch();
                        return bView != null && "host-A".equals(bView.getLeaderId());
                    });

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
            // 条件等待 b 获得领导权（外部可观测状态），上限仅挂死保护。
            LeaderEpoch bEpoch = awaitLeadership(b, "host-B", 5000L);
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
        // 时间确定化（plan 2510）替代旧的「真实 1.5s 窗口内计数 refresh_at 变化」：
        // 注入记录型 IScheduledExecutor，结构性断言 scheduleCheck 实际传入的
        // (delay, unit) = (checkIntervalMs, MILLISECONDS)。MICROSECONDS 回归即红，
        // 与机器负载和真实时间流逝无关。
        RecordingScheduledExecutor exec = new RecordingScheduledExecutor("cadence-test");
        JdbcLeaderElector elector = newElector("host-cadence", exec);
        elector.setCheckIntervalMs(200);
        elector.setLeaseMs(60_000);
        try {
            elector.start();
            elector.whenElectionCompleted().toCompletableFuture().get();

            // 条件等待：成为 leader 后 checkElection 必然再次 scheduleCheck（首次
            // doStart 的 delay=0 调用之后至少出现一次周期重排）。
            TestAwait.until("scheduleCheck re-armed after the initial election check",
                    () -> exec.scheduledChecks.size() > 1);

            // 结构性判据：除 doStart 的首次 delay=0 调用外，每一次 scheduleCheck
            // 都必须以 (checkIntervalMs, MILLISECONDS) 重排 —— MICROSECONDS 单位
            // 回归在此直接变红。
            List<RecordingScheduledExecutor.ScheduledCall> periodic =
                    exec.scheduledChecks.subList(1, exec.scheduledChecks.size());
            assertTrue(!periodic.isEmpty(), "at least one periodic re-schedule must be recorded");
            for (RecordingScheduledExecutor.ScheduledCall call : periodic) {
                assertEquals(200L, call.delayMs,
                        "scheduleCheck must re-arm with the configured checkIntervalMs");
                assertEquals(TimeUnit.MILLISECONDS, call.unit,
                        "scheduleCheck must use TimeUnit.MILLISECONDS — MICROSECONDS is the "
                                + "T2 multi-JVM DB-flooding regression");
            }
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
            // 条件等待 b 获得领导权（外部可观测状态），上限仅挂死保护；被测条件是
            // 多线程执行器下的接管正确性（执行器为被测面，保持真实线程池形态）。
            LeaderEpoch bEpoch = awaitLeadership(b, "host-B-mt", 10_000L);
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

    /**
     * 条件等待目标 host 获得领导权（外部可观测状态 = LeaderEpoch 视图 + isLeader），
     * 上限仅挂死保护。
     */
    private static LeaderEpoch awaitLeadership(JdbcLeaderElector elector, String hostId, long timeoutMs) {
        TestAwait.until(hostId + " must attain leadership (leader epoch + isLeader)", () -> {
            LeaderEpoch le = elector.getLeaderEpoch();
            return le != null && hostId.equals(le.getLeaderId()) && elector.isLeader();
        }, timeoutMs);
        return elector.getLeaderEpoch();
    }

    private JdbcLeaderElector newElector(String hostId, IScheduledExecutor exec) {
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

    /**
     * 记录型 IScheduledExecutor：拦截 {@code schedule(Runnable, delay, unit)} 记录
     * (delay, unit) 后委托给真实单线程定时器执行 —— 被测的选举循环保持功能完整，
     * 调度参数成为可断言的结构性证据（节流单位回归的结构性判据）。
     */
    static final class RecordingScheduledExecutor implements IScheduledExecutor {
        /** 一次 schedule 调用的 (delay, unit) 记录。 */
        static final class ScheduledCall {
            final long delayMs;
            final TimeUnit unit;

            ScheduledCall(long delayMs, TimeUnit unit) {
                this.delayMs = delayMs;
                this.unit = unit;
            }
        }

        final List<ScheduledCall> scheduledChecks = new CopyOnWriteArrayList<>();
        private final DefaultScheduledExecutor delegate;

        RecordingScheduledExecutor(String name) {
            this.delegate = DefaultScheduledExecutor.newSingleThreadTimer(name);
        }

        @Override
        public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            scheduledChecks.add(new ScheduledCall(delay, unit));
            return delegate.schedule(callable, delay, unit);
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            scheduledChecks.add(new ScheduledCall(initialDelay, unit));
            return delegate.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            scheduledChecks.add(new ScheduledCall(initialDelay, unit));
            return delegate.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
        }

        @Override
        public <V> CompletableFuture<V> submit(Callable<V> callable) {
            return delegate.submit(callable);
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return delegate.submit(task, result);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return delegate.getConfig();
        }

        @Override
        public io.nop.commons.concurrent.executor.ThreadPoolStats stats() {
            return delegate.stats();
        }

        @Override
        public void refreshConfig() {
            delegate.refreshConfig();
        }

        @Override
        public void destroy() {
            delegate.destroy();
        }

        @Override
        public boolean isDestroyed() {
            return delegate.isDestroyed();
        }
    }
}
