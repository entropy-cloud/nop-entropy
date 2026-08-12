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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
 * <p>已知 residual 的 pin 语义（非"首日即红"）：
 * <ul>
 *   <li><b>AR-9</b>：JDBC registerNode 写 lease_expire_at=0L → 注册后立即可见性不成立
 *       （getActiveNodes 按 &gt; now 过滤）；InMemory 立即可见。差异被显式断言并登记。</li>
 *   <li><b>AR-18</b>：InMemory renewLease 忽略 leaseTimeoutMs（固定 leaseTtlMs=15s）；
 *       JDBC 按 per-renewal 参数计算过期时间。差异被显式断言并登记。</li>
 * </ul>
 */
public class TestClusterRegistryConsistencyInvariant {

    private static HikariDataSource dataSource;
    private static IJdbcTemplate jdbcTemplate;
    private ClusterRegistry jdbcRegistry;
    private ClusterRegistry inMemoryRegistry;

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
        dropTableSilently("nop_stream_task_assignment");
        dropTableSilently("nop_stream_node");
        dropTableSilently("nop_stream_coordinator");
        jdbcRegistry = new JdbcClusterRegistry(jdbcTemplate);
        inMemoryRegistry = new InMemoryClusterRegistry();
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

    /** registerNode 后 getActiveNodes 可见性（AR-9 缺陷点）：两实现差异显式 pin。 */
    @ParameterizedTest
    @MethodSource("implNames")
    void testRegisterNodeVisibilityIsPinnedPerImpl(String impl) {
        ClusterRegistry registry = registryFor(impl);
        registry.registerNode("node-1", "host1:8080", 4);
        List<NodeInfo> active = registry.getActiveNodes();
        if ("inmemory".equals(impl)) {
            assertEquals(1, active.size(),
                    "InMemory: registerNode must be immediately visible in getActiveNodes");
            assertEquals("node-1", active.get(0).getNodeId());
        } else {
            // AR-9 pin: JDBC registerNode 写 lease_expire_at=0L → 注册窗口内不可见（差异登记 I2 red list）
            assertTrue(active.isEmpty(),
                    "AR-9 pin: JdbcClusterRegistry.registerNode writes lease_expire_at=0L, "
                            + "so the node must NOT be visible immediately (difference pinned for I2, not fixed in I1)");
            // 经 renewLease 后可见（per-renewal 生效路径）
            assertTrue(registry.renewLease("node-1", 60_000L));
            assertEquals(1, registry.getActiveNodes().size(),
                    "after renewLease with a real timeout the node must become visible");
        }
    }

    /** renewLease per-renewal timeout 语义（AR-18）：JDBC 生效，InMemory 忽略 → 差异显式 pin。 */
    @ParameterizedTest
    @MethodSource("implNames")
    void testRenewLeasePerRenewalTimeoutIsPinnedPerImpl(String impl) {
        ClusterRegistry registry = registryFor(impl);
        registry.registerNode("node-1", "host1:8080", 4);
        if ("jdbc".equals(impl)) {
            // JDBC: renewLease 按 per-renewal 参数计算过期时间（AR-18 合规侧）
            long before = System.currentTimeMillis();
            assertTrue(registry.renewLease("node-1", 5_000L));
            LeaseInfo lease = registry.getNodeLease("node-1");
            assertNotNull(lease);
            assertTrue(lease.getLeaseExpireAt() >= before + 4_500L,
                    "JDBC renewLease must honor per-renewal leaseTimeoutMs (expireAt ~ now + 5000)");
            assertTrue(lease.getLeaseExpireAt() <= before + 6_000L);
            assertTrue(lease.isActive());
        } else {
            // AR-18 pin: InMemory 忽略 leaseTimeoutMs（固定 leaseTtlMs=15s）→ 差异登记 I2 red list
            assertTrue(registry.renewLease("node-1", 5_000L));
            LeaseInfo lease = registry.getNodeLease("node-1");
            assertNotNull(lease);
            assertTrue(lease.getLeaseExpireAt() - lease.getLeaseStartAt() > 10_000L,
                    "AR-18 pin: InMemoryClusterRegistry.renewLease ignores leaseTimeoutMs and uses fixed "
                            + "leaseTtlMs (15000ms), so expireAt - startAt must be ~15s, not 5s "
                            + "(difference pinned for I2, not fixed in I1)");
            assertTrue(lease.isActive());
        }
    }

    /** eviction：两实现都必须在 lease 过期后移除节点（语义一致性）。 */
    @ParameterizedTest
    @MethodSource("implNames")
    void testEvictionExpiresNode(String impl) throws Exception {
        ClusterRegistry registry = registryFor(impl);
        registry.registerNode("node-1", "host1:8080", 4);
        if ("jdbc".equals(impl)) {
            // JDBC: renewLease 短超时 → 过期后 getActiveNodes 不再包含该节点
            assertTrue(registry.renewLease("node-1", 200L));
            assertEquals(1, registry.getActiveNodes().size());
            Thread.sleep(400);
            assertTrue(registry.getActiveNodes().isEmpty(),
                    "JDBC: node must be evicted once its lease expires");
        } else {
            // InMemory: evictExpiredNodes 移除过期节点
            InMemoryClusterRegistry inMem = (InMemoryClusterRegistry) registry;
            inMem.setLeaseTimestampForTest("node-1", System.currentTimeMillis() - 30_000L);
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
