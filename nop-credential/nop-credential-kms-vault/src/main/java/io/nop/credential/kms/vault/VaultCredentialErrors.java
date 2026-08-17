/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.kms.vault;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * nop-credential-kms-vault 模块的错误码定义。
 *
 * <p>本模块为独立可选模块（仅依赖 nop-credential-api + nop-http-api，不依赖
 * nop-credential-service），因此错误码独立于 service 模块的 {@code CredentialErrors}
 * 定义，但错误码字符串延续 {@code nop.err.credential.*} 前缀命名空间。
 *
 * <p>全部失败路径（托管端不可达/认证失败/材料缺失/配置矛盾）均为启动期 fail-closed
 * 抛出 {@link io.nop.api.core.exceptions.NopException}，不存在本地降级或静默跳过。
 */
public interface VaultCredentialErrors {

    String ARG_KEY_ID = "keyId";
    String ARG_AVAILABLE_KEY_IDS = "availableKeyIds";
    String ARG_ADDRESS = "address";
    String ARG_PATH = "path";
    String ARG_STATUS = "status";
    String ARG_ERROR = "error";
    String ARG_KEY_MAPPING = "keyMapping";
    String ARG_MIGRATION_KEY = "migrationKey";
    String ARG_KEY_PROVIDER = "keyProvider";

    ErrorCode ERR_CREDENTIAL_VAULT_CONFIG_MISSING = define(
            "nop.err.credential.vault.config-missing",
            "Vault KMS 配置缺失（nop.credential.vault.address/token 为必填）", ARG_ERROR);

    ErrorCode ERR_CREDENTIAL_VAULT_NO_KEY_CONFIGURED = define(
            "nop.err.credential.vault.no-key-configured",
            "未配置任何 Vault 密钥映射（nop.credential.vault.keys 为空，条目格式 keyId:secret路径）");

    ErrorCode ERR_CREDENTIAL_VAULT_KEY_MAPPING_INVALID = define(
            "nop.err.credential.vault.key-mapping-invalid",
            "Vault 密钥映射条目格式非法（期望 keyId:secret路径，keyId 须匹配 [A-Za-z0-9_-]+ 且不得重复）",
            ARG_KEY_MAPPING);

    ErrorCode ERR_CREDENTIAL_VAULT_UNREACHABLE = define(
            "nop.err.credential.vault.unreachable",
            "Vault 托管端不可达（启动期 fail-closed，无本地降级）", ARG_ADDRESS, ARG_ERROR);

    ErrorCode ERR_CREDENTIAL_VAULT_AUTH_FAILED = define(
            "nop.err.credential.vault.auth-failed",
            "Vault 认证失败（token 无效或无权限，HTTP 401/403）", ARG_PATH);

    ErrorCode ERR_CREDENTIAL_VAULT_KEY_NOT_FOUND = define(
            "nop.err.credential.vault.key-not-found",
            "配置的 keyId 在 Vault 托管端缺失（HTTP 404）", ARG_KEY_ID, ARG_PATH);

    ErrorCode ERR_CREDENTIAL_VAULT_READ_FAILED = define(
            "nop.err.credential.vault.read-failed",
            "Vault 密文材料读取失败（HTTP 非 2xx）", ARG_PATH, ARG_STATUS);

    ErrorCode ERR_CREDENTIAL_VAULT_MATERIAL_INVALID = define(
            "nop.err.credential.vault.material-invalid",
            "Vault 密钥材料非法（响应缺少 data.data.passphrase、为空或非字符串）", ARG_KEY_ID, ARG_PATH);

    ErrorCode ERR_CREDENTIAL_VAULT_MASTER_KEYS_RESIDUAL = define(
            "nop.err.credential.vault.master-keys-residual",
            "密钥来源混合：key-provider 已激活 Vault 但 nop.credential.master-keys 非空（本地材料只允许存在于迁移残余列表，须清空）");

    ErrorCode ERR_CREDENTIAL_VAULT_MIGRATION_KEY_INVALID = define(
            "nop.err.credential.vault.migration-key-invalid",
            "迁移残余列表条目格式非法（期望 keyId:passphrase）或与 Vault 密钥 keyId 冲突", ARG_MIGRATION_KEY);

    ErrorCode ERR_CREDENTIAL_VAULT_MIGRATION_KEY_ACTIVE = define(
            "nop.err.credential.vault.migration-key-active",
            "迁移残余列表禁止包含 active keyId（残余 key 只解不加密）", ARG_KEY_ID);

    ErrorCode ERR_CREDENTIAL_VAULT_UNKNOWN_ACTIVE_KEY = define(
            "nop.err.credential.vault.unknown-active-key",
            "显式配置的 active keyId 不在 Vault 密钥映射中（不得指向迁移残余 key）",
            ARG_KEY_ID, ARG_AVAILABLE_KEY_IDS);

    String ARG_SHARED_KEY_ID = "sharedKeyId";

    ErrorCode ERR_CREDENTIAL_VAULT_ACTIVE_KEY_CONFLICT = define(
            "nop.err.credential.vault.active-key-id-conflict",
            "nop.credential.vault.active-key-id 与共享 nop.credential.active-key-id 同时设置且不一致"
                    + "（fail-closed，消除 local→vault 迁移期静默改变 active key 的陷阱；请仅保留 vault 专用配置或保持两者一致）",
            ARG_KEY_ID, ARG_SHARED_KEY_ID);

    ErrorCode ERR_CREDENTIAL_VAULT_UNKNOWN_KEY_ID = define(
            "nop.err.credential.vault.unknown-key-id",
            "keyId 未在 Vault 主密钥提供者中注册（运行期语义与一期 fail-closed 一致）",
            ARG_KEY_ID, ARG_AVAILABLE_KEY_IDS);
}
