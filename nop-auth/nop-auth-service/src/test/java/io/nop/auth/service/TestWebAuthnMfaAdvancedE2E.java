/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.MfaVerifyOperationRequest;
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
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaCredential;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.biz.dto.MfaBindResult;
import io.nop.auth.service.biz.dto.MfaWebauthnBeginResult;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.auth.service.mfa.MfaChallengeHelper;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.auth.service.mfa.MfaLoginPolicyServiceImpl;
import io.nop.auth.service.mfa.OperationMfaCheckerImpl;
import io.nop.auth.service.mfa.RoleMfaPolicyEvaluator;
import io.nop.auth.service.mfa.WebAuthnAuthenticator;
import io.nop.auth.service.mfa.WebAuthnTestClient;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.crypto.HashHelper;
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
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.auth.service.NopAuthErrors.ARG_CHALLENGE_TOKEN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W14-impl Phase 3：E2E 全链矩阵——防重放矩阵（challenge 一次性/options 幂等只读/signCount
 * 回退/并发写竞态/count=0 审计/origin fail-closed）+ 受限会话 webauthn 升级全链（minMfaLevel=3
 * → proof → bind → confirm（白名单）→ 重新登录两阶段）+ OAuth 入口（MfaLoginPolicyServiceImpl
 * 副本触点③）+ 双 credential 生命周期 + 换绑（webauthn→totp）+ 双 ceremony 组合解绑 + 审计
 * 四事件断言（userName 非空——W13 教训专项）。
 */
class TestWebAuthnMfaAdvancedE2E {

    private static final String TENANT_ID = "0";
    private static final String POLICY_ROLE = "wa-policy-role";
    private static Boolean originalMfaEnabled;

    // ---- H2 + ORM stack ----
    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;
    private IJdbcTemplate jdbcTemplate;

    // ---- real beans ----
    private LocalMfaChallengeStore mfaChallengeStore;
    private LocalSmsCodeStore smsCodeStore;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;
    private NopAuthUserBizModel userBizModel;
    private OperationMfaCheckerImpl operationMfaChecker;
    private WebAuthnAuthenticator webAuthnAuthenticator;
    private CapturingSmsSender smsSender;
    private CapturingAuditService auditService;
    private RoleMfaPolicyEvaluator roleMfaPolicyEvaluator;
    private MfaLoginPolicyServiceImpl mfaLoginPolicyService;

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

    // ===================== 防重放矩阵 =====================

    @Test
    void testChallengeOneTimeReplayRejected() {
        String userId = "wa-rp-user";
        String userName = "wa_rp_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);

        String token = loginChallengeOf(userName);
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(token, new ServiceContextImpl());
        LoginResult ok = ormTemplate.runInSession(s ->
                doMfaVerify(token, client.assert_(options.getChallenge(), 6L)));
        assertNotNull(ok.getAccessToken());

        // 重放（challenge 已消费）→ CHALLENGE_EXPIRED
        WebAuthnAssertion replay = client.assert_(options.getChallenge(), 7L);
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doMfaVerify(token, replay)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void testOptionsIdempotentReadOnlyAndAssertionSingleConsume() {
        String userId = "wa-rp2-user";
        String userName = "wa_rp2_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);

        String token = loginChallengeOf(userName);
        // 多次取 options 幂等：同一 cryptoChallenge（只读复用，无后置更新原语）、不消费 challenge
        WebAuthnRequestOptions first = loginApiBizModel.webauthnAuthOptions(token, new ServiceContextImpl());
        for (int i = 0; i < 3; i++) {
            WebAuthnRequestOptions again = loginApiBizModel.webauthnAuthOptions(token, new ServiceContextImpl());
            assertEquals(first.getChallenge(), again.getChallenge(),
                    "options read must be idempotent (write-once payload, no update primitive)");
        }
        assertNotNull(mfaChallengeStore.peek(token));

        // 同一 cryptoChallenge 的断言只能成功消费一次（第二次 → CHALLENGE_EXPIRED）
        LoginResult ok = ormTemplate.runInSession(s ->
                doMfaVerify(token, client.assert_(first.getChallenge(), 6L)));
        assertNotNull(ok.getAccessToken());
        WebAuthnAssertion second = client.assert_(first.getChallenge(), 7L);
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doMfaVerify(token, second)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void testSignCountRegressionRejectedAcrossChallenges() {
        String userId = "wa-rp3-user";
        String userName = "wa_rp3_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);

        // 第一次断言 count=6 → 通过（DB signCount=6）
        String token1 = loginChallengeOf(userName);
        WebAuthnRequestOptions options1 = loginApiBizModel.webauthnAuthOptions(token1, new ServiceContextImpl());
        ormTemplate.runInSession(s -> doMfaVerify(token1, client.assert_(options1.getChallenge(), 6L)));

        // 第二次断言（新 challenge）用回退 count=6 → 拒绝（克隆检测信号，跨 challenge 持久）
        String token2 = loginChallengeOf(userName);
        WebAuthnRequestOptions options2 = loginApiBizModel.webauthnAuthOptions(token2, new ServiceContextImpl());
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doMfaVerify(token2, client.assert_(options2.getChallenge(), 6L))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), ex.getErrorCode());
        // challenge 未被失败消费（peek 可见——失败计数路径），credential signCount 仍 6
        assertNotNull(mfaChallengeStore.peek(token2));
        assertEquals(Long.valueOf(6L), findCredentialByCredentialId(client.credentialIdB64Url()).getSignCount());
    }

