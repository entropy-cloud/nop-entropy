/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.auth.api.messages.WebAuthnAssertion;
import io.nop.auth.api.messages.WebAuthnAttestation;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.auth.service.mfa.WebAuthnTestClient.ORIGIN;
import static io.nop.auth.service.mfa.WebAuthnTestClient.RP_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W14-impl Phase 2 Proof：{@link WebAuthnAuthenticator} 组件级正负例（设计 §5.3.2）。
 * 正例经 {@link WebAuthnTestClient} 构造协议合法的 attestation/assertion（Phase 1 POC
 * 结论 (e) 实证——EC 密钥 + CBOR 编码能力）；负例逐项覆盖：challenge 不匹配 / origin 不匹配
 * （fail-closed 防钓鱼域）/ rpId 不匹配 / signCount 回退 / 载体缺失 / 非法 base64url。
 * count=0 认证器（协议允许的无计数实现）跳过单调校验并返回审计标记。
 */
class TestWebAuthnAuthenticator {

    private static final WebAuthnAuthenticator AUTHENTICATOR = new WebAuthnAuthenticator();
    private static final String USER_ID = "assert-user";
    private static final WebAuthnTestClient CLIENT = new WebAuthnTestClient();

    private static Boolean originalRpId;
    private static Boolean originalRpName;
    private static Boolean originalOrigins;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalRpId = provider.getConfigValue("nop.auth.mfa.webauthn.rp-id", null) != null;
        provider.assignConfigValue("nop.auth.mfa.webauthn.rp-id", RP_ID);
        provider.assignConfigValue("nop.auth.mfa.webauthn.rp-name", WebAuthnTestClient.RP_NAME);
        provider.assignConfigValue("nop.auth.mfa.webauthn.origins", ORIGIN);
    }

    @AfterAll
    static void destroy() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.mfa.webauthn.rp-id", originalRpId != null && originalRpId ? RP_ID : null);
        provider.assignConfigValue("nop.auth.mfa.webauthn.rp-name", null);
        provider.assignConfigValue("nop.auth.mfa.webauthn.origins", null);
    }

    // ===================== 注册（attestation）验证 =====================

    @Test
    void testRegistrationPositive() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        WebAuthnAttestation attestation = CLIENT.attest(cryptoChallenge, 5L);

        WebAuthnAuthenticator.RegistrationCheck check =
                AUTHENTICATOR.verifyRegistration(cryptoChallenge, "reg-user", "reg_user", attestation);
        assertNotNull(check, "protocol-valid attestation must verify (fmt=none + origin/rpId strong checks)");
        assertEquals(CLIENT.credentialIdB64Url(), check.getCredentialId(),
                "credentialId must come from attestedCredentialData (not client-supplied id)");
        assertEquals(CLIENT.publicKeyCoseB64Url(), check.getPublicKeyCose(), "COSE public key must round-trip");
        assertEquals(5L, check.getSignCount());
    }

    @Test
    void testRegistrationChallengeMismatchRejected() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        // 客户端对另一个 challenge 生成的 attestation（防客户端自造挑战）
        WebAuthnAttestation attestation = CLIENT.attest(WebAuthnAuthenticator.randomCryptoChallenge(), 0L);
        assertNull(AUTHENTICATOR.verifyRegistration(cryptoChallenge, "u", "n", attestation));
    }

    @Test
    void testRegistrationOriginMismatchRejected() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        WebAuthnAttestation attestation = CLIENT.attest(cryptoChallenge, 0L, "https://evil.example.net", RP_ID);
        assertNull(AUTHENTICATOR.verifyRegistration(cryptoChallenge, "u", "n", attestation),
                "origin mismatch must be rejected (fail-closed anti-phishing)");
    }

    @Test
    void testRegistrationRpIdMismatchRejected() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        WebAuthnAttestation attestation = CLIENT.attest(cryptoChallenge, 0L, ORIGIN, "other-rp.example.com");
        assertNull(AUTHENTICATOR.verifyRegistration(cryptoChallenge, "u", "n", attestation),
                "rpId hash mismatch must be rejected");
    }

    @Test
    void testRegistrationMalformedInputReturnsNull() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        WebAuthnAttestation malformed = new WebAuthnAttestation();
        malformed.setClientDataJSON("not-base64url!!!");
        malformed.setAttestationObject("####");
        assertNull(AUTHENTICATOR.verifyRegistration(cryptoChallenge, "u", "n", malformed));

        // 载体缺失字段 → null（不抛库异常）
        assertNull(AUTHENTICATOR.verifyRegistration(cryptoChallenge, "u", "n", new WebAuthnAttestation()));
        assertNull(AUTHENTICATOR.verifyRegistration(cryptoChallenge, "u", "n", null));
    }

    // ===================== 断言（assertion）验证 =====================

    @Test
    void testAssertionPositiveAndSignCountAdvance() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        // 注册基线 prevSignCount=5，断言计数 6（严格递增）→ 通过
        WebAuthnAssertion assertion = CLIENT.assert_(cryptoChallenge, 6L);
        WebAuthnAuthenticator.AssertionCheck check =
                AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge, assertion, 5L, USER_ID);
        assertNotNull(check, "valid assertion with advanced signCount must verify");
        assertEquals(6L, check.getSignatureCount());
        assertFalse(check.isZeroCounter());
    }

    @Test
    void testAssertionUserHandleEchoMustMatchServerHandle() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        // 断言携带与服务端 userId 句柄不一致的 userHandle → 拒绝（句柄漂移防护）
        String foreignHandle = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("another-user".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        WebAuthnAssertion assertion = CLIENT.assert_(cryptoChallenge, 6L, foreignHandle);
        assertNull(AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge,
                assertion, 5L, USER_ID), "assertion userHandle must match server-side userId handle");

        // 句柄一致（= userId 字节）→ 通过
        String matchingHandle = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(USER_ID.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        WebAuthnAssertion echo = CLIENT.assert_(cryptoChallenge, 7L, matchingHandle);
        assertNotNull(AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge,
                echo, 6L, USER_ID), "matching userHandle echo must verify");
    }

    @Test
    void testAssertionSignCountRegressionRejected() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        // prevSignCount=10，断言计数 9（回退——克隆检测信号）→ 拒绝
        WebAuthnAssertion assertion = CLIENT.assert_(cryptoChallenge, 9L);
        assertNull(AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge, assertion, 10L, USER_ID),
                "signCount regression must be rejected (clone detection)");
    }

    @Test
    void testAssertionZeroCounterAuthenticatorSkipsMonotonicCheck() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        // count=0 认证器（prev=0, new=0——协议允许）：跳过单调校验，返回审计标记
        WebAuthnAssertion assertion = CLIENT.assert_(cryptoChallenge, 0L);
        WebAuthnAuthenticator.AssertionCheck check =
                AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge, assertion, 0L, USER_ID);
        assertNotNull(check, "zero-counter authenticator must still verify (protocol allows)");
        assertTrue(check.isZeroCounter(), "zero-counter flag must be set for audit");
    }

    @Test
    void testAssertionChallengeMismatchRejected() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        WebAuthnAssertion assertion = CLIENT.assert_(WebAuthnAuthenticator.randomCryptoChallenge(), 1L);
        assertNull(AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge, assertion, 0L, USER_ID));
    }

    @Test
    void testAssertionOriginMismatchRejected() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        WebAuthnAssertion assertion = CLIENT.assert_(cryptoChallenge, 1L, "https://evil.example.net", RP_ID, null);
        assertNull(AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge, assertion, 0L, USER_ID));
    }

    @Test
    void testAssertionRpIdMismatchRejected() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        WebAuthnAssertion assertion = CLIENT.assert_(cryptoChallenge, 1L, ORIGIN, "other-rp.example.com", null);
        assertNull(AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge, assertion, 0L, USER_ID));
    }

    @Test
    void testAssertionTamperedSignatureRejected() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        WebAuthnAssertion assertion = CLIENT.assert_(cryptoChallenge, 6L);
        assertion.setSignature(assertion.getSignature().substring(0, 8) + "AAAA");
        assertNull(AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge, assertion, 5L, USER_ID),
                "tampered signature must fail COSE verification");
    }

    @Test
    void testAssertionWrongKeyRejected() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        WebAuthnAssertion assertion = CLIENT.assert_(cryptoChallenge, 6L);
        // 用另一把钥匙的公钥验签（credential 行公钥与断言签名不匹配）
        WebAuthnTestClient otherClient = new WebAuthnTestClient();
        assertNull(AUTHENTICATOR.verifyAssertion(otherClient.publicKeyCoseB64Url(), cryptoChallenge, assertion, 5L, USER_ID));
    }

    @Test
    void testAssertionIncompleteCarrierReturnsNull() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        assertNull(AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge, null, 0L, USER_ID));

        WebAuthnAssertion empty = new WebAuthnAssertion();
        assertNull(AUTHENTICATOR.verifyAssertion(CLIENT.publicKeyCoseB64Url(), cryptoChallenge, empty, 0L, USER_ID),
                "missing carrier fields must fail closed without exception");
    }

    // ===================== options 构造与配置门禁 =====================

    @Test
    void testBuildCreationOptionsAndRequestOptions() {
        String cryptoChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        io.nop.auth.api.messages.WebAuthnCreationOptions creation =
                AUTHENTICATOR.buildCreationOptions(cryptoChallenge, "opt-user", "opt_user",
                        java.util.List.of("existing-cred-id"));
        assertEquals(cryptoChallenge, creation.getChallenge());
        assertEquals(RP_ID, creation.getRpId());
        assertEquals(java.util.List.of("existing-cred-id"), creation.getExcludeCredentials());
        assertEquals(WebAuthnTestClient.RP_NAME, creation.getRpName());

        io.nop.auth.api.messages.WebAuthnRequestOptions request =
                AUTHENTICATOR.buildRequestOptions(cryptoChallenge, java.util.List.of("cred-1", "cred-2"));
        assertEquals(cryptoChallenge, request.getChallenge());
        assertEquals(RP_ID, request.getRpId());
        assertEquals(java.util.List.of("cred-1", "cred-2"), request.getAllowCredentials());
    }

    @Test
    void testConfigGateFailsClosedWhenIncomplete() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        Object savedRpId = provider.getConfigValue("nop.auth.mfa.webauthn.rp-id", RP_ID);
        try {
            provider.assignConfigValue("nop.auth.mfa.webauthn.rp-id", null);
            assertFalse(AUTHENTICATOR.isConfigured(), "incomplete RP config must not be usable");
            // 缺配置时 options 构造显式报错（不静默放行验证——fail-closed）
            try {
                AUTHENTICATOR.buildRequestOptions("c", java.util.List.of());
                throw new AssertionError("incomplete config must throw");
            } catch (io.nop.api.core.exceptions.NopException expected) {
                // expected: fail-closed explicit error
            }
        } finally {
            provider.assignConfigValue("nop.auth.mfa.webauthn.rp-id", savedRpId != null ? savedRpId : RP_ID);
        }
    }
}
