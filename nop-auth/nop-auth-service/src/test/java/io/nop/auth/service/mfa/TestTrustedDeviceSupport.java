/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * W15-impl：设备指纹算法单元测试（设计 §6.3）——三输入（device-id + UA + Accept-Language）
 * SHA-256 hex / device-id 缺失返回 null（降级非错误）/ 头读取大小写不敏感 / 输入敏感性。
 */
public class TestTrustedDeviceSupport {

    private static Map<String, Object> headers(String deviceId, String ua, String al) {
        Map<String, Object> h = new HashMap<>();
        if (deviceId != null)
            h.put("X-Nop-Mfa-Device-Id", deviceId);
        if (ua != null)
            h.put("User-Agent", ua);
        if (al != null)
            h.put("Accept-Language", al);
        return h;
    }

    @Test
    public void testNoDeviceIdReturnsNull() {
        assertNull(MfaTrustedDeviceManager.fingerprint(null));
        assertNull(MfaTrustedDeviceManager.fingerprint(new HashMap<>()));
        assertNull(MfaTrustedDeviceManager.fingerprint(headers(null, "Mozilla/5.0", "zh-CN")),
                "missing device-id must degrade to null (normal MFA, not an error)");
    }

    @Test
    public void testDeterministic() {
        String h1 = MfaTrustedDeviceManager.fingerprint(headers("device-1", "Mozilla/5.0", "zh-CN"));
        String h2 = MfaTrustedDeviceManager.fingerprint(headers("device-1", "Mozilla/5.0", "zh-CN"));
        assertNotNull(h1);
        assertEquals(64, h1.length(), "SHA-256 hex must be 64 chars");
        assertEquals(h1, h2, "same inputs must produce the same fingerprint");
    }

    @Test
    public void testCaseInsensitiveHeaderRead() {
        Map<String, Object> mixed = new HashMap<>();
        mixed.put("x-nop-mfa-device-id", "device-1");
        mixed.put("user-agent", "Mozilla/5.0");
        mixed.put("accept-language", "zh-CN");
        assertEquals(MfaTrustedDeviceManager.fingerprint(headers("device-1", "Mozilla/5.0", "zh-CN")),
                MfaTrustedDeviceManager.fingerprint(mixed),
                "header lookup must be case-insensitive (extractClientIp precedent)");
    }

    @Test
    public void testInputSensitivity() {
        String base = MfaTrustedDeviceManager.fingerprint(headers("device-1", "UA-1", "zh-CN"));
        assertNotEquals(base, MfaTrustedDeviceManager.fingerprint(headers("device-2", "UA-1", "zh-CN")),
                "different device-id must change the fingerprint");
        assertNotEquals(base, MfaTrustedDeviceManager.fingerprint(headers("device-1", "UA-2", "zh-CN")),
                "different User-Agent must change the fingerprint");
        assertNotEquals(base, MfaTrustedDeviceManager.fingerprint(headers("device-1", "UA-1", "en-US")),
                "different Accept-Language must change the fingerprint");
    }

    @Test
    public void testMissingUaOrAlStillFingerprints() {
        // UA/Accept-Language 可选（null → 空串参与拼接）；device-id 存在即可指纹
        String h = MfaTrustedDeviceManager.fingerprint(headers("device-1", null, null));
        assertNotNull(h);
        // 与带 UA 的指纹不同（空 UA 参与拼接）
        assertNotEquals(h, MfaTrustedDeviceManager.fingerprint(headers("device-1", "UA", null)));
    }
}
