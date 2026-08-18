/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.api.messages.WebAuthnAssertion;
import io.nop.auth.api.messages.WebAuthnRequestOptions;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.IUserContextCache;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.mfa.store.LocalSmsCodeStore;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaCredential;
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthMfaTrustedDevice;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.biz.dto.MfaWebauthnAddKeyBeginResult;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.auth.service.mfa.MfaChallengeHelper;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.auth.service.mfa.WebAuthnAuthenticator;
import io.nop.auth.service.mfa.WebAuthnTestClient;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.VarCollector;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2-followup-2 add-key-while-enabled 正门 E2E（设计 §10.2，真实组件 + H2 内存库）：
 * <ol>
 *   <li><b>全链</b>（Anti-Hollow Rule #22）：enabled webauthn 双钥匙用户经
 *       webauthnBeginAddKey（双 challenge 行）→ confirmWebauthnAddKey（持有证明 + attestation）
 *       → 第三把钥匙落库 enabled；既有钥匙/恢复码/可信设备/setting 逐项零副作用；双 challenge
 *       一并消费；审计双事件落库。</li>
 *   <li><b>登录链回归</b>：add-key 后原 enabled 钥匙仍走完整 webauthn 登录（新钥匙不破坏既有
 *       认证路径）。</li>
 *   <li><b>负例矩阵</b>（No Silent No-Op）：持有证明四负例（无断言/错挑战断言/跨会话/跨用户）+
 *       发起守卫三负例（未 enabled/非 webauthn/零 enabled credential）+ attestation 失败 +
 *       同钥匙重复注册拒绝 + 多钥匙语境 last-credential 守卫。</li>
 * </ol>
 */
class TestWebAuthnAddKeyE2E {

    private static final String TENANT_ID = "0";
    private static Boolean originalMfaEnabled;

    // ---- H2 + ORM stack ----
    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;
    private IJdbcTemplate jdbcTemplate;

