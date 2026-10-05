/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.DaoErrors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.PRIMARY;
import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.REPLICA;
import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.UNSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2319 Phase 2：ReadWriteRoutingDataSource路由判定矩阵、负载均衡与故障冷却状态机。
 * StubDataSource.lastResolved桩模式（TestDynamicDataSource先例）。
 */
public class TestReadWriteRoutingDataSource {

    // 可控时钟（测试覆写currentTimeMillis读取）
    final ThreadLocal<Long> fakeNow = ThreadLocal.withInitial(() -> 0L);

    /**
     * 可编程失败桩：前failuresBeforeSuccess次getConnection抛SQLException，之后成功（返回null连接，
     * 测试只经由lastResolved观察路由结果，不使用连接对象）
     */
    static class StubDataSource implements DataSource {
        final String name;
        int failuresBeforeSuccess;
        int calls;
        static StubDataSource lastResolved;

        StubDataSource(String name) {
            this.name = name;
        }

        @Override
        public Connection getConnection() throws SQLException {
            calls++;
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--;
                throw new SQLException("stub-fail:" + name);
            }
            lastResolved = this;
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }
    }

    @AfterEach
    public void tearDown() {
        ReadWriteHint.leave(UNSET);
        fakeNow.remove();
        StubDataSource.lastResolved = null;
    }

    private ReadWriteRoutingDataSource newRouting(StubDataSource write, StubDataSource... reads) {
        ReadWriteRoutingDataSource ds = new ReadWriteRoutingDataSource();
        ds.setWriteDataSource(write);
        for (StubDataSource read : reads) {
            ds.addReplica(read.name, read);
        }
        return ds;
    }

    private ReadWriteRoutingDataSource newFakeClockRouting(StubDataSource write, StubDataSource... reads) {
        ReadWriteRoutingDataSource ds = new ReadWriteRoutingDataSource() {
            @Override
            protected long currentTimeMillis() {
                return fakeNow.get();
            }
        };
        ds.setWriteDataSource(write);
        for (StubDataSource read : reads) {
            ds.addReplica(read.name, read);
        }
        return ds;
    }

    // ---- 路由判定矩阵 ----

    @Test
    public void testUnsetHintGoesToWrite() throws SQLException {
        StubDataSource write = new StubDataSource("write");
        StubDataSource r1 = new StubDataSource("r1");
        newRouting(write, r1).getConnection();

        assertSame(write, StubDataSource.lastResolved);
        assertEquals(0, r1.calls);
    }

    @Test
    public void testPrimaryHintGoesToWrite() throws SQLException {
        StubDataSource write = new StubDataSource("write");
        StubDataSource r1 = new StubDataSource("r1");
        ReadWriteRoutingDataSource routing = newRouting(write, r1);

        ReadWriteHint.runWithPrimary(() -> {
            try {
                routing.getConnection();
            } catch (SQLException e) {
                throw NopException.adapt(e);
            }
            return null;
        });

        assertSame(write, StubDataSource.lastResolved);
        assertEquals(0, r1.calls);
    }

    @Test
    public void testReplicaHintGoesToRead() throws SQLException {
        StubDataSource write = new StubDataSource("write");
        StubDataSource r1 = new StubDataSource("r1");
        ReadWriteRoutingDataSource routing = newRouting(write, r1);

        ReadWriteHint.runWithReplica(() -> {
            try {
                routing.getConnection();
            } catch (SQLException e) {
                throw NopException.adapt(e);
            }
            return null;
        });

        assertSame(r1, StubDataSource.lastResolved);
        assertEquals(0, write.calls);
    }

    @Test
    public void testReplicaPoolEmptyFailsExplicitlyWithoutWriteFallback() {
        StubDataSource write = new StubDataSource("write");
        ReadWriteRoutingDataSource routing = newRouting(write);

        NopException e = assertThrows(NopException.class, () -> ReadWriteHint.runWithReplica(() -> {
            try {
                routing.getConnection();
                return null;
            } catch (SQLException ex) {
                throw NopException.adapt(ex);
            }
        }));

        assertEquals(DaoErrors.ERR_DAO_NO_REPLICA_AVAILABLE.getErrorCode(), e.getErrorCode());
        assertTrue(e.getMessage().contains("read pool is empty"));
        // 不静默回退主库
        assertEquals(0, write.calls);
        assertNull(StubDataSource.lastResolved);
    }

    // ---- 负载均衡 ----

    @Test
    public void testRoundRobinRotationSequence() throws SQLException {
        StubDataSource write = new StubDataSource("write");
        StubDataSource r1 = new StubDataSource("r1");
        StubDataSource r2 = new StubDataSource("r2");
        ReadWriteRoutingDataSource routing = newRouting(write, r1, r2);

        for (int i = 0; i < 4; i++) {
            ReadWriteHint.runWithReplica(() -> {
                try {
                    routing.getConnection();
                } catch (SQLException e) {
                    throw NopException.adapt(e);
                }
                return null;
            });
        }

        assertEquals(2, r1.calls);
        assertEquals(2, r2.calls);
        // 轮转交替：两次调用不落在同一台（当且仅当轮转生效）
        assertFalse(r1.calls == 0 || r2.calls == 0);
    }

    @Test
    public void testWeightDistribution() throws SQLException {
        StubDataSource write = new StubDataSource("write");
        StubDataSource r1 = new StubDataSource("r1");
        StubDataSource r2 = new StubDataSource("r2");
        ReadWriteRoutingDataSource routing = newRouting(write, r1, r2);
        Map<String, Integer> weights = new HashMap<>();
        weights.put("r1", 3);
        weights.put("r2", 1);
        routing.setLoadBalancer(ReadWriteLoadBalancers.weight(weights));

        int total = 400;
        for (int i = 0; i < total; i++) {
            ReadWriteHint.runWithReplica(() -> {
                try {
                    routing.getConnection();
                } catch (SQLException e) {
                    throw NopException.adapt(e);
                }
                return null;
            });
        }

        // 权重3:1，统计上允许较大抖动但不允许接近等分或全偏一侧
        assertTrue(r1.calls > total / 2, "r1 weight=3 should get majority, calls=" + r1.calls);
        assertTrue(r2.calls > 0 && r2.calls < total / 2, "r2 weight=1 should get minority, calls=" + r2.calls);
    }

    @Test
    public void testLoadBalancerTypeFactory() {
        assertThrows(IllegalArgumentException.class, () -> ReadWriteLoadBalancers.of("bogus", null));
        assertTrue(ReadWriteLoadBalancers.of(null, null) instanceof ReadWriteLoadBalancers.RoundRobinLoadBalancer);
        assertTrue(ReadWriteLoadBalancers.of("round-robin", null) instanceof ReadWriteLoadBalancers.RoundRobinLoadBalancer);
        assertTrue(ReadWriteLoadBalancers.of("random", null) instanceof ReadWriteLoadBalancers.RandomLoadBalancer);
        assertTrue(ReadWriteLoadBalancers.of("weight", null) instanceof ReadWriteLoadBalancers.WeightLoadBalancer);
    }

    // ---- 故障状态机 ----

    @Test
    public void testFailureSwitchesToNextReplica() throws SQLException {
        StubDataSource write = new StubDataSource("write");
        StubDataSource r1 = new StubDataSource("r1");
        StubDataSource r2 = new StubDataSource("r2");
        r1.failuresBeforeSuccess = 1; // 第一次调用失败，换下一台
        ReadWriteRoutingDataSource routing = newRouting(write, r1, r2);
        routing.setReplicaFailThreshold(100); // 本次不触发冷却

        ReadWriteHint.runWithReplica(() -> {
            try {
                routing.getConnection();
            } catch (SQLException e) {
                throw NopException.adapt(e);
            }
            return null;
        });

        assertEquals(1, r1.calls);
        assertSame(r2, StubDataSource.lastResolved);
        assertEquals(0, write.calls);
    }

    @Test
    public void testConsecutiveFailuresTriggerCooldown() throws SQLException {
        StubDataSource write = new StubDataSource("write");
        StubDataSource r1 = new StubDataSource("r1");
        StubDataSource r2 = new StubDataSource("r2");
        r1.failuresBeforeSuccess = Integer.MAX_VALUE; // r1总是失败
        ReadWriteRoutingDataSource routing = newFakeClockRouting(write, r1, r2);
        routing.setReplicaFailThreshold(2);
        routing.setReplicaCooldownSeconds(30);
        fakeNow.set(1_000_000L);

        for (int i = 0; i < 3; i++) {
            ReadWriteHint.runWithReplica(() -> {
                try {
                    routing.getConnection();
                } catch (SQLException e) {
                    throw NopException.adapt(e);
                }
                return null;
            });
        }

        // 前两次调用r1各失败1次（fail-over到r2），达到阈值2进入冷却；
        // 第三次调用r1已冷却不再被尝试
        assertEquals(2, r1.calls);
        assertEquals(3, r2.calls);
    }

    @Test
    public void testAllCoolingFailsExplicitlyWithSummary() throws SQLException {
        StubDataSource write = new StubDataSource("write");
        StubDataSource r1 = new StubDataSource("r1");
        StubDataSource r2 = new StubDataSource("r2");
        r1.failuresBeforeSuccess = Integer.MAX_VALUE;
        r2.failuresBeforeSuccess = Integer.MAX_VALUE;
        ReadWriteRoutingDataSource routing = newFakeClockRouting(write, r1, r2);
        routing.setReplicaFailThreshold(2);
        routing.setReplicaCooldownSeconds(30);
        fakeNow.set(2_000_000L);

        // 前两次调用：每台各累积1次失败，fail-over到最后一台仍失败，显式报错（不回退主库）
        for (int i = 0; i < 2; i++) {
            NopException err = assertThrows(NopException.class,
                    () -> replicaGetConnection(routing));
            assertEquals(DaoErrors.ERR_DAO_NO_REPLICA_AVAILABLE.getErrorCode(), err.getErrorCode());
        }

        // 两台均达到阈值2进入冷却；第三次调用不再尝试任何从库，直接报"全冷却"并携带摘要
        assertEquals(2, r1.calls);
        assertEquals(2, r2.calls);
        NopException e = assertThrows(NopException.class, () -> replicaGetConnection(routing));

        assertEquals(DaoErrors.ERR_DAO_NO_REPLICA_AVAILABLE.getErrorCode(), e.getErrorCode());
        String summary = String.valueOf(e.getParam("summary"));
        assertTrue(summary.contains("r1"), summary);
        assertTrue(summary.contains("r2"), summary);
        assertTrue(summary.contains("cooling"), summary);
        // 不回退主库
        assertEquals(0, write.calls);
    }

    private void replicaGetConnection(ReadWriteRoutingDataSource routing) {
        ReadWriteHint.runWithReplica(() -> {
            try {
                routing.getConnection();
            } catch (SQLException e) {
                throw NopException.adapt(e);
            }
            return null;
        });
    }

    @Test
    public void testHalfOpenProbeAndRecovery() throws SQLException {
        StubDataSource write = new StubDataSource("write");
        StubDataSource r1 = new StubDataSource("r1");
        StubDataSource r2 = new StubDataSource("r2");
        r1.failuresBeforeSuccess = Integer.MAX_VALUE;
        ReadWriteRoutingDataSource routing = newFakeClockRouting(write, r1, r2);
        routing.setReplicaFailThreshold(1); // 一次失败即冷却
        routing.setReplicaCooldownSeconds(30);
        // 固定尝试顺序为注册顺序（r1优先），使半开探测行为可确定性断言
        routing.setLoadBalancer(candidates -> candidates);
        fakeNow.set(3_000_000L);

        // 第一次：r1失败立即冷却，fail-over到r2成功
        ReadWriteHint.runWithReplica(() -> {
            try {
                routing.getConnection();
            } catch (SQLException e) {
                throw NopException.adapt(e);
            }
            return null;
        });
        assertEquals(1, r1.calls);
        assertSame(r2, StubDataSource.lastResolved);

        // 冷却期内r1不可选
        ReplicaChannel ch1 = routing.getReplicas().get(0);
        assertEquals("r1", ch1.getName());
        assertTrue(ch1.isCooling(3_000_100L));

        // 冷却期满：半开。探测失败（r1仍在失败态）→ 重新冷却
        fakeNow.set(3_030_001L);
        assertTrue(ch1.isHalfOpen(3_030_001L));
        ReadWriteHint.runWithReplica(() -> {
            try {
                routing.getConnection();
            } catch (SQLException e) {
                throw NopException.adapt(e);
            }
            return null;
        });
        assertEquals(2, r1.calls);
        assertTrue(ch1.isCooling(3_030_002L), "failed half-open probe must re-cool");

        // 冷却期满：半开。探测成功 → 恢复
        r1.failuresBeforeSuccess = 0;
        fakeNow.set(3_060_002L);
        assertFalse(ch1.isCooling(3_060_002L));
        ReadWriteHint.runWithReplica(() -> {
            try {
                routing.getConnection();
            } catch (SQLException e) {
                throw NopException.adapt(e);
            }
            return null;
        });
        assertEquals(3, r1.calls);
        assertFalse(ch1.isCooling(3_060_003L));
        assertFalse(ch1.isHalfOpen(3_060_003L), "successful probe must fully recover the replica");
    }

    @Test
    public void testReplicaChannelStateMachine() {
        StubDataSource stub = new StubDataSource("ch");
        ReplicaChannel ch = new ReplicaChannel("ch", stub);
        long t0 = 10_000L;

        assertFalse(ch.isCooling(t0));
        assertFalse(ch.isHalfOpen(t0));
        assertTrue(ch.describe(t0).contains("available"));

        // 未达阈值不冷却
        ch.recordFailure(3, 5_000L, t0);
        assertFalse(ch.isCooling(t0));

        // 达到阈值进入冷却
        ch.recordFailure(3, 5_000L, t0);
        ch.recordFailure(3, 5_000L, t0);
        assertTrue(ch.isCooling(t0));
        assertTrue(ch.describe(t0).contains("cooling"));

        // 冷却期满半开
        assertTrue(ch.isHalfOpen(t0 + 5_000L));

        // 半开探测失败 → 重新冷却，不再累积计数
        ch.recordFailure(3, 5_000L, t0 + 5_000L);
        assertTrue(ch.isCooling(t0 + 5_000L));
        assertTrue(ch.isCooling(t0 + 9_999L));
        assertFalse(ch.isCooling(t0 + 10_000L));

        // 半开探测成功 → 完全恢复
        ch.recordSuccess();
        assertFalse(ch.isCooling(t0 + 20_000L));
        assertFalse(ch.isHalfOpen(t0 + 20_000L));
        assertEquals("ch", ch.getName());
        assertTrue(ch.describe(t0 + 20_000L).contains("available"));
    }
}
