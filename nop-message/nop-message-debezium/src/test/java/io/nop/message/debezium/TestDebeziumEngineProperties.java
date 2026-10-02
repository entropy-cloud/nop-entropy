/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.message.debezium;

import io.nop.api.core.exceptions.NopException;
import io.nop.message.debezium.engine.DebeziumEngineConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3/WI6/WI7（ai-dev/backlog/nop-stream-cdc-roadmap.md）：引擎属性契约——
 * A 层并行快照透传、schema history jdbc/file 显式模式（无静默内存默认）、
 * connectorType 白名单、offset.storage / record.processing.threads 守卫。
 */
public class TestDebeziumEngineProperties {

    private DebeziumConfig newConfig(String connectorType) {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("cdc-1");
        config.setConnectorType(connectorType);
        config.setDatabaseHost("localhost");
        config.setDatabasePort(3306);
        config.setDatabaseUser("repl");
        config.setDatabasePassword("secret");
        config.setSchemaHistoryStore("file");
        config.setSchemaHistoryPath("/tmp/cdc-1/schema-history.dat");
        return config;
    }

    @Test
    void testTopicPrefixFromServerName() {
        DebeziumConfig config = newConfig("mysql");
        config.setServerName("dbserver1");
        Properties props = DebeziumEngineConfig.buildProperties(config, true);
        assertEquals("dbserver1", props.getProperty("topic.prefix"));
        assertFalse(props.containsKey("database.server.name"),
                "database.server.name was removed in Debezium 2.x and must not be emitted");
    }

    @Test
    void testTopicPrefixFallsBackToName() {
        DebeziumConfig config = newConfig("mysql");
        Properties props = DebeziumEngineConfig.buildProperties(config, true);
        assertEquals("cdc-1", props.getProperty("topic.prefix"));
    }

    @Test
    void testSnapshotMaxThreadsDefaultOff() {
        DebeziumConfig config = newConfig("mysql");
        Properties props = DebeziumEngineConfig.buildProperties(config, true);
        assertNull(props.getProperty("snapshot.max.threads"),
                "default serial snapshot must not emit snapshot.max.threads");
    }

    @Test
    void testSnapshotMaxThreadsPassthrough() {
        DebeziumConfig config = newConfig("mysql");
        config.setSnapshotMaxThreads(4);
        Properties props = DebeziumEngineConfig.buildProperties(config, true);
        assertEquals("4", props.getProperty("snapshot.max.threads"));
    }

    @Test
    void testSignalDataCollectionPassthrough() {
        DebeziumConfig config = newConfig("mysql");
        config.setSignalDataCollection("dbz.signal");
        Properties props = DebeziumEngineConfig.buildProperties(config, true);
        assertEquals("dbz.signal", props.getProperty("signal.data.collection"));
    }

    @Test
    void testConnectorWhitelist() {
        DebeziumConfig mysql = newConfig("mysql");
        assertEquals("io.debezium.connector.mysql.MySqlConnector",
                DebeziumEngineConfig.buildProperties(mysql, true).getProperty("connector.class"));

        DebeziumConfig postgres = newConfig("postgres");
        postgres.setDatabaseName("inventory");
        assertEquals("io.debezium.connector.postgresql.PostgresConnector",
                DebeziumEngineConfig.buildProperties(postgres, true).getProperty("connector.class"));

        DebeziumConfig sqlserver = newConfig("sqlserver");
        NopException err = assertThrows(NopException.class,
                () -> DebeziumEngineConfig.buildProperties(sqlserver, true));
        assertTrue(String.valueOf(err.getParams().get("supportedTypes")).contains("postgres"),
                "error must carry the supported connector whitelist");
    }

    @Test
    void testIncludeSchemaChangesMysqlOnly() {
        DebeziumConfig mysql = newConfig("mysql");
        mysql.setIncludeSchemaChanges(true);
        Properties mysqlProps = DebeziumEngineConfig.buildProperties(mysql, true);
        assertEquals("true", mysqlProps.getProperty("include.schema.changes"));

        DebeziumConfig postgres = newConfig("postgres");
        postgres.setDatabaseName("inventory");
        Properties postgresProps = DebeziumEngineConfig.buildProperties(postgres, true);
        assertFalse(postgresProps.containsKey("include.schema.changes"),
                "postgres does not emit schema change events; the option is MySQL-specific");
    }

