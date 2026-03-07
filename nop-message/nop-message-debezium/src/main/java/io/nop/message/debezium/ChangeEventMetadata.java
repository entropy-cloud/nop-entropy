/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

/**
 * CDC 事件元数据
 */
@DataBean
public class ChangeEventMetadata {

    /**
     * 连接器名称
     */
    private final String connector;

    /**
     * 逻辑服务器名称
     */
    private final String serverName;

    /**
     * 数据库名称
     */
    private final String database;

    /**
     * Schema名称 (PostgreSQL, SQL Server)
     */
    private final String schema;

    /**
     * 表名称
     */
    private final String table;

    /**
     * Binlog位置 (MySQL)
     */
    private final Long binlogPosition;

    /**
     * Binlog文件名 (MySQL)
     */
    private final String binlogFile;

    /**
     * LSN位置 (PostgreSQL)
     */
    private final Long lsn;

    /**
     * Commit LSN (SQL Server)
     */
    private final Long commitLsn;

    /**
     * 事件来源: data, ddl
     */
    private final String source;

    public ChangeEventMetadata(
            @JsonProperty("connector") String connector,
            @JsonProperty("serverName") String serverName,
            @JsonProperty("database") String database,
            @JsonProperty("schema") String schema,
            @JsonProperty("table") String table,
            @JsonProperty("source") String source) {
        this.connector = connector;
        this.serverName = serverName;
        this.database = database;
        this.schema = schema;
        this.table = table;
        this.source = source;
        this.binlogPosition = null;
        this.binlogFile = null;
        this.lsn = null;
        this.commitLsn = null;
    }

    public ChangeEventMetadata(
            @JsonProperty("connector") String connector,
            @JsonProperty("serverName") String serverName,
            @JsonProperty("database") String database,
            @JsonProperty("schema") String schema,
            @JsonProperty("table") String table,
            @JsonProperty("source") String source,
            @JsonProperty("binlogPosition") Long binlogPosition,
            @JsonProperty("binlogFile") String binlogFile,
            @JsonProperty("lsn") Long lsn,
            @JsonProperty("commitLsn") Long commitLsn) {
        this.connector = connector;
        this.serverName = serverName;
        this.database = database;
        this.schema = schema;
        this.table = table;
        this.source = source;
        this.binlogPosition = binlogPosition;
        this.binlogFile = binlogFile;
        this.lsn = lsn;
        this.commitLsn = commitLsn;
    }
    public String getConnector() {
        return connector;
    }

    public String getServerName() {
        return serverName;
    }
    public String getDatabase() {
        return database;
    }
    public String getSchema() {
        return schema;
    }
    public String getTable() {
        return table;
    }
    public String getSource() {
        return source;
    }
    public Long getBinlogPosition() {
        return binlogPosition;
    }
    public String getBinlogFile() {
        return binlogFile;
    }
    public Long getLsn() {
        return lsn;
    }
    public Long getCommitLsn() {
        return commitLsn;
    }
    /**
     * 获取 Topic 名称 (Kafka 兼容格式)
     * 格式: serverName.database.table
     */
    public String getTopicName() {
        StringBuilder sb = new StringBuilder();
        sb.append(serverName);
        if (database != null) {
            sb.append('.').append(database);
        }
        if (table != null) {
            sb.append('.').append(table);
        }
        return sb.toString();
    }
    @Override
    public String toString() {
        return "ChangeEventMetadata{" +
                "connector='" + connector + '\'' +
                ", serverName='" + serverName + '\'' +
                ", database='" + database + '\'' +
                ", schema='" + schema + '\'' +
                ", table='" + table + '\'' +
                '}';
    }
}