    // ---- real beans ----
    private LocalMfaChallengeStore mfaChallengeStore;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;
    private NopAuthUserBizModel userBizModel;
    private WebAuthnAuthenticator webAuthnAuthenticator;
    private CapturingAuditService auditService;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalMfaEnabled = provider.getConfigValue("nop.auth.mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.mfa.enabled", true);
        provider.assignConfigValue("nop.auth.mfa.webauthn.rp-id", WebAuthnTestClient.RP_ID);
        provider.assignConfigValue("nop.auth.mfa.webauthn.rp-name", WebAuthnTestClient.RP_NAME);
        provider.assignConfigValue("nop.auth.mfa.webauthn.origins", WebAuthnTestClient.ORIGIN);
    }

    @AfterAll
    static void destroyCore() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.mfa.enabled",
                originalMfaEnabled != null ? originalMfaEnabled : Boolean.FALSE);
        provider.assignConfigValue("nop.auth.mfa.webauthn.rp-id", null);
        provider.assignConfigValue("nop.auth.mfa.webauthn.rp-name", null);
        provider.assignConfigValue("nop.auth.mfa.webauthn.origins", null);
    }

    @BeforeEach
    void setUp() {
        if (VarCollector.instance() == null) {
            VarCollector.registerInstance(new VarCollector());
        }
        buildH2Stack();
        wireRealBeans();
    }

    @AfterEach
    void tearDown() {
        if (factoryBean != null) {
            try {
                factoryBean.destroy();
            } catch (Exception ignored) {
                // best-effort teardown
            }
        }
    }

    // ===================== 1. 全链正例：双钥匙 → 第三把 + 零副作用 =====================

    @Test
    void testAddKeyFullChainWithSideEffectsUnchanged() {
        String userId = "addkey-full-user";
        String userName = "addkey_full_user";
        saveUser(userId, userName);
        WebAuthnTestClient key1 = new WebAuthnTestClient();
        WebAuthnTestClient key2 = new WebAuthnTestClient();
        enableWebauthnDirectly(userId);
        saveCredentialDirectly(userId, key1, 5L, NopAuthConstants.MFA_STATUS_ENABLED);
        saveCredentialDirectly(userId, key2, 3L, NopAuthConstants.MFA_STATUS_ENABLED);
        String recoverySid1 = saveRecoveryCodeDirectly(userId, "hash-1");
        String recoverySid2 = saveRecoveryCodeDirectly(userId, "hash-2");
        String trustedSid = saveTrustedDeviceDirectly(userId, "device-hash-1");
        IServiceContext ctx = ctx(userId, userName, "sess-add");

        // 1. 发起：双 challenge 行 + 双 options
        MfaWebauthnAddKeyBeginResult begin = ormTemplate.runInSession(s ->
                userBizModel.webauthnBeginAddKey(ctx));
        assertNotNull(begin.getVerifyChallengeToken(), "possession-proof row token");
        assertNotNull(begin.getAddChallengeToken(), "registration row token");
        assertNotNull(begin.getAssertionOptions().getChallenge());
        assertNotNull(begin.getCreationOptions().getChallenge());
        assertTrue(begin.getAssertionOptions().getAllowCredentials().contains(key1.credentialIdB64Url()),
                "allowCredentials must include enabled key1");
        assertTrue(begin.getAssertionOptions().getAllowCredentials().contains(key2.credentialIdB64Url()),
                "allowCredentials must include enabled key2");
        assertTrue(begin.getCreationOptions().getExcludeCredentials().contains(key1.credentialIdB64Url()),
                "excludeCredentials must include existing key1 (same-key re-register prevention)");
        assertTrue(begin.getCreationOptions().getExcludeCredentials().contains(key2.credentialIdB64Url()),
                "excludeCredentials must include existing key2");

        // 双行 scene/payload 契约：scene=webauthn-add + sessionId 绑定 + 独立 cryptoChallenge
        MfaChallenge verifyRow = mfaChallengeStore.peek(begin.getVerifyChallengeToken());
        MfaChallenge addRow = mfaChallengeStore.peek(begin.getAddChallengeToken());
        assertEquals(MfaChallenge.SCENE_WEBAUTHN_ADD, verifyRow.getScene());
        assertEquals(MfaChallenge.SCENE_WEBAUTHN_ADD, addRow.getScene());
        assertEquals("sess-add", MfaChallengeHelper.sessionIdOf(verifyRow));
        assertEquals("sess-add", MfaChallengeHelper.sessionIdOf(addRow));
        assertEquals(begin.getAssertionOptions().getChallenge(), MfaChallengeHelper.cryptoChallengeOf(verifyRow),
                "assertionOptions.challenge = verify-row cryptoChallenge (write-once)");
        assertEquals(begin.getCreationOptions().getChallenge(), MfaChallengeHelper.cryptoChallengeOf(addRow),
                "creationOptions.challenge = add-row cryptoChallenge (write-once)");

        // 2. 确认：key1 持有证明 + key3 attestation → 第三把钥匙
        WebAuthnTestClient key3 = new WebAuthnTestClient();
        ormTemplate.runInSession(s -> {
            userBizModel.confirmWebauthnAddKey(begin.getAddChallengeToken(),
                    key3.attest(begin.getCreationOptions().getChallenge(), 0L),
                    begin.getVerifyChallengeToken(),
                    key1.assert_(begin.getAssertionOptions().getChallenge(), 6L), ctx);
            return null;
        });

        // 新钥匙落库 enabled；key1 signCount 被持有证明推进（verifier 内聚）
        NopAuthMfaCredential newCred = findCredentialByCredentialId(key3.credentialIdB64Url());
        assertNotNull(newCred, "new key must be persisted");
        assertEquals(userId, newCred.getUserId());
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, newCred.getStatus());
        assertEquals(Long.valueOf(0L), newCred.getSignCount());
        assertEquals(Long.valueOf(6L), findCredentialByCredentialId(key1.credentialIdB64Url()).getSignCount(),
                "possession proof must advance key1 signCount (verifier internal)");

        // 零副作用：setting 仍 enabled（不经 pending）/恢复码行不变/可信设备行保留/既有钥匙不删
        NopAuthMfaSetting setting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, setting.getStatus(), "setting must stay enabled");
        assertNull(setting.getBindToken(), "no pending state machine involvement");
        assertEquals(2, countRecoveryCodes(userId), "recovery codes must be untouched");
        assertNotNull(daoProvider.daoFor(NopAuthMfaTrustedDevice.class).getEntityById(trustedSid),
                "trusted device row must survive add-key");
        assertNotNull(findCredentialByCredentialId(key1.credentialIdB64Url()), "existing key1 survives");
        assertNotNull(findCredentialByCredentialId(key2.credentialIdB64Url()), "existing key2 survives");
        assertEquals(recoverySid1, daoProvider.daoFor(NopAuthMfaRecoveryCode.class)
                .getEntityById(recoverySid1).getSid(), "recovery row identity unchanged");

        // 双 challenge 一并消费（整体成功才收口）
        assertNull(mfaChallengeStore.peek(begin.getVerifyChallengeToken()), "verify row consumed on success");
        assertNull(mfaChallengeStore.peek(begin.getAddChallengeToken()), "add row consumed on success");

        // 审计双事件（持有证明 + 钥匙新增）
        assertTrue(auditService.requests.stream().anyMatch(r -> "webauthn-add-proof-ok".equals(r.getDescription())),
                "possession-proof success must be audited");
        assertTrue(auditService.requests.stream().anyMatch(r -> "webauthn-key-added".equals(r.getDescription())),
                "key addition must be audited");
    }

    // ===================== 2. 登录链回归：原 enabled 钥匙仍可 webauthn 登录 =====================

    @Test
    void testOriginalKeyStillLogsInAfterAddKey() {
        String userId = "addkey-login-user";
        String userName = "addkey_login_user";
        saveUser(userId, userName);
        WebAuthnTestClient key1 = new WebAuthnTestClient();
        WebAuthnTestClient key2 = new WebAuthnTestClient();
        enableWebauthnDirectly(userId);
        saveCredentialDirectly(userId, key1, 5L, NopAuthConstants.MFA_STATUS_ENABLED);
        saveCredentialDirectly(userId, key2, 3L, NopAuthConstants.MFA_STATUS_ENABLED);
        IServiceContext ctx = ctx(userId, userName, "sess-login-add");

        MfaWebauthnAddKeyBeginResult begin = ormTemplate.runInSession(s ->
                userBizModel.webauthnBeginAddKey(ctx));
        WebAuthnTestClient key3 = new WebAuthnTestClient();
        ormTemplate.runInSession(s -> {
            userBizModel.confirmWebauthnAddKey(begin.getAddChallengeToken(),
                    key3.attest(begin.getCreationOptions().getChallenge(), 0L),
                    begin.getVerifyChallengeToken(),
                    key1.assert_(begin.getAssertionOptions().getChallenge(), 6L), ctx);
            return null;
        });

        // 原钥匙（key2——未被任何 proof 触碰）完整登录链：密码 → MFA_REQUIRED → options → 断言 → accessToken
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin(userName)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        String loginChallengeToken = (String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(loginChallengeToken,
                new ServiceContextImpl());
        assertTrue(options.getAllowCredentials().contains(key2.credentialIdB64Url()),
                "original key2 must remain a valid login credential");

        MfaVerifyRequest verify = new MfaVerifyRequest();
        verify.setChallengeToken(loginChallengeToken);
        verify.setAssertion(key2.assert_(options.getChallenge(), 4L));
        LoginResult result = ormTemplate.runInSession(s -> FutureHelper.syncGet(
                loginApiBizModel.mfaVerifyAsync(verify, new ServiceContextImpl())));
        assertNotNull(result.getAccessToken(), "original key must still complete webauthn login");

        // 新钥匙同样可登录（端到端可用性——从发起端点到新钥匙可用）
        NopException ex2 = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin(userName)));
        String token2 = (String) ex2.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
        WebAuthnRequestOptions options2 = loginApiBizModel.webauthnAuthOptions(token2, new ServiceContextImpl());
        MfaVerifyRequest verify2 = new MfaVerifyRequest();
        verify2.setChallengeToken(token2);
        verify2.setAssertion(key3.assert_(options2.getChallenge(), 1L));
        LoginResult result2 = ormTemplate.runInSession(s -> FutureHelper.syncGet(
                loginApiBizModel.mfaVerifyAsync(verify2, new ServiceContextImpl())));
        assertNotNull(result2.getAccessToken(), "newly added key must be usable for login");
    }

    // ===================== 3. 持有证明负例矩阵（≥4） =====================

    @Test
    void testPossessionProofNegativeMatrix() {
        String userId = "addkey-proof-user";
        String userName = "addkey_proof_user";
        saveUser(userId, userName);
        WebAuthnTestClient key1 = new WebAuthnTestClient();
        enableWebauthnDirectly(userId);
        saveCredentialDirectly(userId, key1, 5L, NopAuthConstants.MFA_STATUS_ENABLED);
        IServiceContext ctx = ctx(userId, userName, "sess-proof");

        // (a) 无断言 → MFA_FAIL（显式拒绝，计数不消费）
        MfaWebauthnAddKeyBeginResult beginA = ormTemplate.runInSession(s ->
                userBizModel.webauthnBeginAddKey(ctx));
        NopException noAssertion = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.confirmWebauthnAddKey(beginA.getAddChallengeToken(),
                    new WebAuthnTestClient().attest(beginA.getCreationOptions().getChallenge(), 0L),
                    beginA.getVerifyChallengeToken(), null, ctx);
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), noAssertion.getErrorCode(),
                "null assertion must be rejected");
        assertNotNull(mfaChallengeStore.peek(beginA.getVerifyChallengeToken()),
                "proof failure counts per-token but does not consume (below limit)");

        // (b) 错误挑战断言（自造 challenge）→ MFA_FAIL
        MfaWebauthnAddKeyBeginResult beginB = ormTemplate.runInSession(s ->
                userBizModel.webauthnBeginAddKey(ctx));
        String bogusChallenge = WebAuthnAuthenticator.randomCryptoChallenge();
        NopException wrongChallenge = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.confirmWebauthnAddKey(beginB.getAddChallengeToken(),
                    new WebAuthnTestClient().attest(beginB.getCreationOptions().getChallenge(), 0L),
                    beginB.getVerifyChallengeToken(),
                    key1.assert_(bogusChallenge, 6L), ctx);
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), wrongChallenge.getErrorCode(),
                "self-made challenge assertion must be rejected");

        // (c) 跨会话 challenge（begin 于 sess-proof，confirm 于 sess-other）→ MFA_FAIL
        IServiceContext otherSession = ctx(userId, userName, "sess-other");
        NopException crossSession = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.confirmWebauthnAddKey(beginB.getAddChallengeToken(),
                    new WebAuthnTestClient().attest(beginB.getCreationOptions().getChallenge(), 0L),
                    beginB.getVerifyChallengeToken(),
                    key1.assert_(beginB.getAssertionOptions().getChallenge(), 6L), otherSession);
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), crossSession.getErrorCode(),
                "cross-session challenge must be rejected (session binding)");

        // (d) 跨用户 challenge（他人 token 在本人会话使用）→ MFA_FAIL（userId 绑定）
        String otherUserId = "addkey-proof-other";
        saveUser(otherUserId, "addkey_proof_other");
        WebAuthnTestClient otherKey = new WebAuthnTestClient();
        enableWebauthnDirectly(otherUserId);
        saveCredentialDirectly(otherUserId, otherKey, 5L, NopAuthConstants.MFA_STATUS_ENABLED);
        IServiceContext otherCtx = ctx(otherUserId, "addkey_proof_other", "sess-proof-other");
        MfaWebauthnAddKeyBeginResult otherBegin = ormTemplate.runInSession(s ->
                userBizModel.webauthnBeginAddKey(otherCtx));
        NopException crossUser = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            // 本人（userId）确认他人（otherUserId）的 challenge 行——userId 绑定拒绝
            userBizModel.confirmWebauthnAddKey(otherBegin.getAddChallengeToken(),
                    new WebAuthnTestClient().attest(otherBegin.getCreationOptions().getChallenge(), 0L),
                    otherBegin.getVerifyChallengeToken(),
                    key1.assert_(otherBegin.getAssertionOptions().getChallenge(), 6L), ctx);
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), crossUser.getErrorCode(),
                "another user's add-key challenge must be rejected (userId binding)");
    }

    // ===================== 4. 发起守卫负例（≥3） =====================

    @Test
    void testBeginGuards() {
        // (a) 无 MFA setting（未 enabled）→ NOT_ENABLED
        String plainUser = "addkey-plain-user";
        saveUser(plainUser, "addkey_plain_user");
        NopException notEnabled = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.webauthnBeginAddKey(ctx(plainUser, "addkey_plain_user", "sess-g1"));
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_NOT_ENABLED.getErrorCode(), notEnabled.getErrorCode());

        // (b) 非 webauthn（totp enabled）→ NOT_ENABLED
        String totpUser = "addkey-totp-user";
        saveUser(totpUser, "addkey_totp_user");
        enableTotpDirectly(totpUser);
        NopException notWebauthn = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.webauthnBeginAddKey(ctx(totpUser, "addkey_totp_user", "sess-g2"));
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_NOT_ENABLED.getErrorCode(), notWebauthn.getErrorCode());

        // (c) webauthn enabled 但零 credential（持有证明对象缺失）→ 显式拒绝
        String noKeyUser = "addkey-nokey-user";
        saveUser(noKeyUser, "addkey_nokey_user");
        enableWebauthnDirectly(noKeyUser);
        NopException noCredential = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.webauthnBeginAddKey(ctx(noKeyUser, "addkey_nokey_user", "sess-g3"));
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), noCredential.getErrorCode(),
                "zero enabled credential must be rejected explicitly");
    }

    // ===================== 5. attestation 失败 + 同钥匙重复注册拒绝 =====================

    @Test
    void testAttestationFailureAndDuplicateRejected() {
        String userId = "addkey-attest-user";
        String userName = "addkey_attest_user";
        saveUser(userId, userName);
        WebAuthnTestClient key1 = new WebAuthnTestClient();
        enableWebauthnDirectly(userId);
        saveCredentialDirectly(userId, key1, 5L, NopAuthConstants.MFA_STATUS_ENABLED);
        IServiceContext ctx = ctx(userId, userName, "sess-attest");

        // 空 attestation（载体缺失）→ MFA_FAIL，计数 add 行不消费
        MfaWebauthnAddKeyBeginResult begin = ormTemplate.runInSession(s ->
                userBizModel.webauthnBeginAddKey(ctx));
        NopException bad = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.confirmWebauthnAddKey(begin.getAddChallengeToken(),
                    new io.nop.auth.api.messages.WebAuthnAttestation(),
                    begin.getVerifyChallengeToken(),
                    key1.assert_(begin.getAssertionOptions().getChallenge(), 6L), ctx);
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), bad.getErrorCode());
        assertNotNull(mfaChallengeStore.peek(begin.getAddChallengeToken()),
                "attestation failure counts add row but does not consume (below limit)");

        // 同钥匙重复注册（既有 key1 再 attest）→ MFA_FAIL + already registered
        MfaWebauthnAddKeyBeginResult begin2 = ormTemplate.runInSession(s ->
                userBizModel.webauthnBeginAddKey(ctx));
        NopException dup = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.confirmWebauthnAddKey(begin2.getAddChallengeToken(),
                    key1.attest(begin2.getCreationOptions().getChallenge(), 9L),
                    begin2.getVerifyChallengeToken(),
                    key1.assert_(begin2.getAssertionOptions().getChallenge(), 7L), ctx);
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), dup.getErrorCode());
        assertTrue(dup.getMessage().contains("already registered"), "duplicate must be explicit");
    }

    // ===================== 6. 多钥匙语境 last-credential 守卫 + remove（Phase 2 已标注） =====================

    @Test
    void testLastCredentialGuardInMultiKeyContext() {
        String userId = "addkey-guard-user";
        String userName = "addkey_guard_user";
        saveUser(userId, userName);
        WebAuthnTestClient key1 = new WebAuthnTestClient();
        WebAuthnTestClient key2 = new WebAuthnTestClient();
        enableWebauthnDirectly(userId);
        saveCredentialDirectly(userId, key1, 5L, NopAuthConstants.MFA_STATUS_ENABLED);
        saveCredentialDirectly(userId, key2, 3L, NopAuthConstants.MFA_STATUS_ENABLED);
        IServiceContext ctx = ctx(userId, userName, "sess-guard");

        // 添加第三把
        MfaWebauthnAddKeyBeginResult begin = ormTemplate.runInSession(s ->
                userBizModel.webauthnBeginAddKey(ctx));
        WebAuthnTestClient key3 = new WebAuthnTestClient();
        ormTemplate.runInSession(s -> {
            userBizModel.confirmWebauthnAddKey(begin.getAddChallengeToken(),
                    key3.attest(begin.getCreationOptions().getChallenge(), 0L),
                    begin.getVerifyChallengeToken(),
                    key1.assert_(begin.getAssertionOptions().getChallenge(), 6L), ctx);
            return null;
        });

        // 3 把 enabled：删 key1（非最后）→ 成功；物理删除后不可见
        String key1Sid = findCredentialByCredentialId(key1.credentialIdB64Url()).getSid();
        ormTemplate.runInSession(s -> {
            userBizModel.removeWebauthnCredential(key1Sid, ctx);
            return null;
        });
        assertNull(findCredentialByCredentialId(key1.credentialIdB64Url()), "removed key must disappear");

        // 删 key2 → 1 把剩余；最后一把 → LAST_CREDENTIAL 拒绝
        String key2Sid = findCredentialByCredentialId(key2.credentialIdB64Url()).getSid();
        ormTemplate.runInSession(s -> {
            userBizModel.removeWebauthnCredential(key2Sid, ctx);
            return null;
        });
        String key3Sid = findCredentialByCredentialId(key3.credentialIdB64Url()).getSid();
        NopException last = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.removeWebauthnCredential(key3Sid, ctx);
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_LAST_CREDENTIAL.getErrorCode(), last.getErrorCode(),
                "last enabled credential must be guarded even in add-key context");
    }

    // ===================== Helpers: login operations =====================

    private LoginResult doPasswordLogin(String userName) {
        LoginRequest req = new LoginRequest();
        req.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        req.setPrincipalId(userName);
        req.setPrincipalSecret("123");
        IServiceContext c = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.loginAsync(req, c));
    }

    // ===================== Helpers: data setup =====================

    private void saveUser(String userId, String userName) {
        SHA256PasswordEncoder encoder = new SHA256PasswordEncoder();
        String salt = encoder.generateSalt();
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
            NopAuthUser user = dao.newEntity();
            user.setUserId(userId);
            user.setUserName(userName);
            user.setNickName(userName);
            user.setPassword(encoder.encodePassword(salt, "123"));
            user.setSalt(salt);
            user.setOpenId(userId);
            user.setUserType(1);
            user.setStatus(1);
            user.setGender(1);
            user.setTenantId(TENANT_ID);
            dao.saveEntity(user);
            return null;
        });
    }

    private void enableWebauthnDirectly(String userId) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaSetting> settingDao = daoProvider.daoFor(NopAuthMfaSetting.class);
            NopAuthMfaSetting setting = settingDao.newEntity();
            setting.setUserId(userId);
            setting.setMfaType(NopAuthConstants.MFA_TYPE_WEBAUTHN);
            setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
            setting.setTenantId(TENANT_ID);
            settingDao.saveEntity(setting);
            return null;
        });
    }

    private void enableTotpDirectly(String userId) {
        TOTPAuthenticator totp = new TOTPAuthenticator();
        String encrypted = totp.getCipher().encrypt(totp.generateSecret());
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaSetting> dao = daoProvider.daoFor(NopAuthMfaSetting.class);
            NopAuthMfaSetting setting = dao.newEntity();
            setting.setUserId(userId);
            setting.setMfaType(NopAuthConstants.MFA_TYPE_TOTP);
            setting.setSecret(encrypted);
            setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
            setting.setTenantId(TENANT_ID);
            dao.saveEntity(setting);
            return null;
        });
    }

    private void saveCredentialDirectly(String userId, WebAuthnTestClient client, long signCount, String status) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaCredential> dao = daoProvider.daoFor(NopAuthMfaCredential.class);
            NopAuthMfaCredential c = dao.newEntity();
            c.setUserId(userId);
            c.setCredentialId(client.credentialIdB64Url());
            c.setPublicKey(client.publicKeyCoseB64Url());
            c.setSignCount(signCount);
            c.setStatus(status);
            c.setTenantId(TENANT_ID);
            dao.saveEntity(c);
            return null;
        });
    }

    private String saveRecoveryCodeDirectly(String userId, String codeHash) {
        return ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaRecoveryCode> dao = daoProvider.daoFor(NopAuthMfaRecoveryCode.class);
            NopAuthMfaRecoveryCode row = dao.newEntity();
            row.setUserId(userId);
            row.setCodeHash(codeHash);
            row.setUsed((byte) 0);
            dao.saveEntity(row);
            return row.getSid();
        });
    }

    private String saveTrustedDeviceDirectly(String userId, String deviceHash) {
        return ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaTrustedDevice> dao = daoProvider.daoFor(NopAuthMfaTrustedDevice.class);
            NopAuthMfaTrustedDevice row = dao.newEntity();
            row.setUserId(userId);
            row.setDeviceHash(deviceHash);
            row.setDeviceName("test-device");
            row.setExpireAt(new Timestamp(System.currentTimeMillis() + 3_600_000L));
            dao.saveEntity(row);
            return row.getSid();
        });
    }

    private int countRecoveryCodes(String userId) {
        return ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaRecoveryCode> dao = daoProvider.daoFor(NopAuthMfaRecoveryCode.class);
            NopAuthMfaRecoveryCode example = dao.newEntity();
            example.setUserId(userId);
            return dao.findAllByExample(example).size();
        });
    }

    private NopAuthMfaCredential findCredentialByCredentialId(String credentialId) {
        return ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaCredential> dao = daoProvider.daoFor(NopAuthMfaCredential.class);
            NopAuthMfaCredential example = dao.newEntity();
            example.setCredentialId(credentialId);
            List<NopAuthMfaCredential> found = dao.findAllByExample(example);
            return found.isEmpty() ? null : found.get(0);
        });
    }

    /** Build a self-service ServiceContext with session binding（webauthn ceremony 会话绑定用）。 */
    private IServiceContext ctx(String userId, String userName, String sessionId) {
        ServiceContextImpl c = new ServiceContextImpl();
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userName);
        uc.setTenantId(TENANT_ID);
        uc.setSessionId(sessionId);
        c.setUserContext(uc);
        return c;
    }

    // ===================== H2 + wiring（TestWebAuthnMfaE2E 同型） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:mfa-webauthn-addkey-e2e-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("mfa-webauthn-addkey", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);

        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new io.nop.core.lang.sql.SQL(createSql));

        daoProvider = new OrmDaoProvider(ormTemplate);
    }

    private void wireRealBeans() {
        JwtAuthTokenProvider authTokenProvider = new JwtAuthTokenProvider();
        authTokenProvider.setEncKey("test-enc-key-mfa-webauthn-addkey");

        LocalUserContextCache userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        SHA256PasswordEncoder passwordEncoder = new SHA256PasswordEncoder();
        TOTPAuthenticator totpAuthenticator = new TOTPAuthenticator();
        mfaChallengeStore = new LocalMfaChallengeStore();
        LocalSmsCodeStore smsCodeStore = new LocalSmsCodeStore();
        webAuthnAuthenticator = new WebAuthnAuthenticator();
        auditService = new CapturingAuditService();

        MfaFactorVerifier verifier = new MfaFactorVerifier();
        setField(verifier, "totpAuthenticator", totpAuthenticator);
        setField(verifier, "smsCodeStore", new LocalSmsCodeStore());
        setField(verifier, "daoProvider", daoProvider);
        setField(verifier, "jdbcTemplate", jdbcTemplate);
        setField(verifier, "webAuthnAuthenticator", webAuthnAuthenticator);
        setField(verifier, "auditService", auditService);

        loginService = new LoginServiceImpl();
        setField(loginService, "daoProvider", daoProvider);
        setField(loginService, "passwordEncoder", passwordEncoder);
        setField(loginService, "auditService", auditService);
        setField(loginService, "authTokenProvider", authTokenProvider);
        setField(loginService, "userContextCache", userContextCache);
        setField(loginService, "loginSessionStore", new UuidSessionStore());
        setField(loginService, "mfaChallengeStore", mfaChallengeStore);
        setField(loginService, "smsCodeStore", smsCodeStore);
        setField(loginService, "totpAuthenticator", totpAuthenticator);
        setField(loginService, "mfaFactorVerifier", verifier);
        loginService.setReturnDeptName(false);

        userBizModel = new NopAuthUserBizModel();
        setField(userBizModel, "daoProvider", daoProvider);
        setField(userBizModel, "passwordEncoder", passwordEncoder);
        setField(userBizModel, "totpAuthenticator", totpAuthenticator);
        setField(userBizModel, "smsCodeStore", smsCodeStore);
        setField(userBizModel, "mfaChallengeStore", mfaChallengeStore);
        setField(userBizModel, "mfaFactorVerifier", verifier);
        setField(userBizModel, "webAuthnAuthenticator", webAuthnAuthenticator);
        setField(userBizModel, "auditService", auditService);

        loginApiBizModel = new LoginApiBizModel();
        setField(loginApiBizModel, "loginService", loginService);
        setField(loginApiBizModel, "mfaChallengeStore", mfaChallengeStore);
        setField(loginApiBizModel, "smsCodeStore", smsCodeStore);
        setField(loginApiBizModel, "mfaFactorVerifier", verifier);
        setField(loginApiBizModel, "daoProvider", daoProvider);
        setField(loginApiBizModel, "auditService", auditService);
        setField(loginApiBizModel, "webAuthnAuthenticator", webAuthnAuthenticator);
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

    // ===================== Stubs =====================

    static class CapturingAuditService implements io.nop.api.core.audit.IAuditService {
        final List<io.nop.api.core.audit.AuditRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public boolean isAllProcessed() {
            return true;
        }

        @Override
        public void saveAudit(io.nop.api.core.audit.AuditRequest request) {
            requests.add(request);
        }
    }

    static class UuidSessionStore implements ILoginSessionStore {
        @Override
        public SessionInfo getSessionInfoForUser(String userName) {
            return null;
        }

        @Override
        public String saveSession(IUserContext userContext, LoginRequest request, Map<String, Object> headers) {
            return StringHelper.generateUUID();
        }

        @Override
        public void logoutSession(String sessionId, int logoutType, String logoutUser) {
        }

        @Override
        public List<String> getActionSessions(String userName) {
            return Collections.emptyList();
        }
    }

    static class MinimalBeanProvider implements io.nop.api.core.ioc.IBeanProvider {
        @Override
        public boolean containsBean(String name) {
            return false;
        }

        @Override
        public <T> T getBeanByType(Class<T> clazz) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot instantiate " + clazz.getName(), e);
            }
        }

        @Override
        public Object getBean(String name) {
            return null;
        }

        @Override
        public String getBeanScope(String name) {
            return null;
        }
    }
}
