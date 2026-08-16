/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.crypto;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * nop-credential 模块的错误码定义。所有失败路径（格式错误、未知 keyId、非法 keyId、
 * 配置错误）均使用这些错误码抛出 {@link io.nop.api.core.exceptions.NopException}，
 * 不静默跳过（遵循 Plan Guide Rule #24 禁止静默跳过）。
 */
public interface CredentialErrors {

    String ARG_KEY_ID = "keyId";
    String ARG_CIPHERTEXT = "ciphertext";
    String ARG_AVAILABLE_KEY_IDS = "availableKeyIds";
    String ARG_MASTER_KEY_ENTRY = "masterKeyEntry";
    String ARG_TYPE_NAME = "typeName";
    String ARG_AVAILABLE_TYPE_NAMES = "availableTypeNames";
    String ARG_CREDENTIAL_ID = "credentialId";
    String ARG_CONSUMER_REF = "consumerRef";
    String ARG_FIELD_NAME = "fieldName";
    String ARG_USAGE_COUNT = "usageCount";

    ErrorCode ERR_CREDENTIAL_INVALID_CIPHERTEXT_FORMAT = define(
            "nop.err.credential.invalid-ciphertext-format",
            "凭证密文格式非法（期望 cv1:{keyId}:v1:{base64data}）", ARG_CIPHERTEXT);

    ErrorCode ERR_CREDENTIAL_UNKNOWN_KEY_ID = define(
            "nop.err.credential.unknown-key-id",
            "凭证密文中的 keyId 未在主密钥提供者中注册", ARG_KEY_ID, ARG_AVAILABLE_KEY_IDS);

    ErrorCode ERR_CREDENTIAL_INVALID_KEY_ID = define(
            "nop.err.credential.invalid-key-id",
            "keyId 不符合约束 [A-Za-z0-9_-]+", ARG_KEY_ID);

    ErrorCode ERR_CREDENTIAL_MASTER_KEY_ENTRY_INVALID = define(
            "nop.err.credential.master-key-entry-invalid",
            "主密钥配置条目格式非法（期望 keyId:passphrase，keyId 须匹配 [A-Za-z0-9_-]+）",
            ARG_MASTER_KEY_ENTRY);

    ErrorCode ERR_CREDENTIAL_NO_MASTER_KEY_CONFIGURED = define(
            "nop.err.credential.no-master-key-configured",
            "未配置任何主密钥（nop.credential.master-keys 为空）");

    ErrorCode ERR_CREDENTIAL_UNKNOWN_TYPE = define(
            "nop.err.credential.unknown-type",
            "未知的凭证类型名", ARG_TYPE_NAME, ARG_AVAILABLE_TYPE_NAMES);

    ErrorCode ERR_CREDENTIAL_NOT_FOUND = define(
            "nop.err.credential.not-found",
            "凭证不存在", ARG_CREDENTIAL_ID);

    ErrorCode ERR_CREDENTIAL_DELETED = define(
            "nop.err.credential.deleted",
            "凭证已被软删除，拒绝访问（fail-closed）", ARG_CREDENTIAL_ID);

    ErrorCode ERR_CREDENTIAL_TYPE_LOAD_FAILED = define(
            "nop.err.credential.type-load-failed",
            "凭证类型文件加载失败", ARG_TYPE_NAME);

    ErrorCode ERR_CREDENTIAL_FIELDS_REQUIRED = define(
            "nop.err.credential.fields-required",
            "凭证字段不能为空（fields 为必填，至少包含一个明文字段）");

    ErrorCode ERR_CREDENTIAL_NAME_REQUIRED = define(
            "nop.err.credential.name-required",
            "凭证名称不能为空");

    ErrorCode ERR_CREDENTIAL_HAS_ACTIVE_USAGE = define(
            "nop.err.credential.has-active-usage",
            "凭证存在活跃的使用引用，拒绝删除（fail-closed）",
            ARG_CREDENTIAL_ID, ARG_USAGE_COUNT);

    ErrorCode ERR_CREDENTIAL_REENCRYPT_FAILED = define(
            "nop.err.credential.reencrypt-failed",
            "凭证重新加密失败",
            ARG_CREDENTIAL_ID);

    // ==================== W10 外部 KMS 集成（default-bean 守卫） ====================

    String ARG_KEY_PROVIDER = "keyProvider";

