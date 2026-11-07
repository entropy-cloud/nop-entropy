/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.util.List;
import java.util.function.Supplier;

import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.test.time.ManualClock;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate ⑤ — ClusterRegistry 多实现语义一致性（invariant #5）.
 *
 * <p>对 {@link JdbcClusterRegistry} / {@link InMemoryClusterRegistry} 两实现跑同一语义场景
 * （registerNode 后 getActiveNodes 可见性（AR-9 缺陷点）、renewLease per-renewal timeout
 * （AR-18）、eviction），行为差异**显式断言（pin）差异存在并登记 red list 移交 I2，不修复**。
 *
 * <p>I1 期间两实现差异曾被显式 pin（AR-9：JDBC 注册后不可见；AR-18：InMemory 忽略 per-renewal
 * leaseTimeoutMs）；I4 修复后断言翻转——两实现现在同语义：registerNode 后立即可见、renewLease
 * 按 per-renewal 参数计算过期时间（不变式 #5）。
 *
 * <p>时间确定化（plan 2510）：两实现的租约/活性判定读取均经 {@code CoreMetrics}（JDBC 侧
 * expire_at 写入与 {@code WHERE lease_expire_at > ?} 比较同源），测试注册 {@link ManualClock}
 * 显式推进虚拟时间越过租约到期点，替代固定墙钟等待。时钟为 JVM 全局量：逐测试注册、
 * {@code @AfterEach} 恢复默认时钟；测试内的注册/到期时刻一律取自虚拟时钟线（时间戳陷阱）。
 */
public class TestClusterRegistryConsistencyInvariant {

    private static HikariDataSource dataSource;
    private static IJdbcTemplate jdbcTemplate;
    private ClusterRegistry jdbcRegistry;
    private ClusterRegistry inMemoryRegistry;
    private ManualClock clock;

    @BeforeAll
    static void initAll() {
        CoreInitialization.initialize();
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(4);
        JdbcFactory factory = new JdbcFactory();
        jdbcTemplate = factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
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
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
        dropTableSilently("nop_stream_task_assignment");
        dropTableSilently("nop_stream_node");
        dropTableSilently("nop_stream_coordinator");
        jdbcRegistry = new JdbcClusterRegistry(jdbcTemplate);
        inMemoryRegistry = new InMemoryClusterRegistry();
    }

