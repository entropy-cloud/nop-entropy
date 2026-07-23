/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

/**
 * ARG_* parameter name constants for {@link NopMetadataErrors} ErrorCode definitions.
 */
public interface NopMetadataArgs {

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
    String ARG_VALUE = "value";
    String ARG_LENGTH = "length";
    String ARG_CLAUSE = "clause";
    String ARG_COLUMN = "column";
    String ARG_EXPR = "expr";
    String ARG_SQL_HASH = "sqlHash";
    String ARG_ACTION_TYPE = "actionType";
    String ARG_GRANULARITY = "granularity";
    String ARG_MATCH_STRATEGY = "matchStrategy";
    String ARG_DEPTH = "depth";
    String ARG_CLASSIFICATION_ID = "classificationId";
    String ARG_AUTO_CLASSIFICATION_CONFIG = "autoClassificationConfig";
    String ARG_CONFIG = "config";
    String ARG_PATTERN = "pattern";
    String ARG_TAG_FQN = "tagFQN";
    String ARG_PRIORITY = "priority";
    String ARG_FIELD_TYPE_FILTER = "fieldTypeFilter";
}