    ErrorCode ERR_CREDENTIAL_KEY_PROVIDER_MODULE_MISSING = define(
            "nop.err.credential.key-provider-module-missing",
            "nop.credential.key-provider 指向外部 KMS 实现但对应模块未部署（default bean 守卫拦截，fail-closed 拒绝回退 local）",
            ARG_KEY_PROVIDER);

    ErrorCode ERR_CREDENTIAL_MASTER_KEYS_RESIDUAL = define(
            "nop.err.credential.master-keys-residual",
            "密钥来源混合：key-provider 已激活外部 KMS 但 nop.credential.master-keys 非空（本地材料只允许存在于迁移残余列表，须清空）");

    // ==================== W9 OAuth 流程引擎 ====================

    String ARG_FIELD_NAMES = "fieldNames";
    String ARG_AUTH_TYPE = "authType";
    String ARG_STATE = "state";
    String ARG_USER_ID = "userId";
    String ARG_REDIRECT_URI = "redirectUri";
    String ARG_AUTHORIZATION_URL = "authorizationUrl";
    String ARG_TOKEN_ENDPOINT = "tokenEndpoint";
    String ARG_ERROR = "error";

    ErrorCode ERR_CREDENTIAL_TYPE_RESERVED_FIELD = define(
            "nop.err.credential.type-reserved-field",
            "凭证类型文件占用了 OAuth 引擎保留字段名", ARG_TYPE_NAME, ARG_FIELD_NAMES);

    ErrorCode ERR_CREDENTIAL_TYPE_INVALID_AUTH_TYPE = define(
            "nop.err.credential.type-invalid-auth-type",
            "凭证类型 authType 取值域外（合法值 none|apiKey|basic|oauth2）", ARG_TYPE_NAME, ARG_AUTH_TYPE);

    ErrorCode ERR_CREDENTIAL_TYPE_OAUTH_METADATA_MISSING = define(
            "nop.err.credential.type-oauth-metadata-missing",
            "authType=oauth2 的凭证类型缺少 OAuth 端点元数据（authorizationEndpoint/tokenEndpoint 必填）",
            ARG_TYPE_NAME);

    ErrorCode ERR_CREDENTIAL_TYPE_OAUTH_METADATA_NOT_ALLOWED = define(
            "nop.err.credential.type-oauth-metadata-not-allowed",
            "非 oauth2 类型的凭证类型文件声明了 <oauth2> 元数据（配置错位，拒绝而非静默忽略）", ARG_TYPE_NAME,
            ARG_AUTH_TYPE);

    ErrorCode ERR_CREDENTIAL_DISABLED = define(
            "nop.err.credential.disabled",
            "凭证已被禁用（status=disabled），oauth2 类型全路径拒绝访问（fail-closed）", ARG_CREDENTIAL_ID);

    ErrorCode ERR_CREDENTIAL_OAUTH_NOT_OAUTH2_TYPE = define(
            "nop.err.credential.oauth-not-oauth2-type",
            "发起 OAuth 授权要求凭证类型为 oauth2", ARG_TYPE_NAME, ARG_AUTH_TYPE);

    ErrorCode ERR_CREDENTIAL_OAUTH_CLIENT_CREDENTIALS_MISSING = define(
            "nop.err.credential.oauth-client-credentials-missing",
            "凭证 data 中缺少 clientId/clientSecret，发起授权前需先经 saveCredential 录入",
            ARG_CREDENTIAL_ID, ARG_FIELD_NAMES);

    ErrorCode ERR_CREDENTIAL_OAUTH_USER_CONTEXT_REQUIRED = define(
            "nop.err.credential.oauth-user-context-required",
            "发起 OAuth 授权要求登录态");

    ErrorCode ERR_CREDENTIAL_OAUTH_CALLBACK_NOT_CONFIGURED = define(
            "nop.err.credential.oauth-callback-not-configured",
            "OAuth 回调基础地址未配置（nop.credential.oauth.callback-base-url）");

    ErrorCode ERR_CREDENTIAL_OAUTH_STATE_INVALID = define(
            "nop.err.credential.oauth-state-invalid",
            "OAuth state 无效（未命中/过期/已消费，fail-closed 不区分细节）", ARG_STATE);

    ErrorCode ERR_CREDENTIAL_OAUTH_CALLBACK_PARAM_MISSING = define(
            "nop.err.credential.oauth-callback-param-missing",
            "OAuth 回调缺少必要参数（code/state）");

    ErrorCode ERR_CREDENTIAL_OAUTH_TOKEN_EXCHANGE_FAILED = define(
            "nop.err.credential.oauth-token-exchange-failed",
            "OAuth 令牌端点换取失败", ARG_TOKEN_ENDPOINT, ARG_ERROR);

