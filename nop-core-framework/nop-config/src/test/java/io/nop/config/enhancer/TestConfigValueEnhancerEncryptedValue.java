/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.enhancer;

import io.nop.api.core.config.IConfigValue;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.config.ConfigConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 334 Phase 3 接线验证：{@link DefaultConfigValueEnhancer} 在启动时解密版本化（v1）
 * 配置值的端到端路径。{@code ConfigStarter.newValueEnhancer} 内部使用 {@code new AESTextCipher()}，
 * 全局改造后默认即 v1 格式，因此 enhancer 自动获得版本化解密能力。
 */
public class TestConfigValueEnhancerEncryptedValue {

    @Test
    public void testEnhanceDecryptsVersionedConfigValue() {
        // 模拟 ConfigStarter 的默认装配：new AESTextCipher()（v1 模式）
        AESTextCipher cipher = new AESTextCipher();
        DefaultConfigValueEnhancer enhancer = new DefaultConfigValueEnhancer(cipher);

        // 用同一 cipher 加密得到版本化密文
        String secret = "db-password-secret";
        String cipherText = cipher.encrypt(secret);
        assertTrue(cipherText.startsWith(AESTextCipher.V1_MARKER),
                "config cipher must produce v1 ciphertext");

        // enhancer 必须能解密 @sec:v1:... 形式的配置值
        String rawValue = ConfigConstants.CFG_SEC_PREFIX + cipherText;
        IConfigValue<String> enhanced = enhancer.enhance(rawValue, String.class);

        assertEquals(secret, enhanced.get(),
                "config enhancer must decrypt a versioned (@sec:v1:) value at startup");
    }

    /**
     * Legacy 兼容：以 legacy 格式加密的配置值（无版本标记）仍能被 enhancer 解密。
     */
    @Test
    public void testEnhanceDecryptsLegacyConfigValue() {
        AESTextCipher legacyCipher = new AESTextCipher().versionedFormat(false);
        DefaultConfigValueEnhancer enhancer = new DefaultConfigValueEnhancer(new AESTextCipher());

        String secret = "legacy-config-secret";
        String legacyCipherText = legacyCipher.encrypt(secret);
        assertTrue(!legacyCipherText.startsWith(AESTextCipher.V1_MARKER));

        String rawValue = ConfigConstants.CFG_SEC_PREFIX + legacyCipherText;
        IConfigValue<String> enhanced = enhancer.enhance(rawValue, String.class);
        assertEquals(secret, enhanced.get(),
                "config enhancer must still read legacy-format (@sec:) values");
    }
}