    @AfterEach
    void tearDown() {
        // 时钟是 JVM 全局量：逐测试恢复默认时钟，避免冻结同 fork 后续所有类。
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    private void dropTableSilently(String tableName) {
        try {
            SQL dropSql = SQL.begin().sql("DROP TABLE IF EXISTS " + tableName).end();
            jdbcTemplate.executeUpdate(dropSql);
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    static List<Supplier<ClusterRegistry>> registryProviders() {
        return List.of(
                TestClusterRegistryConsistencyInvariant::newJdbcRegistryHolder,
                TestClusterRegistryConsistencyInvariant::newInMemoryRegistryHolder);
    }

    private static ClusterRegistry newJdbcRegistryHolder() {
        return new JdbcClusterRegistry(jdbcTemplate);
    }

    private static ClusterRegistry newInMemoryRegistryHolder() {
        return new InMemoryClusterRegistry();
    }

    static List<String> implNames() {
        return List.of("jdbc", "inmemory");
    }

    private ClusterRegistry registryFor(String name) {
        return "jdbc".equals(name) ? jdbcRegistry : inMemoryRegistry;
    }

    /** registerNode 后 getActiveNodes 可见性（不变式 #5）：两实现必须立即可见（RL-1 修复，断言翻转）。 */
    @ParameterizedTest
    @MethodSource("implNames")
    void testRegisterNodeVisibilityIsPinnedPerImpl(String impl) {
        ClusterRegistry registry = registryFor(impl);
        registry.registerNode("node-1", "host1:8080", 4);
        List<NodeInfo> active = registry.getActiveNodes();
        assertEquals(1, active.size(),
                "RL-1 fixed: registerNode must be immediately visible in getActiveNodes (impl=" + impl + ")");
        assertEquals("node-1", active.get(0).getNodeId());
    }

    /** renewLease per-renewal timeout 语义（不变式 #5）：两实现都按参数计算（RL-3 修复，断言翻转）。 */
    @ParameterizedTest
    @MethodSource("implNames")
    void testRenewLeasePerRenewalTimeoutIsPinnedPerImpl(String impl) {
        ClusterRegistry registry = registryFor(impl);
        registry.registerNode("node-1", "host1:8080", 4);
        // 注册/到期时刻一律取自虚拟时钟线：renewLease 的 now 读取 CoreMetrics（ManualClock），
        // 与 peekMillis 同值 —— 虚拟线上的 expireAt 精确等于 now + leaseTimeoutMs。
        long before = clock.peekMillis();
        assertTrue(registry.renewLease("node-1", 5_000L));
        LeaseInfo lease = registry.getNodeLease("node-1");
        assertNotNull(lease);
        assertEquals(before + 5_000L, lease.getLeaseExpireAt(),
                "RL-3 fixed (impl=" + impl + "): renewLease must honor per-renewal leaseTimeoutMs "
                        + "(expireAt = virtual now + 5000 exactly)");
        assertTrue(lease.isActive());
    }

    /** RL-3：InMemory renewLease 按 per-renewal leaseTimeoutMs 计算活性（自定义 timeout 到期后淘汰、未到期活性）。 */
    @Test
    void testInMemoryRenewLeaseHonorsPerRenewalTimeout() {
        InMemoryClusterRegistry inMem = new InMemoryClusterRegistry();
        inMem.registerNode("node-1", "host1:8080", 4);

        // 未到期：按自定义 timeout 保持活性（不得用固定 leaseTtlMs=15s 兜底）
        assertTrue(inMem.renewLease("node-1", 300L));
        assertEquals(1, inMem.getActiveNodes().size());
        assertTrue(inMem.getNodeLease("node-1").isActive());

        // 推进虚拟时间越过自定义 timeout（300ms）：getActiveNodes 的活性判定读取
        // CoreMetrics，推进后立即失活 —— 替代旧的固定墙钟等待。
        clock.advance(300L);
        assertTrue(inMem.getActiveNodes().isEmpty(),
                "RL-3: InMemory node must be inactive once the custom leaseTimeoutMs (300ms) expires");
        assertFalse(inMem.getNodeLease("node-1").isActive());
        inMem.evictExpiredNodes();
        assertFalse(inMem.renewLease("node-1", 5_000L), "evicted node must not be renewable");
    }

    /** RL-1 UPDATE 分支：注册 → 短租约过期 → 重新注册（UPDATE 路径）→ 立即可见（UPDATE 不得停留旧过期值）。 */
    @Test
    void testJdbcReregisterAfterLeaseExpiryIsImmediatelyVisible() {
        ClusterRegistry registry = new JdbcClusterRegistry(jdbcTemplate);
        registry.registerNode("node-1", "host1:8080", 4);
        assertTrue(registry.renewLease("node-1", 200L));
        // 推进虚拟时间越过 200ms 短租约：expire_at 写入与 WHERE lease_expire_at > ? 比较
        // 同读 CoreMetrics，推进后节点立即从 getActiveNodes 消失（RL-1 前置条件）。
        clock.advance(200L);
        assertTrue(registry.getActiveNodes().isEmpty(), "precondition: node lease must have expired");

        // 节点已存在 → registerNode 走 UPDATE 分支；UPDATE 必须刷新 lease_expire_at 使节点立即可见
        registry.registerNode("node-1", "host1:9090", 8);
        List<NodeInfo> active = registry.getActiveNodes();
        assertEquals(1, active.size(),
                "RL-1: re-register via the UPDATE path must refresh lease_expire_at and make the node immediately visible");
        assertEquals("node-1", active.get(0).getNodeId());
    }

    /** eviction：两实现都必须在 lease 过期后移除节点（语义一致性）。 */
    @ParameterizedTest
    @MethodSource("implNames")
    void testEvictionExpiresNode(String impl) {
        ClusterRegistry registry = registryFor(impl);
        registry.registerNode("node-1", "host1:8080", 4);
        if ("jdbc".equals(impl)) {
            // JDBC: renewLease 短超时 → 推进虚拟时间越过后 getActiveNodes 不再包含该节点
            assertTrue(registry.renewLease("node-1", 200L));
            assertEquals(1, registry.getActiveNodes().size());
            clock.advance(200L);
            assertTrue(registry.getActiveNodes().isEmpty(),
                    "JDBC: node must be evicted once its lease expires");
        } else {
            // InMemory: evictExpiredNodes 移除过期节点
            // 过期时刻取自虚拟时钟线（evictExpiredNodes 的 now 读取 CoreMetrics/ManualClock）。
            InMemoryClusterRegistry inMem = (InMemoryClusterRegistry) registry;
            inMem.setLeaseTimestampForTest("node-1", clock.peekMillis() - 30_000L);
            inMem.evictExpiredNodes();
            assertTrue(inMem.getActiveNodes().isEmpty(),
                    "InMemory: evictExpiredNodes must remove expired nodes");
            assertFalse(inMem.renewLease("node-1", 5_000L),
                    "evicted node must not be renewable (lease state removed)");
        }
    }

    /** 两实现同语义场景：registerNode → renewLease → 持续活跃 → 正常释放（一致性正例）。 */
    @ParameterizedTest
    @MethodSource("implNames")
    void testConsistentLifecycleAcrossImpls(String impl) {
        ClusterRegistry registry = registryFor(impl);
        registry.registerNode("node-1", "host1:8080", 4);
        // 语义一致侧：renewLease 返回 true、getNodeLease 非 null（lease 元数据形状一致）
        assertTrue(registry.renewLease("node-1", 60_000L));
        LeaseInfo lease = registry.getNodeLease("node-1");
        assertNotNull(lease);
        assertEquals("node-1", lease.getNodeId());
        assertTrue(lease.getLeaseStartAt() > 0);
        assertTrue(lease.getLeaseExpireAt() > lease.getLeaseStartAt());
    }
}