    @Test
    void testSignCountConcurrentWriteRaceDoesNotOverwrite() {
        String userId = "wa-rp4-user";
        String userName = "wa_rp4_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);

        // 模拟并发竞态：DB 已被并发方推进到 count=10，本断言带 count=7（< 10）→
        // 条件 UPDATE WHERE SIGN_COUNT<7 affected=0 → 按验证失败处理（不覆盖更大计数）
        bumpSignCountDirectly(client.credentialIdB64Url(), 10L);
        String token = loginChallengeOf(userName);
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(token, new ServiceContextImpl());
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doMfaVerify(token, client.assert_(options.getChallenge(), 7L))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), ex.getErrorCode(),
                "race loser must fail (conditional update affected 0)");
        assertEquals(Long.valueOf(10L), findCredentialByCredentialId(client.credentialIdB64Url()).getSignCount(),
                "loser must not overwrite the larger concurrent count");
    }

    @Test
    void testZeroCounterAuthenticatorSkipsMonotonicAndAudits() {
        String userId = "wa-rp5-user";
        String userName = "wa_rp5_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 0L); // count=0 认证器（协议允许的无计数实现）

        String token = loginChallengeOf(userName);
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(token, new ServiceContextImpl());
        LoginResult ok = assertDoesNotThrow(() -> ormTemplate.runInSession(s ->
                doMfaVerify(token, client.assert_(options.getChallenge(), 0L))),
                "zero-counter authenticator must still verify (skip monotonic check)");
        assertNotNull(ok.getAccessToken());
        assertEquals(Long.valueOf(0L), findCredentialByCredentialId(client.credentialIdB64Url()).getSignCount(),
                "zero-counter must not advance signCount");

        // 审计标记（watch-only）+ userName 非空（W13 教训专项）
        AuditRequest audit = auditService.findLast("webauthn-assertion-success");
        assertNotNull(audit, "zero-counter assertion success must be audited");
        assertNotNull(audit.getUserName(), "audit userName must be non-null (NopAuthOpLog non-null column)");
        assertTrue(audit.getRequestData().contains("zero-counter-authenticator"),
                "zero-counter audit marker must be recorded: " + audit.getRequestData());
    }

    @Test
    void testOriginMismatchRejectedFailClosed() {
        String userId = "wa-rp6-user";
        String userName = "wa_rp6_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);

        String token = loginChallengeOf(userName);
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(token, new ServiceContextImpl());
        WebAuthnAssertion evil = client.assert_(options.getChallenge(), 6L, "https://evil.example.net",
                WebAuthnTestClient.RP_ID, null);
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doMfaVerify(token, evil)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), ex.getErrorCode(),
                "origin mismatch must be rejected (fail-closed anti-phishing)");
        AuditRequest audit = auditService.findLast("webauthn-assertion-fail");
        assertNotNull(audit, "origin-mismatch failure must be audited");
        assertNotNull(audit.getUserName());
    }

    // ===================== 受限会话 webauthn 升级全链（minMfaLevel=3） =====================

    @Test
    void testRestrictedSessionWebauthnUpgradeFullChain() {
        String userId = "wa-upgrade-user";
        String userName = "wa_upgrade_user";
        saveUser(userId, userName, "13800138002");
        savePolicyRole(POLICY_ROLE, 3);
        assignRole(userId, POLICY_ROLE);
        WebAuthnTestClient client = new WebAuthnTestClient();

        // 1. 受限登录（无 MFA + 策略 level 3 → mfaRestricted=TRUE，session 带受限标志）
        LoginResult restricted = ormTemplate.runInSession(s -> doPasswordLogin(userName));
        assertEquals(Boolean.TRUE, restricted.getMfaRestricted(), "level-3 policy with no MFA => restricted");

        // 2. 受限会话内 bindMfa(webauthn) 无 proof → 发码引导（proof 前置继承）
        IServiceContext rCtx = ctx(userId, userName, "sess-upg");
        ((UserContextImpl) rCtx.getUserContext()).setMfaRestricted(true);
        NopException proof = assertThrows(NopException.class, () -> ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, null, rCtx)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED.getErrorCode(), proof.getErrorCode());
        String proofCode = smsSender.lastMessage.get().getParams().get(0);

        // 3. verifyChannelProof → proof 票
        String proofToken = ormTemplate.runInSession(s -> loginApiBizModel.verifyChannelProof(proofCode, rCtx));

        // 4. bindMfa(webauthn, proof) → creationOptions（受限会话内可达）
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, proofToken, rCtx));
        assertNotNull(bind.getCreationOptions());

        // 5. confirmWebauthnRegistration——受限会话白名单放行专项断言（checker 直调：
        //    executor 路径同判定，直调钉定白名单常量本身）
        assertDoesNotThrow(() -> operationMfaChecker.check("NopAuthUser__confirmWebauthnRegistration",
                rCtx.getUserContext(), null),
                "confirm must be whitelisted for the restricted upgrade path");
        List<String> recovery = ormTemplate.runInSession(s -> userBizModel.confirmWebauthnRegistration(
                bind.getChallengeToken(),
                client.attest(bind.getCreationOptions().getChallenge(), 5L), rCtx));
        assertEquals(10, recovery.size());

        // 6. 重新登录：webauthn（level 3）≥ 策略 3 → 不再受限，走完整两阶段（MFA_REQUIRED → 断言 → token）
        NopException mfaRequired = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin(userName)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), mfaRequired.getErrorCode());
        String loginToken = (String) mfaRequired.getParam(ARG_CHALLENGE_TOKEN);
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(loginToken, new ServiceContextImpl());
        LoginResult full = ormTemplate.runInSession(s ->
                doMfaVerify(loginToken, client.assert_(options.getChallenge(), 6L)));
        assertNotNull(full.getAccessToken(), "upgraded user completes full webauthn two-phase login");
        assertNull(full.getMfaRestricted(), "webauthn level 3 satisfies policy — session not restricted");
    }

    // ===================== OAuth 入口（MfaLoginPolicyServiceImpl 副本——触点③） =====================

    @Test
    void testOAuthEntryChallengeCarriesCryptoChallengeAndCompletes() {
        String userId = "wa-oauth-user";
        String userName = "wa_oauth_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);

        // OAuth/SSO 入口经 MfaLoginPolicyServiceImpl 副本判定 → ERR_AUTH_MFA_REQUIRED + challenge
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> {
                    mfaLoginPolicyService.checkMfaForUserName(userName, AuthApiConstants.LOGIN_TYPE_SSO);
                    return null;
                }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        String token = (String) ex.getParam(ARG_CHALLENGE_TOKEN);

        // 触点③同步增量：副本创建的 challenge payload 携带 cryptoChallenge（W13 同步不变式履行）
        MfaChallenge challenge = mfaChallengeStore.peek(token);
        assertNotNull(MfaChallengeHelper.cryptoChallengeOf(challenge),
                "OAuth-entry challenge (copy touchpoint 3) must carry cryptoChallenge");

        // 公开 options → 断言 → completeLogin（SSO 信道类出口 = accessCode）
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(token, new ServiceContextImpl());
        LoginResult result = ormTemplate.runInSession(s ->
                doMfaVerify(token, client.assert_(options.getChallenge(), 6L)));
        assertNotNull(result.getAccessCode(), "channel-typed (SSO) mfaVerify exit yields accessCode");
        assertNull(result.getAccessToken(), "channel-typed exit must not issue accessToken");
    }

    // ===================== 双 credential 生命周期 =====================

    @Test
    void testDualCredentialLifecycle() {
        String userId = "wa-dual-user";
        String userName = "wa_dual_user";
        saveUser(userId, userName, null);
        IServiceContext selfCtx = ctx(userId, userName, "sess-dual");

        // 注册第一把（完整 ceremony）
        WebAuthnTestClient keyA = new WebAuthnTestClient();
        MfaBindResult bindA = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, null, selfCtx));
        ormTemplate.runInSession(s -> userBizModel.confirmWebauthnRegistration(bindA.getChallengeToken(),
                keyA.attest(bindA.getCreationOptions().getChallenge(), 5L), selfCtx));

        // 第二把直接落库（A2-audit D3-F1 修复后 unbind 物理删除 credential 行，"解绑保留行
        // 再重绑"不再累积第二把——多钥匙状态以直接落库构造，断言面不变）
        WebAuthnTestClient keyB = new WebAuthnTestClient();
        saveCredentialDirectly(userId, keyB, 5L, NopAuthConstants.MFA_STATUS_ENABLED);

        // 禁用 B（直接落库——禁用单把不解绑整体）
        setCredentialStatus(keyB.credentialIdB64Url(), NopAuthConstants.MFA_STATUS_DISABLED);

        // 登录：options 的 allowCredentials 只含 enabled 的 A；用 A 断言成功
        String token = loginChallengeOf(userName);
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(token, new ServiceContextImpl());
        assertEquals(1, options.getAllowCredentials().size());
        assertTrue(options.getAllowCredentials().contains(keyA.credentialIdB64Url()),
                "allowCredentials must only contain enabled credentials");
        LoginResult viaA = ormTemplate.runInSession(s ->
                doMfaVerify(token, keyA.assert_(options.getChallenge(), 7L)));
        assertNotNull(viaA.getAccessToken(), "login must succeed with the other (enabled) credential");

        // 移除 disabled 的 B → 允许；移除最后一把 enabled A → LAST_CREDENTIAL 拒绝
        String sidB = findCredentialByCredentialId(keyB.credentialIdB64Url()).getSid();
        ormTemplate.runInSession(s -> {
            userBizModel.removeWebauthnCredential(sidB, selfCtx);
            return null;
        });
        String sidA = findCredentialByCredentialId(keyA.credentialIdB64Url()).getSid();
        NopException last = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.removeWebauthnCredential(sidA, selfCtx);
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_LAST_CREDENTIAL.getErrorCode(), last.getErrorCode());
    }

    // ===================== 换绑：webauthn → totp（一期路径回归） =====================

    @Test
    void testSwitchBindWebauthnToTotp() {
        String userId = "wa-switch-user";
        String userName = "wa_switch_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);
        IServiceContext selfCtx = ctx(userId, userName, "sess-switch");

        // 解绑 webauthn（ceremony）
        MfaWebauthnBeginResult begin = ormTemplate.runInSession(s -> userBizModel.webauthnBeginVerify(selfCtx));
        ormTemplate.runInSession(s -> {
            userBizModel.unbindMfa(null, begin.getChallengeToken(),
                    client.assert_(begin.getRequestOptions().getChallenge(), 6L), selfCtx);
            return null;
        });

        // bindMfa(totp) 一期路径 + confirm（TOTP 码）
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_TOTP, null, selfCtx));
        assertNotNull(bind.getProvisioningUri());
        String base32Secret = extractSecretFromUri(bind.getProvisioningUri());
        ormTemplate.runInSession(s -> userBizModel.confirmMfa(bind.getBindToken(),
                computeTotpCode(base32Secret, System.currentTimeMillis()), selfCtx));

        // 登录 → MFA_REQUIRED（totp）→ 码验证 → token（一期链路无回归）
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin(userName)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals(NopAuthConstants.MFA_TYPE_TOTP, ex.getParam(NopAuthErrors.ARG_MFA_TYPE));
        String token = (String) ex.getParam(ARG_CHALLENGE_TOKEN);

        // confirm 消费了当前窗口——用下一窗口码验证（防重放窗口推进语义）
        NopAuthMfaSetting setting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        long nextWindow = (setting.getLastVerifiedWindow() == null ? 0 : setting.getLastVerifiedWindow()) + 1;
        String code = computeTotpCode(base32Secret, nextWindow * TOTPAuthenticator.PERIOD_SECONDS * 1000L);
        LoginResult result = ormTemplate.runInSession(s -> doMfaVerifyCode(token, code));
        assertNotNull(result.getAccessToken(), "switched totp user completes phase-one mfaVerify path");
    }

    // ===================== 双 ceremony 组合：operation-mfa 开启下的解绑 =====================

    @Test
    void testDoubleCeremonyUnbindWithOperationMfaEnabled() {
        String userId = "wa-double-user";
        String userName = "wa_double_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);
        IServiceContext selfCtx = ctx(userId, userName, "sess-double");

        IConfigProvider provider = AppConfig.getConfigProvider();
        Boolean original = provider.getConfigValue("nop.auth.operation-mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.operation-mfa.enabled", true);
        try {
            // ceremony 1：操作级票（unbindMfa 自身 @MfaRequired → 拦截 → mfaVerifyOperation 断言 → 票）
            NopException opEx = assertThrows(NopException.class, () ->
                    operationMfaChecker.check("NopAuthUser__unbindMfa", selfCtx.getUserContext(), null));
            assertEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), opEx.getErrorCode());
            String opToken = (String) opEx.getParam(ARG_CHALLENGE_TOKEN);
            WebAuthnRequestOptions opOptions = loginApiBizModel.webauthnAuthOptions(opToken, selfCtx);
            MfaVerifyOperationRequest opReq = new MfaVerifyOperationRequest();
            opReq.setChallengeToken(opToken);
            opReq.setAssertion(client.assert_(opOptions.getChallenge(), 6L));
            loginApiBizModel.mfaVerifyOperation(opReq, selfCtx);

            // 票放行操作级检查点（与 totp 用户"输两次码"同构——两段断言非缺陷）
            Map<String, Object> headers = new HashMap<>();
            headers.put(OperationMfaCheckerImpl.HEADER_OP_MFA_TOKEN, opToken);
            assertDoesNotThrow(() -> operationMfaChecker.check("NopAuthUser__unbindMfa",
                    selfCtx.getUserContext(), headers));

            // ceremony 1 后票据断言已推进 signCount（5→6，单调性证据在此读取）
            assertEquals(Long.valueOf(6L), findCredentialByCredentialId(client.credentialIdB64Url()).getSignCount(),
                    "op-ticket assertion must advance signCount (5→6)");

            // ceremony 2：解绑 challenge 断言（count 再推进一次——两把断言各自单调；unbind 成功
            // 本身即证明断言 count 7 严格大于存储值 6，回退/重放会被拒）
            MfaWebauthnBeginResult begin = ormTemplate.runInSession(s -> userBizModel.webauthnBeginVerify(selfCtx));
            ormTemplate.runInSession(s -> {
                userBizModel.unbindMfa(null, begin.getChallengeToken(),
                        client.assert_(begin.getRequestOptions().getChallenge(), 7L), selfCtx);
                return null;
            });
            assertEquals(NopAuthConstants.MFA_STATUS_DISABLED,
                    daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId).getStatus());
            // A2-audit D3-F1（P1 修复）：解绑 = 因子作废 → credential 行物理删除（count 7 的
            // 落库值随行删除不可再读，单调推进由上方 6L 读取 + unbind 成功双重证实）
            assertNull(findCredentialByCredentialId(client.credentialIdB64Url()),
                    "unbind must physically delete credential rows (A2-audit D3-F1)");
        } finally {
            provider.assignConfigValue("nop.auth.operation-mfa.enabled",
                    original != null && original);
        }
    }

    // ===================== A2-audit D3-F1（P1 修复验证）：解绑/管理员重置物理删除 credential 行 =====================

    @Test
    void testUnbindAndAdminResetDeleteCredentialRows() {
        String userId = "wa-inval-user";
        String userName = "wa_inval_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);
        IServiceContext selfCtx = ctx(userId, userName, "sess-inval");

        // (a) unbind ceremony → credential 行物理删除（旧行为：残留 enabled，重绑后复活被窃钥匙）
        MfaWebauthnBeginResult begin = ormTemplate.runInSession(s -> userBizModel.webauthnBeginVerify(selfCtx));
        ormTemplate.runInSession(s -> {
            userBizModel.unbindMfa(null, begin.getChallengeToken(),
                    client.assert_(begin.getRequestOptions().getChallenge(), 6L), selfCtx);
            return null;
        });
        assertEquals(NopAuthConstants.MFA_STATUS_DISABLED,
                daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId).getStatus());
        assertNull(findCredentialByCredentialId(client.credentialIdB64Url()),
                "unbind must physically delete the credential row (A2-audit D3-F1)");

        // (b) 同钥匙复注册可行（credentialId 唯一键已随物理删除释放——逻辑删除会撞唯一约束）
        MfaBindResult rebind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, null, selfCtx));
        ormTemplate.runInSession(s -> userBizModel.confirmWebauthnRegistration(rebind.getChallengeToken(),
                client.attest(rebind.getCreationOptions().getChallenge(), 7L), selfCtx));
        assertNotNull(findCredentialByCredentialId(client.credentialIdB64Url()),
                "re-registering the same key after full unbind must succeed (unique key freed)");

        // (c) 管理员重置 → credential 行物理删除（"全因子作废"语义闭合）
        ormTemplate.runInSession(s -> {
            userBizModel.resetUserMfa(userId, adminCtx("wa-inval-admin"));
            return null;
        });
        assertNull(findCredentialByCredentialId(client.credentialIdB64Url()),
                "admin reset must physically delete the credential row (A2-audit D3-F1)");
        assertEquals(NopAuthConstants.MFA_STATUS_DISABLED,
                daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId).getStatus());
    }

    // ===================== 审计四事件（userName 非空——W13 教训专项） =====================

    @Test
    void testAuditEventsForAllCeremonies() {
        String userId = "wa-audit-user";
        String userName = "wa_audit_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        IServiceContext selfCtx = ctx(userId, userName, "sess-audit");

        // 注册失败事件先走（pending 期间 bindMfa 覆盖写允许——fail 路径不 enabled）
        MfaBindResult badBind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, null, selfCtx));
        String badToken = badBind.getChallengeToken();
        assertThrows(NopException.class, () -> ormTemplate.runInSession(s ->
                userBizModel.confirmWebauthnRegistration(badToken,
                        new io.nop.auth.api.messages.WebAuthnAttestation(), selfCtx)));
        assertAuditEvent("webauthn-register-fail", userName);

        // 注册成功事件（重新 bind 覆盖 pending → 合法 attestation confirm）
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, null, selfCtx));
        ormTemplate.runInSession(s -> userBizModel.confirmWebauthnRegistration(bind.getChallengeToken(),
                client.attest(bind.getCreationOptions().getChallenge(), 5L), selfCtx));
        assertAuditEvent("webauthn-register-ok", userName);

        // 认证成功/失败事件（verifier 组件内）
        String token = loginChallengeOf(userName);
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(token, new ServiceContextImpl());
        ormTemplate.runInSession(s -> doMfaVerify(token, client.assert_(options.getChallenge(), 6L)));
        assertAuditEvent("webauthn-assertion-success", userName);

        String token2 = loginChallengeOf(userName);
        WebAuthnRequestOptions options2 = loginApiBizModel.webauthnAuthOptions(token2, new ServiceContextImpl());
        assertThrows(NopException.class, () -> ormTemplate.runInSession(s ->
                doMfaVerify(token2, client.assert_(options2.getChallenge(), 5L)))); // 回退 → fail
        assertAuditEvent("webauthn-assertion-fail", userName);

        // 解绑事件 + credential 移除事件
        MfaWebauthnBeginResult begin = ormTemplate.runInSession(s -> userBizModel.webauthnBeginVerify(selfCtx));
        ormTemplate.runInSession(s -> {
            userBizModel.unbindMfa(null, begin.getChallengeToken(),
                    client.assert_(begin.getRequestOptions().getChallenge(), 7L), selfCtx);
            return null;
        });
        assertAuditEvent("webauthn-unbind-ok", userName);

        // credential 移除事件（移除 disabled 的 second 把——enabled 的 client 把因
        // "最后一把 enabled"守卫不可移除，该守卫已有专项用例）
        WebAuthnTestClient second = new WebAuthnTestClient();
        saveCredentialDirectly(userId, second, 0L, NopAuthConstants.MFA_STATUS_DISABLED);
        String sid = findCredentialByCredentialId(second.credentialIdB64Url()).getSid();
        ormTemplate.runInSession(s -> {
            userBizModel.removeWebauthnCredential(sid, selfCtx);
            return null;
        });
        assertAuditEvent("webauthn-credential-removed", userName);

        // 全部事件 userName 非空（列非空约束——批处理整批回滚风险）
        for (AuditRequest audit : auditService.requests) {
            assertNotNull(audit.getUserName(), "audit userName must never be null: " + audit.getDescription());
        }
    }

    // ===================== Helpers =====================

    private void assertAuditEvent(String event, String userName) {
        AuditRequest audit = auditService.findLast(event);
        assertNotNull(audit, event + " must be audited");
        assertEquals(userName, audit.getUserName(), event + " audit userName");
    }

    private String loginChallengeOf(String userName) {
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin(userName)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        return (String) ex.getParam(ARG_CHALLENGE_TOKEN);
    }

    private LoginResult doPasswordLogin(String userName) {
        LoginRequest req = new LoginRequest();
        req.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        req.setPrincipalId(userName);
        req.setPrincipalSecret("123");
        IServiceContext c = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.loginAsync(req, c));
    }

    private LoginResult doMfaVerify(String challengeToken, WebAuthnAssertion assertion) {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setChallengeToken(challengeToken);
        req.setAssertion(assertion);
        IServiceContext c = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(req, c));
    }

    private LoginResult doMfaVerifyCode(String challengeToken, String code) {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setChallengeToken(challengeToken);
        req.setCode(code);
        IServiceContext c = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(req, c));
    }

    private void saveUser(String userId, String userName, String phone) {
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
            if (phone != null) {
                user.setPhone(phone);
            }
            dao.saveEntity(user);
            return null;
        });
    }

    private void savePolicyRole(String roleId, int minMfaLevel) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthRole> roleDao = daoProvider.daoFor(NopAuthRole.class);
            NopAuthRole role = roleDao.newEntity();
            role.setRoleId(roleId);
            role.setRoleName(roleId);
            roleDao.saveEntity(role);

            IEntityDao<NopAuthRoleMfaPolicy> policyDao = daoProvider.daoFor(NopAuthRoleMfaPolicy.class);
            NopAuthRoleMfaPolicy policy = policyDao.newEntity();
            policy.setRoleId(roleId);
            policy.setMinMfaLevel(minMfaLevel);
            policy.setAllowTrustedDevice((byte) 1);
            policyDao.saveEntity(policy);
            return null;
        });
    }

    private void assignRole(String userId, String roleId) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUserRole> mappingDao = daoProvider.daoFor(NopAuthUserRole.class);
            NopAuthUserRole mapping = mappingDao.newEntity();
            mapping.setUserId(userId);
            mapping.setRoleId(roleId);
            mappingDao.saveEntity(mapping);
            return null;
        });
    }

    private void enableWebauthnDirectly(String userId, WebAuthnTestClient client, long signCount) {
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
        saveCredentialDirectly(userId, client, signCount, NopAuthConstants.MFA_STATUS_ENABLED);
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

    private void setCredentialStatus(String credentialId, String status) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaCredential> dao = daoProvider.daoFor(NopAuthMfaCredential.class);
            NopAuthMfaCredential c = findCredentialByCredentialId(credentialId);
            c.setStatus(status);
            dao.updateEntityDirectly(c);
            return null;
        });
    }

    private void bumpSignCountDirectly(String credentialId, long signCount) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaCredential> dao = daoProvider.daoFor(NopAuthMfaCredential.class);
            NopAuthMfaCredential c = findCredentialByCredentialId(credentialId);
            c.setSignCount(signCount);
            dao.updateEntityDirectly(c);
            return null;
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

    /** 管理员上下文（resetUserMfa requireAdmin 运行时校验——TestTrustedDeviceE2E 同型）。 */
    private IServiceContext adminCtx(String userId) {
        ServiceContextImpl c = new ServiceContextImpl();
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userId);
        uc.setTenantId(TENANT_ID);
        java.util.Set<String> roles = new java.util.HashSet<>();
        roles.add(NopAuthConstants.ROLE_ADMIN);
        uc.setRoles(roles);
        c.setUserContext(uc);
        return c;
    }

    private static String extractSecretFromUri(String uri) {
        int idx = uri.indexOf("secret=");
        int end = uri.indexOf("&", idx);
        return uri.substring(idx + "secret=".length(), end > 0 ? end : uri.length());
    }

    // ===================== TOTP 计算（TestMfaUserSelfService 同型） =====================

    private static String computeTotpCode(String base32Secret, long timeMillis) {
        byte[] secretBytes = base32Decode(base32Secret);
        long window = timeMillis / 1000L / TOTPAuthenticator.PERIOD_SECONDS;
        return computeHotp(secretBytes, window);
    }

    private static String computeHotp(byte[] secret, long counter) {
        byte[] counterBytes = new byte[8];
        long t = counter;
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (t & 0xFF);
            t >>>= 8;
        }
        byte[] hash = HashHelper.hmac(TOTPAuthenticator.HMAC_ALGORITHM, counterBytes, secret);
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
        for (char c : encoded.toCharArray()) {
            int val = alphabet.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    // ===================== H2 + wiring（TestWebAuthnMfaE2E 同型） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:mfa-webauthn-adv-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("mfa-webauthn-adv", CacheConfig.newConfig(100)));
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
        authTokenProvider.setEncKey("test-enc-key-mfa-webauthn-adv");

        LocalUserContextCache userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        SHA256PasswordEncoder passwordEncoder = new SHA256PasswordEncoder();
        TOTPAuthenticator totpAuthenticator = new TOTPAuthenticator();
        mfaChallengeStore = new LocalMfaChallengeStore();
        smsCodeStore = new LocalSmsCodeStore();
        smsSender = new CapturingSmsSender();
        webAuthnAuthenticator = new WebAuthnAuthenticator();
        auditService = new CapturingAuditService();

        roleMfaPolicyEvaluator = new RoleMfaPolicyEvaluator();
        setField(roleMfaPolicyEvaluator, "daoProvider", daoProvider);

        MfaFactorVerifier mfaFactorVerifier = new MfaFactorVerifier();
        setField(mfaFactorVerifier, "totpAuthenticator", totpAuthenticator);
        setField(mfaFactorVerifier, "smsCodeStore", smsCodeStore);
        setField(mfaFactorVerifier, "daoProvider", daoProvider);
        setField(mfaFactorVerifier, "ormTemplate", ormTemplate);
        setField(mfaFactorVerifier, "webAuthnAuthenticator", webAuthnAuthenticator);
        setField(mfaFactorVerifier, "auditService", auditService);

        loginService = new LoginServiceImpl();
        setField(loginService, "daoProvider", daoProvider);
        setField(loginService, "passwordEncoder", passwordEncoder);
        setField(loginService, "auditService", new NoopAuditService());
        setField(loginService, "authTokenProvider", authTokenProvider);
        setField(loginService, "userContextCache", userContextCache);
        setField(loginService, "loginSessionStore", new UuidSessionStore());
        setField(loginService, "mfaChallengeStore", mfaChallengeStore);
        setField(loginService, "smsCodeStore", smsCodeStore);
        setField(loginService, "totpAuthenticator", totpAuthenticator);
        setField(loginService, "mfaFactorVerifier", mfaFactorVerifier);
        setField(loginService, "roleMfaPolicyEvaluator", roleMfaPolicyEvaluator);
        loginService.setReturnDeptName(false);

        userBizModel = new NopAuthUserBizModel();
        setField(userBizModel, "daoProvider", daoProvider);
        setField(userBizModel, "passwordEncoder", passwordEncoder);
        setField(userBizModel, "totpAuthenticator", totpAuthenticator);
        setField(userBizModel, "smsCodeStore", smsCodeStore);
        setField(userBizModel, "smsSender", smsSender);
        setField(userBizModel, "mfaChallengeStore", mfaChallengeStore);
        setField(userBizModel, "mfaFactorVerifier", mfaFactorVerifier);
        setField(userBizModel, "webAuthnAuthenticator", webAuthnAuthenticator);
        setField(userBizModel, "auditService", auditService);

        loginApiBizModel = new LoginApiBizModel();
        setField(loginApiBizModel, "loginService", loginService);
        setField(loginApiBizModel, "mfaChallengeStore", mfaChallengeStore);
        setField(loginApiBizModel, "smsCodeStore", smsCodeStore);
        setField(loginApiBizModel, "mfaFactorVerifier", mfaFactorVerifier);
        setField(loginApiBizModel, "daoProvider", daoProvider);
        setField(loginApiBizModel, "auditService", new NoopAuditService());
        setField(loginApiBizModel, "webAuthnAuthenticator", webAuthnAuthenticator);

        operationMfaChecker = new OperationMfaCheckerImpl();
        setField(operationMfaChecker, "mfaChallengeStore", mfaChallengeStore);
        setField(operationMfaChecker, "daoProvider", daoProvider);
        setField(operationMfaChecker, "auditService", new NoopAuditService());

        mfaLoginPolicyService = new MfaLoginPolicyServiceImpl();
        setField(mfaLoginPolicyService, "daoProvider", daoProvider);
        setField(mfaLoginPolicyService, "mfaChallengeStore", mfaChallengeStore);
        setField(mfaLoginPolicyService, "roleMfaPolicyEvaluator", roleMfaPolicyEvaluator);
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

    static class CapturingSmsSender implements ISmsSender {
        final AtomicReference<SmsMessage> lastMessage = new AtomicReference<>();

        @Override
        public void sendMessage(SmsMessage message) {
            lastMessage.set(message);
        }
    }

    /** 捕获审计事件（四事件断言 + userName 非空专项）。 */
    static class CapturingAuditService implements io.nop.api.core.audit.IAuditService {
        final ConcurrentLinkedQueue<AuditRequest> requests = new ConcurrentLinkedQueue<>();

        @Override
        public boolean isAllProcessed() {
            return true;
        }

        @Override
        public void saveAudit(AuditRequest request) {
            requests.add(request);
        }

        AuditRequest findLast(String event) {
            AuditRequest found = null;
            for (AuditRequest r : requests) {
                if (event.equals(r.getDescription()) || String.valueOf(r.getRequestData()).contains(event)) {
                    found = r;
                }
            }
            return found;
        }
    }

    static class NoopAuditService implements io.nop.api.core.audit.IAuditService {
        @Override
        public boolean isAllProcessed() {
            return true;
        }

        @Override
        public void saveAudit(io.nop.api.core.audit.AuditRequest request) {
        }
    }

    static class UuidSessionStore implements ILoginSessionStore {
        @Override
        public SessionInfo getSessionInfoForUser(String userName) {
            return null;
        }

        @Override
        public String saveSession(io.nop.api.core.auth.IUserContext userContext, LoginRequest request,
                                  Map<String, Object> headers) {
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
