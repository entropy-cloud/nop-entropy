package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface SqlErrors extends NopMetadataArgs {

    ErrorCode ERR_SQL_VIEW_SQL_EMPTY =
            ErrorCode.define("nop.err.metadata.sql-view-sql-empty",
                    "Source sql is empty", ARG_SQL);
    ErrorCode ERR_SQL_VIEW_PARSE_FAILED =
            ErrorCode.define("nop.err.metadata.sql-view-parse-failed",
                    "Failed to parse source sql", ARG_SQL);
    ErrorCode ERR_SQL_VIEW_MULTI_STATEMENT =
            ErrorCode.define("nop.err.metadata.sql-view-multi-statement",
                    "Sql view source must be a single SELECT statement, but got {count} statements", ARG_COUNT, ARG_SQL);
    ErrorCode ERR_SQL_VIEW_NOT_SELECT =
            ErrorCode.define("nop.err.metadata.sql-view-not-select",
                    "Sql view source must be a SELECT statement, but got {statementKind}", ARG_STATEMENT_KIND, ARG_SQL);
    ErrorCode ERR_SQL_VIEW_WILDCARD_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.sql-view-wildcard-not-supported",
                    "Wildcard projection (* or t.*) is not supported in sql view source; "
                            + "please expand to explicit columns (pure AST parse cannot resolve wildcard)", ARG_SQL);
    ErrorCode ERR_SQL_TYPE_INFERENCE_DIALECT_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.sql-type-inference-dialect-not-supported",
                    "Dialect not supported for sql view type inference (only H2/MySQL/PostgreSQL): "
                            + "{databaseProductName} querySpace={querySpace}",
                    ARG_DATABASE_PRODUCT_NAME, ARG_QUERY_SPACE);
    ErrorCode ERR_SQL_TYPE_INFERENCE_COLUMN_MISMATCH =
            ErrorCode.define("nop.err.metadata.sql-type-inference-column-mismatch",
                    "Sql view column count mismatch: extractor={extractedCount} resultSet={resultSetCount}",
                    ARG_EXTRACTED_COUNT, ARG_RESULT_SET_COUNT, ARG_QUERY_SPACE);
    ErrorCode ERR_SQL_TYPE_INFERENCE_FAILED =
            ErrorCode.define("nop.err.metadata.sql-type-inference-failed",
                    "Sql view type inference failed (LIMIT 0 execution or ResultSetMetaData read failed): "
                            + "{error} querySpace={querySpace}", ARG_ERROR, ARG_QUERY_SPACE);
    ErrorCode ERR_SQL_VIEW_MODULE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.sql-view-module-not-found",
                    "MetaModule not found for createSqlTable: {metaModuleId}", ARG_META_MODULE_ID);
    ErrorCode ERR_SQL_VIEW_TABLE_EXISTS =
            ErrorCode.define("nop.err.metadata.sql-view-table-exists",
                    "Sql view table already exists in module: {metaModuleId} tableName={tableName}",
                    ARG_META_MODULE_ID, ARG_TABLE_NAME);
    ErrorCode ERR_SQL_VIEW_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.sql-view-table-not-found",
                    "NopMetaTable not found for resolveTableFields: {metaTableId}", ARG_META_TABLE_ID);
}
