package io.nop.commons.crypto.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-C3-1 回归测试：encKey 为空时派生密钥必须触发一次性 WARN（观测 flag），
 * 配置了 encKey 的实例永远不触发。覆盖 v1 加密、legacy 解密两条派生路径。
 */
public class TestAESTextCipherEmptyKeyWarn {

    @Test
    public void testV1EncryptWarnsOnceOnEmptyKey() {
        AESTextCipher cipher = new AESTextCipher();
        assertFalse(cipher.wasEmptyKeyWarned());
        String cipherText = cipher.encrypt("secret");
        assertTrue(cipherText.startsWith(AESTextCipher.V1_MARKER));
        assertTrue(cipher.wasEmptyKeyWarned());

        // 只告警一次：flag 保持 true，且解密（v1 派生路径复用缓存/已告警）不再改变状态
        cipher.decrypt(cipherText);
        assertTrue(cipher.wasEmptyKeyWarned());
    }

    @Test
    public void testLegacyDecryptPathWarnsOnEmptyKey() {
        // legacy 加密用实例 A 产出确定性密文；新鲜实例 B 解密时走 legacy 派生路径
        AESTextCipher producer = new AESTextCipher();
        producer.setVersionedFormat(false);
        String legacyCipherText = producer.encrypt("legacy-secret");

        AESTextCipher consumer = new AESTextCipher();
        assertFalse(consumer.wasEmptyKeyWarned());
        assertEquals("legacy-secret", consumer.decrypt(legacyCipherText));
        assertTrue(consumer.wasEmptyKeyWarned());
    }

    @Test
    public void testConfiguredKeyNeverWarns() {
        AESTextCipher cipher = new AESTextCipher().encKey("configured-key").saltKey("salt");
        String cipherText = cipher.encrypt("secret");
        cipher.decrypt(cipherText);
        assertFalse(cipher.wasEmptyKeyWarned());
    }
}
