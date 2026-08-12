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
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.credential.api.crypto.ICredentialKeyProvider;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CredentialCipher} 的聚焦单元测试。覆盖 cv1 格式、round-trip、多密钥轮换解密、
 * fail-closed（篡改/未知 keyId/格式错误）、以及 keyId 校验。
 *
 * <p>使用一个内存版 {@link ICredentialKeyProvider} 实现，避免依赖 Nop IoC 容器与配置加载，
 * 纯粹验证密码学层的格式与语义。
 */
public class TestCredentialCipher {

    /**
     * 构造一个内存版 key provider，持有给定 {@code keyId:passphrase} 条目。
     * active key 默认取首项，可通过参数覆盖。
     */
    private static ICredentialKeyProvider provider(String activeKeyId, String... keys) {
        Map<String, ITextCipher> map = new LinkedHashMap<>();
        for (String entry : keys) {
            int idx = entry.indexOf(':');
            String keyId = entry.substring(0, idx);
            String pass = entry.substring(idx + 1);
            map.put(keyId, new AESTextCipher().encKey(pass));
        }
        final String active = activeKeyId != null ? activeKeyId : map.keySet().iterator().next();
        return new ICredentialKeyProvider() {
            @Override
            public String getActiveKeyId() {
                return active;
            }

            @Override
            public ITextCipher getKey(String keyId) {
                ITextCipher c = map.get(keyId);
                if (c == null) {
                    throw new NopException(CredentialErrors.ERR_CREDENTIAL_UNKNOWN_KEY_ID)
                            .param(CredentialErrors.ARG_KEY_ID, keyId)
                            .param(CredentialErrors.ARG_AVAILABLE_KEY_IDS, map.keySet());
                }
                return c;
            }

            @Override
            public Set<String> getKeyIds() {
                return Collections.unmodifiableSet(map.keySet());
            }
        };
    }

    private static CredentialCipher cipherWith(ICredentialKeyProvider provider) {
        CredentialCipher cipher = new CredentialCipher();
        cipher.setKeyProvider(provider);
        return cipher;
    }

    // ==================== 格式与 round-trip ====================

    /**
     * encrypt 输出匹配 {@code ^cv1:[A-Za-z0-9_-]+:v1:[A-Za-z0-9+/=]+$}。
     * 证明 4 段格式：cv1、keyId、v1 标记、base64 payload。
     */
    @Test
    public void encryptOutputMatchesCv1Format() {
        ICredentialKeyProvider provider = provider(null, "keyA:passphrase-a");
        CredentialCipher cipher = cipherWith(provider);

        String ct = cipher.encrypt("{\"apiKey\":\"sk-test123\"}", "keyA");

        assertNotNull(ct);
        assertTrue(ct.startsWith("cv1:keyA:v1:"),
                "cv1 output must start with cv1:{keyId}:v1:, got: " + ct);
        assertTrue(ct.matches("^cv1:[A-Za-z0-9_-]+:v1:[A-Za-z0-9+/=]+$"),
                "cv1 output must match 4-segment format, got: " + ct);
    }

    /**
     * round-trip：decrypt(encrypt(plain, keyId)) == plain。
     * 使用 JSON 字符串明文，模拟凭证字段序列化。
     */
    @Test
    public void roundTripWithExplicitKeyId() {
        ICredentialKeyProvider provider = provider(null, "keyA:passphrase-a");
        CredentialCipher cipher = cipherWith(provider);

        String plain = "{\"apiKey\":\"sk-test123\",\"orgId\":\"org1\"}";
        String ct = cipher.encrypt(plain, "keyA");
        String back = cipher.decrypt(ct);

        assertEquals(plain, back);
        assertNotEquals(plain, ct, "ciphertext must differ from plaintext");
    }

    /**
     * 便捷方法 encrypt(plain) 使用 active key。
     */
    @Test
    public void roundTripWithActiveKey() {
        ICredentialKeyProvider provider = provider(null, "activeKey:p-a");
        CredentialCipher cipher = cipherWith(provider);

        String plain = "{\"apiKey\":\"sk-test123\"}";
        String ct = cipher.encrypt(plain);
        assertTrue(ct.startsWith("cv1:activeKey:v1:"),
                "encrypt(plain) must use active key id");

        assertEquals(plain, cipher.decrypt(ct));
    }

