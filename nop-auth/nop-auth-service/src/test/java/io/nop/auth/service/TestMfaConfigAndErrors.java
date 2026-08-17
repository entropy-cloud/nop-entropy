/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.json.JSON;
import io.nop.auth.api.messages.LoginResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 tests: MFA/SMS config defaults are bound with documented values;
 * the new error codes exist and {@code ERR_AUTH_MFA_REQUIRED} carries the
 * challenge params; {@code LoginResult.accessCode} is an optional field that
 * stays backward-compatible (responses without it still deserialize).
 */
public class TestMfaConfigAndErrors {

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroyCore() {
        CoreInitialization.destroy();
    }

    @Test
    void mfaConfigDefaultsAreDocumentedValues() {
        assertEquals(false, NopAuthConfigs.CFG_AUTH_MFA_ENABLED.get());
        assertEquals("db", NopAuthConfigs.CFG_AUTH_MFA_STORE_TYPE.get());
        assertEquals(300, NopAuthConfigs.CFG_AUTH_MFA_CHALLENGE_EXPIRE_SECONDS.get());
        assertEquals(5, NopAuthConfigs.CFG_AUTH_MFA_MAX_ATTEMPTS.get());
        assertEquals("nop", NopAuthConfigs.CFG_AUTH_MFA_TOTP_ISSUER.get());
        assertEquals(1, NopAuthConfigs.CFG_AUTH_MFA_TOTP_WINDOW_SKEW.get());
        assertEquals(300, NopAuthConfigs.CFG_AUTH_MFA_ACCESS_CODE_EXPIRE_SECONDS.get());
    }

    @Test
    void smsCodeConfigDefaultsAreDocumentedValues() {
        assertEquals(false, NopAuthConfigs.CFG_AUTH_SMS_CODE_ENABLED.get());
        assertEquals(300, NopAuthConfigs.CFG_AUTH_SMS_CODE_EXPIRE_SECONDS.get());
        assertEquals(60, NopAuthConfigs.CFG_AUTH_SMS_CODE_SEND_INTERVAL_SECONDS.get());
        assertEquals(20, NopAuthConfigs.CFG_AUTH_SMS_CODE_DAILY_LIMIT.get());
        assertEquals(50, NopAuthConfigs.CFG_AUTH_SMS_CODE_IP_DAILY_LIMIT.get());
        assertEquals(5, NopAuthConfigs.CFG_AUTH_SMS_CODE_MAX_ATTEMPTS.get());
        assertEquals(false, NopAuthConfigs.CFG_AUTH_SMS_CODE_ALLOW_REGISTER.get());
    }

    @Test
    void mfaErrorCodesExistAndMfaRequiredCarriesChallengeParams() {
        assertDistinct(NopAuthErrors.ERR_AUTH_MFA_REQUIRED,
                NopAuthErrors.ERR_AUTH_MFA_FAIL,
                NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED,
                NopAuthErrors.ERR_AUTH_MFA_NOT_ENABLED,
                NopAuthErrors.ERR_AUTH_MFA_ALREADY_ENABLED,
                NopAuthErrors.ERR_AUTH_MFA_BIND_EXPIRED,
                NopAuthErrors.ERR_AUTH_SMS_CODE_INVALID,
                NopAuthErrors.ERR_AUTH_SMS_CODE_EXPIRED,
                NopAuthErrors.ERR_AUTH_SMS_RATE_LIMITED,
                NopAuthErrors.ERR_AUTH_SMS_DAILY_LIMIT,
                NopAuthErrors.ERR_AUTH_MFA_RECOVERY_CODE_USED);

        // ERR_AUTH_MFA_REQUIRED declares challenge params as default arg names
        Set<String> params = new HashSet<>(Arrays.asList(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getArgNames()));
        assertTrue(params.contains(NopAuthErrors.ARG_CHALLENGE_TOKEN), "must carry challengeToken");
        assertTrue(params.contains(NopAuthErrors.ARG_MFA_TYPE), "must carry mfaType");
        assertTrue(params.contains(NopAuthErrors.ARG_LOGIN_TYPE), "must carry loginType");
    }

    private void assertDistinct(ErrorCode... codes) {
        Set<String> seen = new HashSet<>();
        for (ErrorCode c : codes) {
            assertNotNull(c.getErrorCode(), "error code must not be null");
            assertTrue(seen.add(c.getErrorCode()), "duplicate error code: " + c.getErrorCode());
        }
        assertEquals(codes.length, seen.size());
    }

    /**
     * W14：webauthn 配置组缺省未配置（fail-closed——因子使用时必配校验在 WebAuthnAuthenticator）+
     * 两个新错误码与既有 MFA 编码互不冲突（不触碰一期编码）。
     */
    @Test
    void webauthnConfigDefaultsUnsetAndNewErrorCodesDistinct() {
        assertNull(NopAuthConfigs.CFG_AUTH_MFA_WEBAUTHN_RP_ID.get(), "rp-id default unset (must be configured to enable)");
        assertNull(NopAuthConfigs.CFG_AUTH_MFA_WEBAUTHN_RP_NAME.get(), "rp-name default unset");
        assertNull(NopAuthConfigs.CFG_AUTH_MFA_WEBAUTHN_ORIGINS.get(), "origins default unset (fail-closed)");

        assertDistinct(NopAuthErrors.ERR_AUTH_MFA_CODE_UNSUPPORTED,
                NopAuthErrors.ERR_AUTH_MFA_LAST_CREDENTIAL,
                NopAuthErrors.ERR_AUTH_MFA_REQUIRED,
                NopAuthErrors.ERR_AUTH_MFA_FAIL,
                NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED,
                NopAuthErrors.ERR_AUTH_MFA_RESTRICTED_SESSION);
        assertTrue(Arrays.asList(NopAuthErrors.ERR_AUTH_MFA_CODE_UNSUPPORTED.getArgNames())
                        .contains(NopAuthErrors.ARG_MFA_TYPE), "CODE_UNSUPPORTED must carry mfaType");
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginResultAccessCodeIsOptionalAndBackwardCompatible() {
        // an old response JSON without accessCode still deserializes
        String legacyJson = "{\"accessToken\":\"a\",\"expiresIn\":60,\"tokenType\":\"bearer\"}";
        LoginResult legacy = (LoginResult) JSON.parseToBean(null, legacyJson, LoginResult.class);
        assertNotNull(legacy);
        assertEquals("a", legacy.getAccessToken());
        assertNull(legacy.getAccessCode(), "accessCode defaults to null for password-login responses");

        // a channel mfaVerify response with accessCode deserializes
        String channelJson = "{\"accessCode\":\"code-xyz\"}";
        LoginResult channel = (LoginResult) JSON.parseToBean(null, channelJson, LoginResult.class);
        assertEquals("code-xyz", channel.getAccessCode());

        // serializing a result with null accessCode must not emit the field (NON_EMPTY)
        LoginResult r = new LoginResult();
        r.setAccessToken("a");
        String out = JSON.stringify(r);
        assertNotNull(out);
        assertFalse(out.contains("\"accessCode\""),
                "null accessCode must be omitted to stay backward-compatible: " + out);

        // setting it serializes the value
        r.setAccessCode("c1");
        assertTrue(JSON.stringify(r).contains("\"accessCode\":\"c1\""));
    }
}
