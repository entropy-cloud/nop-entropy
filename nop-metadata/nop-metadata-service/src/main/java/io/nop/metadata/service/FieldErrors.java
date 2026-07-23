/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface FieldErrors extends NopMetadataArgs {

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
}