    /**
     * 每次加密使用随机 IV，同一明文两次加密产生不同密文（但都能解回原明文）。
     */
    @Test
    public void randomIvProducesDistinctCiphertexts() {
        ICredentialKeyProvider provider = provider(null, "keyA:p-a");
        CredentialCipher cipher = cipherWith(provider);

        String plain = "{\"apiKey\":\"sk-test123\"}";
        String ct1 = cipher.encrypt(plain, "keyA");
        String ct2 = cipher.encrypt(plain, "keyA");

        assertNotEquals(ct1, ct2, "random IV must yield distinct ciphertexts");
        assertEquals(plain, cipher.decrypt(ct1));
        assertEquals(plain, cipher.decrypt(ct2));
    }

    // ==================== 多密钥轮换 ====================

    /**
     * 多密钥解密：用 keyA 加密，切换 active 到 keyB，仍可用 keyA 解密。
     * 证明 cv1 中的 keyId 路由正确，轮换兼容。
     */
    @Test
    public void multiKeyDecryptAfterRotation() {
        // 阶段1：active=keyA
        ICredentialKeyProvider providerA = provider("keyA", "keyA:pass-a", "keyB:pass-b");
        CredentialCipher cipherA = cipherWith(providerA);
        String plain = "{\"apiKey\":\"sk-xxx\"}";
        String ctWithKeyA = cipherA.encrypt(plain, "keyA");
        assertTrue(ctWithKeyA.startsWith("cv1:keyA:v1:"));

        // 阶段2：active 切换到 keyB，但 keyA 仍在 keyIds 中
        ICredentialKeyProvider providerB = provider("keyB", "keyA:pass-a", "keyB:pass-b");
        CredentialCipher cipherB = cipherWith(providerB);

        // 旧密文 cv1:keyA:v1:... 仍可用 keyA 解密
        assertEquals(plain, cipherB.decrypt(ctWithKeyA));
        // 新密文用 keyB
        String ctWithKeyB = cipherB.encrypt(plain);
        assertTrue(ctWithKeyB.startsWith("cv1:keyB:v1:"));
        assertEquals(plain, cipherB.decrypt(ctWithKeyB));
    }

    /**
     * 跨 provider 解密：provider 只含 keyA 时加密，换到只含 keyB 的 provider 解密应失败。
     */
    @Test
    public void decryptFailsWhenKeyIdNotInProvider() {
        ICredentialKeyProvider providerA = provider(null, "keyA:pass-a");
        CredentialCipher cipherA = cipherWith(providerA);
        String ct = cipherA.encrypt("{}", "keyA");

        ICredentialKeyProvider providerB = provider(null, "keyB:pass-b");
        CredentialCipher cipherB = cipherWith(providerB);

        NopException ex = assertThrows(NopException.class, () -> cipherB.decrypt(ct));
        assertTrue(ex.getMessage().contains("keyA"),
                "error message should mention the unknown keyId");
    }

    // ==================== Fail-closed（格式错误与篡改） ====================

    @Test
    public void decryptRejectsMissingCv1Prefix() {
        CredentialCipher cipher = cipherWith(provider(null, "keyA:p-a"));
        NopException ex = assertThrows(NopException.class,
                () -> cipher.decrypt("v1:abc123=="));
        assertNotNull(ex.getMessage());
    }

    @Test
    public void decryptRejectsNullInput() {
        CredentialCipher cipher = cipherWith(provider(null, "keyA:p-a"));
        assertThrows(NopException.class, () -> cipher.decrypt(null));
    }

    @Test
    public void decryptRejectsMissingKeyId() {
        CredentialCipher cipher = cipherWith(provider(null, "keyA:p-a"));
        // cv1: 后直接是 v1，没有 keyId 段
        NopException ex = assertThrows(NopException.class,
                () -> cipher.decrypt("cv1:v1:abc123=="));
        assertNotNull(ex.getMessage());
    }

