/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * nop-metadata 模块统一 ErrorCode 常量集（Phase 2 — 集中化迁移）。
 *
 * <p>本接口集中声明跨多个文件使用的 ErrorCode 常量，避免：
 * <ul>
 *   <li>同一错误码字符串在多文件独立 define（消除重复定义）</li>
 *   <li>inline {@code throw new NopException(ErrorCode.define(...))} 用法</li>
 *   <li>ErrorCode 散落在 40+ 文件顶部</li>
 * </ul>
 *
 * <p>命名规范：{@code nop.err.metadata.<子域>.<错误>}。子域分组（按字母序）：
 * aggr / catalog / checkpoint / col-lineage / contract / datasource / dialect /
 * dimension / dto / event / field / filter-definition / granularity / join /
 * lineage / link-asset / manifest / measure / module / orm-resource / pagination /
 * profiling / profiling-rule / quality / quality-rule / query / query-filter /
 * recon / score / search / sql / sql-module / sql-type-inference / table /
 * tableref / tag-label.
 *
 * <p>维护说明：新增 ErrorCode 时若使用了新的子域前缀，请同步更新上方列表。
 */
public interface NopMetadataErrors {

    // ===== ARG 参数名常量 =====

    String ARG_META_TABLE_ID = "metaTableId";
    String ARG_DATA_SOURCE_ID = "dataSourceId";
    String ARG_DATASOURCE_TYPE = "datasourceType";
    String ARG_JOIN_ID = "joinId";
    String ARG_CONFIG_ID = "configId";
    String ARG_CHECKPOINT_ID = "checkpointId";
    String ARG_QUALITY_RULE_ID = "qualityRuleId";
    String ARG_QUALITY_RESULT_ID = "qualityResultId";
    String ARG_ENTITY_NAME = "entityName";
    String ARG_ENTITY_ID = "entityId";
    String ARG_BASE_ENTITY_ID = "baseEntityId";
    String ARG_META_MODULE_ID = "metaModuleId";
    String ARG_QUERY_SPACE = "querySpace";
    String ARG_TABLE_TYPE = "tableType";
    String ARG_TABLE_NAME = "tableName";
    String ARG_COLUMN_NAME = "columnName";
    String ARG_DATABASE_PRODUCT_NAME = "databaseProductName";
    String ARG_ERROR = "error";
    String ARG_PATH = "path";
    String ARG_CONTRACT_ID = "contractId";
    String ARG_CRON = "cron";
    String ARG_STATUS = "status";
    String ARG_SQL = "sql";
    String ARG_IDENTIFIER = "identifier";
    String ARG_OP = "op";
    String ARG_NAME = "name";
    String ARG_SIDE = "side";
    String ARG_TABLE_ID = "tableId";
    String ARG_FIELD = "field";
    String ARG_MEASURE_NAME = "measureName";
    String ARG_DIMENSION_NAME = "dimensionName";
    String ARG_EXPRESSION = "expression";
    String ARG_REASON = "reason";
    String ARG_AGG_FUNC = "aggFunc";
    String ARG_ENTITY_FIELD_ID = "entityFieldId";
    String ARG_JOIN_TYPE = "joinType";
    String ARG_ROWS = "rows";
    String ARG_OFFSET = "offset";
    String ARG_LIMIT = "limit";
    String ARG_URL = "url";
    String ARG_METHOD = "method";
    String ARG_RULE_KEY = "ruleKey";
    String ARG_JDBC_URL = "jdbcUrl";
    String ARG_RAW_JDBC_URL = "rawJdbcUrl";
    String ARG_DRIVER_CLASS_NAME = "driverClassName";
    String ARG_DATA_SOURCE_COUNT = "dataSourceCount";
    String ARG_INDEX = "index";
    String ARG_EDGE = "edge";
    String ARG_SIZE = "size";
    String ARG_COUNT = "count";
    String ARG_STATEMENT_KIND = "statementKind";
    String ARG_EXTRACTED_COUNT = "extractedCount";
    String ARG_RESULT_SET_COUNT = "resultSetCount";
    String ARG_REF_KIND = "refKind";
    String ARG_AVAILABLE_FIELDS = "availableFields";
    String ARG_ALLOWED_ENTITY_IDS = "allowedEntityIds";
    String ARG_FILTER_NAME = "filterName";
    String ARG_EXISTING_FILTER_ID = "existingFilterId";
    String ARG_EXPECTED_STATUS = "expectedStatus";
    String ARG_CURRENT_STATUS = "currentStatus";
    String ARG_PROFILING_RULE_ID = "profilingRuleId";
    String ARG_FILTER_ID = "filterId";
    String ARG_YEAR_VALUE = "yearValue";
    String ARG_DETAILS_SIZE = "detailsSize";
    String ARG_RESULT_ID = "resultId";
    String ARG_ROW_INDEX = "rowIndex";
    String ARG_LEFT_TYPE = "leftType";
    String ARG_RIGHT_TYPE = "rightType";
    String ARG_LEFT_ENDPOINT_TYPE = "leftEndpointType";
    String ARG_RIGHT_ENDPOINT_TYPE = "rightEndpointType";
    String ARG_ENTITY_PHYSICAL_TABLE = "entityPhysicalTable";
    String ARG_ENTITY_SCHEMA = "entitySchema";
    String ARG_EXTERNAL_QUERY_SPACE = "externalQuerySpace";
    String ARG_DECLARED_SIDE = "declaredSide";
    String ARG_RESOLVED_SIDE = "resolvedSide";
    String ARG_FIELD_META_ENTITY_ID = "fieldMetaEntityId";
    String ARG_LEFT_QUERY_SPACE = "leftQuerySpace";
    String ARG_RIGHT_QUERY_SPACE = "rightQuerySpace";
    String ARG_LEFT_ENTITY_ID = "leftEntityId";
    String ARG_RIGHT_ENTITY_ID = "rightEntityId";
    String ARG_FIELD_KIND = "fieldKind";
    String ARG_RAW_KEY = "rawKey";
    String ARG_LOOKUP_KEY = "lookupKey";
    String ARG_ROW_KEYS = "rowKeys";
    String ARG_SELECTED_MEASURES = "selectedMeasures";
    String ARG_SELECTED_DIMENSIONS = "selectedDimensions";
    String ARG_ENDPOINT_TABLE_TYPE = "endpointTableType";
    String ARG_TABLES = "tables";
    String ARG_UNSUPPORTED_TOKEN = "unsupportedToken";
    String ARG_ENTITY_TYPE = "entityType";
    String ARG_TAG_LABEL_ID = "tagLabelId";
    String ARG_LABEL_TYPE = "labelType";
    String ARG_DATA_PRODUCT_ID = "dataProductId";

