/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface DbMigrationErrors {
    String ARG_CHANGE_TYPE = "changeType";
    String ARG_VERSION = "version";
    String ARG_TABLE_NAME = "tableName";
    String ARG_COLUMN_NAME = "columnName";
    String ARG_MIGRATION_PATH = "migrationPath";
    String ARG_CHECKSUM = "checksum";
    String ARG_EXPECTED_CHECKSUM = "expectedChecksum";
        String ARG_PRECONDITION_TYPE = "preconditionType";

    ErrorCode ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE = define(
            "nop.err.db-migration.unknown-change-type",
            "未知的迁移变更类型:{changeType}",
            ARG_CHANGE_TYPE);

    ErrorCode ERR_DB_MIGRATION_PRECONDITION_FAILED = define(
            "nop.err.db-migration.precondition-failed",
            "迁移前置条件检查失败，版本号:{version},前置条件:{preconditionType}",
            ARG_VERSION,
            ARG_PRECONDITION_TYPE);

    ErrorCode ERR_DB_MIGRATION_EXECUTION_FAILED = define(
            "nop.err.db-migration.execution-failed",
            "迁移执行失败，版本号:{version}",
            ARG_VERSION);

    ErrorCode ERR_DB_MIGRATION_CHECKSUM_MISMATCH = define(
            "nop.err.db-migration.checksum-mismatch",
            "迁移文件校验和不匹配，期望值:{expectedChecksum},实际值:{checksum}",
            ARG_EXPECTED_CHECKSUM,
            ARG_CHECKSUM);

    ErrorCode ERR_DB_MIGRATION_NO_SQL_FOR_DIALECT = define(
            "nop.err.db-migration.no-sql-for-dialect",
            "当前变更类型未配置对应方言SQL:{changeType}",
            ARG_CHANGE_TYPE);

    ErrorCode ERR_DB_MIGRATION_FILE_NOT_FOUND = define(
            "nop.err.db-migration.file-not-found",
            "迁移文件不存在:{migrationPath}",
            ARG_MIGRATION_PATH);

    ErrorCode ERR_DB_MIGRATION_HISTORY_QUERY_FAIL = define(
            "nop.err.db-migration.history-query-fail",
            "查询迁移历史失败，表名:{tableName}",
            ARG_TABLE_NAME);

    ErrorCode ERR_DB_MIGRATION_TABLE_EXISTS = define(
            "nop.err.db-migration.table-already-exists",
            "数据表已存在:{tableName}",
            ARG_TABLE_NAME);

    ErrorCode ERR_DB_MIGRATION_TABLE_NOT_EXISTS = define(
            "nop.err.db-migration.table-not-exists",
            "数据表不存在:{tableName}",
            ARG_TABLE_NAME);

    ErrorCode ERR_DB_MIGRATION_COLUMN_EXISTS = define(
            "nop.err.db-migration.column-already-exists",
            "字段已存在，表名:{tableName},字段名:{columnName}",
            ARG_TABLE_NAME,
            ARG_COLUMN_NAME);

    ErrorCode ERR_DB_MIGRATION_COLUMN_NOT_EXISTS = define(
            "nop.err.db-migration.column-not-exists",
            "字段不存在，表名:{tableName},字段名:{columnName}",
            ARG_TABLE_NAME,
            ARG_COLUMN_NAME);
}
