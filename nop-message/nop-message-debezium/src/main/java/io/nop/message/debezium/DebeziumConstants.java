/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium;

    
import java.util.HashMap;
import java.util.Map;

/**
 * Debezium 娡块常量定义
 */
public interface DebeziumConstants {
    /**
     * 模块名称
     */
    String MODULE_NAME = "nop-message-debezium";

    /**
     * 操作类型
     */
    String OP_CREATE = "c";
    String OP_UPDATE = "u";
    String OP_DELETE = "d";
    String OP_READ = "r";  // snapshot read

    /**
     * 支持的连接器类型
     */
    String CONNECTOR_MYSQL = "mysql";
    String CONNECTOR_POSTGRES = "postgres";
    String CONNECTOR_SQLSERVER = "sqlserver";
    /**
     * 快照模式
     */
    String SNAPSHOT_INITIAL = "initial";
    String SNAPSHOT_SCHEMA_ONLY = "schema_only";
    String SNAPSHOT_NEVER = "never";
    String SNAPSHOT_WHEN_NEEDED = "when_needed";
}
