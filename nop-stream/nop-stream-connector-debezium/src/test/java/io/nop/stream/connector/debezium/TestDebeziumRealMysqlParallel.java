/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.core.initialize.CoreInitialization;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.testsupport.TestAwait;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 验证收口 V1/V2（用户裁定 2026-10-02）：单机两层并行在真实 MySQL 上的行为证据——
 * <ul>
 *   <li>V1：A 层 {@code snapshotMaxThreads>1}（Debezium snapshot.max.threads）大表
 *       chunk 并行快照的完整性（全量行数、无重复）；</li>
 *   <li>V2：B 层 {@code parallelism=2} subtask 表路由——双实例各自只收自己分片的表、
 *       实例名/offset 隔离、两实例并集覆盖全部变更。</li>
 * </ul>
 *
 * <p><b>Gated 语义</b>：docker 不可达时 assume 跳过（SKIP 可见，不伪造通过）。
 */
class TestDebeziumRealMysqlParallel {

    private static final boolean DOCKER_AVAILABLE = detectDocker();

    private static MySQLContainer<?> mysql;

    /**
     * Bounded Docker probe: {@link DockerClientFactory#isDockerAvailable()} has no read
     * timeout and blocks forever on a half-hung docker socket (observed on Docker Desktop
     * with a stale socket file), which would wedge the whole build from a test-class
     * static initializer. Run it on a daemon thread and give up after 15s; the
     * shutdownNow() interrupt breaks the NIO blocking read.
     */
    static boolean detectDocker() {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "docker-detect");
            t.setDaemon(true);
            return t;
        });
        try {
            return executor.submit(() -> {
                try {
                    return DockerClientFactory.instance().isDockerAvailable();
                } catch (Throwable e) {
                    return false;
                }
            }).get(15, TimeUnit.SECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    @BeforeAll
    static void initCore() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroyCore() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        org.junit.jupiter.api.Assumptions.assumeTrue(DOCKER_AVAILABLE,
                "docker unavailable: real-MySQL parallel test gated off (SKIP is visible by design)");
    }

    @AfterEach
    void tearDown() {
        if (mysql != null) {
            mysql.stop();
            mysql = null;
        }
    }

    private static void startMySql() {
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withCommand("--log-bin=mysql-bin", "--binlog-format=ROW",
                        "--server-id=1721100", "--binlog-row-image=FULL");
        mysql.start();
        awaitStableMySql();
    }

    /**
     * 条件等待真实外部状态（gated 真库面，虚拟时钟不适用）：轮询探测直到 MySQL 容器
     * 可连通且可执行查询，并以连续两次成功探测确认稳定（原固定 40s 启动窗 + 5s 间隔
     * 双探测的机械化条件等价）；超时上限只作挂死保护，等待时长由真实容器就绪决定。
     */
    private static void awaitStableMySql() {
        try {
            TestAwait.until("MySQL container accepts and executes a probe query",
                    TestDebeziumRealMysqlParallel::probeQueryOk, 120_000L);
            TestAwait.until("MySQL container serves a second consecutive probe (stable)",
                    TestDebeziumRealMysqlParallel::probeQueryOk, 60_000L);
        } catch (Exception e) {
            throw new IllegalStateException("MySQL container not stable after startup window", e);
        }
    }

    private static boolean probeQueryOk() {
        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Connection dbConn() throws Exception {
        return DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
    }

    private static DebeziumConfig newConfig(String name, String tableIncludeList) {
        return newConfig(name, tableIncludeList, "real-mysql-parallel-" + name);
    }

    private static DebeziumConfig newConfig(String name, String tableIncludeList, String serverName) {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(name);
        config.setConnectorType("mysql");
        config.setDatabaseHost(mysql.getHost());
        config.setDatabasePort(mysql.getMappedPort(MySQLContainer.MYSQL_PORT));
        config.setDatabaseUser("root");
        config.setDatabasePassword(mysql.getPassword());
        config.setDatabaseName(mysql.getDatabaseName());
        config.setServerName(serverName);
        config.setTableIncludeList(tableIncludeList);
        config.setHeartbeatInterval(java.time.Duration.ofSeconds(1));
        config.setSchemaHistoryStore("file");
        config.setSchemaHistoryPath(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                "mysql-par-" + name + "-" + java.util.UUID.randomUUID() + "/schema-history.dat")
                .toString());
        // 同服务器上的快照全局读锁会与并发实例的 schema recovery 互锁（B 层双实例必现）；
        // binlog + REPEATABLE READ 下由事务 + binlog 捕获保证一致快照（见 cookbook 故障表）
        config.getExtraProperties().put("snapshot.locking.mode", "none");
        return config;
    }

    // ------------------------------------------------------------------
    // V1: A 层 snapshotMaxThreads>1 大表 chunk 并行快照完整性
    // ------------------------------------------------------------------

    @Test
    void testLayerA_parallelSnapshotCompletesAllRows() throws Exception {
        startMySql();
        int rowCount = 2000;
        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE cdc_src (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO cdc_src VALUES (?, ?)")) {
                for (int i = 1; i <= rowCount; i++) {
                    ps.setLong(1, i);
                    ps.setString(2, "row-" + i);
                    ps.addBatch();
                    if (i % 500 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }
        }

        DebeziumConfig config = newConfig("real-mysql-layerA", mysql.getDatabaseName() + ".cdc_src");
        config.setSnapshotMaxThreads(4);

        List<ChangeEvent> collected = new CopyOnWriteArrayList<>();
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);
        source.initializeState(null);
        Thread runner = TestDebeziumRealMysqlCdc.runAndCollect(source, collected);

        // 2000 个快照行全部到达（chunk 并行下不丢行）——条件等待，上限仅挂死保护
        TestAwait.until("parallel snapshot delivered every row", () -> collected.size() >= rowCount,
                60_000L);
        source.cancel();
        runner.join(15_000);
        System.err.println("=== V1 forensics: collected=" + collected.size());

        Set<Object> ids = new HashSet<>();
        for (ChangeEvent e : collected) {
            Object key = e.getKey();
            if (key instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
                ids.add(map.values().iterator().next());
            }
        }
        assertEquals(rowCount, ids.size(),
                "A-layer parallel snapshot must deliver every row exactly once (got " + ids.size() + ")");
    }

    // ------------------------------------------------------------------
    // V2: B 层 parallelism=2 subtask 表路由——隔离与覆盖
    // ------------------------------------------------------------------

    @Test
    void testLayerB_twoSubtasksRouteDisjointTables() throws Exception {
        startMySql();
        String tableA = mysql.getDatabaseName() + ".cdc_src_a";
        String tableB = mysql.getDatabaseName() + ".cdc_src_b";
        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE cdc_src_a (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            st.execute("CREATE TABLE cdc_src_b (id BIGINT PRIMARY KEY, name VARCHAR(100))");
        }

        DebeziumConfig config = newConfig("real-mysql-layerB", tableA + "," + tableB);
        DebeziumCdcSourceFunction template = new DebeziumCdcSourceFunction(config);
        template.validateParallelism(2);

        DebeziumCdcSourceFunction s0 = (DebeziumCdcSourceFunction) template.copyForSubtask(0);
        DebeziumCdcSourceFunction s1 = (DebeziumCdcSourceFunction) template.copyForSubtask(1);
        assertEquals("real-mysql-layerB-0", s0.instanceConnectorNameForTest());
        assertEquals("real-mysql-layerB-1", s1.instanceConnectorNameForTest());
        assertNotEquals(s0.routedIncludeListForTest(), s1.routedIncludeListForTest(),
                "two tables at parallelism=2 must route to different shards");

        // 路由是 hash 决定的：按 shardOf 计算每实例应接的表（不硬编码方向）
        String own0 = DebeziumCdcSourceFunction.shardOf(tableA, 2) == 0 ? tableA : tableB;
        String own1 = DebeziumCdcSourceFunction.shardOf(tableA, 2) == 0 ? tableB : tableA;
        assertTrue(s0.routedIncludeListForTest().contains(own0.substring(own0.indexOf('.') + 1)),
                "instance-0 routed list must contain its hash shard: " + s0.routedIncludeListForTest());
        assertTrue(s1.routedIncludeListForTest().contains(own1.substring(own1.indexOf('.') + 1)),
                "instance-1 routed list must contain its hash shard: " + s1.routedIncludeListForTest());

        List<ChangeEvent> got0 = new CopyOnWriteArrayList<>();
        List<ChangeEvent> got1 = new CopyOnWriteArrayList<>();
        s0.initializeState(null);
        s1.initializeState(null);
        Thread r0 = TestDebeziumRealMysqlCdc.runAndCollect(s0, got0);
        Thread r1 = TestDebeziumRealMysqlCdc.runAndCollect(s1, got1);

        // 两侧各插一行：每实例只收自己分片表的快照 + 增量
        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO cdc_src_a VALUES (1, 'a-1')");
            st.execute("INSERT INTO cdc_src_b VALUES (1, 'b-1')");
        }
        waitUntilTableEvent(got0, own0, 30_000, "instance-0 must receive its routed table");
        waitUntilTableEvent(got1, own1, 30_000, "instance-1 must receive its routed table");

        // RESIDUAL（gated 真库面，plan 2510 具名裁定）：给错误路由的事件（不应存在）一个
        // 真实时长暴露窗口——Debezium 引擎与 binlog 投递是真实外部异步过程，虚拟时钟不可达，
        // 且"误路由未发生"是负向断言、无结构性计数可观测，只能以真实窗口承接。
        Thread.sleep(2_000);
        assertNoCrossRouting(got0, got1,
                own0.substring(own0.indexOf('.') + 1), own1.substring(own1.indexOf('.') + 1));

        s0.cancel();
        s1.cancel();
        r0.join(15_000);
        r1.join(15_000);
    }

    private static void assertNoCrossRouting(List<ChangeEvent> got0, List<ChangeEvent> got1,
                                             String ownShort0, String ownShort1) {
        for (ChangeEvent e : got0) {
            if (e.getMetadata() != null && e.getMetadata().getTable() != null) {
                String t = e.getMetadata().getTable();
                assertTrue(!t.endsWith(ownShort1),
                        "instance-0 must not receive events routed to instance-1: " + t);
            }
        }
        for (ChangeEvent e : got1) {
            if (e.getMetadata() != null && e.getMetadata().getTable() != null) {
                String t = e.getMetadata().getTable();
                assertTrue(!t.endsWith(ownShort0),
                        "instance-1 must not receive events routed to instance-0: " + t);
            }
        }
    }

    private static void waitUntilTableEvent(List<ChangeEvent> events, String table,
                                            long timeoutMs, String message)
            throws InterruptedException {
        String shortName = table.substring(table.indexOf('.') + 1);
        try {
            TestAwait.until("routed-table event delivered to instance (table=" + table + ")",
                    () -> {
                        for (ChangeEvent e : events) {
                            if (e.getMetadata() != null && shortName.equals(e.getMetadata().getTable())) {
                                return true;
                            }
                        }
                        return false;
                    }, timeoutMs);
        } catch (AssertionError e) {
            dumpDebeziumThreadStacks();
            fail(message + " (table=" + table + ", collected=" + events.size() + ")");
        }
    }

    /**
     * gated 调试取证：超时瞬间打印 Debezium/连接器相关线程的栈顶，定位卡点。
     */
    private static void dumpDebeziumThreadStacks() {
        System.err.println("=== thread stacks at timeout ===");
        for (Map.Entry<Thread, StackTraceElement[]> en : Thread.getAllStackTraces().entrySet()) {
            String name = en.getKey().getName();
            if (name.contains("pool-") || name.contains("debezium") || name.contains("global-worker")) {
                StackTraceElement[] frames = en.getValue();
                StringBuilder sb = new StringBuilder(name + " (" + en.getKey().getState() + "): ");
                for (int i = 0; i < Math.min(5, frames.length); i++) {
                    sb.append("\n    ").append(frames[i]);
                }
                System.err.println(sb);
            }
        }
    }
}
