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
    // AR-21（plan 2026-08-06-1228-1 Phase 3）：自动化/传播路径标签保存本身失败（与提交提审失败 ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED
    // 语义区分——该码消息描述"已保存但未进审批流"，用于"保存即失败"会误导定位）——显式错误码，不再被 catch-all 吞掉静默返回 null。
    ErrorCode ERR_TAG_LABEL_SAVE_FAILED =
            ErrorCode.define("nop.err.metadata.tag-label-save-failed",
                    "TagLabel save failed (automated/propagated label could not be persisted, failing loudly "
                            + "instead of silent drop): {entityType} entityId={entityId} tagId={tagId} -- {error}",
                    ARG_ENTITY_TYPE, ARG_ENTITY_ID, ARG_TAG_ID, ARG_ERROR);
    // P2-01（plan 2026-08-16-0920-1，裁决选项 ii）：GLOSSARY 来源、tagId=NULL 的标注行不被
    // UK_NOP_META_TAG_LABEL 约束（复合 UK 任一列 NULL 即豁免唯一性），显式客户端 save 可累积重复行。
    // 应用层查重守卫 fail-loud 拒绝（沿 existingPropagatedLabel / ERR_SQL_VIEW_TABLE_EXISTS 先例），UK 保持不变。
    ErrorCode ERR_TAG_LABEL_DUPLICATE_GLOSSARY_TERM =
            ErrorCode.define("nop.err.metadata.tag-label-duplicate-glossary-term",
                    "Duplicate glossary term tag label rejected (source=Glossary rows with null tagId are not "
                            + "covered by UK_NOP_META_TAG_LABEL due to NULL-distinct semantics): "
                            + "entityType={entityType} entityId={entityId} glossaryTermId={glossaryTermId}",
                    ARG_ENTITY_TYPE, ARG_ENTITY_ID, ARG_GLOSSARY_TERM_ID);

    // ===== BusinessDomain =====

    // P2-28（plan 2026-08-16-0920-1）：UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME 对根域
    // （parentDomainId NULL）不生效（NULL-distinct），应用层根域重名守卫 fail-loud 拒绝，UK 保持不变。
    ErrorCode ERR_BUSINESS_DOMAIN_DUPLICATE_ROOT_NAME =
            ErrorCode.define("nop.err.metadata.business-domain-duplicate-root-name",
                    "Duplicate root business domain name rejected (root domains with null parentDomainId are not "
                            + "covered by UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME due to NULL-distinct semantics): "
                            + "name={name}",
                    ARG_NAME);

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
    ErrorCode ERR_SEARCH_LIMIT_INVALID =
            ErrorCode.define("nop.err.metadata.search-limit-invalid",
                    "Search limit must be null or a positive integer: {limit}", ARG_LIMIT);
    ErrorCode ERR_SEARCH_INDEX_BUILD_FAILED =
            ErrorCode.define("nop.err.metadata.search-index-build-failed",
                    "Search index build/rebuild failed for entityType={entityType} -- {error}",
                    ARG_ENTITY_TYPE, ARG_ERROR);
    ErrorCode ERR_SEARCH_INDEX_PURGE_FAILED =
            ErrorCode.define("nop.err.metadata.search-index-purge-failed",
                    "Search index topic/type purge failed -- {error}", ARG_ERROR);
    ErrorCode ERR_SEARCH_INDEX_REFRESH_FAILED =
            ErrorCode.define("nop.err.metadata.search-index-refresh-failed",
                    "Search index refresh failed for entityType={entityType} -- {error}",
                    ARG_ENTITY_TYPE, ARG_ERROR);
    ErrorCode ERR_SEARCH_DOC_CONVERT_FAILED =
            ErrorCode.define("nop.err.metadata.search-doc-convert-failed",
                    "Search document conversion failed (isolated, batch continues): "
                            + "entityType={entityType} -- {error}",
                    ARG_ENTITY_TYPE, ARG_ERROR);

    // ===== Profiling isolation / type probe (clause-b formalize) =====

    ErrorCode ERR_PROFILING_COLUMN_PROFILE_ISOLATED =
            ErrorCode.define("nop.err.metadata.profiling-column-profile-isolated",
                    "Profiling column failed (isolated, batch continues): table={tableName} column={columnName} "
                            + "-- {error}",
                    ARG_TABLE_NAME, ARG_COLUMN_NAME, ARG_ERROR);
    ErrorCode ERR_PROFILING_TYPE_PROBE_FAILED =
            ErrorCode.define("nop.err.metadata.profiling-type-probe-failed",
                    "Profiling type probe failed (fallback applied): {error}", ARG_ERROR);

    // ===== Contract type probe (clause-b formalize) =====

    ErrorCode ERR_CONTRACT_TYPE_PROBE_FAILED =
            ErrorCode.define("nop.err.metadata.contract-type-probe-failed",
                    "Contract column type probe failed (fallback applied): {error}", ARG_ERROR);

    // ===== Entity / Glossary / TagLabel sync isolation (clause-b formalize) =====

    ErrorCode ERR_ENTITY_SYNC_ISOLATED =
            ErrorCode.define("nop.err.metadata.entity-sync-isolated",
                    "Entity sync failed (isolated, batch continues): entityType={entityType} entityId={entityId} "
                            + "-- {error}",
                    ARG_ENTITY_TYPE, ARG_ENTITY_ID, ARG_ERROR);
    ErrorCode ERR_AUTOMATION_PROCESS_ISOLATED =
            ErrorCode.define("nop.err.metadata.automation-process-isolated",
                    "Automation processing failed (isolated, batch continues): entityType={entityType} "
                            + "entityId={entityId} -- {error}",
                    ARG_ENTITY_TYPE, ARG_ENTITY_ID, ARG_ERROR);
}
