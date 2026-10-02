/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink;
import io.nop.stream.core.common.functions.source.SourceFunction;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 验证收口 V3（用户裁定 2026-10-02）：**跨库单管线 e2e**——PostgreSQL CDC source
 * （Debezium 3.7，pgoutput）与 jdbc-2pc UPSERT_DELETE sink（真实 MySQL 目标，方言
 * upsert/delete）在同一条数据链内串跑：
 *
 * <pre>
 * pg(source, DebeziumCdcSourceFunction)
 *   → ChangeEvent 流
 *   → JdbcTwoPhaseCommitSink(dmlMode=UPSERT_DELETE, epoch 2PC + ledger 幂等)
 *   → mysql 目标表镜像（insert/update/delete 全谱）
 * </pre>
 *
 * <p>这是 roadmap §六「取代 SeaTunnel」验收场景 2（pg→mysql 初始化 + 增量）的
 * 组合回归：源/汇两侧此前已分别真库验证，本测试验证组合。
 * <b>Gated 语义</b>：docker 不可达时 assume 跳过（SKIP 可见，不伪造通过）。
 */
class TestDebeziumCrossDbSyncPipeline {

    private static final boolean DOCKER_AVAILABLE = detectDocker();

    private static PostgreSQLContainer<?> pg;
    private static MySQLContainer<?> mysqlTarget;

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
                "docker unavailable: cross-db pipeline test gated off (SKIP is visible by design)");
    }

    @AfterEach
    void tearDown() {
        if (pg != null) {
            pg.stop();
            pg = null;
        }
        if (mysqlTarget != null) {
            mysqlTarget.stop();
            mysqlTarget = null;
        }
    }

    private static void startContainers() throws Exception {
        pg = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("sourcedb")
                .withUsername("test")
                .withPassword("test")
                .withCommand("postgres", "-c", "wal_level=logical");
        pg.start();

        // 目标库不需要 binlog——只作为 UPSERT_DELETE 的写入端
        mysqlTarget = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("targetdb")
                .withUsername("test")
                .withPassword("test");
        mysqlTarget.start();

        try (Connection conn = pgConn(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE cdc_src (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            st.execute("INSERT INTO cdc_src VALUES (1, 'snap-a'), (2, 'snap-b')");
        }
        try (Connection conn = targetConn(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE cdc_target (id BIGINT PRIMARY KEY, name VARCHAR(100))");
        }
    }

    private static Connection pgConn() throws Exception {
        return DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    }

    private static Connection targetConn() throws Exception {
        return DriverManager.getConnection(mysqlTarget.getJdbcUrl(),
                mysqlTarget.getUsername(), mysqlTarget.getPassword());
    }

    private DebeziumConfig newSourceConfig() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("cross-db-pg-src");
        config.setConnectorType("postgres");
        config.setDatabaseHost(pg.getHost());
        config.setDatabasePort(pg.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        config.setDatabaseUser(pg.getUsername());
        config.setDatabasePassword(pg.getPassword());
        config.setDatabaseName(pg.getDatabaseName());
        config.setServerName("cross-db-pg");
        config.setTableIncludeList("public.cdc_src");
        config.setHeartbeatInterval(java.time.Duration.ofSeconds(1));
        config.setSchemaHistoryStore("file");
        config.setSchemaHistoryPath(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                "cross-db-pg-" + java.util.UUID.randomUUID() + "/schema-history.dat").toString());
        return config;
    }

    private JdbcTwoPhaseCommitSink<ChangeEvent> newSink() {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setJdbcUrl(mysqlTarget.getJdbcUrl());
        ds.setUsername(mysqlTarget.getUsername());
        ds.setPassword(mysqlTarget.getPassword());
        IJdbcTemplate jdbcTemplate = JdbcFactory.newJdbcTemplateFor(ds);
        JdbcTwoPhaseCommitSink<ChangeEvent> sink = JdbcTwoPhaseCommitSink.<ChangeEvent>builder()
                .jdbcTemplate(jdbcTemplate)
                .tableName("cdc_target")
                .dmlMode(JdbcTwoPhaseCommitSink.DmlMode.UPSERT_DELETE)
                .keyColumns("id")
                .build();
        sink.initializeLedgerTable();
        return sink;
    }

    /**
     * 验收场景 2：pg 初始化 + 增量（含 DELETE 传播）→ 真实 MySQL 目标镜像。
     * epoch 边界由测试驱动（collect → saveState → preCommit → commit），
     * 与 StreamSinkOperator.processBarrier 的调用序一致（saveState 先于 preCommit）。
     */
    @Test
    void testPgSourceToMySqlUpsertDeleteMirror() throws Exception {
        startContainers();

        List<ChangeEvent> collected = new CopyOnWriteArrayList<>();
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(newSourceConfig());
        source.initializeState(null);
        Thread runner = TestDebeziumRealMysqlCdc.runAndCollect(source, collected);

        // ---- epoch 1：snapshot 全量镜像 ----
        waitUntilOp(collected, "r", 2, 30_000, "snapshot rows must arrive");
        JdbcTwoPhaseCommitSink<ChangeEvent> sink = newSink();
        drainIntoEpoch(sink, collected, 1L);

        assertEquals(2, countTargetRows(), "epoch 1 must mirror the snapshot exactly once");
        assertEquals("snap-a", targetName(1));
        assertEquals("snap-b", targetName(2));

        // ---- epoch 2：增量 c / u / d 全谱 ----
        try (Connection conn = pgConn(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO cdc_src VALUES (3, 'ins-c')");
            st.execute("UPDATE cdc_src SET name = 'snap-a2' WHERE id = 1");
            st.execute("DELETE FROM cdc_src WHERE id = 2");
        }
        waitUntilOpKey(collected, "c", "3", 30_000, "insert must arrive");
        waitUntilOpKey(collected, "u", "1", 30_000, "update must arrive");
        waitUntilOpKey(collected, "d", "2", 30_000, "delete must arrive");
        drainIntoEpoch(sink, collected, 2L);

        assertEquals(2, countTargetRows(), "target must mirror the source row set");
        assertEquals("snap-a2", targetName(1), "upsert must apply the update");
        assertNull(targetName(2), "UPSERT_DELETE must propagate the delete");
        assertEquals("ins-c", targetName(3), "upsert must apply the insert");

        // ---- epoch 2 重提交：ledger 幂等守卫，镜像不被重放破坏 ----
        drainIntoEpoch(sink, collected, 2L);
        assertEquals(2, countTargetRows(), "idempotent re-commit must not duplicate");
        assertEquals("snap-a2", targetName(1));

        source.cancel();
        runner.join(15_000);
    }

    /**
     * 把当前收集到的全部事件作为一个 checkpoint epoch 写入 sink 并提交。
     */
    private static void drainIntoEpoch(JdbcTwoPhaseCommitSink<ChangeEvent> sink,
                                       List<ChangeEvent> collected, long epochId) throws Exception {
        sink.beginTransaction();
        for (ChangeEvent event : collected) {
            sink.invoke(event);
        }
        collected.clear();
        sink.saveState(epochId);
        sink.preCommit(epochId);
        sink.commit(epochId);
    }

    private static int countTargetRows() throws Exception {
        try (Connection conn = targetConn(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM cdc_target")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String targetName(long id) throws Exception {
        try (Connection conn = targetConn();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT name FROM cdc_target WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void waitUntilOp(List<ChangeEvent> events, String operation, int minCount,
                                    long timeoutMs, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long count;
        while (true) {
            count = events.stream().filter(e -> operation.equals(e.getOperation())).count();
            if (count >= minCount || System.currentTimeMillis() >= deadline) {
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(count >= minCount, message + " (op=" + operation + " count=" + count + ")");
    }

    private static void waitUntilOpKey(List<ChangeEvent> events, String operation, String key,
                                       long timeoutMs, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (ChangeEvent e : events) {
                if (operation.equals(e.getOperation()) && key.equals(keyString(e))) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        fail(message + " (op=" + operation + ", key=" + key + ")");
    }

    private static String keyString(ChangeEvent e) {
        Object key = e.getKey();
        if (key instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
            return String.valueOf(map.values().iterator().next());
        }
        return String.valueOf(key);
    }
}
