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
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultCredentialKeyProvider} 的聚焦单元测试。覆盖主密钥加载、active key 选择、
 * keyId 校验（fail-closed 启动失败）。
 */
public class TestDefaultCredentialKeyProvider {

    private static DefaultCredentialKeyProvider newProvider(java.util.List<String> masterKeys) {
        DefaultCredentialKeyProvider p = new DefaultCredentialKeyProvider();
        p.setMasterKeys(masterKeys);
        return p;
    }

    @Test
    public void loadsSingleKeyAndUsesItAsActive() {
        DefaultCredentialKeyProvider p = assertDoesNotThrow(
                () -> newProvider(Collections.singletonList("keyA:pass-a")));
        p.init();

        assertEquals("keyA", p.getActiveKeyId());
        assertTrue(p.getKeyIds().contains("keyA"));
        ITextCipher cipher = p.getKey("keyA");
        assertNotNull(cipher);
    }

    @Test
    public void loadsMultipleKeysAndPicksExplicitActiveKey() {
        DefaultCredentialKeyProvider p = newProvider(Arrays.asList("keyA:pass-a", "keyB:pass-b"));
        p.setActiveKeyId("keyB");
        p.init();

        assertEquals("keyB", p.getActiveKeyId());
        assertTrue(p.getKeyIds().contains("keyA"));
        assertTrue(p.getKeyIds().contains("keyB"));
    }

    @Test
    public void defaultsActiveKeyToFirstEntry() {
        DefaultCredentialKeyProvider p = assertDoesNotThrow(
                () -> newProvider(Arrays.asList("first:pass-f", "second:pass-s")));
        p.init();
        assertEquals("first", p.getActiveKeyId());
    }

    /**
     * round-trip via DefaultCredentialKeyProvider + AESTextCipher.
     */
    @Test
    public void cipherRoundTripThroughProvider() {
        DefaultCredentialKeyProvider p = newProvider(Collections.singletonList("keyA:pass-a"));
        p.init();

        ITextCipher cipher = p.getKey("keyA");
        String ct = cipher.encrypt("secret");
        assertEquals("secret", cipher.decrypt(ct));
    }

    // ==================== Fail-closed：启动期 keyId 校验 ====================

    @Test
    public void initThrowsWhenNoMasterKeyConfigured() {
        DefaultCredentialKeyProvider p = newProvider(Collections.emptyList());
        assertThrows(NopException.class, p::init);

        DefaultCredentialKeyProvider p2 = newProvider(null);
        assertThrows(NopException.class, p2::init);
    }

    @Test
    public void initThrowsForInvalidKeyIdWithSpace() {
        // keyId 含空格（在冒号之前），不符合 [A-Za-z0-9_-]+ 约束
        DefaultCredentialKeyProvider p = newProvider(Collections.singletonList("key with space:pass"));
        assertThrows(NopException.class, p::init);
    }

    @Test
    public void initThrowsForInvalidKeyIdWithDot() {
        // keyId 含点号（非法字符）
        DefaultCredentialKeyProvider p = newProvider(Collections.singletonList("key.dot:pass"));
        assertThrows(NopException.class, p::init);
    }

    @Test
    public void passphraseMayContainColons() {
        // keyId="my"，passphrase="key:pass"（passphrase 允许含冒号，因为按首个冒号拆分）
        DefaultCredentialKeyProvider p = assertDoesNotThrow(
                () -> newProvider(Collections.singletonList("my:key:pass")));
        p.init();
        assertEquals("my", p.getActiveKeyId());
        // round-trip 证明该 passphrase 构造的 cipher 工作正常
        ITextCipher cipher = p.getKey("my");
        assertEquals("v", cipher.decrypt(cipher.encrypt("v")));
    }

    @Test
    public void initThrowsForEntryWithoutPassphrase() {
        DefaultCredentialKeyProvider p = newProvider(Collections.singletonList("keyA"));
        assertThrows(NopException.class, p::init);

        DefaultCredentialKeyProvider p2 = newProvider(Collections.singletonList("keyA:"));
        assertThrows(NopException.class, p2::init);
    }

    @Test
    public void initThrowsForDuplicateKeyId() {
        DefaultCredentialKeyProvider p = newProvider(Arrays.asList("keyA:pass-a", "keyA:pass-b"));
        assertThrows(NopException.class, p::init);
    }

    @Test
    public void initThrowsForUnknownExplicitActiveKeyId() {
        DefaultCredentialKeyProvider p = newProvider(Collections.singletonList("keyA:pass-a"));
        p.setActiveKeyId("nonExistent");
        assertThrows(NopException.class, p::init);
    }

    @Test
    public void getKeyThrowsForUnknownKeyIdAfterInit() {
        DefaultCredentialKeyProvider p = newProvider(Collections.singletonList("keyA:pass-a"));
        p.init();
        assertThrows(NopException.class, () -> p.getKey("unknown"));
    }

    /**
     * 合法 keyId 字符集边界：字母/数字/下划线/连字符均通过校验。
     */
    @Test
    public void acceptsValidKeyIdCharset() {
        DefaultCredentialKeyProvider p = assertDoesNotThrow(
                () -> newProvider(Arrays.asList("key_A-1:p1", "AbC09-X_y: p2")));
        p.init();
        assertEquals(2, p.getKeyIds().size());
    }

    /**
     * 集成：DefaultCredentialKeyProvider + CredentialCipher 端到端 round-trip。
     */
    @Test
    public void endToEndWithCredentialCipher() {
        DefaultCredentialKeyProvider provider = newProvider(
                Arrays.asList("keyA:pass-a", "keyB:pass-b"));
        provider.setActiveKeyId("keyB");
        provider.init();

        CredentialCipher cipher = new CredentialCipher();
        cipher.setKeyProvider(provider);

        String plain = "{\"apiKey\":\"sk-test123\"}";
        String ct = cipher.encrypt(plain);
        assertTrue(ct.startsWith("cv1:keyB:v1:"));
        assertEquals(plain, cipher.decrypt(ct));
    }
}
