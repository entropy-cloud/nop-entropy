/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.core.initialize.CoreInitialization;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.common.functions.source.SourceFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * WI2/WI8（nop-stream-cdc-roadmap）：真实 PostgreSQL 上的 CDC——pgoutput 逻辑解码、
 * initial snapshot（r）、增量 c/u/d 事件全谱观测（roadmap 验收场景 2 的 source 侧：
 * DELETE 传播是 UPSERT_DELETE sink 的输入前提）。
 *
 * <p><b>Gated 语义</b>：docker 不可达时 assume 跳过（SKIP 可见，不伪造通过）。
 */
class TestDebeziumRealPostgresCdc {

    private static final boolean DOCKER_AVAILABLE = detectDocker();

    private static PostgreSQLContainer<?> pg;

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
                "docker unavailable: real-PostgreSQL CDC test gated off (SKIP is visible by design)");
    }

    @AfterEach
    void tearDown() {
        if (pg != null) {
            pg.stop();
            pg = null;
        }
    }

    private static void startPostgres() {
        pg = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                // pgoutput 逻辑解码依赖 wal_level=logical
                .withCommand("postgres", "-c", "wal_level=logical");
        pg.start();
    }

    private static Connection dbConn() throws Exception {
        return DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    }

    private DebeziumConfig newConfig() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("real-pg-cdc");
        config.setConnectorType("postgres");
        config.setDatabaseHost(pg.getHost());
        config.setDatabasePort(pg.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        config.setDatabaseUser(pg.getUsername());
        config.setDatabasePassword(pg.getPassword());
        config.setDatabaseName(pg.getDatabaseName());
        config.setServerName("real-pg-server");
        config.setTableIncludeList("public.cdc_src");
        config.setHeartbeatInterval(java.time.Duration.ofSeconds(1));
        config.setSchemaHistoryStore("file");
        config.setSchemaHistoryPath(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                        "real-pg-cdc-" + java.util.UUID.randomUUID() + "/schema-history.dat")
                        .toString());
        return config;
    }

    @Test
    void testSnapshotAndFullChangeSpectrum() throws Exception {
        startPostgres();
        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE cdc_src (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            st.execute("INSERT INTO cdc_src VALUES (1, 'snap-a')");
        }

        DebeziumConfig config = newConfig();
        List<ChangeEvent> collected = new CopyOnWriteArrayList<>();
        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(config);
        source.initializeState(null);
        Thread runner = runAndCollect(source, collected);

        // snapshot（r）
        waitUntilOp(collected, "r", 30_000, "snapshot read event must arrive");

        // 增量三谱：c / u / d
        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO cdc_src VALUES (2, 'ins-b')");
        }
        waitUntilOpKey(collected, "c", "2", 30_000, "insert event must arrive");

        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("UPDATE cdc_src SET name = 'ins-b2' WHERE id = 2");
        }
        waitUntilOpKey(collected, "u", "2", 30_000, "update event must arrive");

        try (Connection conn = dbConn(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM cdc_src WHERE id = 2");
        }
        waitUntilOpKey(collected, "d", "2", 30_000, "delete event must arrive (UPSERT_DELETE input)");

        source.cancel();
        runner.join(15_000);

        // 事件谱完整性：r/c/u/d 全部到达，且 delete 事件携带 before-image 键
        boolean hasDeleteWithKey = collected.stream()
                .anyMatch(e -> "d".equals(e.getOperation())
                        && e.getKey() != null && !e.getKey().isEmpty());
        assertTrue(hasDeleteWithKey, "delete event must carry the key image");
    }

    // ---- infra helpers ----

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
        }, "cdc-real-pg-runner");
        runner.start();
        return runner;
    }

    private static String keyString(ChangeEvent e) {
        Object key = e.getKey();
        if (key instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
            return String.valueOf(map.values().iterator().next());
        }
        return String.valueOf(key);
    }

    private static void waitUntilOp(List<ChangeEvent> events, String operation, long timeoutMs,
                                    String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (ChangeEvent e : events) {
                if (operation.equals(e.getOperation())) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        fail(message + " (op=" + operation + ", collected=" + events.size() + ")");
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
        fail(message + " (op=" + operation + ", key=" + key + ", collected=" + events.size() + ")");
    }
}
