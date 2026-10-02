/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.core.initialize.CoreInitialization;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.SourceFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI2/WI8（nop-stream-cdc-roadmap）：真实 MySQL 上的 CDC 闭环——Debezium 3.7 嵌入式引擎、
 * initial snapshot + 增量流、JdbcSchemaHistory 真实落库、kill → checkpoint restore →
 * 续传不重不丢（roadmap 验收场景 1 的 source 侧）。
 *
 * <p><b>Gated 语义</b>：docker 不可达时 assume 跳过（SKIP 可见，不伪造通过）；依赖真实
 * 引擎与真实 binlog 行为的完成判定以本测试为准。
 */
class TestDebeziumRealMysqlCdc {

    private static final boolean DOCKER_AVAILABLE = detectDocker();

    private static MySQLContainer<?> mysql;

    static boolean detectDocker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
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
                "docker unavailable: real-MySQL CDC test gated off (SKIP is visible by design)");
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
                // Debezium 需要 ROW binlog；官方镜像默认关闭，经 server 参数开启
                .withCommand("--log-bin=mysql-bin", "--binlog-format=ROW",
                        "--server-id=1721100", "--binlog-row-image=FULL");
        mysql.start();
        awaitStableMySql();
    }

    /**
     * MySQL 官方镜像初始化会经历「临时 server → SHUTDOWN → 正式 server」自重启窗口，
     * 容器就绪探测可能落在临时 server 上——Debezium 的连接横跨重启窗口即 EOF。
     * 引擎启动前等待 server 稳定（uptime 40s + 双间隔探测成功）。
     */
    private static void awaitStableMySql() {
        long startedAt = System.currentTimeMillis();
        try {
            while (System.currentTimeMillis() - startedAt < 40_000L) {
                Thread.sleep(2_000L);
            }
            for (int i = 0; i < 2; i++) {
                try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
                    st.execute("SELECT 1");
                }
                Thread.sleep(5_000L);
            }
            // 观测辅助（gated 调试）：general_log 落表，失败时可从服务器视角还原连接行为
            try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
                st.execute("SET GLOBAL general_log = 'ON'");
                st.execute("SET GLOBAL log_output = 'TABLE'");
            }
        } catch (Exception e) {
            throw new IllegalStateException("MySQL container not stable after startup window", e);
        }
    }

    private static Connection dbConn() throws Exception {
        return DriverManager.getConnection(mysql.getJdbcUrl(),
                "root", mysql.getPassword());
    }

    private DebeziumConfig newConfig() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("real-mysql-cdc");
        config.setConnectorType("mysql");
        config.setDatabaseHost(mysql.getHost());
        config.setDatabasePort(mysql.getMappedPort(MySQLContainer.MYSQL_PORT));
        config.setDatabaseUser("root");
        config.setDatabasePassword(mysql.getPassword());
        config.setDatabaseName(mysql.getDatabaseName());
        config.setServerName("real-mysql-server");
        config.setTableIncludeList(mysql.getDatabaseName() + ".cdc_src");
        config.setHeartbeatInterval(java.time.Duration.ofSeconds(1));
        // WI6 端到端验证：JdbcSchemaHistory 真实落库（建在同一个 MySQL 实例上）
        config.setSchemaHistoryStore("jdbc");
        // 容器化 MySQL 的 SSL 握手在本环境下 EOF：与 Debezium 主连接（默认禁 SSL）对齐
        config.setSchemaHistoryJdbcUrl(mysql.getJdbcUrl() + "?useSSL=false&allowPublicKeyRetrieval=true");
        config.setSchemaHistoryJdbcUser("root");
        config.setSchemaHistoryJdbcPassword(mysql.getPassword());
        config.setSchemaHistoryJdbcTable("cdc_schema_history");
        // Debezium MySQL initial snapshot 默认持全局读锁（FTWRL），而 JDBC schema history
        // 从第二条连接写历史——INSERT 被 global read lock 阻塞到被杀（processlist 取证：
        // Waiting for global read lock ~30s）。binlog 已开启且隔离级别 REPEATABLE READ，
        // locking=none 的一致快照语义由事务 + binlog 捕获保证。
        config.getExtraProperties().put("snapshot.locking.mode", "none");
        return config;
    }

    /**
     * 验收场景 1（source 侧）：initial snapshot → 增量流 → kill → checkpoint restore →
     * 续传。跨两次运行记录键不重复，恢复后插入的行必须到达。
     */
    @Test
    void testSnapshotStreamKillRestoreResume() throws Exception {
        startMySql();
        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE cdc_src (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            st.execute("INSERT INTO cdc_src VALUES (1, 'snap-a'), (2, 'snap-b')");
            // Debezium JdbcSchemaHistory 默认 DDL 为 history_data VARCHAR(65000)，超 MySQL
            // 行宽限制（utf8mb4 上限 16383 字节）。按其运行时列结构预建（history_data 用
            // MEDIUMTEXT），存储检测到表存在即跳过 DDL。
            st.execute("CREATE TABLE cdc_schema_history (id VARCHAR(36) NOT NULL, "
                    + "history_data MEDIUMTEXT, history_data_seq INTEGER, "
                    + "record_insert_ts TIMESTAMP NOT NULL, record_insert_seq INTEGER NOT NULL, "
                    + "PRIMARY KEY(id, history_data_seq))");
        }

        DebeziumConfig config = newConfig();
        List<ChangeEvent> collected = new CopyOnWriteArrayList<>();
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);
        // 生产契约：operator 先 initializeState（fresh=null）再 run——绑定 offset store，
        // 否则引擎落到 MemoryOffsetBackingStore，checkpoint 协议不生效。
        source.initializeState(null);
        Thread runner = runAndCollect(source, collected);

        // snapshot 事件（r）必须到达；随后增量插入（c）也必须到达
        waitUntil(collected, 2, 30_000, "snapshot rows must arrive");
        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO cdc_src VALUES (3, 'stream-c')");
        }
        waitUntilKey(collected, "3", 30_000, "streaming insert must arrive");

        // kill + checkpoint（join 后快照，消除 emitter 与 snapshot 的竞争）
        source.cancel();
        joinRunner(runner);
        OperatorSnapshotResult snapshot = source.snapshotState(1L);

        // restore：恢复分支绑定 checkpoint 位点，之后只收增量
        DebeziumCdcSourceFunction recovered = new DebeziumCdcSourceFunction(config);
        recovered.initializeState(restoredState(snapshot));
        assertNotNull(recovered.getOffsetStore());

        List<ChangeEvent> recoveredCollected = new CopyOnWriteArrayList<>();
        Thread recoveredRunner = runAndCollect(recovered, recoveredCollected);
        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO cdc_src VALUES (4, 'after-recovery-d')");
        }
        waitUntilKey(recoveredCollected, "4", 30_000,
                "insert after recovery must arrive (engine resumed from checkpoint offset)");

        recovered.cancel();
        joinRunner(recoveredRunner);

        // 不重不丢：两次运行的记录键全局唯一
        Set<String> keys = new HashSet<>();
        List<ChangeEvent> all = new ArrayList<>(collected);
        all.addAll(recoveredCollected);
        for (ChangeEvent e : all) {
            String key = String.valueOf(e.getKey());
            assertTrue(keys.add(key), "duplicate record key across kill/recover: " + key);
        }
        assertTrue(collected.size() >= 2);
        assertTrue(recoveredCollected.size() >= 1);
    }

    // ---- infra helpers ----

    private static TaskStateSnapshot restoredState(OperatorSnapshotResult snapshot) {
        // 单进程测试语义：operator state 快照直接作为恢复输入
        TaskStateSnapshot restored = new TaskStateSnapshot(
                new TaskLocation("job", "pipeline", "vertex", 0));
        restored.putOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY,
                snapshot.getOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY));
        return restored;
    }

    private static Thread runAndCollect(DebeziumCdcSourceFunction source,
                                        List<ChangeEvent> collected) {
        Thread runner = new Thread(() -> {
            try {
                source.run(new SourceFunction.SourceContext<ChangeEvent>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void collect(ChangeEvent element) {
                        collected.add(element);
                    }

                    @Override
                    public void collectWithTimestamp(ChangeEvent element, long timestamp) {
                        collected.add(element);
                    }

                    @Override
                    public void emitWatermark(long mark) {
                    }

                    @Override
                    public void markAsTemporarilyIdle() {
                    }

                    @Override
                    public long getProcessingTime() {
                        return System.currentTimeMillis();
                    }
                });
            } catch (InterruptedException ignored) {
                // cancel 路径的预期退出
            } catch (Exception e) {
                throw new io.nop.stream.core.exceptions.StreamException(
                        io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR, e)
                        .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                                "CDC source run failed");
            }
        }, "cdc-real-mysql-runner");
        runner.start();
        return runner;
    }

    private static void joinRunner(Thread runner) throws InterruptedException {
        runner.join(TimeUnit.SECONDS.toMillis(15));
        assertTrue(!runner.isAlive(),
                "CDC runner thread must terminate after cancel() before assertions");
    }

    private static void waitUntil(List<ChangeEvent> events, int minCount, long timeoutMs,
                                  String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (events.size() < minCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        if (events.size() < minCount) {
            dumpServerStateOnTimeout();
        }
        assertTrue(events.size() < minCount ? false : true, message + " (collected=" + events.size() + ")");
    }

    /**
     * gated 调试取证：超时瞬间打印服务器侧 processlist 与容器日志尾部（容器在 tearDown 才销毁）。
     */
    private static void dumpServerStateOnTimeout() {
        try (Connection conn = dbConn(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, user, host, db, command, time, state, SUBSTRING(info,1,80) FROM information_schema.processlist")) {
            System.err.println("=== processlist at timeout ===");
            while (rs.next()) {
                System.err.println(rs.getObject(1) + " " + rs.getObject(2) + " " + rs.getObject(3)
                        + " " + rs.getObject(4) + " " + rs.getObject(5) + " " + rs.getObject(6)
                        + " " + rs.getObject(7) + " " + rs.getObject(8));
            }
        } catch (Exception e) {
            System.err.println("processlist dump failed: " + e);
        }
    }

    private static void waitUntilKey(List<ChangeEvent> events, String key, long timeoutMs,
                                     String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (ChangeEvent e : events) {
                if (key.equals(keyString(e))) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        fail(message + " (key=" + key + ", collected=" + events.size() + ")");
    }

    /**
     * 主键字符串化：Debezium key 是单列主键的 map（id → N），取首个值。
     */
    private static String keyString(ChangeEvent e) {
        Object key = e.getKey();
        if (key instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
            return String.valueOf(map.values().iterator().next());
        }
        return String.valueOf(key);
    }
}
