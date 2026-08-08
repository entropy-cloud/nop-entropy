/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.crypt;

import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.commons.util.StringHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖 Plan 334 的加密值格式硬化契约：v1 自描述格式、per-message IV、PBKDF2 KDF、
 * legacy 读取兼容、fail-closed 失败语义、并发安全。
 */
public class TestAesEncryptedValueFormat {

    private static final String PLAINTEXT = "secret-value-to-encrypt";
    private static final String ENC_KEY = "test-enc-key-2026";

    // ==================== Phase 2: v1 per-message-IV format ====================

    /**
     * v1 加密输出必须带版本标记前缀。
     */
    @Test
    public void testV1EncryptProducesVersionMarker() {
        AESTextCipher cipher = new AESTextCipher().encKey(ENC_KEY);
        String code = cipher.encrypt(PLAINTEXT);
        assertTrue(code.startsWith(AESTextCipher.V1_MARKER),
                "v1 ciphertext must start with " + AESTextCipher.V1_MARKER + ": " + code);
    }

    /**
     * 两次加密同一明文必须产生不同密文（per-message IV 的核心证据）。
     */
    @Test
    public void testV1EncryptProducesDistinctIvs() {
        AESTextCipher cipher = new AESTextCipher().encKey(ENC_KEY);
        String code1 = cipher.encrypt(PLAINTEXT);
        String code2 = cipher.encrypt(PLAINTEXT);
        assertNotEquals(code1, code2, "per-message IV must produce distinct ciphertexts");

        // 两者都必须能正确解密
        assertEquals(PLAINTEXT, cipher.decrypt(code1));
        assertEquals(PLAINTEXT, cipher.decrypt(code2));
    }

    /**
     * v1 加解密往返。
     */
    @Test
    public void testV1RoundTrip() {
        AESTextCipher cipher = new AESTextCipher().encKey(ENC_KEY).saltKey("salt-abc");
        String code = cipher.encrypt(PLAINTEXT);
        assertEquals(PLAINTEXT, cipher.decrypt(code));
    }

    /**
     * 不同 encKey 加密的密文用错误的 key 解密必须失败（fail-closed）。
     */
    @Test
    public void testV1WrongKeyFails() {
        AESTextCipher enc = new AESTextCipher().encKey(ENC_KEY);
        String code = enc.encrypt(PLAINTEXT);

        AESTextCipher dec = new AESTextCipher().encKey("wrong-key");
        assertThrows(Exception.class, () -> dec.decrypt(code));
    }

    // ==================== Phase 2: fail-closed failure semantics ====================

    /**
     * 篡改密文（破坏 GCM tag）必须抛异常，绝不静默返回。
     */
    @Test
    public void testV1TamperedCiphertextThrows() {
        AESTextCipher cipher = new AESTextCipher().encKey(ENC_KEY);
        String code = cipher.encrypt(PLAINTEXT);
        assertTrue(code.startsWith(AESTextCipher.V1_MARKER));

        // 篡改 payload 中段的一个字符（破坏 GCM tag）
        int pos = AESTextCipher.V1_MARKER.length() + 4;
        char orig = code.charAt(pos);
        char tampered = orig == 'A' ? 'B' : 'A';
        String tamperedCode = code.substring(0, pos) + tampered + code.substring(pos + 1);

        assertThrows(Exception.class, () -> cipher.decrypt(tamperedCode),
                "tampered GCM tag must fail closed");
    }

    /**
     * 截断的 v1 密文（缺少 IV）必须抛异常。
     */
    @Test
    public void testV1TruncatedCiphertextThrows() {
        AESTextCipher cipher = new AESTextCipher().encKey(ENC_KEY);
        // 构造一个只有版本标记 + 不足 IV 长度的 payload
        String truncated = AESTextCipher.V1_MARKER + "aGk="; // base64("hi")，远短于 IV
        assertThrows(Exception.class, () -> cipher.decrypt(truncated),
                "truncated v1 ciphertext must fail closed");
    }

    /**
     * 未知格式（无版本标记的垃圾值）走 legacy 解密路径也必须抛异常（fail-closed）。
     */
    @Test
    public void testUnknownFormatThrows() {
        AESTextCipher cipher = new AESTextCipher().encKey(ENC_KEY);
        // 不是合法 base64/hex，也不是 v1 标记
        assertThrows(Exception.class, () -> cipher.decrypt("not-a-valid-cipher-text!!!"),
                "unknown-format value must fail closed");
    }

    /**
     * 无版本标记但 base64 合法、内容不匹配的值也必须失败。
     */
    @Test
    public void testLegacyGarbageBase64Throws() {
        AESTextCipher cipher = new AESTextCipher().encKey(ENC_KEY);
        // 合法 base64 但不是合法密文
        assertThrows(Exception.class, () -> cipher.decrypt(StringHelper.encodeBase64(new byte[]{1, 2, 3, 4})),
                "legacy garbage base64 must fail closed");
    }

