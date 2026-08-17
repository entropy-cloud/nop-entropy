/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.crypto;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.crypto.ITextCipher;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import jakarta.inject.Inject;

import java.util.regex.Pattern;

/**
 * 凭证密文格式 {@code cv1:{keyId}:{v1密文}} 的编解码器。
 *
 * <p>加密时委托 {@link ICredentialKeyProvider#getKey(String)} 取得对应 keyId 的
 * {@link ITextCipher}（{@code AESTextCipher}），对其输出（已是 {@code v1:{base64data}}）
 * 再包装一层 {@code cv1:{keyId}:} 前缀。解密时反向：解析 keyId，路由到对应密钥，
 * 把内层 {@code v1:} 密文原样交给 {@code AESTextCipher.decrypt()} 处理。
 *
 * <p>本类只处理 {@code String}。调用层（W2 {@code CredentialProviderImpl}）负责
 * JSON 序列化/反序列化。
 */
public class CredentialCipher {

    /**
     * cv1 格式前缀。明文输出形如 {@code cv1:{keyId}:{v1密文}}。
     * base64 字母表与 keyId 约束字符集都不含冒号，因此按冒号 split 无歧义。
     */
    public static final String CV1_MARKER = "cv1:";

    /**
     * 内层 {@code AESTextCipher} 密文的版本前缀（D5-05，A1-audit successor 2026-08-17）：
     * {@code decrypt} 强制内层载荷以 {@code v1:} 开头——cv1 包装的 legacy 裸载荷
     * （无版本前缀）无法落入弱路径，按密文格式错误 fail-closed 拒绝。
     */
    public static final String V1_MARKER = "v1:";

    /**
     * keyId 允许的字符集约束：{@code [A-Za-z0-9_-]+}。
     * 这是 {@code cv1:} 按冒号 split 无歧义的前提（keyId 不含冒号）。
     *
     * @deprecated D3-04 单源收敛：权威定义已下沉 api 模块
     *             {@link ICredentialKeyProvider#KEY_ID_PATTERN}，本常量为兼容别名。
     */
    @Deprecated
    public static final String CV1_KEY_ID_PATTERN = ICredentialKeyProvider.KEY_ID_PATTERN;

    private static final Pattern KEY_ID_PATTERN = Pattern.compile(ICredentialKeyProvider.KEY_ID_PATTERN);

    /**
     * D1-04（A1-audit successor，2026-08-17）：{@code ARG_CIPHERTEXT} 异常参数的截断上限——
     * 密文整串不进日志/错误响应，仅保留前缀（格式归因）+ 总长标注。
     */
    static final int CIPHERTEXT_PARAM_MAX_LEN = 16;

    /**
     * 截断密文用于异常 param（D1-04）：保留前 16 字符前缀 + {@code ...(len=N)} 总长标注，
     * 足以归因格式问题（前缀/结构），不泄露密文体。
     */
    static String truncateCiphertext(String cv1Text) {
        if (cv1Text == null) {
            return null;
        }
        if (cv1Text.length() <= CIPHERTEXT_PARAM_MAX_LEN) {
            return cv1Text + "(len=" + cv1Text.length() + ")";
        }
        return cv1Text.substring(0, CIPHERTEXT_PARAM_MAX_LEN) + "...(len=" + cv1Text.length() + ")";
    }

    /**
     * 主密钥提供者，由 Nop IoC 注入。字段须为 protected（非 private）以兼容 NopIoC 字段注入。
     */
    @Inject
    protected ICredentialKeyProvider keyProvider;

    public void setKeyProvider(ICredentialKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /**
     * 使用指定 keyId 加密明文。输出格式 {@code cv1:{keyId}:v1:{base64data}}。
     *
     * @param plainJson 明文（通常是凭证字段的 JSON 序列化字符串）
     * @param keyId     主密钥标识，必须已在 {@link ICredentialKeyProvider} 注册
     * @return cv1 格式密文
     * @throws NopException 当 keyId 未注册时抛出
     */
    public String encrypt(String plainJson, String keyId) {
        if (keyId == null || !KEY_ID_PATTERN.matcher(keyId).matches()) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_INVALID_KEY_ID)
                    .param(CredentialErrors.ARG_KEY_ID, keyId);
        }
        ITextCipher cipher = keyProvider.getKey(keyId);
        String v1Ciphertext = cipher.encrypt(plainJson);
        return CV1_MARKER + keyId + ":" + v1Ciphertext;
    }

    /**
     * 使用 active key 加密明文（便捷方法）。
     *
     * @param plainJson 明文
     * @return cv1 格式密文，keyId 取自 {@link ICredentialKeyProvider#getActiveKeyId()}
     */
    public String encrypt(String plainJson) {
        return encrypt(plainJson, keyProvider.getActiveKeyId());
    }

    /**
     * 解密 cv1 格式密文。解析 keyId 后路由到对应密钥，内层 {@code v1:} 密文原样交给
     * {@code AESTextCipher.decrypt()} 处理（保留 {@code v1:} 前缀以触发其格式分派）。
     *
     * @param cv1Text cv1 格式密文
     * @return 明文
     * @throws NopException 输入格式错误、keyId 未注册、或解密失败（篡改/截断）时抛出（fail-closed）
     */
    public String decrypt(String cv1Text) {
        if (cv1Text == null || !cv1Text.startsWith(CV1_MARKER)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_INVALID_CIPHERTEXT_FORMAT)
                    .param(CredentialErrors.ARG_CIPHERTEXT, truncateCiphertext(cv1Text));
        }

        // 去掉 "cv1:" 前缀，剩余形如 {keyId}:v1:{base64data}
        String rest = cv1Text.substring(CV1_MARKER.length());

        int colonIdx = rest.indexOf(':');
        if (colonIdx <= 0) {
            // 缺少 keyId 或分隔冒号
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_INVALID_CIPHERTEXT_FORMAT)
                    .param(CredentialErrors.ARG_CIPHERTEXT, truncateCiphertext(cv1Text));
        }

        String keyId = rest.substring(0, colonIdx);
        String v1Payload = rest.substring(colonIdx + 1);

        if (!KEY_ID_PATTERN.matcher(keyId).matches() || v1Payload.isEmpty()) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_INVALID_CIPHERTEXT_FORMAT)
                    .param(CredentialErrors.ARG_CIPHERTEXT, truncateCiphertext(cv1Text));
        }

        // D5-05：强制内层 v1: 版本前缀——cv1 包装的 legacy 裸载荷（无版本标记）不可解，
        // 按 INVALID_CIPHERTEXT_FORMAT fail-closed（AESTextCipher.decrypt 依赖 v1: 前缀分派）
        if (!v1Payload.startsWith(V1_MARKER)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_INVALID_CIPHERTEXT_FORMAT)
                    .param(CredentialErrors.ARG_CIPHERTEXT, truncateCiphertext(cv1Text));
        }

        if (!keyProvider.getKeyIds().contains(keyId)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_UNKNOWN_KEY_ID)
                    .param(CredentialErrors.ARG_KEY_ID, keyId)
                    .param(CredentialErrors.ARG_AVAILABLE_KEY_IDS, keyProvider.getKeyIds());
        }

        // v1Payload 保留 "v1:" 前缀，AESTextCipher.decrypt() 依赖它进行格式分派
        ITextCipher cipher = keyProvider.getKey(keyId);
        return cipher.decrypt(v1Payload);
    }
}
