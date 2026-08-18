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

    // ==================== W10 default-bean 守卫（key-provider 门控） ====================

    /**
     * key-provider 未配置（缺省 local）：守卫零触发，一期行为不变。
     */
    @Test
    public void guardInertWhenKeyProviderUnset() {
        DefaultCredentialKeyProvider p = newProvider(Collections.singletonList("keyA:pass-a"));
        p.init();
        assertEquals("keyA", p.getActiveKeyId());
    }

    /**
     * key-provider=local 显式配置：守卫零触发。
     */
    @Test
    public void guardInertWhenKeyProviderLocal() {
        DefaultCredentialKeyProvider p = newProvider(Collections.singletonList("keyA:pass-a"));
        p.setKeyProvider("local");
        p.init();
        assertEquals("keyA", p.getActiveKeyId());
    }

    /**
     * key-provider=vault 而实现模块未部署（default bean 兜底到此）：init 抛
     * ERR_CREDENTIAL_KEY_PROVIDER_MODULE_MISSING（fail-closed，堵死假安全回退）。
     * master-keys 为空（该分支的部署形态：KMS 配置已切换、本地材料已清空）。
     */
    @Test
    public void initThrowsWhenKeyProviderPointsToMissingKmsModule() {
        DefaultCredentialKeyProvider p = newProvider(Collections.emptyList());
        p.setKeyProvider("vault");
        NopException ex = assertThrows(NopException.class, p::init);
        assertEquals("nop.err.credential.key-provider-module-missing", ex.getErrorCode());

        DefaultCredentialKeyProvider p2 = newProvider(null);
        p2.setKeyProvider("vault");
        NopException ex2 = assertThrows(NopException.class, p2::init);
        assertEquals("nop.err.credential.key-provider-module-missing", ex2.getErrorCode());
    }

    /**
     * key-provider=vault 且 master-keys 残留：init 抛 ERR_CREDENTIAL_MASTER_KEYS_RESIDUAL
     * （密钥来源混合拒绝；该检查置于守卫使模块缺失场景下也可达）。
     */
    @Test
    public void initThrowsWhenKmsActiveAndMasterKeysResidual() {
        DefaultCredentialKeyProvider p = newProvider(Arrays.asList("keyA:pass-a", "keyB:pass-b"));
        p.setKeyProvider("vault");
        NopException ex = assertThrows(NopException.class, p::init);
        assertEquals("nop.err.credential.master-keys-residual", ex.getErrorCode());
    }

    /**
     * key-provider 指向其他 KMS 标识（如 aws）而模块未部署：同样被守卫拦截
     * （守卫对一切非 local 取值 fail-closed，不限于 vault）。
     */
    @Test
    public void initThrowsForArbitraryNonLocalKeyProvider() {
        DefaultCredentialKeyProvider p = newProvider(Collections.emptyList());
        p.setKeyProvider("aws");
        NopException ex = assertThrows(NopException.class, p::init);
        assertEquals("nop.err.credential.key-provider-module-missing", ex.getErrorCode());
    }

    // ==================== D3-03：key-provider 取值 trim + 大小写归一 ====================

    /**
     * D3-03（A1-audit successor，2026-08-17）：带空白/大小写噪声的取值归一后再比较——
     * {@code " Vault "} 归一为 vault → 准确命中 module-missing（而非被当作未知 local 变体放行）。
     */
    @Test
    public void keyProviderValueTrimmedAndCaseNormalizedBeforeGuard() {
        DefaultCredentialKeyProvider p = newProvider(Collections.emptyList());
        p.setKeyProvider(" Vault ");
        NopException ex = assertThrows(NopException.class, p::init);
        assertEquals("nop.err.credential.key-provider-module-missing", ex.getErrorCode(),
                "' Vault ' must normalize to vault and hit the module-missing guard (D3-03)");

        DefaultCredentialKeyProvider p2 = newProvider(Collections.emptyList());
        p2.setKeyProvider("VaUlT");
        assertEquals("nop.err.credential.key-provider-module-missing",
                assertThrows(NopException.class, p2::init).getErrorCode());
    }

    /**
     * D3-03：{@code "LOCAL"} 大写归一后等同 local——守卫零触发（错误归因准确化的另一面：
     * 配置噪声不产生假 KMS 告警，也不放行真 KMS 指向）。
     */
    @Test
    public void keyProviderLocalInAnyCaseOrPaddingIsInert() {
        DefaultCredentialKeyProvider p = newProvider(Collections.singletonList("keyA:pass-a"));
        p.setKeyProvider("  LOCAL ");
        p.init();
        assertEquals("keyA", p.getActiveKeyId(), "'  LOCAL ' must normalize to local and keep the guard inert (D3-03)");
    }

    // ==================== D5-06：passphrase isBlank 收紧 ====================

    /**
     * D5-06（A1-audit successor，2026-08-17）：纯空白 passphrase（空格/制表符）拒绝——
     * 空白材料构造的 cipher 形同弱密钥（原实现仅拦截"冒号结尾"的空串形态）。
     */
    @Test
    public void initThrowsForWhitespaceOnlyPassphrase() {
        DefaultCredentialKeyProvider spaces = newProvider(Collections.singletonList("keyA:   "));
        NopException ex = assertThrows(NopException.class, spaces::init);
        assertEquals("nop.err.credential.master-key-entry-invalid", ex.getErrorCode(),
                "whitespace-only passphrase must be rejected (D5-06)");

        DefaultCredentialKeyProvider tabs = newProvider(Collections.singletonList("keyA:\t \t"));
        assertEquals("nop.err.credential.master-key-entry-invalid",
                assertThrows(NopException.class, tabs::init).getErrorCode());
    }

    /**
     * D5-06 边界：含空格的<b>非空白</b> passphrase 保持合法（isBlank 只拒纯空白，
     * 不收紧合法字符空间）。
     */
    @Test
    public void passphraseContainingSpacesRemainsLegal() {
        DefaultCredentialKeyProvider p = assertDoesNotThrow(
                () -> newProvider(Collections.singletonList("keyA:pass phrase with spaces")));
        p.init();
        ITextCipher cipher = p.getKey("keyA");
        assertEquals("v", cipher.decrypt(cipher.encrypt("v")),
                "non-blank passphrase with spaces must still construct a working cipher (D5-06)");
    }
}
