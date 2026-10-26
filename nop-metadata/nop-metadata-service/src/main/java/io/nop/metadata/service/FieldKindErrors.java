package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * 字段类别（fieldKind）守卫与计算列求值错误码（设计 14 §3.2/§3.6，plan 2026-10-26-2047-1 Phase 2）。
 *
 * <p>kind 非法值在 GraphQL 入口先被 xmeta dict 校验拒绝（平台码
 * {@code nop.err.biz.invalid-dict-option}）；{@code ERR_FIELD_KIND_UNKNOWN} 为 DAO 直写行
 * 经 update 触达的 defense-in-depth 码（Phase 1 同款分层）。
 */
interface FieldKindErrors extends NopMetadataArgs {

    ErrorCode ERR_FIELD_KIND_UNKNOWN =
            ErrorCode.define("nop.err.metadata.field-kind-unknown",
                    "Unknown fieldKind (expected storage/computed/alias): "
                            + "fieldKind={fieldKind}", ARG_FIELD_KIND);
    ErrorCode ERR_FIELD_COMPUTED_EXPR_REQUIRED =
            ErrorCode.define("nop.err.metadata.field-computed-expr-required",
                    "fieldKind=computed requires a non-empty computeExpr: "
                            + "fieldName={fieldName}", ARG_FIELD_NAME);
    ErrorCode ERR_FIELD_STORAGE_EXPR_FORBIDDEN =
            ErrorCode.define("nop.err.metadata.field-storage-expr-forbidden",
                    "storage field must not carry computeExpr or mapToProp: "
                            + "fieldName={fieldName}", ARG_FIELD_NAME);
    ErrorCode ERR_FIELD_KIND_EXTRA_FORBIDDEN =
            ErrorCode.define("nop.err.metadata.field-kind-extra-forbidden",
                    "fieldKind={fieldKind} must not carry both computeExpr and mapToProp: "
                            + "fieldName={fieldName}", ARG_FIELD_KIND, ARG_FIELD_NAME);
    ErrorCode ERR_FIELD_ALIAS_TARGET_UNKNOWN =
            ErrorCode.define("nop.err.metadata.field-alias-target-unknown",
                    "alias target must be another storage field of the same entity "
                            + "(missing/self-reference/non-storage all reject): "
                            + "fieldName={fieldName} mapToProp={mapToProp}", ARG_FIELD_NAME, ARG_MAP_TO_PROP);
    ErrorCode ERR_FIELD_COMPUTED_EXPR_UNPARSEABLE =
            ErrorCode.define("nop.err.metadata.field-computed-expr-unparseable",
                    "computeExpr is not a parseable simple expression: "
                            + "fieldName={fieldName}", ARG_FIELD_NAME);
    ErrorCode ERR_FIELD_COMPUTED_IDENTIFIER_UNKNOWN =
            ErrorCode.define("nop.err.metadata.field-computed-identifier-unknown",
                    "computeExpr references a name outside the entity field set: "
                            + "fieldName={fieldName} identifier={identifier}", ARG_FIELD_NAME, ARG_IDENTIFIER);
    ErrorCode ERR_FIELD_COMPUTED_EVAL_FAILED =
            ErrorCode.define("nop.err.metadata.field-computed-eval-failed",
                    "Failed to evaluate computeExpr at query time: "
                            + "fieldName={fieldName} error={error}", ARG_FIELD_NAME, ARG_ERROR);
}