    // ===== Aggregation (aggr) =====

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
                    ARG_META_TABLE_ID, ARG_MEASURE_NAME, "length", ARG_LIMIT);
    ErrorCode ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED =
            ErrorCode.define("nop.err.metadata.aggr-expression-having-order-by-unsupported",
                    "Expression-type measure is referenced by HAVING or ORDER BY name: "
                            + "{metaTableId} measureName={measureName} clause={clause}",
                    ARG_META_TABLE_ID, ARG_MEASURE_NAME, "clause");
    ErrorCode ERR_AGGR_AGG_FUNC_UNSUPPORTED =
            ErrorCode.define("nop.err.metadata.aggr-agg-func-unsupported",
                    "aggFunc not supported (expected sum/count/avg/min/max/countDistinct): "
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
                            + "joinId={joinId}", ARG_META_TABLE_ID, ARG_NAME, ARG_SIDE, ARG_ENDPOINT_TABLE_TYPE, "column", ARG_JOIN_ID);
    ErrorCode ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH =
            ErrorCode.define("nop.err.metadata.aggr-join-entity-side-mismatch",
                    "Measure/Dimension side is inconsistent with entityFieldId->metaEntityId attribution on entity endpoint: "
                            + "{metaTableId} name={name} declaredSide={declaredSide} resolvedSide={resolvedSide} "
                            + "fieldMetaEntityId={fieldMetaEntityId} joinId={joinId}",
                    ARG_META_TABLE_ID, ARG_NAME, ARG_DECLARED_SIDE, ARG_RESOLVED_SIDE, ARG_FIELD_META_ENTITY_ID, ARG_JOIN_ID);
    ErrorCode ERR_AGGR_JOIN_EXTERNAL_CROSS_QUERY_SPACE =
            ErrorCode.define("nop.err.metadata.aggr-join-external-cross-query-space-deferred",
                    "Cross-querySpace (cross-DB) external<->external JOIN aggregation is deferred: "
                            + "{joinId} leftQuerySpace={leftQuerySpace} rightQuerySpace={rightQuerySpace}",
                    ARG_JOIN_ID, ARG_LEFT_QUERY_SPACE, ARG_RIGHT_QUERY_SPACE);
    ErrorCode ERR_AGGR_JOIN_SELF_JOIN =
            ErrorCode.define("nop.err.metadata.aggr-join-self-join-unsupported",
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
            ErrorCode.define("nop.err.metadata.aggr-join-cross-query-space-deferred",
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
                    ARG_META_TABLE_ID, "expr", ARG_ERROR);
    ErrorCode ERR_AGGR_HAVING_EXPR_UNSAFE =
            ErrorCode.define("nop.err.metadata.aggr-having-expr-unsafe",
                    "Multi-column arithmetic HAVING expression is unsafe: "
                            + "{metaTableId} expr={expr} reason={reason}",
                    ARG_META_TABLE_ID, "expr", ARG_REASON);
    ErrorCode ERR_AGGR_HAVING_EXPR_MEMORY_NOT_COMPUTABLE =
            ErrorCode.define("nop.err.metadata.aggr-having-expr-memory-not-computable",
                    "Multi-column arithmetic HAVING expression is not computable in cross-DB in-memory GROUP BY path: "
                            + "{metaTableId} expr={expr}",
                    ARG_META_TABLE_ID, "expr");

    // ===== Join (join) =====

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
    ErrorCode ERR_PAGINATION_OFFSET_TOO_LARGE =
            ErrorCode.define("nop.err.metadata.pagination-offset-too-large",
                    "Pagination offset exceeds Integer.MAX_VALUE: {offset}", ARG_OFFSET);
    ErrorCode ERR_PAGINATION_LIMIT_TOO_LARGE =
            ErrorCode.define("nop.err.metadata.pagination-limit-too-large",
                    "Pagination limit exceeds Integer.MAX_VALUE: {limit}", ARG_LIMIT);

    // ===== Query filter (query-filter) =====

    ErrorCode ERR_FILTER_INVALID_IDENTIFIER =
            ErrorCode.define("nop.err.metadata.query-filter-invalid-identifier",
                    "Filter field name does not match identifier whitelist ^[A-Za-z_][A-Za-z0-9_]*$: {identifier}",
                    ARG_IDENTIFIER);
    ErrorCode ERR_FILTER_UNSUPPORTED_OP =
            ErrorCode.define("nop.err.metadata.query-filter-unsupported-op",
                    "Filter op not supported in first version: {op}", ARG_OP);
    ErrorCode ERR_FILTER_MISSING_FIELD =
            ErrorCode.define("nop.err.metadata.query-filter-missing-field",
                    "Filter leaf condition missing 'name' attr (field name): {op}", ARG_OP);
    ErrorCode ERR_FILTER_MISSING_VALUE =
            ErrorCode.define("nop.err.metadata.query-filter-missing-value",
                    "Filter leaf condition missing 'value' attr: {op} name={name}", ARG_OP, ARG_NAME);
    ErrorCode ERR_FILTER_IN_VALUE_NOT_COLLECTION =
            ErrorCode.define("nop.err.metadata.query-filter-in-not-collection",
                    "Filter 'in'/'notIn' value must be a collection: name={name}", ARG_NAME);
    ErrorCode ERR_FILTER_BETWEEN_MISSING_BOUNDS =
            ErrorCode.define("nop.err.metadata.query-filter-between-missing-bounds",
                    "Filter 'between' requires min and/or max attrs: name={name}", ARG_NAME);
    ErrorCode ERR_FILTER_FIELD_RESOLVER_MISS =
            ErrorCode.define("nop.err.metadata.query-filter-field-resolver-miss",
                    "Filter field resolver returned no SQL expression for name (likely unknown measure/dimension "
                            + "in having/orderBy): {op} name={name}", ARG_OP, ARG_NAME);

    // ===== DataSource (datasource) =====

    ErrorCode ERR_DATASOURCE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.datasource-not-found",
                    "MetaDataSource not found: {dataSourceId}", ARG_DATA_SOURCE_ID);
    ErrorCode ERR_DATASOURCE_DISABLED =
            ErrorCode.define("nop.err.metadata.datasource-disabled",
                    "DataSource is disabled, cannot test connection: {dataSourceId}", ARG_DATA_SOURCE_ID);
    ErrorCode ERR_DATASOURCE_TYPE_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.datasource-type-not-supported",
                    "DataSource type not supported yet: {datasourceType}", ARG_DATASOURCE_TYPE);
    ErrorCode ERR_DATASOURCE_CONFIG_INVALID =
            ErrorCode.define("nop.err.metadata.datasource-config-invalid",
                    "Invalid connection config for datasourceType={datasourceType}: {reason}",
                    ARG_DATASOURCE_TYPE, ARG_REASON);
    ErrorCode ERR_DATASOURCE_CONNECT_FAILED =
            ErrorCode.define("nop.err.metadata.datasource-connect-failed",
                    "DataSource connection failed for datasourceType={datasourceType}: {error}",
                    ARG_DATASOURCE_TYPE, ARG_ERROR);
    ErrorCode ERR_DATASOURCE_JDBC_URL_BLOCKED =
            ErrorCode.define("nop.err.metadata.datasource-jdbc-url-blocked",
                    "JDBC URL is blocked by security policy (protocol/host not allowed or dangerous parameter present): "
                            + "{jdbcUrl} reason={reason}", ARG_JDBC_URL, ARG_REASON);
    ErrorCode ERR_DATASOURCE_DRIVER_NOT_ALLOWED =
            ErrorCode.define("nop.err.metadata.datasource-driver-not-allowed",
                    "JDBC driver class is not in the allowed whitelist: {driverClassName}", ARG_DRIVER_CLASS_NAME);
    ErrorCode ERR_DATASOURCE_RESOLVE_NO_DATASOURCE =
            ErrorCode.define("nop.err.metadata.datasource-resolve-not-found",
                    "No registered MetaDataSource for querySpace: {querySpace}", ARG_QUERY_SPACE);
    ErrorCode ERR_DATASOURCE_RESOLVE_DISABLED =
            ErrorCode.define("nop.err.metadata.datasource-resolve-disabled",
                    "MetaDataSource is DISABLED, cannot be used for query execution: {dataSourceId} querySpace={querySpace}",
                    ARG_DATA_SOURCE_ID, ARG_QUERY_SPACE);
    ErrorCode ERR_DATASOURCE_DUPLICATE_QUERY_SPACE =
            ErrorCode.define("nop.err.metadata.datasource-duplicate-query-space",
                    "Multiple MetaDataSource rows match the same querySpace (UK violation in live data): "
                            + "querySpace={querySpace} dataSourceCount={dataSourceCount}",
                    ARG_QUERY_SPACE, ARG_DATA_SOURCE_COUNT);

    // ===== TableReference (tableref) =====

    ErrorCode ERR_TABLEREF_ENTITY_QUERY_SPACE_NOT_JDBC =
            ErrorCode.define("nop.err.metadata.tableref-entity-query-space-not-jdbc",
                    "Platform transaction for entity querySpace is not a JDBC transaction: {querySpace}",
                    ARG_QUERY_SPACE);
    ErrorCode ERR_TABLEREF_UNKNOWN_TABLE_TYPE =
            ErrorCode.define("nop.err.metadata.tableref-unknown-table-type",
                    "Unknown tableType for table-reference resolution: {metaTableId} tableType={tableType}",
                    ARG_META_TABLE_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_TABLEREF_ENTITY_BASE_NULL =
            ErrorCode.define("nop.err.metadata.tableref-entity-base-null",
                    "Cannot resolve entity table: baseEntityId is null (dangling reference): {metaTableId}",
                    ARG_META_TABLE_ID);
    ErrorCode ERR_TABLEREF_ENTITY_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.tableref-entity-not-found",
                    "Cannot resolve entity table: NopMetaEntity not found for baseEntityId: "
                            + "{metaTableId} baseEntityId={baseEntityId}", ARG_META_TABLE_ID, ARG_BASE_ENTITY_ID);
    ErrorCode ERR_TABLEREF_ENTITY_NOT_REGISTERED =
            ErrorCode.define("nop.err.metadata.tableref-entity-not-registered",
                    "Entity is not registered in runtime IOrmSessionFactory: "
                            + "{metaTableId} entityName={entityName}", ARG_META_TABLE_ID, ARG_ENTITY_NAME);
    ErrorCode ERR_TABLEREF_ENTITY_TABLE_NAME_EMPTY =
            ErrorCode.define("nop.err.metadata.tableref-entity-table-name-empty",
                    "Cannot resolve entity table: NopMetaEntity.tableName is empty: "
                            + "{metaTableId} entityName={entityName}", ARG_META_TABLE_ID, ARG_ENTITY_NAME);
    ErrorCode ERR_TABLEREF_SQL_SOURCE_EMPTY =
            ErrorCode.define("nop.err.metadata.tableref-sql-source-empty",
                    "Cannot resolve sql table: sourceSql is empty: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_TABLEREF_PLATFORM_META_FAILED =
            ErrorCode.define("nop.err.metadata.tableref-platform-meta-failed",
                    "Failed to get DatabaseMetaData from platform connection: {error}", ARG_ERROR);
    ErrorCode ERR_TABLEREF_EXEC_FAILED =
            ErrorCode.define("nop.err.metadata.tableref-exec-failed",
                    "Table-reference execution failed: {metaTableId} -- {error}",
                    ARG_META_TABLE_ID, ARG_ERROR);

    // ===== Profiling =====

    ErrorCode ERR_PROFILING_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.profiling-table-not-found",
                    "Profiling target table not found: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_PROFILING_TABLE_NOT_EXTERNAL =
            ErrorCode.define("nop.err.metadata.profiling-table-not-external",
                    "Profiling target table is not external (first version supports external-only execution): "
                            + "{metaTableId} tableType={tableType}", ARG_META_TABLE_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_PROFILING_NO_DATASOURCE =
            ErrorCode.define("nop.err.metadata.profiling-no-datasource",
                    "No registered MetaDataSource for querySpace of target table: "
                            + "{metaTableId} querySpace={querySpace}", ARG_META_TABLE_ID, ARG_QUERY_SPACE);
    ErrorCode ERR_PROFILING_DATASOURCE_DISABLED =
            ErrorCode.define("nop.err.metadata.profiling-datasource-disabled",
                    "MetaDataSource is disabled, cannot profile table: {dataSourceId}", ARG_DATA_SOURCE_ID);
    ErrorCode ERR_PROFILING_TABLE_FAILED =
            ErrorCode.define("nop.err.metadata.profiling-table-failed",
                    "Profile table failed: {metaTableId} -- {error}", ARG_META_TABLE_ID, ARG_ERROR);
    ErrorCode ERR_PROFILING_INVALID_IDENTIFIER =
            ErrorCode.define("nop.err.metadata.profiling-invalid-identifier",
                    "Identifier (column/table/schema) does not match whitelist ^[A-Za-z_][A-Za-z0-9_]*$: {identifier}",
                    ARG_IDENTIFIER);
    ErrorCode ERR_PROFILING_AGGREGATE_NO_ROW =
            ErrorCode.define("nop.err.metadata.profiling-aggregate-no-row",
                    "Profile aggregate SQL returned no row (logical impossibility): {sql}", ARG_SQL);
    ErrorCode ERR_PROFILING_SQL_FAILED =
            ErrorCode.define("nop.err.metadata.profiling-sql-failed",
                    "Profile table SQL execution failed: {tableName} -- {error}", ARG_TABLE_NAME, ARG_ERROR);

    // ===== SQL views (sql) =====

    ErrorCode ERR_SQL_VIEW_SQL_EMPTY =
            ErrorCode.define("nop.err.metadata.sql-empty",
                    "Source sql is empty", ARG_SQL);
    ErrorCode ERR_SQL_VIEW_PARSE_FAILED =
            ErrorCode.define("nop.err.metadata.sql-parse-failed",
                    "Failed to parse source sql", ARG_SQL);
    ErrorCode ERR_SQL_VIEW_MULTI_STATEMENT =
            ErrorCode.define("nop.err.metadata.sql-multi-statement",
                    "Sql view source must be a single SELECT statement, but got {count} statements", ARG_COUNT, ARG_SQL);
    ErrorCode ERR_SQL_VIEW_NOT_SELECT =
            ErrorCode.define("nop.err.metadata.sql-not-select",
                    "Sql view source must be a SELECT statement, but got {statementKind}", ARG_STATEMENT_KIND, ARG_SQL);
    ErrorCode ERR_SQL_VIEW_WILDCARD_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.sql-wildcard-not-supported",
                    "Wildcard projection (* or t.*) is not supported in sql view source; "
                            + "please expand to explicit columns (pure AST parse cannot resolve wildcard)", ARG_SQL);

    // ===== SQL type inference (sql-type-inference) =====

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

    // ===== Quality (quality) =====

    ErrorCode ERR_QUALITY_INVALID_IDENTIFIER =
            ErrorCode.define("nop.err.metadata.quality-invalid-identifier",
                    "Identifier (column/table/schema) does not match whitelist ^[A-Za-z_][A-Za-z0-9_]*$: {identifier}",
                    ARG_IDENTIFIER);
    ErrorCode ERR_QUALITY_CUSTOM_SQL_BLOCKED =
            ErrorCode.define("nop.err.metadata.quality-custom-sql-blocked",
                    "custom_sql rule SQL contains forbidden keyword: "
                            + "{ruleKey} reason={reason} sqlHash={sqlHash}",
                    ARG_RULE_KEY, ARG_REASON, "sqlHash");
    ErrorCode ERR_QUALITY_SQL_NO_ROW =
            ErrorCode.define("nop.err.metadata.quality-sql-no-row",
                    "Quality custom_sql returned no rows: {ruleKey}", ARG_RULE_KEY);
    ErrorCode ERR_QUALITY_SQL_FAILED =
            ErrorCode.define("nop.err.metadata.quality-sql-failed",
                    "Quality rule SQL execution failed: {ruleKey} -- {error}",
                    ARG_RULE_KEY, ARG_ERROR);

    // ===== Checkpoint (checkpoint) =====

    ErrorCode ERR_CHECKPOINT_SCHEDULER_INVALID_CRON =
            ErrorCode.define("nop.err.metadata.checkpoint-scheduler-invalid-cron",
                    "Quality checkpoint schedule cron expression is invalid: "
                            + "{checkpointId} cron={cron}", ARG_CHECKPOINT_ID, ARG_CRON);
    ErrorCode ERR_CHECKPOINT_NOT_ACTIVE =
            ErrorCode.define("nop.err.metadata.checkpoint-not-active",
                    "Quality checkpoint is not ACTIVE (paused/disabled), cannot execute: "
                            + "{checkpointId} status={status}", ARG_CHECKPOINT_ID, ARG_STATUS);
    ErrorCode ERR_CHECKPOINT_NO_RULES =
            ErrorCode.define("nop.err.metadata.checkpoint-no-rules",
                    "Quality checkpoint resolved to an empty rule set: {checkpointId}", ARG_CHECKPOINT_ID);
    ErrorCode ERR_CHECKPOINT_ACTION_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.checkpoint-action-not-supported",
                    "Quality checkpoint action type is not supported (only store/webhook/notify): "
                            + "{checkpointId} actionType={actionType}", ARG_CHECKPOINT_ID, "actionType");
    ErrorCode ERR_CHECKPOINT_RULE_TARGET_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.checkpoint-rule-target-table-not-found",
                    "Quality rule in checkpoint target table not found: "
                            + "{checkpointId} qualityRuleId={qualityRuleId} entityId={entityId}",
                    ARG_CHECKPOINT_ID, ARG_QUALITY_RULE_ID, ARG_ENTITY_ID);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_NO_CLIENT =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-no-client",
                    "Quality checkpoint webhook action configured but IHttpClient is not registered: {checkpointId}",
                    ARG_CHECKPOINT_ID);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_NO_URL =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-no-url",
                    "Quality checkpoint webhook action config is missing required 'url': {checkpointId}",
                    ARG_CHECKPOINT_ID);
    ErrorCode ERR_CHECKPOINT_NOTIFY_NO_SERVICE =
            ErrorCode.define("nop.err.metadata.checkpoint-notify-no-service",
                    "Quality checkpoint notify action configured but IMessageService is not registered: {checkpointId}",
                    ARG_CHECKPOINT_ID);
    ErrorCode ERR_CHECKPOINT_NOTIFY_NO_CHANNEL =
            ErrorCode.define("nop.err.metadata.checkpoint-notify-no-channel",
                    "Quality checkpoint notify action config is missing required 'channel': {checkpointId}",
                    ARG_CHECKPOINT_ID);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_URL_BLOCKED =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-url-blocked",
                    "Quality checkpoint webhook URL is blocked by SSRF protection policy: "
                            + "{checkpointId} url={url} reason={reason}",
                    ARG_CHECKPOINT_ID, ARG_URL, ARG_REASON);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_METHOD_BLOCKED =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-method-blocked",
                    "Quality checkpoint webhook method is not in whitelist (allowed: POST/PUT): "
                            + "{checkpointId} method={method}", ARG_CHECKPOINT_ID, ARG_METHOD);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_NULL_RESPONSE =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-null-response",
                    "Quality checkpoint webhook returned null response: {checkpointId} url={url}",
                    ARG_CHECKPOINT_ID, ARG_URL);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_NON_2XX =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-non-2xx",
                    "Quality checkpoint webhook returned non-2xx HTTP status: {checkpointId} url={url} status={status}",
                    ARG_CHECKPOINT_ID, ARG_URL, ARG_STATUS);

    // ===== Score (score) =====

    ErrorCode ERR_SCORE_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.score-table-not-found",
                    "Quality score target table not found (NopMetaTable missing): {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_SCORE_NO_RULES =
            ErrorCode.define("nop.err.metadata.score-no-rules",
                    "Quality score target table has no mounted quality rules, nothing to score: {metaTableId}",
                    ARG_META_TABLE_ID);
    ErrorCode ERR_SCORE_ALL_SKIP =
            ErrorCode.define("nop.err.metadata.score-all-skip",
                    "Quality score target table's all rule latest results are SKIP (or never executed), every "
                            + "dimension is null, cannot score: {metaTableId}", ARG_META_TABLE_ID);

    // ===== Quality rule (quality-rule) =====

    ErrorCode ERR_QUALITY_RULE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.quality-rule-not-found",
                    "Quality rule not found: {qualityRuleId}", ARG_QUALITY_RULE_ID);
    ErrorCode ERR_QUALITY_RESULT_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.quality-result-not-found",
                    "Quality result not found: {qualityResultId}", ARG_QUALITY_RESULT_ID);
    ErrorCode ERR_QUALITY_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.quality-table-not-found",
                    "Quality rule target table not found (entityId does not refer to an existing NopMetaTable): "
                            + "{qualityRuleId} entityId={entityId}", ARG_QUALITY_RULE_ID, ARG_ENTITY_ID);
    ErrorCode ERR_QUALITY_TABLE_NOT_EXTERNAL =
            ErrorCode.define("nop.err.metadata.quality-table-not-external",
                    "Quality rule target table is not external (first version supports external-only execution): "
                            + "{qualityRuleId} tableType={tableType}", ARG_QUALITY_RULE_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_QUALITY_NO_DATASOURCE =
            ErrorCode.define("nop.err.metadata.quality-no-datasource",
                    "No registered MetaDataSource for querySpace of target table: "
                            + "{qualityRuleId} querySpace={querySpace}", ARG_QUALITY_RULE_ID, ARG_QUERY_SPACE);
    ErrorCode ERR_QUALITY_DATASOURCE_DISABLED =
            ErrorCode.define("nop.err.metadata.quality-datasource-disabled",
                    "MetaDataSource is disabled, cannot execute quality rule: {dataSourceId}", ARG_DATA_SOURCE_ID);

    // ===== Field (field) =====

    ErrorCode ERR_FIELD_RESOLVE_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.field-resolve-table-not-found",
                    "MetaTable not found for field resolution: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_FIELD_RESOLVE_BASE_ENTITY_NULL =
            ErrorCode.define("nop.err.metadata.field-resolve-base-entity-null",
                    "Cannot resolve fields: entity table has null baseEntityId (dangling reference not allowed): "
                            + "{metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_FIELD_RESOLVE_NO_FIELDS =
            ErrorCode.define("nop.err.metadata.field-resolve-no-fields",
                    "Resolved field set is empty for table: {metaTableId} tableType={tableType}",
                    ARG_META_TABLE_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID =
            ErrorCode.define("nop.err.metadata.field-resolve-external-build-sql-invalid",
                    "Failed to parse external table buildSql JSON (expecting JSON array of column descriptors): "
                            + "{metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_FIELD_RESOLVE_UNKNOWN_TABLE_TYPE =
            ErrorCode.define("nop.err.metadata.field-resolve-unknown-table-type",
                    "Unknown tableType for field resolution: {metaTableId} tableType={tableType}",
                    ARG_META_TABLE_ID, ARG_TABLE_TYPE);

    // ===== Lineage (lineage) =====

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

    // ===== Table (table) =====

    ErrorCode ERR_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.table-not-found",
                    "Meta table not found: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_QUERY_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.query-table-not-found",
                    "NopMetaTable not found for queryTableData: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_QUERY_UNSUPPORTED_TABLE_TYPE =
            ErrorCode.define("nop.err.metadata.query-unsupported-table-type",
                    "Unsupported tableType for queryTableData: {metaTableId} tableType={tableType}",
                    ARG_META_TABLE_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_QUERY_ENTITY_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.query-entity-not-found",
                    "Entity record not found for entity table (baseEntityId dangling): "
                            + "{metaTableId} baseEntityId={baseEntityId}", ARG_META_TABLE_ID, ARG_BASE_ENTITY_ID);
    ErrorCode ERR_QUERY_ENTITY_NOT_REGISTERED =
            ErrorCode.define("nop.err.metadata.query-entity-not-registered",
                    "Entity is not registered in runtime IOrmSessionFactory: "
                            + "{metaTableId} entityName={entityName}", ARG_META_TABLE_ID, ARG_ENTITY_NAME);
    ErrorCode ERR_QUERY_SQL_SOURCE_EMPTY =
            ErrorCode.define("nop.err.metadata.query-sql-source-empty",
                    "sql table sourceSql is empty, cannot query: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_QUERY_UNSUPPORTED_DIALECT =
            ErrorCode.define("nop.err.metadata.query-unsupported-dialect",
                    "Dialect not supported in first version (only H2/MySQL/PostgreSQL): "
                            + "{databaseProductName} metaTableId={metaTableId}",
                    ARG_DATABASE_PRODUCT_NAME, ARG_META_TABLE_ID);
    ErrorCode ERR_QUERY_SQL_EXEC_FAILED =
            ErrorCode.define("nop.err.metadata.query-sql-exec-failed",
                    "Query SQL execution failed: metaTableId={metaTableId} -- {error}",
                    ARG_META_TABLE_ID, ARG_ERROR);

    // ===== SQL view module (sql-module) =====

    ErrorCode ERR_SQL_VIEW_MODULE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.sql-module-not-found",
                    "MetaModule not found for createSqlTable: {metaModuleId}", ARG_META_MODULE_ID);
    ErrorCode ERR_SQL_VIEW_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.sql-table-not-found",
                    "NopMetaTable not found for resolveTableFields: {metaTableId}", ARG_META_TABLE_ID);

    // ===== Join entity (join) =====

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

    // ===== Module (module) =====

    ErrorCode ERR_MODULE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.module-not-found",
                    "Module not found: {metaModuleId}", ARG_META_MODULE_ID);
    ErrorCode ERR_MODULE_NOT_DRAFTING =
            ErrorCode.define("nop.err.metadata.module-not-drafting",
                    "Module is not in drafting status: {status}", ARG_STATUS);
    ErrorCode ERR_MODULE_FULL_MODEL_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.module-full-model-not-found",
                    "Full ORM model (isDelta=false) not found for module, cannot generate manifest: {metaModuleId}",
                    ARG_META_MODULE_ID);
    ErrorCode ERR_ORM_RESOURCE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.orm-resource-not-found",
                    "ORM resource not found: {path}", ARG_PATH);
    ErrorCode ERR_ORM_RESOURCE_READ_FAILED =
            ErrorCode.define("nop.err.metadata.orm-resource-read-failed",
                    "ORM resource read failed: {path} -- {error}", ARG_PATH, ARG_ERROR);

    // ===== Dimension / Measure =====

    ErrorCode ERR_DIMENSION_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.dimension-table-not-found",
                    "MetaTable not found for dimension save: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_DIMENSION_FIELD_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.dimension-field-not-found",
                    "Dimension field reference does not belong to the table's reachable fields/entities: "
                            + "{metaTableId} entityFieldId={entityFieldId} ({refKind}); "
                            + "availableFields={availableFields} allowedEntityIds={allowedEntityIds}",
                    ARG_META_TABLE_ID, ARG_ENTITY_FIELD_ID, ARG_REF_KIND, ARG_AVAILABLE_FIELDS, ARG_ALLOWED_ENTITY_IDS);
    ErrorCode ERR_MEASURE_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.measure-table-not-found",
                    "MetaTable not found for measure save: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_MEASURE_FIELD_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.measure-field-not-found",
                    "Measure field reference does not belong to the table's reachable fields/entities: "
                            + "{metaTableId} entityFieldId={entityFieldId} ({refKind}); "
                            + "availableFields={availableFields} allowedEntityIds={allowedEntityIds}",
                    ARG_META_TABLE_ID, ARG_ENTITY_FIELD_ID, ARG_REF_KIND, ARG_AVAILABLE_FIELDS, ARG_ALLOWED_ENTITY_IDS);

    // ===== Filter definition =====

    ErrorCode ERR_FILTER_DEFINITION_INVALID =
            ErrorCode.define("nop.err.metadata.filter-definition-invalid",
                    "Filter definition JSON is not a valid TreeBean filter tree: {metaTableId} filterName={filterName}",
                    ARG_META_TABLE_ID, ARG_FILTER_NAME);
    ErrorCode ERR_FILTER_DEFINITION_EMPTY =
            ErrorCode.define("nop.err.metadata.filter-definition-empty",
                    "Filter definition is empty: {metaTableId} filterName={filterName}",
                    ARG_META_TABLE_ID, ARG_FILTER_NAME);
    ErrorCode ERR_FILTER_DEFAULT_ALREADY_EXISTS =
            ErrorCode.define("nop.err.metadata.filter-default-already-exists",
                    "Only one default filter (isDefault=true) is allowed per table: "
                            + "{metaTableId} existingDefault={existingFilterId}",
                    ARG_META_TABLE_ID, ARG_EXISTING_FILTER_ID);
    ErrorCode ERR_DEFAULT_FILTER_PARSE =
            ErrorCode.define("nop.err.metadata.default-filter-parse-failed",
                    "Failed to parse isDefault filter definition JSON: {filterId} -- {error}",
                    ARG_FILTER_ID, ARG_ERROR);

    // ===== Contract =====

    ErrorCode ERR_CONTRACT_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.contract-not-found",
                    "Data contract not found: {contractId}", ARG_CONTRACT_ID);
    ErrorCode ERR_CONTRACT_INVALID_TRANSITION =
            ErrorCode.define("nop.err.metadata.contract-invalid-transition",
                    "Invalid contract status transition: contractId={contractId} currentStatus={currentStatus} "
                            + "expectedStatus={expectedStatus}", ARG_CONTRACT_ID, ARG_CURRENT_STATUS, ARG_EXPECTED_STATUS);
    ErrorCode ERR_CONTRACT_QUALITY_EXPECTATIONS_INVALID =
            ErrorCode.define("nop.err.metadata.contract-quality-expectations-invalid",
                    "Failed to parse qualityExpectations JSON for contract: {contractId} error={error}",
                    ARG_CONTRACT_ID, ARG_ERROR);
    ErrorCode ERR_CONTRACT_SLA_INVALID =
            ErrorCode.define("nop.err.metadata.contract-sla-invalid",
                    "Failed to parse sla JSON for contract: {contractId} error={error}",
                    ARG_CONTRACT_ID, ARG_ERROR);

    // ===== TagLabel =====

    ErrorCode ERR_TAG_LABEL_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.tag-label-not-found",
                    "TagLabel not found: {tagLabelId}", ARG_TAG_LABEL_ID);
    ErrorCode ERR_TAG_LABEL_INVALID_LABEL_TYPE =
            ErrorCode.define("nop.err.metadata.tag-label-invalid-label-type",
                    "Unknown or unsupported labelType for approval trigger: {labelType}",
                    ARG_LABEL_TYPE);

    // ===== DataProduct link-asset =====

    ErrorCode ERR_LINK_ASSET_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.link-asset-not-found",
                    "TagLabel not found for DataProduct link-asset: "
                            + "dataProductId={dataProductId} entityType={entityType} entityId={entityId}",
                    ARG_DATA_PRODUCT_ID, ARG_ENTITY_TYPE, ARG_ENTITY_ID);
    ErrorCode ERR_LINK_ASSET_ENTITY_TYPE_INVALID =
            ErrorCode.define("nop.err.metadata.link-asset-entity-type-invalid",
                    "Entity type not recognized as a linkable asset: {entityType}",
                    ARG_ENTITY_TYPE);

    // ===== Checkpoint (checkpoint biz) =====

    ErrorCode ERR_CHECKPOINT_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.checkpoint-not-found",
                    "Quality checkpoint not found: {checkpointId}", ARG_CHECKPOINT_ID);

    // ===== Profiling rule =====

    ErrorCode ERR_PROFILING_RULE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.profiling-rule-not-found",
                    "Profiling rule not found: {profilingRuleId}", ARG_PROFILING_RULE_ID);

    // ===== Event =====

    ErrorCode ERR_EVENT_SNAPSHOT_SERIALIZE_FAILED =
            ErrorCode.define("nop.err.metadata.event-snapshot-serialize-failed",
                    "Failed to serialize change-event snapshot: entityType={entityType} entityId={entityId} error={error}",
                    ARG_ENTITY_TYPE, ARG_ENTITY_ID, ARG_ERROR);

    // ===== Catalog =====

    ErrorCode ERR_CATALOG_INVALID_IDENTIFIER =
            ErrorCode.define("nop.err.metadata.catalog-invalid-identifier",
                    "Identifier (table/schema) does not match whitelist ^[A-Za-z_][A-Za-z0-9_]*$: {identifier}",
                    ARG_IDENTIFIER);
    ErrorCode ERR_CATALOG_AGGREGATE_NO_ROW =
            ErrorCode.define("nop.err.metadata.catalog-aggregate-no-row",
                    "Catalog aggregate SQL returned no row (logical impossibility): {sql}", ARG_SQL);

    // ===== Granularity =====

    ErrorCode ERR_GRANULARITY_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.aggr-granularity-not-supported",
                    "granularity value not in supported set [year,quarter,month,week,day,hour]: "
                            + "{granularity} dimensionName={dimensionName}",
                    "granularity", ARG_DIMENSION_NAME);

    // ===== Reconciliation =====

    ErrorCode ERR_RECON_ROW_MISSING_COLUMN =
            ErrorCode.define("nop.err.metadata.recon-row-missing-column",
                    "Reconciliation row is missing configured columnName key: configId={configId} "
                            + "columnName={columnName} rowIndex={rowIndex}",
                    ARG_CONFIG_ID, ARG_COLUMN_NAME, ARG_ROW_INDEX);
    ErrorCode ERR_RECON_UNSUPPORTED_MATCH_STRATEGY =
            ErrorCode.define("nop.err.metadata.recon-unsupported-match-strategy",
                    "Unsupported matchStrategy for reconciliation: {matchStrategy}", "matchStrategy");
    ErrorCode ERR_RECON_UNKNOWN_STATUS =
            ErrorCode.define("nop.err.metadata.recon-unknown-status",
                    "Reconciliation produced unknown status: {status}", ARG_STATUS);
    ErrorCode ERR_RECON_CONFIG_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.recon-config-not-found",
                    "Reconciliation config not found: {configId}", ARG_CONFIG_ID);
    ErrorCode ERR_RECON_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.recon-table-not-found",
                    "MetaTable not found for reconciliation: configId={configId} metaTableId={metaTableId}",
                    ARG_CONFIG_ID, ARG_META_TABLE_ID);
    ErrorCode ERR_RECON_COLUMN_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.recon-column-not-found",
                    "Configured columnName is not in the table's available field set: "
                            + "configId={configId} metaTableId={metaTableId} columnName={columnName} "
                            + "availableFields={availableFields}",
                    ARG_CONFIG_ID, ARG_META_TABLE_ID, ARG_COLUMN_NAME, ARG_AVAILABLE_FIELDS);
    ErrorCode ERR_RECON_FETCH_TABLE_DATA_FAILED =
            ErrorCode.define("nop.err.metadata.recon-fetch-table-data-failed",
                    "queryTableData failed for reconciliation: configId={configId} metaTableId={metaTableId} "
                            + "-- {error}", ARG_CONFIG_ID, ARG_META_TABLE_ID, ARG_ERROR);
    ErrorCode ERR_RECON_RESULT_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.recon-result-not-found",
                    "Reconciliation result not found: {resultId}", ARG_RESULT_ID);
    ErrorCode ERR_RECON_DETAILS_EMPTY =
            ErrorCode.define("nop.err.metadata.recon-details-empty",
                    "Reconciliation result has empty details, cannot confirm: resultId={resultId}", ARG_RESULT_ID);
    ErrorCode ERR_RECON_ROW_INDEX_OUT_OF_RANGE =
            ErrorCode.define("nop.err.metadata.recon-row-index-out-of-range",
                    "Reconciliation rowIndex is out of range: resultId={resultId} rowIndex={rowIndex} "
                            + "detailsSize={detailsSize}", ARG_RESULT_ID, ARG_ROW_INDEX, ARG_DETAILS_SIZE);
    ErrorCode ERR_RECON_SELECTIONS_EMPTY =
            ErrorCode.define("nop.err.metadata.recon-selections-empty",
                    "Reconciliation batch confirm selections is empty: resultId={resultId}", ARG_RESULT_ID);

    // ===== Legacy preserved ErrorCodes (Phase 1) now grouped above =====

    /** 解析属性失败（LocalReconciliationProcessor.parseProperties 静默吞异常修复后抛出）。 */
    ErrorCode ERR_RECON_PARSE_PROPERTIES_FAILED =
            ErrorCode.define("nop.err.metadata.recon-parse-properties-failed",
                    "Reconciliation parseProperties failed: {error}", ARG_ERROR);

    /** expectPassWhen 表达式非法（plan Phase 5 维度 AR-11）。 */
    ErrorCode ERR_QUALITY_EXPECT_PASS_WHEN_INVALID =
            ErrorCode.define("nop.err.metadata.quality-expect-pass-when-invalid",
                    "Quality rule expectPassWhen expression is invalid: {qualityRuleId} expr={expr}",
                    ARG_QUALITY_RULE_ID, "expr");

    /** 字段序列化失败（plan Phase 1 DTO 序列化异常路径，无静默跳过）。 */
    ErrorCode ERR_DTO_SERIALIZE_FAILED =
            ErrorCode.define("nop.err.metadata.dto-serialize-failed",
                    "DTO serialize failed: {entityType} -- {error}", ARG_ENTITY_TYPE, ARG_ERROR);

    /** manifest 构建失败（替代 MetaManifestBuilder 的 IllegalArgumentException）。 */
    ErrorCode ERR_MANIFEST_BUILD_FAILED =
            ErrorCode.define("nop.err.metadata.manifest-build-failed",
                    "MetaManifest build failed: {metaModuleId} -- {error}",
                    ARG_META_MODULE_ID, ARG_ERROR);

    /** manifest 模块为空。 */
    ErrorCode ERR_MANIFEST_MODULE_NULL =
            ErrorCode.define("nop.err.metadata.manifest.module-null",
                    "MetaManifest build failed: module is null",
                    ARG_META_MODULE_ID);

    /** manifest ORM 模型为空。 */
    ErrorCode ERR_MANIFEST_ORM_MODEL_NULL =
            ErrorCode.define("nop.err.metadata.manifest.orm-model-null",
                    "MetaManifest build failed: full ORM model is null",
                    ARG_META_MODULE_ID);

    // ===== Search =====

    ErrorCode ERR_SEARCH_INDEX_REBUILD_FAILED =
            ErrorCode.define("nop.err.metadata.search-index-rebuild-failed",
                    "Search index rebuild failed: {entityType} -- {error}",
                    ARG_ENTITY_TYPE, ARG_ERROR);

    ErrorCode ERR_SEARCH_ENGINE_UNAVAILABLE =
            ErrorCode.define("nop.err.metadata.search-engine-unavailable",
                    "Search engine is not available: {error}",
                    ARG_ERROR);

    // ===== Sync =====

    ErrorCode ERR_DIALECT_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.dialect-not-supported",
                    "Dialect not supported: {databaseProductName} -- {error}",
                    ARG_DATABASE_PRODUCT_NAME, ARG_ERROR);
}
