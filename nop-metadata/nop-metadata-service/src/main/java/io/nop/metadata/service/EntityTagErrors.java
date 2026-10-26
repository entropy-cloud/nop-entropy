package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * 标准 CRUD 保留标签（std-crud/std-crud-ro）守卫与目录合成入口错误码
 * （设计 14 §3.3，plan 2026-10-26-2047-1 Phase 4）。
 */
interface EntityTagErrors extends NopMetadataArgs {

    ErrorCode ERR_ENTITY_TAG_STD_CRUD_PHYSICAL_ONLY =
            ErrorCode.define("nop.err.metadata.entity-tag-std-crud-physical-only",
                    "std-crud tag is only allowed on PHYSICAL entities "
                            + "(SQL_VIEW/EXTERNAL must declare std-crud-ro): "
                            + "entityKind={entityKind} tagSet={tagSet}", ARG_TABLE_TYPE, ARG_TAG_SET);
    ErrorCode ERR_ENTITY_TAG_STD_CRUD_RO_ENTITY_KIND =
            ErrorCode.define("nop.err.metadata.entity-tag-std-crud-ro-entity-kind",
                    "std-crud-ro tag is only allowed on SQL_VIEW/EXTERNAL entities "
                            + "(PHYSICAL standard CRUD comes from its own bizObj, declare std-crud): "
                            + "entityKind={entityKind} tagSet={tagSet}", ARG_TABLE_TYPE, ARG_TAG_SET);
    ErrorCode ERR_ENTITY_STD_CRUD_NOT_DECLARED =
            ErrorCode.define("nop.err.metadata.entity-std-crud-not-declared",
                    "Entity does not declare the std-crud/std-crud-ro reserved tag; "
                            + "catalog composed query entries are disabled for it: "
                            + "metaEntityId={metaEntityId}", ARG_META_ENTITY_ID);
}
