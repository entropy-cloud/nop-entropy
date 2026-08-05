package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface MiscErrors extends NopMetadataArgs {

    // ===== Query filter (query-filter) =====

    ErrorCode ERR_FILTER_INVALID_IDENTIFIER =
            ErrorCode.define("nop.err.metadata.filter-invalid-identifier",
                    "Filter field name does not match identifier whitelist ^[A-Za-z_][A-Za-z0-9_]*$: {identifier}",
                    ARG_IDENTIFIER);
    ErrorCode ERR_FILTER_UNSUPPORTED_OP =
            ErrorCode.define("nop.err.metadata.filter-unsupported-op",
                    "Filter op not supported in first version: {op}", ARG_OP);
    ErrorCode ERR_FILTER_MISSING_FIELD =
            ErrorCode.define("nop.err.metadata.filter-missing-field",
                    "Filter leaf condition missing 'name' attr (field name): {op}", ARG_OP);
    ErrorCode ERR_FILTER_MISSING_VALUE =
            ErrorCode.define("nop.err.metadata.filter-missing-value",
                    "Filter leaf condition missing 'value' attr: {op} name={name}", ARG_OP, ARG_NAME);
    ErrorCode ERR_FILTER_IN_VALUE_NOT_COLLECTION =
            ErrorCode.define("nop.err.metadata.filter-in-value-not-collection",
                    "Filter 'in'/'notIn' value must be a collection: name={name}", ARG_NAME);
    ErrorCode ERR_FILTER_BETWEEN_MISSING_BOUNDS =
            ErrorCode.define("nop.err.metadata.filter-between-missing-bounds",
                    "Filter 'between' requires min and/or max attrs: name={name}", ARG_NAME);
    ErrorCode ERR_FILTER_FIELD_RESOLVER_MISS =
            ErrorCode.define("nop.err.metadata.filter-field-resolver-miss",
                    "Filter field resolver returned no SQL expression for name (likely unknown measure/dimension "
                            + "in having/orderBy): {op} name={name}", ARG_OP, ARG_NAME);

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
            ErrorCode.define("nop.err.metadata.default-filter-parse",
                    "Failed to parse isDefault filter definition JSON: {filterId} -- {error}",
                    ARG_FILTER_ID, ARG_ERROR);

    // ===== Profiling =====

    ErrorCode ERR_PROFILING_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.profiling-table-not-found",
                    "Profiling target table not found: {metaTableId}", ARG_META_TABLE_ID);
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

    // ===== Contract =====

    ErrorCode ERR_CONTRACT_QUALITY_EXPECTATIONS_INVALID =
            ErrorCode.define("nop.err.metadata.contract-quality-expectations-invalid",
                    "Failed to parse qualityExpectations JSON for contract: {contractId} error={error}",
                    ARG_CONTRACT_ID, ARG_ERROR);
    ErrorCode ERR_CONTRACT_SLA_INVALID =
            ErrorCode.define("nop.err.metadata.contract-sla-invalid",
                    "Failed to parse sla JSON for contract: {contractId} error={error}",
                    ARG_CONTRACT_ID, ARG_ERROR);

    // ===== TagLabel =====

    ErrorCode ERR_TAG_LABEL_INVALID_LABEL_TYPE =
            ErrorCode.define("nop.err.metadata.tag-label-invalid-label-type",
                    "Unknown or unsupported labelType for approval trigger: {labelType}",
                    ARG_LABEL_TYPE);
    ErrorCode ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED =
            ErrorCode.define("nop.err.metadata.tag-label-submit-approval-failed",
                    "TagLabel submit for approval failed (label saved but never enters approval flow would be "
                            + "silent data loss): {tagLabelId} -- {error}",
                    ARG_TAG_LABEL_ID, ARG_ERROR);

    // ===== Propagation =====

    ErrorCode ERR_PROPAGATE_UNSUPPORTED_ENTITY_TYPE =
            ErrorCode.define("nop.err.metadata.propagate-unsupported-entity-type",
                    "Tag propagation only supports entityType=NopMetaTable, got: {entityType}",
                    ARG_ENTITY_TYPE);

    // ===== AutoClassification =====

    ErrorCode ERR_AUTOCLASSIFY_UNSUPPORTED_ENTITY_TYPE =
            ErrorCode.define("nop.err.metadata.autoclassify-unsupported-entity-type",
                    "Auto-classification only supports entityType=NopMetaTable, got: {entityType}",
                    ARG_ENTITY_TYPE);
    ErrorCode ERR_AUTOCLASSIFY_UNSUPPORTED_TABLE_TYPE =
            ErrorCode.define("nop.err.metadata.autoclassify-unsupported-table-type",
                    "Auto-classification only supports tableType=entity, got: {tableType}",
                    ARG_TABLE_TYPE);

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

    // ===== Search =====

    ErrorCode ERR_SEARCH_ENGINE_UNAVAILABLE =
            ErrorCode.define("nop.err.metadata.search-engine-unavailable",
                    "Search engine is not available: {error}",
                    ARG_ERROR);
    ErrorCode ERR_SEARCH_INDEX_ADD_FAILED =
            ErrorCode.define("nop.err.metadata.search-index-add-failed",
                    "Failed to add document to search index: entityType={entityType} entityId={entityId}",
                    ARG_ENTITY_TYPE, ARG_ENTITY_ID);
    ErrorCode ERR_SEARCH_INDEX_REMOVE_FAILED =
            ErrorCode.define("nop.err.metadata.search-index-remove-failed",
                    "Failed to remove document from search index: entityType={entityType} entityId={entityId}",
                    ARG_ENTITY_TYPE, ARG_ENTITY_ID);

    // ===== Dialect =====

    ErrorCode ERR_DIALECT_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.dialect-not-supported",
                    "Dialect not supported: {databaseProductName} -- {error}",
                    ARG_DATABASE_PRODUCT_NAME, ARG_ERROR);
}
