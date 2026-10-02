/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium.engine;

import io.nop.api.core.time.CoreMetrics;
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

        // 逻辑名称：Debezium 2.x 起 topic.prefix 取代了旧的 database.server.name，
        // 是 topic 命名与 offset key 的基础；name 属性继续作为 connector 实例名供引擎使用。
        String serverName = config.getServerName();
        if (serverName == null) {
            serverName = config.getName();
        }
        props.setProperty("topic.prefix", serverName);

        // 根据连接器类型设置特定属性（白名单见 getConnectorClass）
        switch (config.getConnectorType().toLowerCase()) {
            case "mysql":
                configureMySqlConnector(props, config, serverName);
                break;
            case "postgres":
            case "postgresql":
                configurePostgresConnector(props, config, serverName);
                break;
            default:
                throw new NopException(DebeziumErrors.ERR_DEBEZIUM_UNSUPPORTED_CONNECTOR_TYPE)
                        .param("connectorType", config.getConnectorType())
                        .param("supportedTypes", "mysql, postgres");
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

        // Schema 历史存储（cdc-design.md §3.4）：jdbc 默认 / file 单机回退，无静默内存默认
        applySchemaHistory(props, config);
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

        // A 层并行快照（cdc-design.md §3.3）：默认 1 = 串行，>1 透传 Debezium 单表 chunk 并行
        if (config.getSnapshotMaxThreads() > 1) {
            props.setProperty("snapshot.max.threads", String.valueOf(config.getSnapshotMaxThreads()));
        }

        // 增量快照信号表（预留信号驱动的增量快照）
        if (config.getSignalDataCollection() != null) {
            props.setProperty("signal.data.collection", config.getSignalDataCollection());
        }

        // WI7 守卫：nop checkpoint 是 offset 唯一恢复真相源，offset.storage 由本类钉定，
        // 外部覆盖会造成 restore 位点与引擎外部位点两主并存，类型化拒绝。
        if (config.getExtraProperties().containsKey("offset.storage")) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                    .param("detail", "offset.storage is managed by nop (NopStreamOffsetBackingStore via "
                            + "the checkpoint protocol is the single recovery source of truth); "
                            + "override via extraProperties is not allowed");
        }
        // WI7 守卫：事件处理并发钉定为 1，保护 source 单写者不变量（Barrier 由 source 读取线程注入）
        String processingThreads = config.getExtraProperties().get("record.processing.threads");
        if (processingThreads != null && !"1".equals(processingThreads.trim())) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                    .param("detail", "record.processing.threads is pinned to 1 to preserve the "
                            + "source single-writer invariant (00-vision.md invariant #4)");
        }
        props.setProperty("record.processing.threads", "1");

        // 心跳配置
        props.setProperty("heartbeat.interval.ms", String.valueOf(config.getHeartbeatInterval().toMillis()));
        // 额外属性
        for (Map.Entry<String, String> entry : config.getExtraProperties().entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        return props;
    }

    /**
     * Schema history 存储装配。jdbc 模式（默认）要求 schemaHistoryJdbcUrl，凭证字段支持
     * credential 引用（由调用方在引擎侧瞬态解密）；file 模式要求 schemaHistoryPath。
     * 两者皆缺 = 配置错误——静默内存 schema history 会导致快照后无法回放 DDL 上下文。
     */
    private static void applySchemaHistory(Properties props, DebeziumConfig config) {
        String mode = config.getSchemaHistoryStore() == null ? "jdbc"
                : config.getSchemaHistoryStore().trim().toLowerCase();
        switch (mode) {
            case "jdbc": {
                String url = config.getSchemaHistoryJdbcUrl();
                if (url == null || url.isEmpty()) {
                    throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                            .param("detail", "schemaHistoryJdbcUrl is required when schemaHistoryStore=jdbc"
                                    + " (the default); set schemaHistoryStore=file + schemaHistoryPath for"
                                    + " the single-node fallback");
                }
                props.setProperty("schema.history.internal", "io.debezium.storage.jdbc.history.JdbcSchemaHistory");
                props.setProperty("schema.history.internal.jdbc.url", url);
                if (config.getSchemaHistoryJdbcUser() != null) {
                    props.setProperty("schema.history.internal.jdbc.user", config.getSchemaHistoryJdbcUser());
                }
                if (config.getSchemaHistoryJdbcPassword() != null) {
                    props.setProperty("schema.history.internal.jdbc.password", config.getSchemaHistoryJdbcPassword());
                }
                if (config.getSchemaHistoryJdbcTable() != null) {
                    props.setProperty("schema.history.internal.jdbc.table.name", config.getSchemaHistoryJdbcTable());
                }
                break;
            }
            case "file": {
                String path = config.getSchemaHistoryPath();
                if (path == null || path.isEmpty()) {
                    throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                            .param("detail", "schemaHistoryPath is required when schemaHistoryStore=file");
                }
                props.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
                props.setProperty("schema.history.internal.file.filename", path);
                break;
            }
            default:
                throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                        .param("detail", "unknown schemaHistoryStore: " + config.getSchemaHistoryStore()
                                + " (supported: jdbc, file)");
        }
    }
    private static void configureMySqlConnector(Properties props, DebeziumConfig config, String serverName) {
        if (config.getDatabaseServerId() != null) {
            props.setProperty("database.server.id", String.valueOf(config.getDatabaseServerId()));
        } else {
            // 随机 server id 混入连接器名 hash：B 层表路由的多个实例可能同毫秒启动，
            // 纯时间戳派生会同 id 撞车（MySQL 拒绝重复 server id 的 binlog 注册）。
            long mixed = CoreMetrics.currentTimeMillis() * 31L
                    + config.getName().hashCode();
            props.setProperty("database.server.id", String.valueOf(Math.floorMod(mixed, 1_000_000_000L) + 1));
        }
        // schema change 事件为 MySQL 连接器能力（Postgres 经 schema history 承载 DDL 上下文）
        props.setProperty("include.schema.changes", String.valueOf(config.isIncludeSchemaChanges()));
    }
    private static void configurePostgresConnector(Properties props, DebeziumConfig config, String serverName) {
        if (config.getDatabaseName() == null || config.getDatabaseName().isEmpty()) {
            throw new NopException(DebeziumErrors.ERR_DEBEZIUM_CONFIG_INVALID)
                    .param("detail", "databaseName is required for the postgres connector"
                            + " (mapped to database.dbname)");
        }
        props.setProperty("database.dbname", config.getDatabaseName());
        props.setProperty("plugin.name", "pgoutput");
    }
    private static String getConnectorClass(String connectorType) {
        switch (connectorType.toLowerCase()) {
            case "mysql":
                return "io.debezium.connector.mysql.MySqlConnector";
            case "postgres":
            case "postgresql":
                return "io.debezium.connector.postgresql.PostgresConnector";
            default:
                throw new NopException(DebeziumErrors.ERR_DEBEZIUM_UNSUPPORTED_CONNECTOR_TYPE)
                        .param("connectorType", connectorType)
                        .param("supportedTypes", "mysql, postgres");
        }
    }
}