    @Test
    void testSchemaHistoryJdbcDefaultRequiresUrl() {
        DebeziumConfig config = newConfig("mysql");
        config.setSchemaHistoryStore(null); // 显式 null 仍按默认 jdbc 解析
        config.setSchemaHistoryPath(null);
        NopException err = assertThrows(NopException.class,
                () -> DebeziumEngineConfig.buildProperties(config, true));
        assertTrue(String.valueOf(err.getParams().get("detail")).contains("schemaHistoryJdbcUrl"),
                "jdbc (default) without URL must fail with remediation guidance");
    }

    @Test
    void testSchemaHistoryJdbcMode() {
        DebeziumConfig config = newConfig("mysql");
        config.setSchemaHistoryStore("jdbc");
        config.setSchemaHistoryJdbcUrl("jdbc:mysql://localhost:3306/meta");
        config.setSchemaHistoryJdbcUser("u");
        config.setSchemaHistoryJdbcPassword("p");
        config.setSchemaHistoryJdbcTable("cdc_schema_history");
        Properties props = DebeziumEngineConfig.buildProperties(config, true);
        assertEquals("io.debezium.storage.jdbc.history.JdbcSchemaHistory",
                props.getProperty("schema.history.internal"));
        assertEquals("jdbc:mysql://localhost:3306/meta", props.getProperty("schema.history.internal.jdbc.url"));
        assertEquals("u", props.getProperty("schema.history.internal.jdbc.user"));
        assertEquals("p", props.getProperty("schema.history.internal.jdbc.password"));
        assertEquals("cdc_schema_history", props.getProperty("schema.history.internal.jdbc.table.name"));
    }

    @Test
    void testSchemaHistoryFileMode() {
        DebeziumConfig config = newConfig("mysql");
        Properties props = DebeziumEngineConfig.buildProperties(config, true);
        assertEquals("io.debezium.storage.file.history.FileSchemaHistory",
                props.getProperty("schema.history.internal"));
        assertEquals("/tmp/cdc-1/schema-history.dat",
                props.getProperty("schema.history.internal.file.filename"));
    }

    @Test
    void testSchemaHistoryFileModeRequiresPath() {
        DebeziumConfig config = newConfig("mysql");
        config.setSchemaHistoryPath(null);
        assertThrows(NopException.class, () -> DebeziumEngineConfig.buildProperties(config, true));
    }

    @Test
    void testSchemaHistoryUnknownModeRejected() {
        DebeziumConfig config = newConfig("mysql");
        config.setSchemaHistoryStore("memory");
        NopException err = assertThrows(NopException.class,
                () -> DebeziumEngineConfig.buildProperties(config, true));
        assertTrue(String.valueOf(err.getParams().get("detail")).contains("supported: jdbc, file"),
                "no silent in-memory schema history (cdc-design.md §3.4)");
    }

    @Test
    void testOffsetStorageOverrideRejected() {
        DebeziumConfig config = newConfig("mysql");
        config.setExtraProperties(new java.util.HashMap<>(
                Collections.singletonMap("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")));
        NopException err = assertThrows(NopException.class,
                () -> DebeziumEngineConfig.buildProperties(config, true));
        assertTrue(String.valueOf(err.getParams().get("detail")).contains("single recovery source of truth"),
                "offset.storage is nop-managed: nop checkpoint is the single recovery truth (cdc-design.md §3.4)");
    }

    @Test
    void testProcessingThreadsPinned() {
        DebeziumConfig config = newConfig("mysql");
        config.getExtraProperties().put("record.processing.threads", "4");
        NopException err = assertThrows(NopException.class,
                () -> DebeziumEngineConfig.buildProperties(config, true));
        assertTrue(String.valueOf(err.getParams().get("detail")).contains("single-writer"));

        DebeziumConfig ok = newConfig("mysql");
        ok.getExtraProperties().put("record.processing.threads", "1");
        assertEquals("1",
                DebeziumEngineConfig.buildProperties(ok, true).getProperty("record.processing.threads"));
    }
}