    // ==================== Phase 2: concurrency ====================

    /**
     * 并发加密同一明文：每条密文互不相同（IV 不复用），且都能正确解密，无状态损坏。
     */
    @Test
    public void testConcurrentEncryptDistinctIvsNoCorruption() throws Exception {
        int threads = 16;
        int perThread = 50;
        AESTextCipher cipher = new AESTextCipher().encKey(ENC_KEY);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<String>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                List<String> codes = new ArrayList<>(perThread);
                for (int j = 0; j < perThread; j++) {
                    codes.add(cipher.encrypt(PLAINTEXT));
                }
                return codes;
            }));
        }

        start.countDown();
        Set<String> allCodes = new HashSet<>();
        for (Future<List<String>> f : futures) {
            for (String code : f.get()) {
                assertTrue(allCodes.add(code), "concurrent encrypt reused a ciphertext (IV collision)");
                assertEquals(PLAINTEXT, cipher.decrypt(code), "concurrent ciphertext failed to decrypt");
            }
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(threads * perThread, allCodes.size(), "all concurrent ciphertexts must be distinct");
    }

    // ==================== Phase 1: legacy fixtures (baseline proof) ====================

    /**
     * Legacy 密文（静态 IV + MD5 KDF，无版本标记）必须仍能被默认 decrypt 正确读取。
     * 这是 read-only 兼容的核心证据。
     */
    @Test
    public void testLegacyCiphertextReadableByDefaultDecrypt() {
        // 用 legacy 模式产生密文（静态 IV + MD5 KDF）
        AESTextCipher legacy = new AESTextCipher().encKey(ENC_KEY).versionedFormat(false);
        String legacyCode = legacy.encrypt(PLAINTEXT);
        assertTrue(!legacyCode.startsWith(AESTextCipher.V1_MARKER),
                "legacy ciphertext must not have v1 marker");

        // 默认实例（v1 模式）的 decrypt 必须能读取 legacy 密文
        AESTextCipher reader = new AESTextCipher().encKey(ENC_KEY);
        assertEquals(PLAINTEXT, reader.decrypt(legacyCode),
                "default decrypt must read legacy ciphertext (read-only compatibility)");
    }

    /**
     * Legacy 加密是确定性的（静态 IV 证据）：同一明文两次加密产生相同密文。
     * 这与 v1 的非确定性形成对照，证明 legacy 路径未被意外改动。
     */
    @Test
    public void testLegacyEncryptIsDeterministicStaticIv() {
        AESTextCipher legacy = new AESTextCipher().encKey(ENC_KEY).versionedFormat(false);
        String c1 = legacy.encrypt(PLAINTEXT);
        String c2 = legacy.encrypt(PLAINTEXT);
        assertEquals(c1, c2, "legacy static-IV encrypt must be deterministic");
    }

    /**
     * Legacy concatIv 模式的密文也必须能被读取。
     */
    @Test
    public void testLegacyConcatIvReadable() {
        AESTextCipher legacy = new AESTextCipher().encKey(ENC_KEY)
                .versionedFormat(false).concatIv(true);
        String legacyCode = legacy.encrypt(PLAINTEXT);

        AESTextCipher reader = new AESTextCipher().encKey(ENC_KEY).versionedFormat(false).concatIv(true);
        assertEquals(PLAINTEXT, reader.decrypt(legacyCode),
                "legacy concatIv ciphertext must remain readable");
    }

    // ==================== KDF dispatch (version-keyed) ====================

    /**
     * v1 使用 PBKDF2，legacy 使用 MD5：同一 encKey 下两种格式派生出的密钥不同，
     * 因此 v1 密文（去掉标记后）无法被 legacy 路径解密，反之亦然。
     */
    @Test
    public void testKdfDispatchVersionKeyed() {
        AESTextCipher v1 = new AESTextCipher().encKey(ENC_KEY);
        AESTextCipher legacy = new AESTextCipher().encKey(ENC_KEY).versionedFormat(false);

        // v1 密文去掉版本标记后，用 legacy 路径（MD5 key + 静态 IV）解密必须失败
        String v1Code = v1.encrypt(PLAINTEXT);
        String v1Payload = v1Code.substring(AESTextCipher.V1_MARKER.length());
        assertThrows(Exception.class, () -> legacy.decrypt(v1Payload),
                "v1 (PBKDF2) ciphertext must not decrypt with legacy MD5 key");

        // 两种格式各自能正确解密自己的密文
        assertEquals(PLAINTEXT, v1.decrypt(v1Code));
        assertEquals(PLAINTEXT, legacy.decrypt(legacy.encrypt(PLAINTEXT)));
    }
}
