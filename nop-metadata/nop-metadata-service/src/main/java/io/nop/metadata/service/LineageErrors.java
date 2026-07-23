/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface LineageErrors extends NopMetadataArgs {

    ErrorCode ERR_COL_LINEAGE_SQL_EMPTY =
            ErrorCode.define("nop.err.metadata.col-lineage-sql-empty",
                    "Source sql is empty", ARG_SQL);
    ErrorCode ERR_COL_LINEAGE_SQL_PARSE_FAILED =
            ErrorCode.define("nop.err.metadata.col-lineage-sql-parse-failed",
                    "Failed to parse source sql for column lineage", ARG_SQL);
    ErrorCode ERR_COL_LINEAGE_MULTI_STATEMENT =
            ErrorCode.define("nop.err.metadata.col-lineage-multi-statement",
                    "Sql source must be a single SELECT statement, but got {count} statements", ARG_COUNT, ARG_SQL);
    ErrorCode ERR_COL_LINEAGE_NOT_SELECT =
            ErrorCode.define("nop.err.metadata.col-lineage-not-select",
                    "Sql source must be a SELECT statement, but got {statementKind}", ARG_STATEMENT_KIND, ARG_SQL);
    ErrorCode ERR_LINEAGE_SQL_EMPTY =
            ErrorCode.define("nop.err.metadata.lineage-sql-empty",
                    "Source sql is empty", ARG_SQL);
    ErrorCode ERR_LINEAGE_SQL_PARSE_FAILED =
            ErrorCode.define("nop.err.metadata.lineage-sql-parse-failed",
                    "Failed to parse source sql", ARG_SQL);
    ErrorCode ERR_LINEAGE_NO_EDGES =
            ErrorCode.define("nop.err.metadata.lineage-no-edges",
                    "No lineage edges provided to record", ARG_SIZE);
    ErrorCode ERR_LINEAGE_TABLE_ID_MISSING =
            ErrorCode.define("nop.err.metadata.lineage-table-id-missing",
                    "Lineage edge is missing required table id (sourceTableId or targetTableId)", ARG_INDEX, ARG_EDGE);
    ErrorCode ERR_LINEAGE_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.lineage-table-not-found",
                    "Referenced table does not exist in catalog: {tableId}", ARG_TABLE_ID);
    ErrorCode ERR_LINEAGE_SQL_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.lineage-sql-table-not-found",
                    "Lineage sql table not found: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_LINEAGE_NOT_SQL_VIEW_TABLE =
            ErrorCode.define("nop.err.metadata.lineage-not-sql-view-table",
                    "Table is not a sql-view table, cannot extract lineage: {metaTableId} (tableType={tableType})",
                    ARG_META_TABLE_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_LINEAGE_SQL_SOURCE_EMPTY =
            ErrorCode.define("nop.err.metadata.lineage-sql-source-empty",
                    "Sql table sourceSql is empty, cannot extract column lineage: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_LINEAGE_GRAPH_TOO_LARGE =
            ErrorCode.define("nop.err.metadata.lineage-graph-too-large",
                    "Lineage graph edge count exceeds size limit (abort to avoid OOM): "
                            + "edges={edges} limit={limit}. Increase nop.metadata.lineage.max-edges if legitimate.",
                    ARG_EDGE, ARG_LIMIT);
    ErrorCode ERR_LINEAGE_TABLE_INDEX_TOO_LARGE =
            ErrorCode.define("nop.err.metadata.lineage-table-index-too-large",
                    "Lineage table-name index size exceeds limit (abort to avoid OOM): "
                            + "tables={tables} limit={limit}. Increase nop.metadata.lineage.max-tables if legitimate.",
                    ARG_TABLES, ARG_LIMIT);
}
