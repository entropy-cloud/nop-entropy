/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.api.crypto;

import io.nop.commons.crypto.ITextCipher;

import java.util.Set;

/**
 * 凭证主密钥提供者 SPI。管理多个主密钥（{@code keyId -> ITextCipher}），
 * 支持密钥轮换：加密使用 active key，解密按 {@code cv1:} 中的 keyId 路由到对应密钥。
 *
 * <p>实现类位于 {@code nop-credential-service}（{@code DefaultCredentialKeyProvider}），
 * 主密钥来源为 Nop 配置项 {@code nop.credential.master-keys}（每条 {@code keyId:passphrase}）。
 */
public interface ICredentialKeyProvider {

    /**
     * keyId 允许的字符集约束（D3-04 单源，A1-audit successor 2026-08-17）：
     * {@code [A-Za-z0-9_-]+}。这是 {@code cv1:{keyId}:v1:...} 密文按冒号 split
     * 无歧义的前提（keyId 不含冒号），api 模块为本约束的唯一权威定义处——
     * service 模块的 {@code CredentialCipher} 与 KMS 各实现（如 Vault）统一引用本常量，
     * 消除多副本硬编码漂移。
     */
    String KEY_ID_PATTERN = "[A-Za-z0-9_-]+";

    /**
     * 当前用于加密的主密钥 keyId。新写入的凭证密文以该 keyId 作为前缀。
     * 密钥轮换时切换 active key，旧密文仍按自身 keyId 解密。
     *
     * @return active key 的 keyId，非 null
     */
    String getActiveKeyId();

    /**
     * 按 keyId 取对应的单钥加密器（基于 {@code AESTextCipher}，{@code v1:} 密文）。
     *
     * @param keyId 主密钥标识，必须匹配 {@code [A-Za-z0-9_-]+} 且已在提供者中注册
     * @return 对应的 {@link ITextCipher}，非 null
     * @throws io.nop.api.core.exceptions.NopException 当 keyId 未注册时抛出（fail-closed）
     */
    ITextCipher getKey(String keyId);

    /**
     * 已注册的全部主密钥 keyId 集合。轮换期新旧 key 同时存在。
     *
     * @return 不可变 keyId 集合，非 null
     */
    Set<String> getKeyIds();
}
