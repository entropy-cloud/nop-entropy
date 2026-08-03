/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

/**
 * Debezium 引擎配置构建器
 */
public class DebeziumEngineConfig {
    private static final Logger LOG = LoggerFactory.getLogger(DebeziumEngineConfig.class);

    /**
     * 构建嵌入式引擎配置属性
     */
    public static Properties buildProperties(DebeziumConfig config) {
        return buildProperties(config, false);
    }

    /**
     * 构建嵌入式引擎配置属性。
     *
     * <p>当 {@code useCustomOffsetStore} 为 true 时，{@code offset.storage} 指向
     * {@link NopStreamOffsetBackingStore} 的 FQCN（且不设 {@code offset.storage.file.filename}），
     * 由嵌入式引擎经反射实例化。此时不读取 {@link DebeziumConfig#getOffsetStoragePath()}，
     * 避免与自定义 store 冲突。
     *
     * @param config               Debezium 配置
     * @param useCustomOffsetStore 是否使用自定义 {@code NopStreamOffsetBackingStore}
     * @return 构建好的 Properties
     */
    public static Properties buildProperties(DebeziumConfig config, boolean useCustomOffsetStore) {
        Properties props = new Properties();

        // 引擎名称
        props.setProperty("name", config.getName());

        // 连接器类名
        String connectorClass = getConnectorClass(config.getConnectorType());
        props.setProperty("connector.class", connectorClass);

        // 数据库连接配置
        props.setProperty("database.hostname", config.getDatabaseHost());
        props.setProperty("database.port", String.valueOf(config.getDatabasePort()));
        if (config.getDatabaseUser() != null) {
            props.setProperty("database.user", config.getDatabaseUser());
        }
        if (config.getDatabasePassword() != null) {
            props.setProperty("database.password", config.getDatabasePassword());
        }

        // 逻辑服务器名称
        String serverName = config.getServerName();
        if (serverName == null) {
            serverName = config.getName();
        }

        // 根据连接器类型设置特定属性
        switch (config.getConnectorType().toLowerCase()) {
            case "mysql":
                configureMySqlConnector(props, config, serverName);
                break;
            case "postgres":
            case "postgresql":
                configurePostgresConnector(props, config, serverName);
                break;
            case "sqlserver":
                configureSqlServerConnector(props, config, serverName);
                break;
            default:
                throw new NopException(DebeziumErrors.ERR_DEBEZIUM_UNSUPPORTED_CONNECTOR_TYPE)
                        .param("connectorType", config.getConnectorType());
        }

        // 偏移量存储配置
        if (useCustomOffsetStore) {
            // 自定义 NopStreamOffsetBackingStore：由嵌入式引擎经反射实例化，connector-name
            // registry 桥接 source function 实例与 engine 实例共享同一份 offset 数据。
            props.setProperty("offset.storage", NopStreamOffsetBackingStore.class.getName());
            props.setProperty("offset.flush.interval.ms", String.valueOf(config.getOffsetFlushInterval().toMillis()));
        } else if (config.getOffsetStoragePath() != null) {
            props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
            props.setProperty("offset.storage.file.filename", config.getOffsetStoragePath());
            props.setProperty("offset.flush.interval.ms", String.valueOf(config.getOffsetFlushInterval().toMillis()));
        } else {
            // 默认使用内存存储
            props.setProperty("offset.storage", "org.apache.kafka.connect.storage.MemoryOffsetBackingStore");
        }

        // Schema 历史存储
        if (config.getSchemaHistoryPath() != null) {
            props.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
            props.setProperty("schema.history.internal.file.filename", config.getSchemaHistoryPath());
        } else {
            props.setProperty("schema.history.internal", "io.debezium.relational.history.MemorySchemaHistory");
        }
        // 表过滤
        if (config.getTableIncludeList() != null) {
            props.setProperty("table.include.list", config.getTableIncludeList());
        }
        // 数据库过滤
        if (config.getDatabaseIncludeList() != null) {
            props.setProperty("database.include.list", config.getDatabaseIncludeList());
        }
        // 快照模式
        props.setProperty("snapshot.mode", config.getSnapshotMode());

        // DDL 配置
        props.setProperty("include.schema.changes", String.valueOf(config.isIncludeSchemaChanges()));
        props.setProperty("include.ddl", String.valueOf(config.isIncludeDdl()));
        // 心跳配置
        props.setProperty("heartbeat.interval.ms", String.valueOf(config.getHeartbeatInterval().toMillis()));
        // 额外属性
        for (Map.Entry<String, String> entry : config.getExtraProperties().entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        return props;
    }
    private static void configureMySqlConnector(Properties props, DebeziumConfig config, String serverName) {
        props.setProperty("database.server.name", serverName);
        if (config.getDatabaseServerId() != null) {
            props.setProperty("database.server.id", String.valueOf(config.getDatabaseServerId()));
        } else {
            // 生成随机 server id
            props.setProperty("database.server.id", String.valueOf(System.currentTimeMillis() % 1000000000L));
        }
    }
    private static void configurePostgresConnector(Properties props, DebeziumConfig config, String serverName) {
        props.setProperty("database.server.name", serverName);
        props.setProperty("database.dbname", config.getDatabaseName());
        props.setProperty("plugin.name", "pgoutput");
    }
    private static void configureSqlServerConnector(Properties props, DebeziumConfig config, String serverName) {
        props.setProperty("database.server.name", serverName);
        props.setProperty("database.names", config.getDatabaseName());
    }
    private static String getConnectorClass(String connectorType) {
        switch (connectorType.toLowerCase()) {
            case "mysql":
                return "io.debezium.connector.mysql.MySqlConnector";
            case "postgres":
            case "postgresql":
                return "io.debezium.connector.postgresql.PostgresConnector";
            case "sqlserver":
                return "io.debezium.connector.sqlserver.SqlServerConnector";
            default:
                throw new NopException(DebeziumErrors.ERR_DEBEZIUM_UNSUPPORTED_CONNECTOR_TYPE)
                        .param("connectorType", connectorType);
        }
    }
}
