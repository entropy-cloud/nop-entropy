/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium;

import java.io.Serializable;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debezium 嵌入式引擎配置
 * <p>
 * 支持配置数据库连接器、偏移量存储、Schema历史等
 * <p>
 * Implements {@link Serializable} so that {@code DebeziumCdcSourceFunction} can persist its
 * {@code config} field across checkpoint recovery (the field is no longer {@code transient}).
 * All fields are serializable POJO types.
 */
public class DebeziumConfig implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 连接器名称
     */
    private String name;

    /**
     * 连接器类型: mysql, postgres（白名单，其他值在引擎配置构建期 fail-fast）
     */
    private String connectorType;

    /**
     * 数据库主机
     */
    private String databaseHost;

    /**
     * 数据库端口
     */
    private int databasePort;

    /**
     * 数据库用户名
     */
    private String databaseUser;

    /**
     * 数据库密码
     */
    private String databasePassword;

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * 数据库服务器ID (MySQL需要)
     */
    private Long databaseServerId;

    /**
     * 服务器名称/逻辑名称
     */
    private String serverName;

    /**
     * 要捕获的表列表 (逗号分隔)
     */
    private String tableIncludeList;

    /**
     * 要捕获的数据库列表 (逗号分隔)
     */
    private String databaseIncludeList;

    /**
     * 偏移量存储文件路径
     */
    private String offsetStoragePath;

    /**
     * 偏移量刷新间隔
     */
    private Duration offsetFlushInterval = Duration.ofMinutes(1);

    /**
     * Schema历史存储路径
     */
    private String schemaHistoryPath;

    /**
     * 快照模式
     */
    private String snapshotMode = "initial";

    /**
     * A 层并行快照线程数：透传 Debezium snapshot.max.threads（引擎内单表 chunk 级并行
     * initial snapshot）。默认 1 = 串行。快照期写入必须配合幂等 upsert（cdc-design.md §3.3）。
     */
    private int snapshotMaxThreads = 1;

    /**
     * schema history 存储模式：jdbc（默认，多节点可靠）或 file（单机零依赖回退）。
     * 不再提供静默内存默认——无历史上下文的快照是错误行为（cdc-design.md §3.4）。
     */
    private String schemaHistoryStore = "jdbc";

    /**
     * jdbc 模式的 schema history 连接 URL（schema.history.internal.jdbc.url）
     */
    private String schemaHistoryJdbcUrl;

    /**
     * jdbc 模式用户名，支持 credential:{id}#{field} 引用（引擎侧瞬态解密）
     */
    private String schemaHistoryJdbcUser;

    /**
     * jdbc 模式密码，支持 credential:{id}#{field} 引用（引擎侧瞬态解密）
     */
    private String schemaHistoryJdbcPassword;

    /**
     * jdbc 模式存储表名（可选；缺省使用 Debezium 官方默认表名）
     */
    private String schemaHistoryJdbcTable;

    /**
     * 增量快照信号表（透传 signal.data.collection，为信号驱动的增量快照预留）
     */
    private String signalDataCollection;

    /**
     * 心跳间隔
     */
    private Duration heartbeatInterval = Duration.ofMinutes(5);

    /**
     * 是否包含Schema变更（仅 MySQL 连接器映射 include.schema.changes）
     */
    private boolean includeSchemaChanges = false;

    /**
     * 额外的配置属性（装配期专用——读侧经 {@link #getExtraProperties()} 返回冻结视图，
     * 见 C1-012 / plan 2558 裁定 G）。ConcurrentHashMap backing makes the frozen read
     * side safe even when a write races a snapshot: Map.copyOf iterates a weakly-consistent
     * view instead of throwing ConcurrentModificationException. Null keys/values are still
     * rejected before they reach the map (ConcurrentHashMap forbids both).
     */
    private Map<String, String> extraProperties = new ConcurrentHashMap<>();

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(DebeziumConfig.class);

    // ============== Getters and Setters ==============

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }

    public String getDatabaseHost() {
        return databaseHost;
    }

    public void setDatabaseHost(String databaseHost) {
        this.databaseHost = databaseHost;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    public void setDatabasePort(int databasePort) {
        this.databasePort = databasePort;
    }

    public String getDatabaseUser() {
        return databaseUser;
    }

    public void setDatabaseUser(String databaseUser) {
        this.databaseUser = databaseUser;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    public void setDatabasePassword(String databasePassword) {
        this.databasePassword = databasePassword;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public Long getDatabaseServerId() {
        return databaseServerId;
    }

    public void setDatabaseServerId(Long databaseServerId) {
        this.databaseServerId = databaseServerId;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getTableIncludeList() {
        return tableIncludeList;
    }

    public void setTableIncludeList(String tableIncludeList) {
        this.tableIncludeList = tableIncludeList;
    }

    public String getDatabaseIncludeList() {
        return databaseIncludeList;
    }

    public void setDatabaseIncludeList(String databaseIncludeList) {
        this.databaseIncludeList = databaseIncludeList;
    }

    public String getOffsetStoragePath() {
        return offsetStoragePath;
    }

    public void setOffsetStoragePath(String offsetStoragePath) {
        this.offsetStoragePath = offsetStoragePath;
    }

    public Duration getOffsetFlushInterval() {
        return offsetFlushInterval;
    }

    public void setOffsetFlushInterval(Duration offsetFlushInterval) {
        this.offsetFlushInterval = offsetFlushInterval;
    }

    public String getSchemaHistoryPath() {
        return schemaHistoryPath;
    }

    public void setSchemaHistoryPath(String schemaHistoryPath) {
        this.schemaHistoryPath = schemaHistoryPath;
    }

    public String getSnapshotMode() {
        return snapshotMode;
    }

    public void setSnapshotMode(String snapshotMode) {
        this.snapshotMode = snapshotMode;
    }

    public int getSnapshotMaxThreads() {
        return snapshotMaxThreads;
    }

    public void setSnapshotMaxThreads(int snapshotMaxThreads) {
        this.snapshotMaxThreads = snapshotMaxThreads;
    }

    public String getSchemaHistoryStore() {
        return schemaHistoryStore;
    }

    public void setSchemaHistoryStore(String schemaHistoryStore) {
        this.schemaHistoryStore = schemaHistoryStore;
    }

    public String getSchemaHistoryJdbcUrl() {
        return schemaHistoryJdbcUrl;
    }

    public void setSchemaHistoryJdbcUrl(String schemaHistoryJdbcUrl) {
        this.schemaHistoryJdbcUrl = schemaHistoryJdbcUrl;
    }

    public String getSchemaHistoryJdbcUser() {
        return schemaHistoryJdbcUser;
    }

    public void setSchemaHistoryJdbcUser(String schemaHistoryJdbcUser) {
        this.schemaHistoryJdbcUser = schemaHistoryJdbcUser;
    }

    public String getSchemaHistoryJdbcPassword() {
        return schemaHistoryJdbcPassword;
    }

    public void setSchemaHistoryJdbcPassword(String schemaHistoryJdbcPassword) {
        this.schemaHistoryJdbcPassword = schemaHistoryJdbcPassword;
    }

    public String getSchemaHistoryJdbcTable() {
        return schemaHistoryJdbcTable;
    }

    public void setSchemaHistoryJdbcTable(String schemaHistoryJdbcTable) {
        this.schemaHistoryJdbcTable = schemaHistoryJdbcTable;
    }

    public String getSignalDataCollection() {
        return signalDataCollection;
    }

    public void setSignalDataCollection(String signalDataCollection) {
        this.signalDataCollection = signalDataCollection;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public boolean isIncludeSchemaChanges() {
        return includeSchemaChanges;
    }

    public void setIncludeSchemaChanges(boolean includeSchemaChanges) {
        this.includeSchemaChanges = includeSchemaChanges;
    }

    /**
     * Returns a <b>frozen view</b> of the extra properties (plan 2558 裁定 G / C1-012:
     * {@code Map.copyOf} — immutable, per-call snapshot). Mutation attempts through this view
     * throw {@code UnsupportedOperationException}; configuration setup must go through
     * {@link #addExtraProperty(String, String)} / {@link #setExtraProperties(Map)}.
     *
     * <p><b>装配期专用（documented-only contract）</b>: extraProperties is an assembly-time
     * configuration surface — the engine reads it once while building the connector
     * properties. There is no lifecycle hook enforcing assembly-time-only writes; the freeze
     * is the read-side view plus this declaration. Null values are skipped at the write
     * entries ({@code Map.copyOf} rejects null values with NPE).
     */
    public Map<String, String> getExtraProperties() {
        Map<String, String> field = this.extraProperties;
        return field == null ? Map.of() : Map.copyOf(field);
    }

    public void setExtraProperties(Map<String, String> extraProperties) {
        // copy into the internal concurrent map (kept serializable for checkpoint
        // persistence), skipping null values — the frozen getter view (Map.copyOf)
        // rejects null values, and ConcurrentHashMap forbids them too
        if (extraProperties == null || extraProperties.isEmpty()) {
            this.extraProperties = new ConcurrentHashMap<>();
            return;
        }
        Map<String, String> copied = new ConcurrentHashMap<>();
        for (Map.Entry<String, String> entry : extraProperties.entrySet()) {
            if (entry.getKey() == null) {
                LOG.warn("nop.message.debezium.config.extra-property-null-key - skipped");
                continue;
            }
            if (entry.getValue() == null) {
                LOG.warn("nop.message.debezium.config.extra-property-null-value:key={} - "
                        + "null values are not representable in the frozen view; entry skipped",
                        entry.getKey());
                continue;
            }
            copied.put(entry.getKey(), entry.getValue());
        }
        this.extraProperties = copied;
    }

    /**
     * 添加额外属性（写 raw 字段——冻结仅作用于读侧视图，见 {@link #getExtraProperties()}）。
     * null 值跳过并 WARN（冻结视图 {@code Map.copyOf} 不接受 null 值）。
     */
    public DebeziumConfig addExtraProperty(String key, String value) {
        if (key == null) {
            LOG.warn("nop.message.debezium.config.extra-property-null-key - skipped");
            return this;
        }
        if (value == null) {
            LOG.warn("nop.message.debezium.config.extra-property-null-value:key={} - "
                    + "null values are not representable in the frozen view; entry skipped", key);
            return this;
        }
        this.extraProperties.put(key, value);
        return this;
    }
}
