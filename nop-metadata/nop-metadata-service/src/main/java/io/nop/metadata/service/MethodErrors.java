package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * 实体方法注册表（设计 14 §3.6）：save 守卫与执行错误码。
 *
 * <p>Phase 1（plan 2026-10-26-2047-1）落地 save 守卫族：保留名/格式/来源/kind 组合。
 * 拒绝面分层（2026-10-27 实测裁定）：methodKind/implKind 非法值在 GraphQL 入口先被
 * xmeta dict 校验拒绝（平台码 {@code nop.err.biz.invalid-dict-option}）；本组
 * kind 错误码是 DAO 直写行（绕过 xmeta 校验，如快照导入通道缺陷）经 GraphQL update
 * 触达 BizModel 守卫的 defense-in-depth 拒绝码。argName 重复无 NULL-distinct 豁免面，
 * 由 DB UK 承载（P2-28 先例），不设应用层查重码。methodName 唯一由
 * {@code UK_NOP_META_METHOD_ENTITY_NAME} 承载。
 *
 * <p>{@code ERR_METHOD_IMPL_INVALID} 本 Phase 仅覆盖 implSource 非空校验，
 * SQL 沙箱语义在 Phase 3 落地并回接 save-time 校验（query-time 拒绝码
 * {@code ERR_METHOD_SQL_UNSAFE} 同 Phase 新增）。
 */
interface MethodErrors extends NopMetadataArgs {

    ErrorCode ERR_METHOD_NAME_RESERVED =
            ErrorCode.define("nop.err.metadata.method-name-reserved",
                    "Method name conflicts with a platform standard action name: "
                            + "methodName={methodName}", ARG_METHOD_NAME);
    ErrorCode ERR_METHOD_NAME_INVALID =
            ErrorCode.define("nop.err.metadata.method-name-invalid",
                    "Method name must be a lowerCamelCase identifier: "
                            + "methodName={methodName}", ARG_METHOD_NAME);
    ErrorCode ERR_METHOD_KIND_UNKNOWN =
            ErrorCode.define("nop.err.metadata.method-kind-unknown",
                    "Unknown methodKind (expected static-query/static-mutation/loader): "
                            + "methodKind={methodKind}", ARG_METHOD_KIND);
    ErrorCode ERR_METHOD_IMPL_KIND_UNSUPPORTED =
            ErrorCode.define("nop.err.metadata.method-impl-kind-unsupported",
                    "Unsupported implKind, only 'sql' is supported in this version: "
                            + "implKind={implKind}", ARG_IMPL_KIND);
    ErrorCode ERR_METHOD_SOURCE_IMPORTED_FORBIDDEN =
            ErrorCode.define("nop.err.metadata.method-source-imported-forbidden",
                    "sourceKind=imported rows can only be written by the snapshot import channel: "
                            + "sourceKind={sourceKind}", ARG_SOURCE_KIND);
    ErrorCode ERR_METHOD_IMPL_INVALID =
            ErrorCode.define("nop.err.metadata.method-impl-invalid",
                    "Method implSource is empty or fails SQL sandbox validation: "
                            + "methodName={methodName}", ARG_METHOD_NAME);
    ErrorCode ERR_METHOD_SQL_UNSAFE =
            ErrorCode.define("nop.err.metadata.method-sql-unsafe",
                    "Method implSource failed query-time SQL sandbox re-validation: "
                            + "methodName={methodName}", ARG_METHOD_NAME);
    ErrorCode ERR_METHOD_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.method-not-found",
                    "Method is not registered on the entity: "
                            + "metaEntityId={metaEntityId} methodName={methodName}",
                    ARG_META_ENTITY_ID, ARG_METHOD_NAME);
    ErrorCode ERR_METHOD_DISABLED =
            ErrorCode.define("nop.err.metadata.method-disabled",
                    "Method is disabled: methodName={methodName}", ARG_METHOD_NAME);
    ErrorCode ERR_METHOD_ARG_MISSING =
            ErrorCode.define("nop.err.metadata.method-arg-missing",
                    "Mandatory method argument is missing: "
                            + "methodName={methodName} argName={argName}", ARG_METHOD_NAME, ARG_ARG_NAME);
    ErrorCode ERR_METHOD_ARG_UNKNOWN =
            ErrorCode.define("nop.err.metadata.method-arg-unknown",
                    "Unknown method arguments (not declared on the method): "
                            + "methodName={methodName} args={args}", ARG_METHOD_NAME, ARG_ARGS);
    ErrorCode ERR_METHOD_ROLE_DENIED =
            ErrorCode.define("nop.err.metadata.method-role-denied",
                    "Caller does not hold any of the method's required roles: "
                            + "methodName={methodName} requiredRoles={requiredRoles}",
                    ARG_METHOD_NAME, ARG_REQUIRED_ROLES);
    ErrorCode ERR_METHOD_EXEC_FAILED =
            ErrorCode.define("nop.err.metadata.method-exec-failed",
                    "Method SQL execution failed (details are not disclosed): "
                            + "methodId={methodId} methodName={methodName} category={category}",
                    ARG_METHOD_ID, ARG_METHOD_NAME, ARG_CATEGORY);
}
