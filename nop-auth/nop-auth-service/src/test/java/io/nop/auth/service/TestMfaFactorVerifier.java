/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.mfa.store.LocalSmsCodeStore;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_CODE_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W12-impl Phase 3：{@link MfaFactorVerifier} 组件契约单测（收敛语义钉定）。
 * 登录级/绑定级等价性由既有套件零断言修改通过证明（TestMfaLoginE2E/TestMfaUserSelfService）。
 */
public class TestMfaFactorVerifier extends JunitBaseTestCase {

    @Inject
    MfaFactorVerifier mfaFactorVerifier;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    TOTPAuthenticator totpAuthenticator;

    private String base32Secret;

    private NopAuthMfaSetting totpSetting(String userId) {
        base32Secret = totpAuthenticator.generateSecret();
        String encrypted = totpAuthenticator.getCipher().encrypt(base32Secret);
        return ContextProvider.runWithTenant("0", () -> {
            IEntityDao<NopAuthMfaSetting> dao = daoProvider.daoFor(NopAuthMfaSetting.class);
            NopAuthMfaSetting setting = dao.getEntityById(userId);
            if (setting == null) {
                setting = dao.newEntity();
                setting.setUserId(userId);
                setting.setTenantId("0");
            }
            setting.setMfaType(NopAuthConstants.MFA_TYPE_TOTP);
            setting.setSecret(encrypted);
            setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
            dao.saveEntity(setting);
            return setting;
        });
    }

    @Test
    public void testTotpSuccessAdvancesWindowUnified() {
        NopAuthMfaSetting setting = totpSetting("mfv-totp-user");
        String code = computeTotp(base32Secret);

        assertTrue(mfaFactorVerifier.verify(setting, NopAuthConstants.MFA_TYPE_TOTP, code),
                "totp verify must succeed");

        NopAuthMfaSetting reloaded = ContextProvider.runWithTenant("0",
                () -> daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(setting.getUserId()));
        assertNotNull(reloaded.getLastVerifiedWindow(), "window must be advanced by the component");
        assertNotNull(reloaded.getLastVerifiedAt(), "lastVerifiedAt must be stamped by the component");

        // 同窗口码重放拒绝（统一推进；跨场景防重放的组件级证据）
        assertFalse(mfaFactorVerifier.verify(reloaded, NopAuthConstants.MFA_TYPE_TOTP, code),
                "same-window code must not verify twice (unified window advancement)");
    }

    @Test
    public void testSmsExpiredThrowsAndMismatchFalse() {
        NopAuthMfaSetting setting = totpSetting("mfv-sms-user");
        setting.setMfaType(NopAuthConstants.MFA_TYPE_SMS);
        setting.setSecret(null);

        LocalSmsCodeStore smsCodeStore = new LocalSmsCodeStore();
        setField(mfaFactorVerifier, "smsCodeStore", smsCodeStore);

        // 无条目 → EXPIRED 抛错（不进失败计数语义）
        NopException expired = assertThrows(NopException.class,
                () -> mfaFactorVerifier.verify(setting, NopAuthConstants.MFA_TYPE_SMS, "000000"));
        assertEquals(ERR_AUTH_SMS_CODE_EXPIRED.getErrorCode(), expired.getErrorCode());

        // 正确码 → true；错码 → false
        String code = smsCodeStore.send("mfa:" + setting.getUserId());
        assertTrue(mfaFactorVerifier.verify(setting, NopAuthConstants.MFA_TYPE_SMS, code));
        String code2 = smsCodeStore.send("mfa:" + setting.getUserId());
        assertFalse(mfaFactorVerifier.verify(setting, NopAuthConstants.MFA_TYPE_SMS, "999999"));
        assertTrue(mfaFactorVerifier.verify(setting, NopAuthConstants.MFA_TYPE_SMS, code2));
    }

    @Test
    public void testUnknownMfaTypeFailClosed() {
        NopAuthMfaSetting setting = totpSetting("mfv-unknown-user");
        assertFalse(mfaFactorVerifier.verify(setting, "webauthn", "123456"),
                "unknown mfaType must fail closed (not spread as verifiable factor)");
        assertFalse(mfaFactorVerifier.verify(null, NopAuthConstants.MFA_TYPE_TOTP, "123456"),
                "null setting must fail closed");
    }

    @Test
    public void testNullAuthenticatorReturnsFalse() {
        NopAuthMfaSetting setting = totpSetting("mfv-nullauth-user");
        setField(mfaFactorVerifier, "totpAuthenticator", null);
        try {
            assertFalse(mfaFactorVerifier.verify(setting, NopAuthConstants.MFA_TYPE_TOTP, "123456"),
                    "missing authenticator must fail closed (bind-level口径；登录级等价：调用方抛 MFA_FAIL)");
        } finally {
            setField(mfaFactorVerifier, "totpAuthenticator", totpAuthenticator);
        }
    }

    private static void setField(Object target, String name, Object value) {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (IllegalAccessException e) {
                throw NopException.adapt(e);
            }
        }
        throw new IllegalArgumentException("no field " + name + " on " + target.getClass());
    }

    private static String computeTotp(String base32Secret) {
        byte[] secretBytes = base32Decode(base32Secret);
        long window = System.currentTimeMillis() / 1000L / TOTPAuthenticator.PERIOD_SECONDS;
        byte[] counterBytes = new byte[8];
        long t = window;
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (t & 0xFF);
            t >>>= 8;
        }
        byte[] hash = io.nop.commons.crypto.HashHelper.hmac(TOTPAuthenticator.HMAC_ALGORITHM, counterBytes,
                secretBytes);
        int offset = hash[hash.length - 1] & 0x0F;
        int truncated = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        return String.format("%06d", truncated % TOTPAuthenticator.MODULUS);
    }

    private static byte[] base32Decode(String encoded) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        encoded = encoded.toUpperCase().replaceAll("[=]", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bitsLeft = 0;
        for (char ch : encoded.toCharArray()) {
            int val = alphabet.indexOf(ch);
            if (val < 0)
                continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
