/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
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
    
    ErrorCode ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE = ErrorCode.define(
        "io.nop.db.migration", 
        "ERR_DB_MIGRATION_UNKNOWN_CHANGE_TYPE",
        "Unknown change type: {changeType}"
    );
    
    ErrorCode ERR_DB_MIGRATION_PRECONDITION_FAILED = ErrorCode.define(
        "io.nop.db.migration",
        "ERR_DB_MIGRATION_PRECONDITION_FAILED",
        "Precondition failed for migration"
    );
    
    ErrorCode ERR_DB_MIGRATION_EXECUTION_FAILED = ErrorCode.define(
        "io.nop.db.migration",
        "ERR_DB_MIGRATION_EXECUTION_FAILED",
        "Migration execution failed for version: {version}"
    );
    
    ErrorCode ERR_DB_MIGRATION_CHECKSUM_MISMATCH = ErrorCode.define(
        "io.nop.db.migration",
        "ERR_DB_MIGRATION_CHECKSUM_MISMATCH",
        "Checksum mismatch for migration: expected={expectedChecksum}, actual={checksum}"
    );
    
    ErrorCode ERR_DB_MIGRATION_NO_SQL_FOR_DIALECT = ErrorCode.define(
        "io.nop.db.migration",
        "ERR_DB_MIGRATION_NO_SQL_FOR_DIALECT",
        "No SQL defined for dialect in change type: {changeType}"
    );
    
    ErrorCode ERR_DB_MIGRATION_FILE_NOT_FOUND = ErrorCode.define(
        "io.nop.db.migration",
        "ERR_DB_MIGRATION_FILE_NOT_FOUND",
        "Migration file not found: {migrationPath}"
    );
    
    ErrorCode ERR_DB_MIGRATION_TABLE_EXISTS = ErrorCode.define(
        "io.nop.db.migration",
        "ERR_DB_MIGRATION_TABLE_EXISTS",
        "Table already exists: {tableName}"
    );
    
    ErrorCode ERR_DB_MIGRATION_TABLE_NOT_EXISTS = ErrorCode.define(
        "io.nop.db.migration",
        "ERR_DB_MIGRATION_TABLE_NOT_EXISTS",
        "Table does not exist: {tableName}"
    );
    
    ErrorCode ERR_DB_MIGRATION_COLUMN_EXISTS = ErrorCode.define(
        "io.nop.db.migration",
        "ERR_DB_MIGRATION_COLUMN_EXISTS",
        "Column already exists: {tableName}.{columnName}"
    );
    
    ErrorCode ERR_DB_MIGRATION_COLUMN_NOT_EXISTS = ErrorCode.define(
        "io.nop.db.migration",
        "ERR_DB_MIGRATION_COLUMN_NOT_EXISTS",
        "Column does not exist: {tableName}.{columnName}"
    );
}
