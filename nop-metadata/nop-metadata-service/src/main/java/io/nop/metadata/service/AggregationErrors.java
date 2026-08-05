package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface AggregationErrors extends NopMetadataArgs {

    ErrorCode ERR_AGGR_NO_MEASURE =
            ErrorCode.define("nop.err.metadata.aggr-no-measure",
                    "No measure selected for aggregation: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_AGGR_NO_DIMENSION =
            ErrorCode.define("nop.err.metadata.aggr-no-dimension",
                    "No dimension selected for aggregation: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_AGGR_MEASURE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.aggr-measure-not-found",
                    "Measure not found for table: {metaTableId} measureName={measureName}",
                    ARG_META_TABLE_ID, ARG_MEASURE_NAME);
    ErrorCode ERR_AGGR_DIMENSION_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.aggr-dimension-not-found",
                    "Dimension not found for table: {metaTableId} dimensionName={dimensionName}",
                    ARG_META_TABLE_ID, ARG_DIMENSION_NAME);
    ErrorCode ERR_AGGR_EXPRESSION_UNPARSEABLE =
            ErrorCode.define("nop.err.metadata.aggr-expression-unparseable",
                    "Expression measure text is unparseable (unbalanced parenthesis / illegal token / "
                            + "statement terminator / suspicious comment): "
                            + "{metaTableId} measureName={measureName} expression={expression} error={error}",
                    ARG_META_TABLE_ID, ARG_MEASURE_NAME, ARG_EXPRESSION, ARG_ERROR);
    ErrorCode ERR_AGGR_EXPRESSION_UNSAFE =
            ErrorCode.define("nop.err.metadata.aggr-expression-unsafe",
                    "Expression measure text is unsafe (contains forbidden keyword/function, or identifier "
                            + "fails whitelist, or join context requires l./r. qualifier): "
                            + "{metaTableId} measureName={measureName} expression={expression} reason={reason}",
                    ARG_META_TABLE_ID, ARG_MEASURE_NAME, ARG_EXPRESSION, ARG_REASON);
    ErrorCode ERR_AGGR_EXPRESSION_DIALECT_UNSUPPORTED =
            ErrorCode.define("nop.err.metadata.aggr-expression-dialect-unsupported",
                    "Expression measure uses function/operator not supported by current dialect: "
                            + "{metaTableId} measureName={measureName} expression={expression} "
                            + "databaseProductName={databaseProductName} unsupportedToken={unsupportedToken}",
                    ARG_META_TABLE_ID, ARG_MEASURE_NAME, ARG_EXPRESSION, ARG_DATABASE_PRODUCT_NAME, ARG_UNSUPPORTED_TOKEN);
    ErrorCode ERR_AGGR_EXPRESSION_MEMORY_NOT_COMPUTABLE =
            ErrorCode.define("nop.err.metadata.aggr-expression-memory-not-computable",
                    "Expression-type measure is not computable in cross-DB in-memory GROUP BY path: "
                            + "{metaTableId} measureName={measureName} joinId={joinId}",
                    ARG_META_TABLE_ID, ARG_MEASURE_NAME, ARG_JOIN_ID);
    ErrorCode ERR_AGGR_EXPRESSION_TOO_LONG =
            ErrorCode.define("nop.err.metadata.aggr-expression-too-long",
                    "Expression measure text exceeds VARCHAR(1000) capacity limit (not truncated, "
                            + "not silently stored): {metaTableId} measureName={measureName} length={length} limit={limit}",
                    ARG_META_TABLE_ID, ARG_MEASURE_NAME, ARG_LENGTH, ARG_LIMIT);
    ErrorCode ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED =
            ErrorCode.define("nop.err.metadata.aggr-expression-having-order-by-unsupported",
                    "Expression-type measure is referenced by HAVING or ORDER BY name: "
                            + "{metaTableId} measureName={measureName} clause={clause}",
                    ARG_META_TABLE_ID, ARG_MEASURE_NAME, ARG_CLAUSE);
    ErrorCode ERR_AGGR_AGG_FUNC_UNSUPPORTED =
            ErrorCode.define("nop.err.metadata.aggr-agg-func-unsupported",
                    "aggFunc not supported (expected sum/count/avg/min/max/count_distinct): "
                            + "{aggFunc} measureName={measureName}", ARG_AGG_FUNC, ARG_MEASURE_NAME);
    ErrorCode ERR_AGGR_FIELD_NOT_RESOLVED =
            ErrorCode.define("nop.err.metadata.aggr-field-not-resolved",
                    "Measure/Dimension field could not be resolved to a physical column: "
                            + "{metaTableId} name={name} entityFieldId={entityFieldId}",
                    ARG_META_TABLE_ID, ARG_NAME, ARG_ENTITY_FIELD_ID);
    ErrorCode ERR_AGGR_ENTITY_NOT_REGISTERED =
            ErrorCode.define("nop.err.metadata.aggr-entity-not-registered",
                    "Aggregation target entity not registered in runtime IOrmSessionFactory: "
                            + "{metaTableId} entityName={entityName}", ARG_META_TABLE_ID, ARG_ENTITY_NAME);
    ErrorCode ERR_AGGR_UNSUPPORTED_DIALECT =
            ErrorCode.define("nop.err.metadata.aggr-unsupported-dialect",
                    "Dialect not supported in first version (only H2/MySQL/PostgreSQL): "
                            + "{databaseProductName} metaTableId={metaTableId}",
                    ARG_DATABASE_PRODUCT_NAME, ARG_META_TABLE_ID);
    ErrorCode ERR_AGGR_EXEC_FAILED =
            ErrorCode.define("nop.err.metadata.aggr-exec-failed",
                    "Aggregation SQL execution failed: metaTableId={metaTableId} -- {error}",
                    ARG_META_TABLE_ID, ARG_ERROR);
    ErrorCode ERR_AGGR_TABLE_VISIBILITY_CHECK_FAILED =
            ErrorCode.define("nop.err.metadata.aggr-table-visibility-check-failed",
                    "Table visibility check failed (metadata lookup error, not a missing table): "
                            + "schema={schema} tableName={tableName} -- {error}",
                    ARG_SCHEMA, ARG_TABLE_NAME, ARG_ERROR);
    ErrorCode ERR_AGGR_UNSUPPORTED_TABLE_TYPE =
            ErrorCode.define("nop.err.metadata.aggr-unsupported-table-type",
                    "SqlAggregationProcessor requires TABLE_TYPE_SQL, got: {tableType}",
                    ARG_TABLE_TYPE);
    ErrorCode ERR_AGGR_JOIN_MIXED_ENDPOINT_DEFERRED =
            ErrorCode.define("nop.err.metadata.aggr-join-mixed-endpoint-deferred",
                    "Mixed-endpoint (entity<->external/sql) JOIN aggregation is deferred: "
                            + "{joinId} leftEndpointType={leftEndpointType} rightEndpointType={rightEndpointType}",
                    ARG_JOIN_ID, ARG_LEFT_ENDPOINT_TYPE, ARG_RIGHT_ENDPOINT_TYPE);
    ErrorCode ERR_AGGR_JOIN_MIXED_CROSS_DB_DEFERRED =
            ErrorCode.define("nop.err.metadata.aggr-join-mixed-cross-db-deferred",
                    "Mixed-endpoint JOIN aggregation: entity physical table is not visible in the selected external "
                            + "connection: {joinId} entityPhysicalTable={entityPhysicalTable} entitySchema={entitySchema} "
                            + "externalQuerySpace={externalQuerySpace}",
                    ARG_JOIN_ID, ARG_ENTITY_PHYSICAL_TABLE, ARG_ENTITY_SCHEMA, ARG_EXTERNAL_QUERY_SPACE);
    ErrorCode ERR_AGGR_JOIN_MIXED_ENTITY_TABLE_EMPTY =
            ErrorCode.define("nop.err.metadata.aggr-join-mixed-entity-table-empty",
                    "Mixed-endpoint JOIN aggregation: entity physical table name (NopMetaEntity.tableName) is empty, "
                            + "cannot build FROM clause: {joinId} entityId={entityId}",
                    ARG_JOIN_ID, ARG_ENTITY_ID);
    ErrorCode ERR_AGGR_JOIN_SIDE_REQUIRED =
            ErrorCode.define("nop.err.metadata.aggr-join-side-required",
                    "Measure/Dimension side is required for external/sql join endpoint at query-time: "
                            + "{metaTableId} name={name} joinId={joinId}",
                    ARG_META_TABLE_ID, ARG_NAME, ARG_JOIN_ID);
    ErrorCode ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE =
            ErrorCode.define("nop.err.metadata.aggr-join-field-not-on-side",
                    "Measure/Dimension column does not exist on the endpoint resolved field set for the given side: "
                            + "{metaTableId} name={name} side={side} endpointTableType={endpointTableType} column={column} "
                            + "joinId={joinId}", ARG_META_TABLE_ID, ARG_NAME, ARG_SIDE, ARG_ENDPOINT_TABLE_TYPE, ARG_COLUMN, ARG_JOIN_ID);
    ErrorCode ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH =
            ErrorCode.define("nop.err.metadata.aggr-join-entity-side-mismatch",
                    "Measure/Dimension side is inconsistent with entityFieldId->metaEntityId attribution on entity endpoint: "
                            + "{metaTableId} name={name} declaredSide={declaredSide} resolvedSide={resolvedSide} "
                            + "fieldMetaEntityId={fieldMetaEntityId} joinId={joinId}",
                    ARG_META_TABLE_ID, ARG_NAME, ARG_DECLARED_SIDE, ARG_RESOLVED_SIDE, ARG_FIELD_META_ENTITY_ID, ARG_JOIN_ID);
    ErrorCode ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE =
            ErrorCode.define("nop.err.metadata.aggr-join-external-cross-query-space",
                    "Cross-querySpace (cross-DB) external<->external JOIN aggregation is deferred: "
                            + "{joinId} leftQuerySpace={leftQuerySpace} rightQuerySpace={rightQuerySpace}",
                    ARG_JOIN_ID, ARG_LEFT_QUERY_SPACE, ARG_RIGHT_QUERY_SPACE);
    ErrorCode ERR_AGGR_JOIN_SELF_JOIN =
            ErrorCode.define("nop.err.metadata.aggr-join-self-join",
                    "Self-join (leftEntityId == rightEntityId) is not supported for JOIN aggregation: "
                            + "{joinId} entityId={entityId}", ARG_JOIN_ID, ARG_ENTITY_ID);
    ErrorCode ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED =
            ErrorCode.define("nop.err.metadata.aggr-join-field-side-unresolved",
                    "Measure/Dimension entityFieldId does not belong to either left or right entity of the join: "
                            + "{metaTableId} name={name} entityFieldId={entityFieldId} "
                            + "fieldMetaEntityId={fieldMetaEntityId} leftEntityId={leftEntityId} "
                            + "rightEntityId={rightEntityId} joinId={joinId}",
                    ARG_META_TABLE_ID, ARG_NAME, ARG_ENTITY_FIELD_ID, ARG_FIELD_META_ENTITY_ID,
                    ARG_LEFT_ENTITY_ID, ARG_RIGHT_ENTITY_ID, ARG_JOIN_ID);
    ErrorCode ERR_AGGR_JOIN_CROSS_QUERY_SPACE =
            ErrorCode.define("nop.err.metadata.aggr-join-cross-query-space",
                    "Cross-querySpace (cross-DB) entity-entity JOIN aggregation is deferred: "
                            + "{joinId} leftQuerySpace={leftQuerySpace} rightQuerySpace={rightQuerySpace}",
                    ARG_JOIN_ID, ARG_LEFT_QUERY_SPACE, ARG_RIGHT_QUERY_SPACE);
    ErrorCode ERR_AGGR_JOIN_COMPILE_FAILED =
            ErrorCode.define("nop.err.metadata.aggr-join-compile-failed",
                    "Entity JOIN aggregation SQL failed to compile via EQL: {joinId} -- {error}",
                    ARG_JOIN_ID, ARG_ERROR);
    ErrorCode ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING =
            ErrorCode.define("nop.err.metadata.aggr-cross-db-field-key-missing",
                    "Cross-DB JOIN aggregation: measure/dimension lookup key not found in executeJoin merged row: "
                            + "{metaTableId} name={name} fieldKind={fieldKind} rawKey={rawKey} lookupKey={lookupKey} "
                            + "rowKeys={rowKeys} joinId={joinId}",
                    ARG_META_TABLE_ID, ARG_NAME, ARG_FIELD_KIND, ARG_RAW_KEY, ARG_LOOKUP_KEY, ARG_ROW_KEYS, ARG_JOIN_ID);
    ErrorCode ERR_AGGR_HAVING_UNKNOWN_NAME =
            ErrorCode.define("nop.err.metadata.aggr-having-unknown-name",
                    "having references a measure/dimension name not in the user-selected measures/dimensions set: "
                            + "{metaTableId} name={name} selectedMeasures={selectedMeasures} selectedDimensions={selectedDimensions}",
                    ARG_META_TABLE_ID, ARG_NAME, ARG_SELECTED_MEASURES, ARG_SELECTED_DIMENSIONS);
    ErrorCode ERR_AGGR_ORDER_BY_UNKNOWN_NAME =
            ErrorCode.define("nop.err.metadata.aggr-order-by-unknown-name",
                    "orderBy references a measure/dimension name not in the user-selected measures/dimensions set: "
                            + "{metaTableId} name={name} selectedMeasures={selectedMeasures} selectedDimensions={selectedDimensions}",
                    ARG_META_TABLE_ID, ARG_NAME, ARG_SELECTED_MEASURES, ARG_SELECTED_DIMENSIONS);
    ErrorCode ERR_AGGR_HAVING_UNSUPPORTED_OP =
            ErrorCode.define("nop.err.metadata.aggr-having-unsupported-op",
                    "MemoryFilterEvaluator: having op not supported in first version: {op} name={name}",
                    ARG_OP, ARG_NAME);
    ErrorCode ERR_AGGR_HAVING_EXPR_UNPARSEABLE =
            ErrorCode.define("nop.err.metadata.aggr-having-expr-unparseable",
                    "Multi-column arithmetic HAVING expression is unparseable: {metaTableId} expr={expr} error={error}",
                    ARG_META_TABLE_ID, ARG_EXPR, ARG_ERROR);
    ErrorCode ERR_AGGR_HAVING_EXPR_UNSAFE =
            ErrorCode.define("nop.err.metadata.aggr-having-expr-unsafe",
                    "Multi-column arithmetic HAVING expression is unsafe: "
                            + "{metaTableId} expr={expr} reason={reason}",
                    ARG_META_TABLE_ID, ARG_EXPR, ARG_REASON);
    ErrorCode ERR_AGGR_HAVING_EXPR_MEMORY_NOT_COMPUTABLE =
            ErrorCode.define("nop.err.metadata.aggr-having-expr-memory-not-computable",
                    "Multi-column arithmetic HAVING expression is not computable in cross-DB in-memory GROUP BY path: "
                            + "{metaTableId} expr={expr}",
                    ARG_META_TABLE_ID, ARG_EXPR);
    ErrorCode ERR_GRANULARITY_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.granularity-not-supported",
                    "granularity value not in supported set [year,quarter,month,week,day,hour]: "
                            + "{granularity} dimensionName={dimensionName}",
                    ARG_GRANULARITY, ARG_DIMENSION_NAME);
}
