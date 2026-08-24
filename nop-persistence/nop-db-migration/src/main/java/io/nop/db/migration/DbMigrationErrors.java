/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical-entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration;

import io.nop.api.core.exceptions.ErrorCode;

public interface DbMigrationErrors {
    String ARG_CHANGE_TYPE = "changeType";
    String ARG_VERSION = "version";
    String ARG_TABLE_NAME = "tableName";
    String ARG_COLUMN_NAME = "columnName";
    String ARG_MIGRATION_PATH = "migrationPath";
    String ARG_CHECKSUM = "checksum";
    String ARG_EXPECTED_CHECKSUM = "expectedChecksum";
    String ARG_QUERY_SPACE = "querySpace";
    String ARG_DEFAULT_VALUE = "defaultValue";
    String ARG_PRECONDITION_TYPE = "preconditionType";

    ErrorCode ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE = ErrorCode.define(
        "nop.err.db-migration.unknown-change-type",
        "Unknown change type: {changeType}",
        ARG_CHANGE_TYPE);

    ErrorCode ERR_DB_MIGRATION_PRECONDITION_FAILED = ErrorCode.define(
        "nop.err.db-migration.precondition-failed",
        "Precondition of type {preconditionType} failed for migration {version}",
        ARG_PRECONDITION_TYPE, ARG_VERSION);

    ErrorCode ERR_DB_MIGRATION_EXECUTION_FAILED = ErrorCode.define(
        "nop.err.db-migration.execution-failed",
        "Migration execution failed for version: {version}",
        ARG_VERSION);

    ErrorCode ERR_DB_MIGRATION_CHECKSUM_MISMATCH = ErrorCode.define(
        "nop.err.db-migration.checksum-mismatch",
        "Checksum mismatch for migration {version}: expected={expectedChecksum}, actual={checksum}",
        ARG_VERSION, ARG_EXPECTED_CHECKSUM, ARG_CHECKSUM);

    ErrorCode ERR_DB_MIGRATION_NO_SQL_FOR_DIALECT = ErrorCode.define(
        "nop.err.db-migration.no-sql-for-dialect",
        "No SQL defined for dialect in change type: {changeType}",
        ARG_CHANGE_TYPE);

    ErrorCode ERR_DB_MIGRATION_FILE_NOT_FOUND = ErrorCode.define(
        "nop.err.db-migration.file-not-found",
        "Migration file not found: {migrationPath}",
        ARG_MIGRATION_PATH);

    ErrorCode ERR_DB_MIGRATION_TABLE_EXISTS = ErrorCode.define(
        "nop.err.db-migration.table-exists",
        "Table already exists: {tableName}",
        ARG_TABLE_NAME);

    ErrorCode ERR_DB_MIGRATION_TABLE_NOT_EXISTS = ErrorCode.define(
        "nop.err.db-migration.table-not-exists",
        "Table does not exist: {tableName}",
        ARG_TABLE_NAME);

    ErrorCode ERR_DB_MIGRATION_COLUMN_EXISTS = ErrorCode.define(
        "nop.err.db-migration.column-exists",
        "Column already exists: {tableName}.{columnName}",
        ARG_TABLE_NAME, ARG_COLUMN_NAME);

    ErrorCode ERR_DB_MIGRATION_COLUMN_NOT_EXISTS = ErrorCode.define(
        "nop.err.db-migration.column-not-exists",
        "Column does not exist: {tableName}.{columnName}",
        ARG_TABLE_NAME, ARG_COLUMN_NAME);

    ErrorCode ERR_DB_MIGRATION_HISTORY_QUERY_FAILED = ErrorCode.define(
        "nop.err.db-migration.history-query-failed",
        "Failed to query migration history table {tableName} in querySpace {querySpace}",
        ARG_TABLE_NAME, ARG_QUERY_SPACE);

    ErrorCode ERR_DB_MIGRATION_INVALID_DEFAULT_VALUE = ErrorCode.define(
        "nop.err.db-migration.invalid-default-value",
        "Invalid default value [{defaultValue}] for column {columnName} of table {tableName}: "
            + "line breaks, semicolons and SQL comment markers are not allowed",
        ARG_DEFAULT_VALUE, ARG_TABLE_NAME, ARG_COLUMN_NAME);
}
