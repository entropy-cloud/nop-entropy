/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Debezium 嵌入式引擎配置
 * <p>
 * 支持配置数据库连接器、偏移量存储、Schema历史等
 */
public class DebeziumConfig {
    /**
     * 连接器名称
     */
    private String name;

    /**
     * 连接器类型: mysql, postgres, sqlserver
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
     * 心跳间隔
     */
    private Duration heartbeatInterval = Duration.ofMinutes(5);

    /**
     * 是否包含Schema变更
     */
    private boolean includeSchemaChanges = false;

    /**
     * 是否包含DDL
     */
    private boolean includeDdl = false;

    /**
     * 额外的配置属性
     */
    private Map<String, String> extraProperties = new HashMap<>();

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

    public boolean isIncludeDdl() {
        return includeDdl;
    }

    public void setIncludeDdl(boolean includeDdl) {
        this.includeDdl = includeDdl;
    }

    public Map<String, String> getExtraProperties() {
        return extraProperties;
    }

    public void setExtraProperties(Map<String, String> extraProperties) {
        this.extraProperties = extraProperties;
    }

    /**
     * 添加额外属性
     */
    public DebeziumConfig addExtraProperty(String key, String value) {
        this.extraProperties.put(key, value);
        return this;
    }
}
