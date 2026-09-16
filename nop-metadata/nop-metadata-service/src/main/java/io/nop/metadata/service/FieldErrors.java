package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface FieldErrors extends NopMetadataArgs {

    /**
     * P1-6（plan 2026-08-15-1913-3 轨 3）：null 防御分支——table 实体本身为 null 时无任何
     * 身份值可传，换用零占位符错误码（ERR_FIELD_RESOLVE_TABLE_NOT_FOUND 的 {metaEntityId}
     * 在端点表不存在点位传齐，削占位符会使该点身份丢失，故新增而非调整）。
     */
    ErrorCode ERR_FIELD_RESOLVE_TABLE_NULL =
            ErrorCode.define("nop.err.metadata.field-resolve-table-null",
                    "MetaEntity entity is null, cannot resolve fields "
                            + "(no identity available)");
    /**
     * P1-6 轨 3：同上——metaEntityId 入参 null/空分支无可传值
     * （resolveAllowedEntityIds 同码点位传齐，禁削占位符）。
     */
    ErrorCode ERR_FIELD_RESOLVE_ENTITY_ID_NULL =
            ErrorCode.define("nop.err.metadata.field-resolve-entity-id-null",
                    "Cannot resolve fields: metaEntityId argument "
                            + "is null or empty");
    /**
     * P1-6 轨 3：buildSql null/空、JSON 解析失败、非数组三阶段无"元素下标"可传
     * （元素级点位 elementIndex 传齐，禁削占位符）。
     */
    ErrorCode ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_UNPARSEABLE =
            ErrorCode.define(
                    "nop.err.metadata.field-resolve-external-build-sql-unparseable",
                    "External table buildSql is null/empty, unparseable JSON, "
                            + "or not a JSON array of column descriptors: "
                            + "{metaEntityId}", ARG_META_ENTITY_ID);
    ErrorCode ERR_FIELD_RESOLVE_NO_FIELDS =
            ErrorCode.define("nop.err.metadata.field-resolve-no-fields",
                    "Resolved field set is empty for table: {metaEntityId} entityKind={entityKind}",
                    ARG_META_ENTITY_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_FIELD_RESOLVE_EXTERNAL_BUILD_SQL_INVALID =
            ErrorCode.define("nop.err.metadata.field-resolve-external-build-sql-invalid",
                    "Failed to parse external table buildSql JSON (expecting JSON array of column descriptors): "
                            + "{metaEntityId} elementIndex={elementIndex}", ARG_META_ENTITY_ID, ARG_ELEMENT_INDEX);
    ErrorCode ERR_FIELD_RESOLVE_UNKNOWN_TABLE_TYPE =
            ErrorCode.define("nop.err.metadata.field-resolve-unknown-table-type",
                    "Unknown entityKind for field resolution: {metaEntityId} entityKind={entityKind}",
                    ARG_META_ENTITY_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_DIMENSION_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.dimension-table-not-found",
                    "MetaEntity not found for dimension save: {metaEntityId}", ARG_META_ENTITY_ID);
    ErrorCode ERR_DIMENSION_FIELD_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.dimension-field-not-found",
                    "Dimension field reference does not belong to the table's reachable fields/entities: "
                            + "{metaEntityId} entityFieldId={entityFieldId} ({refKind}); "
                            + "availableFields={availableFields} allowedEntityIds={allowedEntityIds}",
                    ARG_META_ENTITY_ID, ARG_ENTITY_FIELD_ID, ARG_REF_KIND, ARG_AVAILABLE_FIELDS, ARG_ALLOWED_ENTITY_IDS);
    ErrorCode ERR_MEASURE_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.measure-table-not-found",
                    "MetaEntity not found for measure save: {metaEntityId}", ARG_META_ENTITY_ID);
    ErrorCode ERR_MEASURE_FIELD_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.measure-field-not-found",
                    "Measure field reference does not belong to the table's reachable fields/entities: "
                            + "{metaEntityId} entityFieldId={entityFieldId} ({refKind}); "
                            + "availableFields={availableFields} allowedEntityIds={allowedEntityIds}",
                    ARG_META_ENTITY_ID, ARG_ENTITY_FIELD_ID, ARG_REF_KIND, ARG_AVAILABLE_FIELDS, ARG_ALLOWED_ENTITY_IDS);
}