    @Test
    public void decryptRejectsEmptyV1Payload() {
        CredentialCipher cipher = cipherWith(provider(null, "keyA:p-a"));
        NopException ex = assertThrows(NopException.class,
                () -> cipher.decrypt("cv1:keyA:"));
        assertNotNull(ex.getMessage());
    }

    @Test
    public void decryptRejectsUnknownKeyId() {
        CredentialCipher cipher = cipherWith(provider(null, "keyA:p-a"));
        // keyZ 不在 provider 中
        NopException ex = assertThrows(NopException.class,
                () -> cipher.decrypt("cv1:keyZ:v1:abc123=="));
        assertTrue(ex.getMessage().contains("keyZ"));
    }

    @Test
    public void decryptRejectsTamperedCiphertext() {
        ICredentialKeyProvider provider = provider(null, "keyA:p-a");
        CredentialCipher cipher = cipherWith(provider);

        String ct = cipher.encrypt("{\"a\":1}", "keyA");
        // 篡改 base64 payload 的最后一个字符
        char last = ct.charAt(ct.length() - 1);
        char tampered = last == 'A' ? 'B' : 'A';
        String tamperedCt = ct.substring(0, ct.length() - 1) + tampered;

        // GCM tag 校验失败应抛异常，不返回 null 或乱码
        assertThrows(Exception.class, () -> cipher.decrypt(tamperedCt));
    }

    @Test
    public void decryptNeverReturnsNullForValidFormat() {
        ICredentialKeyProvider provider = provider(null, "keyA:p-a");
        CredentialCipher cipher = cipherWith(provider);

        String ct = cipher.encrypt("hello", "keyA");
        String result = assertDoesNotThrow(() -> cipher.decrypt(ct));
        assertNotNull(result);
        assertEquals("hello", result);
    }

    // ==================== keyId 校验 ====================

    /**
     * encrypt 拒绝非法 keyId（含冒号、空格等）。
     */
    @Test
    public void encryptRejectsInvalidKeyId() {
        ICredentialKeyProvider provider = provider(null, "keyA:p-a");
        CredentialCipher cipher = cipherWith(provider);

        // keyId 含冒号（非法）
        assertThrows(NopException.class, () -> cipher.encrypt("x", "my:key"));
        // keyId 含空格（非法）
        assertThrows(NopException.class, () -> cipher.encrypt("x", "key with space"));
        // keyId 为 null
        assertThrows(NopException.class, () -> cipher.encrypt("x", (String) null));
    }

    /**
     * 合法 keyId 字符集边界：字母、数字、下划线、连字符均接受。
     */
    @Test
    public void encryptAcceptsValidKeyIdCharset() {
        ICredentialKeyProvider provider = provider(null,
                "key_A-1:pass", "UPPER:pass", "lower123:pass");
        CredentialCipher cipher = cipherWith(provider);

        String plain = "v";
        for (String keyId : Arrays.asList("key_A-1", "UPPER", "lower123")) {
            String ct = cipher.encrypt(plain, keyId);
            assertTrue(ct.startsWith("cv1:" + keyId + ":v1:"));
            assertEquals(plain, cipher.decrypt(ct));
        }
    }

    // ==================== 接线验证（Wiring） ====================

    /**
     * 接线验证：encrypt 输出包含 active keyId，decrypt 成功要求 keyId 已注册。
     * 证明 CredentialCipher 在运行时确实调用了 ICredentialKeyProvider。
     */
    @Test
    public void cipherCallsKeyProviderAtRuntime() {
        ICredentialKeyProvider provider = provider("wiredKey", "wiredKey:pass-w");
        CredentialCipher cipher = cipherWith(provider);

        // encrypt 输出含 activeKeyId（证明调用了 getActiveKeyId/getKey）
        String ct = cipher.encrypt("{\"k\":\"v\"}");
        assertTrue(ct.startsWith("cv1:wiredKey:v1:"),
                "encrypt output must contain the active keyId from provider");

        // decrypt 要求 keyId 在 getKeyIds 中注册（证明调用了 getKeyIds/getKey）
        assertEquals("{\"k\":\"v\"}", cipher.decrypt(ct));
        assertTrue(provider.getKeyIds().contains("wiredKey"));
    }
}
