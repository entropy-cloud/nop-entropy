package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface JoinErrors extends NopMetadataArgs {

    ErrorCode ERR_JOIN_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.join-not-found",
                    "NopMetaTableJoin not found or not owned by table: {metaTableId} joinId={joinId}",
                    ARG_META_TABLE_ID, ARG_JOIN_ID);
    ErrorCode ERR_JOIN_TYPE_RIGHT_UNSUPPORTED =
            ErrorCode.define("nop.err.metadata.join-type-right-unsupported",
                    "joinType=right is explicitly unsupported in first version (same-DB and cross-DB): {joinId}",
                    ARG_JOIN_ID);
    ErrorCode ERR_JOIN_TYPE_UNKNOWN =
            ErrorCode.define("nop.err.metadata.join-type-unknown",
                    "Unknown joinType (expected inner/left/right): {joinType} joinId={joinId}",
                    ARG_JOIN_TYPE, ARG_JOIN_ID);
    ErrorCode ERR_JOIN_ENTITY_DANGLING =
            ErrorCode.define("nop.err.metadata.join-entity-dangling",
                    "Join references a dangling entity (leftEntityId/rightEntityId not found): "
                            + "{joinId} side={side} entityId={entityId}", ARG_JOIN_ID, ARG_SIDE, ARG_ENTITY_ID);
    ErrorCode ERR_JOIN_ENTITY_NOT_REGISTERED =
            ErrorCode.define("nop.err.metadata.join-entity-not-registered",
                    "Join entity not registered in runtime IOrmSessionFactory: {joinId} side={side} "
                            + "entityName={entityName}", ARG_JOIN_ID, ARG_SIDE, ARG_ENTITY_NAME);
    ErrorCode ERR_JOIN_TABLE_DANGLING =
            ErrorCode.define("nop.err.metadata.join-table-dangling",
                    "Join references a dangling table endpoint (leftTableId/rightTableId not found): "
                            + "{joinId} side={side} tableId={tableId}", ARG_JOIN_ID, ARG_SIDE, ARG_TABLE_ID);
    ErrorCode ERR_JOIN_TABLE_TYPE_NOT_ALLOWED =
            ErrorCode.define("nop.err.metadata.join-table-type-not-allowed",
                    "Join table endpoint must be external/sql tableType (entity-type table should use entityId path): "
                            + "{joinId} side={side} tableId={tableId} tableType={tableType}",
                    ARG_JOIN_ID, ARG_SIDE, ARG_TABLE_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_JOIN_FIELD_NOT_RESOLVED =
            ErrorCode.define("nop.err.metadata.join-field-not-resolved",
                    "Join field could not be resolved to a physical column: {joinId} side={side} "
                            + "entityId={entityId} field={field}", ARG_JOIN_ID, ARG_SIDE, ARG_ENTITY_ID, ARG_FIELD);
    ErrorCode ERR_JOIN_TABLE_FIELD_NOT_RESOLVED =
            ErrorCode.define("nop.err.metadata.join-table-field-not-resolved",
                    "Join field does not belong to the table endpoint's parsed column set: "
                            + "{joinId} side={side} tableId={tableId} field={field}",
                    ARG_JOIN_ID, ARG_SIDE, ARG_TABLE_ID, ARG_FIELD);
    ErrorCode ERR_JOIN_CROSS_DB_SIZE_LIMIT =
            ErrorCode.define("nop.err.metadata.join-cross-db-size-limit",
                    "Cross-DB join result set exceeds size limit ({limit}) on {side} side, abort to avoid OOM: "
                            + "{joinId} rows={rows}", ARG_JOIN_ID, ARG_SIDE, ARG_ROWS, ARG_LIMIT);
    ErrorCode ERR_JOIN_NAMESPACE_MISMATCH =
            ErrorCode.define("nop.err.metadata.join-namespace-mismatch",
                    "Cross-DB merge join field not found in fetched row key set: "
                            + "{joinId} side={side} field={field} rowKeys={rowKeys}",
                    ARG_JOIN_ID, ARG_SIDE, ARG_FIELD, ARG_ROW_KEYS);
    ErrorCode ERR_JOIN_TABLE_EXEC_FAILED =
            ErrorCode.define("nop.err.metadata.join-table-exec-failed",
                    "Join table-endpoint SQL execution failed: {joinId} side={side} -- {error}",
                    ARG_JOIN_ID, ARG_SIDE, ARG_ERROR);
    ErrorCode ERR_JOIN_NO_ENDPOINT =
            ErrorCode.define("nop.err.metadata.join-no-endpoint",
                    "Join side has neither entityId nor tableId set (require entity/table endpoint): "
                            + "{joinId} side={side}", ARG_JOIN_ID, ARG_SIDE);
    ErrorCode ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH =
            ErrorCode.define("nop.err.metadata.join-cross-db-key-type-mismatch",
                    "Cross-DB merge join key JDBC type mismatch between sides: "
                            + "{joinId} leftType={leftType} rightType={rightType}",
                    ARG_JOIN_ID, ARG_LEFT_TYPE, ARG_RIGHT_TYPE);
    ErrorCode ERR_JOIN_ENTITY_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.join-entity-not-found",
                    "Join references non-existent MetaEntity: {metaTableId} side={side} entityId={entityId}",
                    ARG_META_TABLE_ID, ARG_SIDE, ARG_ENTITY_ID);
    ErrorCode ERR_JOIN_FIELD_NOT_IN_ENTITY =
            ErrorCode.define("nop.err.metadata.join-field-not-in-entity",
                    "Join field does not belong to the referenced entity's field set: "
                            + "{metaTableId} side={side} entityId={entityId} field={field}; available={availableFields}",
                    ARG_META_TABLE_ID, ARG_SIDE, ARG_ENTITY_ID, ARG_FIELD, ARG_AVAILABLE_FIELDS);
    ErrorCode ERR_JOIN_ENTITY_ID_NULL =
            ErrorCode.define("nop.err.metadata.join-entity-id-null",
                    "Join side has neither entityId nor tableId (require entity/table endpoint): "
                            + "{metaTableId} side={side}", ARG_META_TABLE_ID, ARG_SIDE);
    ErrorCode ERR_JOIN_ENDPOINT_BOTH_SET =
            ErrorCode.define("nop.err.metadata.join-endpoint-both-set",
                    "Join side has both entityId and tableId set (require entity/table mutually exclusive): "
                            + "{metaTableId} side={side} entityId={entityId} tableId={tableId}",
                    ARG_META_TABLE_ID, ARG_SIDE, ARG_ENTITY_ID, ARG_TABLE_ID);
    ErrorCode ERR_JOIN_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.join-table-not-found",
                    "Join references non-existent MetaTable as table endpoint: "
                            + "{metaTableId} side={side} tableId={tableId}",
                    ARG_META_TABLE_ID, ARG_SIDE, ARG_TABLE_ID);
    ErrorCode ERR_JOIN_FIELD_NOT_IN_TABLE =
            ErrorCode.define("nop.err.metadata.join-field-not-in-table",
                    "Join field does not belong to the referenced table's parsed column set: "
                            + "{metaTableId} side={side} tableId={tableId} field={field}; available={availableFields}",
                    ARG_META_TABLE_ID, ARG_SIDE, ARG_TABLE_ID, ARG_FIELD, ARG_AVAILABLE_FIELDS);
    ErrorCode ERR_PAGINATION_OFFSET_TOO_LARGE =
            ErrorCode.define("nop.err.metadata.pagination-offset-too-large",
                    "Pagination offset exceeds Integer.MAX_VALUE: {offset}", ARG_OFFSET);
    ErrorCode ERR_PAGINATION_LIMIT_TOO_LARGE =
            ErrorCode.define("nop.err.metadata.pagination-limit-too-large",
                    "Pagination limit exceeds Integer.MAX_VALUE: {limit}", ARG_LIMIT);
}
