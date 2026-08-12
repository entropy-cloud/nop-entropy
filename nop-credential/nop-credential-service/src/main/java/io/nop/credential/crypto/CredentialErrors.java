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
}