    ErrorCode ERR_CREDENTIAL_OAUTH_REFRESH_FAILED = define(
            "nop.err.credential.oauth-refresh-failed",
            "OAuth refreshToken 刷新失败（fail-closed，不静默使用旧 token）", ARG_CREDENTIAL_ID, ARG_ERROR);

    ErrorCode ERR_CREDENTIAL_OAUTH_REAUTH_REQUIRED = define(
            "nop.err.credential.oauth-reauth-required",
            "accessToken 已过期且无 refreshToken，需重新走授权码流程", ARG_CREDENTIAL_ID);

    ErrorCode ERR_CREDENTIAL_RESERVED_FIELD_INPUT = define(
            "nop.err.credential.reserved-field-input",
            "saveCredential 输入包含 OAuth 引擎保留字段名（token 集只能由引擎写入）",
            ARG_FIELD_NAMES);

    // ==================== W11 凭证归属统一（scope=system|user + ownerId） ====================

    String ARG_SCOPE = "scope";
    String ARG_OWNER_ID = "ownerId";
    String ARG_REQUIRED_ROLES = "requiredRoles";

    ErrorCode ERR_CREDENTIAL_OWNER_ONLY = define(
            "nop.err.credential.owner-only",
            "user 级凭证明文取用仅限 owner 本人（无用户上下文/非 owner 一律拒绝，管理员不例外）",
            ARG_CREDENTIAL_ID, ARG_OWNER_ID);

    ErrorCode ERR_CREDENTIAL_OWNER_OR_ADMIN = define(
            "nop.err.credential.owner-or-admin",
            "user 级凭证该操作仅限 owner 或管理员",
            ARG_CREDENTIAL_ID, ARG_OWNER_ID);

    ErrorCode ERR_CREDENTIAL_ADMIN_REQUIRED = define(
            "nop.err.credential.admin-required",
            "该操作要求凭证库管理员角色（nop.credential.admin-roles）",
            ARG_CREDENTIAL_ID, ARG_REQUIRED_ROLES);

    ErrorCode ERR_CREDENTIAL_INVALID_SCOPE = define(
            "nop.err.credential.invalid-scope",
            "scope 取值非法（合法值 system|user）", ARG_SCOPE);

    ErrorCode ERR_CREDENTIAL_OWNER_REQUIRED = define(
            "nop.err.credential.owner-required",
            "scope=user 时 ownerId 必填（普通用户缺省为本人，管理员代建须显式指定）",
            ARG_CREDENTIAL_ID);

    ErrorCode ERR_CREDENTIAL_OWNER_NOT_ALLOWED = define(
            "nop.err.credential.owner-not-allowed",
            "scope=system 时 ownerId 必须为空", ARG_CREDENTIAL_ID);

    ErrorCode ERR_CREDENTIAL_OWNERSHIP_IMMUTABLE = define(
            "nop.err.credential.ownership-immutable",
            "凭证归属不可变更（scope/ownerId 创建后固定；需要换人=删除旧凭证后新建）",
            ARG_CREDENTIAL_ID, ARG_SCOPE, ARG_OWNER_ID);

    // ==================== W11 Part B RBAC 细粒度授权（凭证级 use 授权） ====================

    String ARG_ROLE_IDS = "roleIds";

    ErrorCode ERR_CREDENTIAL_ROLE_NOT_GRANTED = define(
            "nop.err.credential.role-not-granted",
            "凭证已配置取用授权（RBAC 收紧），当前用户角色未命中授权角色集（admin 不自动豁免；无用户上下文的服务端调用不受收紧约束）",
            ARG_CREDENTIAL_ID, ARG_ROLE_IDS);

    ErrorCode ERR_CREDENTIAL_AUTH_NOT_SYSTEM_SCOPE = define(
            "nop.err.credential.auth-not-system-scope",
            "凭证级取用授权仅适用于 system 级凭证（user 级不叠加角色授权，明文出口 owner 唯一）",
            ARG_CREDENTIAL_ID, ARG_SCOPE, ARG_OWNER_ID);

    ErrorCode ERR_CREDENTIAL_ROLE_ID_REQUIRED = define(
            "nop.err.credential.role-id-required",
            "授权/撤销操作要求 roleId 非空（字符串软引用角色，不做跨模块存在性校验）",
            ARG_CREDENTIAL_ID);
}
