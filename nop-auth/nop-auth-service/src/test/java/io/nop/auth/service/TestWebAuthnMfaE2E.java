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
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaCredential;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.biz.dto.MfaBindResult;
import io.nop.auth.service.biz.dto.MfaWebauthnBeginResult;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.auth.service.mfa.MfaChallengeHelper;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.auth.service.mfa.OperationMfaCheckerImpl;
import io.nop.auth.service.mfa.WebAuthnAuthenticator;
import io.nop.auth.service.mfa.WebAuthnTestClient;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W14-impl Phase 2 端到端验证（设计 §5.3.2 三 ceremony，真实组件 + H2 内存库）：
 * <ol>
 *   <li><b>全链</b>（Anti-Hollow Rule #22）：bindMfa(webauthn) → confirmWebauthnRegistration →
 *       密码登录拦截（ERR_AUTH_MFA_REQUIRED）→ webauthnAuthOptions → mfaVerify(assertion) →
 *       completeLogin 签发 accessToken。</li>
 *   <li><b>接线验证</b>（Rule #23）：MfaFactorVerifier webauthn 分支被登录级 mfaVerify 与
 *       操作级 mfaVerifyOperation 两调用点实际命中（计数断言）。</li>
 *   <li><b>操作级票流</b>：拦截 → options → mfaVerifyOperation(assertion) → 凭票重试放行。</li>
 *   <li><b>无静默跳过</b>：sendMfaCode 对 webauthn/totp 显式 CODE_UNSUPPORTED；解绑 ceremony；
 *       credential 管理负例（重复注册/最后一把移除/越权归一）。</li>
 * </ol>
 */
class TestWebAuthnMfaE2E {

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
    private LocalSmsCodeStore smsCodeStore;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;
    private NopAuthUserBizModel userBizModel;
    private OperationMfaCheckerImpl operationMfaChecker;
    private WebAuthnAuthenticator webAuthnAuthenticator;
    private CapturingSmsSender smsSender;
    private CountingVerifier countingVerifier;

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

    // ===================== 1. 注册 ceremony + 登录 ceremony 全链 =====================

    @Test
    void testE2eBindConfirmLoginOptionsVerifyAccessToken() {
        String userId = "wa-e2e-user";
        String userName = "wa_e2e_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();

        // 1. bindMfa(webauthn) → pending setting + scene=webauthn-register challenge + creationOptions
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, null, ctx(userId, userName, "sess-bind")));
        assertEquals(NopAuthConstants.MFA_TYPE_WEBAUTHN, bind.getMfaType());
        assertNotNull(bind.getChallengeToken(), "webauthn bind must return challengeToken");
        assertNotNull(bind.getCreationOptions(), "webauthn bind must return creationOptions");
        assertNull(bind.getBindToken(), "bindToken does not participate in webauthn ceremony");
        assertNotNull(bind.getCreationOptions().getChallenge(), "creationOptions.challenge (cryptoChallenge)");

        NopAuthMfaSetting pending = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_PENDING, pending.getStatus());
        assertEquals(NopAuthConstants.MFA_TYPE_WEBAUTHN, pending.getMfaType());
        assertNull(pending.getSecret(), "webauthn has no shared secret (secret=null)");

        // payload 一次写入契约：challenge payload 的 cryptoChallenge == creationOptions.challenge
        MfaChallenge c = mfaChallengeStore.peek(bind.getChallengeToken());
        assertNotNull(c);
        assertEquals(MfaChallenge.SCENE_WEBAUTHN_REGISTER, c.getScene());
        assertEquals(bind.getCreationOptions().getChallenge(), MfaChallengeHelper.cryptoChallengeOf(c),
                "creationOptions.challenge must be the payload cryptoChallenge (write-once)");
        assertEquals("sess-bind", MfaChallengeHelper.sessionIdOf(c));

        // 2. confirmWebauthnRegistration（同会话 + 协议合法 attestation）→ credential 落库 + enabled + 恢复码
        List<String> recovery = ormTemplate.runInSession(s -> userBizModel.confirmWebauthnRegistration(
                bind.getChallengeToken(),
                client.attest(bind.getCreationOptions().getChallenge(), 5L),
                ctx(userId, userName, "sess-bind")));
        assertEquals(10, recovery.size(), "confirm must generate 10 recovery codes (对齐一期 confirmMfa)");
        NopAuthMfaSetting enabled = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, enabled.getStatus());
        assertNull(mfaChallengeStore.peek(bind.getChallengeToken()), "confirm must consume challenge (一次性)");

        NopAuthMfaCredential credential = findCredentialByCredentialId(client.credentialIdB64Url());
        assertNotNull(credential, "credential row must be persisted");
        assertEquals(userId, credential.getUserId());
        assertEquals(Long.valueOf(5L), credential.getSignCount());
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, credential.getStatus());
        assertEquals(client.publicKeyCoseB64Url(), credential.getPublicKey());

        // 3. 密码登录 → ERR_AUTH_MFA_REQUIRED（一期异常表达不变，errorParams.mfaType=webauthn）
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin(userName)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        String loginChallengeToken = (String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
        assertEquals(NopAuthConstants.MFA_TYPE_WEBAUTHN, ex.getParam(NopAuthErrors.ARG_MFA_TYPE));

        // 登录级 challenge payload 含 cryptoChallenge（触点①——checkMfaRequired 增量）
        MfaChallenge loginChallenge = mfaChallengeStore.peek(loginChallengeToken);
        assertNotNull(MfaChallengeHelper.cryptoChallengeOf(loginChallenge),
                "login challenge payload must carry cryptoChallenge (touchpoint 1)");

        // 4. webauthnAuthOptions（scene=login 公开访问——无用户上下文）
        IServiceContext anonymous = new ServiceContextImpl();
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(loginChallengeToken, anonymous);
        assertEquals(loginChallenge, mfaChallengeStore.peek(loginChallengeToken), "options read must not consume");
        assertEquals(MfaChallengeHelper.cryptoChallengeOf(loginChallenge), options.getChallenge(),
                "options.challenge = payload cryptoChallenge read-only reuse");
        assertTrue(options.getAllowCredentials().contains(client.credentialIdB64Url()),
                "allowCredentials must contain the enabled credential");

        // 5. mfaVerify(assertion) → completeLogin 签发 accessToken（一期出口不变）
        WebAuthnAssertion assertion = client.assert_(options.getChallenge(), 6L);
        LoginResult result = ormTemplate.runInSession(s -> doMfaVerify(loginChallengeToken, assertion));
        assertNotNull(result.getAccessToken(), "full chain must yield accessToken");
        assertNull(result.getAccessCode(), "password-typed mfaVerify must not yield accessCode");

        // signCount 单调递增写（组件内聚）
        NopAuthMfaCredential after = findCredentialByCredentialId(client.credentialIdB64Url());
        assertEquals(Long.valueOf(6L), after.getSignCount(), "assertion must advance signCount");
    }

    // ===================== 2. 接线验证：verifier webauthn 分支双调用点命中 =====================

    /** 计数 verifier（Rule #23 接线验证：登录级 + 操作级两调用点都经组件 webauthn 分支）。 */
    static class CountingVerifier extends MfaFactorVerifier {
        int webauthnCalls;

        @Override
        protected boolean verifyWebauthn(io.nop.auth.dao.entity.NopAuthMfaSetting setting,
                                          WebAuthnAssertion assertion, MfaChallenge challenge) {
            webauthnCalls++;
            return super.verifyWebauthn(setting, assertion, challenge);
        }
    }

    @Test
    void testVerifierWebauthnBranchHitByLoginAndOperation() {
        String userId = "wa-wiring-user";
        String userName = "wa_wiring_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);

        // 登录级命中
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin(userName)));
        String token = (String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
        WebAuthnRequestOptions options = loginApiBizModel.webauthnAuthOptions(token, new ServiceContextImpl());
        ormTemplate.runInSession(s -> doMfaVerify(token, client.assert_(options.getChallenge(), 6L)));
        assertEquals(1, countingVerifier.webauthnCalls, "login-level mfaVerify must hit verifier webauthn branch");

        // 操作级命中（checker 直调创建 operation challenge → options → mfaVerifyOperation(assertion)）
        IUserContext opCtx = ctx(userId, userName, "sess-wiring").getUserContext();
        IConfigProvider provider = AppConfig.getConfigProvider();
        Boolean original = provider.getConfigValue("nop.auth.operation-mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.operation-mfa.enabled", true);
        try {
            NopException opEx = assertThrows(NopException.class,
                    () -> operationMfaChecker.check("NopAuthUser__generateRecoveryCodes", opCtx, null));
            assertEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), opEx.getErrorCode());
            String opToken = (String) opEx.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
            MfaChallenge opChallenge = mfaChallengeStore.peek(opToken);
            assertNotNull(MfaChallengeHelper.cryptoChallengeOf(opChallenge),
                    "operation challenge payload must carry cryptoChallenge (touchpoint 2)");

            WebAuthnRequestOptions opOptions = loginApiBizModel.webauthnAuthOptions(opToken, ctx(userId, userName, "sess-wiring"));
            MfaVerifyOperationRequest req = new MfaVerifyOperationRequest();
            req.setChallengeToken(opToken);
            req.setAssertion(client.assert_(opOptions.getChallenge(), 7L));
            loginApiBizModel.mfaVerifyOperation(req, ctx(userId, userName, "sess-wiring"));
            assertEquals(2, countingVerifier.webauthnCalls,
                    "operation-level mfaVerifyOperation must hit the same verifier webauthn branch");
        } finally {
            provider.assignConfigValue("nop.auth.operation-mfa.enabled",
                    original != null && original);
        }
    }

    // ===================== 3. 操作级票流全链 =====================

    @Test
    void testOperationLevelWebauthnTicketFlow() {
        String userId = "wa-op-user";
        String userName = "wa_op_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);
        IServiceContext opCtx = ctx(userId, userName, "sess-op");

        IConfigProvider provider = AppConfig.getConfigProvider();
        Boolean original = provider.getConfigValue("nop.auth.operation-mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.operation-mfa.enabled", true);
        try {
            // 1. 拦截 → OPERATION_MFA_REQUIRED（payload = {operation, sessionId, cryptoChallenge}）
            NopException opEx = assertThrows(NopException.class,
                    () -> operationMfaChecker.check("NopAuthUser__generateRecoveryCodes",
                            opCtx.getUserContext(), null));
            assertEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), opEx.getErrorCode());
            String opToken = (String) opEx.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
            Map<String, Object> payload = JsonTool.parseMap(mfaChallengeStore.peek(opToken).getPayload());
            assertEquals("NopAuthUser__generateRecoveryCodes", payload.get("operation"));
            assertEquals("sess-op", payload.get("sessionId"));
            assertTrue(payload.containsKey("cryptoChallenge"), "webauthn operation payload carries cryptoChallenge");

            // 2. 非 login 场景 options 读取的会话绑定：错误会话拒绝、正确会话放行
            NopException wrongSession = assertThrows(NopException.class, () ->
                    loginApiBizModel.webauthnAuthOptions(opToken, ctx(userId, userName, "sess-other")));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED.getErrorCode(), wrongSession.getErrorCode());
            WebAuthnRequestOptions opOptions = loginApiBizModel.webauthnAuthOptions(opToken, opCtx);

            // 3. mfaVerifyOperation(assertion) → markVerified 转票（成功不签发任何凭证）
            MfaVerifyOperationRequest req = new MfaVerifyOperationRequest();
            req.setChallengeToken(opToken);
            req.setAssertion(client.assert_(opOptions.getChallenge(), 6L));
            loginApiBizModel.mfaVerifyOperation(req, opCtx);

            // 4. 凭票重试原操作 → 放行（并发双花防护：一次性消费）
            Map<String, Object> headers = new HashMap<>();
            headers.put(OperationMfaCheckerImpl.HEADER_OP_MFA_TOKEN, opToken);
            assertDoesNotThrow(() -> operationMfaChecker.check("NopAuthUser__generateRecoveryCodes",
                    opCtx.getUserContext(), headers));
            // 票已消费：再试拒绝
            assertThrows(NopException.class, () -> operationMfaChecker.check("NopAuthUser__generateRecoveryCodes",
                    opCtx.getUserContext(), headers));
        } finally {
            provider.assignConfigValue("nop.auth.operation-mfa.enabled",
                    original != null && original);
        }
    }

    // ===================== 4. sendMfaCode 分派：显式拒绝（无静默跳过） =====================

    @Test
    void testSendMfaCodeWebauthnAndTotpExplicitlyRejected() {
        String userId = "wa-sendsms-user";
        String userName = "wa_sendsms_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);

        // webauthn challenge → CODE_UNSUPPORTED（显式拒绝，非静默 no-op）
        NopException webauthnEx = assertThrows(NopException.class, () -> {
            NopException login = assertThrows(NopException.class, () ->
                    ormTemplate.runInSession(s -> doPasswordLogin(userName)));
            loginApiBizModel.sendMfaCode((String) login.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN), null);
        });
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CODE_UNSUPPORTED.getErrorCode(), webauthnEx.getErrorCode());
        assertEquals(NopAuthConstants.MFA_TYPE_WEBAUTHN, webauthnEx.getParam(NopAuthErrors.ARG_MFA_TYPE));

        // totp challenge → 同样显式拒绝（#8 分派骨架的 else 兜底）
        String totpUserId = "wa-sendsms-totp";
        String totpUserName = "wa_sendsms_totp";
        saveUser(totpUserId, totpUserName, null);
        enableTotpDirectly(totpUserId);
        NopException totpEx = assertThrows(NopException.class, () -> {
            NopException login = assertThrows(NopException.class, () ->
                    ormTemplate.runInSession(s -> doPasswordLogin(totpUserName)));
            loginApiBizModel.sendMfaCode((String) login.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN), null);
        });
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CODE_UNSUPPORTED.getErrorCode(), totpEx.getErrorCode());
        assertEquals(NopAuthConstants.MFA_TYPE_TOTP, totpEx.getParam(NopAuthErrors.ARG_MFA_TYPE));
    }

    // ===================== 5. 解绑 ceremony =====================

    @Test
    void testUnbindCeremonyAssertionFlow() {
        String userId = "wa-unbind-user";
        String userName = "wa_unbind_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);
        IServiceContext selfCtx = ctx(userId, userName, "sess-unbind");

        // 发起：scene=webauthn-unbind challenge + requestOptions
        MfaWebauthnBeginResult begin = ormTemplate.runInSession(s -> userBizModel.webauthnBeginVerify(selfCtx));
        assertNotNull(begin.getChallengeToken());
        assertNotNull(begin.getRequestOptions().getChallenge());
        assertTrue(begin.getRequestOptions().getAllowCredentials().contains(client.credentialIdB64Url()));

        // 错误断言（count 回退）→ MFA_FAIL 且 challenge 未消费（失败计数路径）
        WebAuthnAssertion badAssertion = client.assert_(begin.getRequestOptions().getChallenge(), 5L);
        NopException fail = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.unbindMfa(null, begin.getChallengeToken(), badAssertion, selfCtx);
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), fail.getErrorCode());
        assertNotNull(mfaChallengeStore.peek(begin.getChallengeToken()), "failed unbind must not consume challenge");

        // 错误会话 → MFA_FAIL（防跨会话搬运）
        NopException wrongSession = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.unbindMfa(null, begin.getChallengeToken(),
                    client.assert_(begin.getRequestOptions().getChallenge(), 6L), ctx(userId, userName, "sess-x"));
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), wrongSession.getErrorCode());

        // 正确断言（count 递增）→ 解绑成功：setting disabled + 恢复码删除 + challenge 消费
        WebAuthnAssertion goodAssertion = client.assert_(begin.getRequestOptions().getChallenge(), 6L);
        ormTemplate.runInSession(s -> {
            userBizModel.unbindMfa(null, begin.getChallengeToken(), goodAssertion, selfCtx);
            return null;
        });
        NopAuthMfaSetting after = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_DISABLED, after.getStatus());
        assertNull(mfaChallengeStore.peek(begin.getChallengeToken()), "successful unbind must consume challenge");

        // 解绑后登录不再拦截
        LoginResult result = assertDoesNotThrow(() -> ormTemplate.runInSession(s -> doPasswordLogin(userName)));
        assertNotNull(result.getAccessToken());
    }

    // ===================== 6. credential 管理 API =====================

    @Test
    void testCredentialManagementApis() {
        String userId = "wa-mgmt-user";
        String userName = "wa_mgmt_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();
        enableWebauthnDirectly(userId, client, 5L);
        IServiceContext selfCtx = ctx(userId, userName, "sess-mgmt");

        // 第二把（disabled 状态直接落库——禁用单把不解绑整体）
        WebAuthnTestClient second = new WebAuthnTestClient();
        saveCredentialDirectly(userId, second, 0L, NopAuthConstants.MFA_STATUS_DISABLED);

        // list：两把；展示字段不含 credentialId/publicKey（公钥材料不展示）
        List<io.nop.auth.api.beans.NopAuthMfaCredentialOutputBean> list =
                ormTemplate.runInSession(s -> userBizModel.listWebauthnCredentials(selfCtx));
        assertEquals(2, list.size());
        String json = JsonTool.stringify(list);
        assertFalse(json.contains("publicKey"), "credential list must not expose public key material");
        assertFalse(json.contains(client.credentialIdB64Url()), "credential list must not expose credentialId");

        // rename
        String sid = list.get(0).getSid();
        ormTemplate.runInSession(s -> {
            userBizModel.renameWebauthnCredential(sid, "My YubiKey", selfCtx);
            return null;
        });
        assertEquals("My YubiKey", daoProvider.daoFor(NopAuthMfaCredential.class).getEntityById(sid).getName());

        // 越权（另一用户的 credential sid）→ 归一"不存在"
        String otherUserId = "wa-mgmt-other";
        saveUser(otherUserId, "wa_mgmt_other", null);
        WebAuthnTestClient otherClient = new WebAuthnTestClient();
        saveCredentialDirectly(otherUserId, otherClient, 0L, NopAuthConstants.MFA_STATUS_ENABLED);
        String otherSid = findCredentialByCredentialId(otherClient.credentialIdB64Url()).getSid();
        NopException cross = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.removeWebauthnCredential(otherSid, selfCtx);
            return null;
        }));
        assertTrue(cross.getMessage().contains("not found"), "cross-user removal normalized to not-found");

        // remove disabled 把 → 允许（不属"最后一把 enabled"；逻辑删除——按 credentialId 查询不可见）
        String disabledSid = findCredentialByCredentialId(second.credentialIdB64Url()).getSid();
        ormTemplate.runInSession(s -> {
            userBizModel.removeWebauthnCredential(disabledSid, selfCtx);
            return null;
        });
        assertNull(findCredentialByCredentialId(second.credentialIdB64Url()),
                "removed credential must no longer be visible (logical delete)");
        List<io.nop.auth.api.beans.NopAuthMfaCredentialOutputBean> afterRemove =
                ormTemplate.runInSession(s -> userBizModel.listWebauthnCredentials(selfCtx));
        assertEquals(1, afterRemove.size(), "only the enabled credential remains in list");

        // remove 最后一把 enabled → LAST_CREDENTIAL 拒绝（整体解绑走 unbindMfa ceremony）
        String lastSid = findCredentialByCredentialId(client.credentialIdB64Url()).getSid();
        NopException last = assertThrows(NopException.class, () -> ormTemplate.runInSession(s -> {
            userBizModel.removeWebauthnCredential(lastSid, selfCtx);
            return null;
        }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_LAST_CREDENTIAL.getErrorCode(), last.getErrorCode());
    }

    // ===================== 7. 重复注册拒绝 + 失败计数不消费 =====================

    @Test
    void testDuplicateRegistrationRejected() {
        String userId = "wa-dup-user";
        String userName = "wa_dup_user";
        saveUser(userId, userName, null);
        WebAuthnTestClient client = new WebAuthnTestClient();

        // 预置同 key 的 credential 行（等价"该钥匙已注册"）。A2-audit D3-F1 修复后 unbind
        // 物理删除 credential 行，重复注册守卫的覆盖不再依赖"解绑后行保留"旧语义。
        saveCredentialDirectly(userId, client, 5L, NopAuthConstants.MFA_STATUS_ENABLED);

        // bind（覆盖/新建 pending）+ 用同一把钥匙 confirm → 重复注册拒绝
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, null, ctx(userId, userName, "sess-dup")));
        NopException dup = assertThrows(NopException.class, () -> ormTemplate.runInSession(s ->
                userBizModel.confirmWebauthnRegistration(bind.getChallengeToken(),
                        client.attest(bind.getCreationOptions().getChallenge(), 6L),
                        ctx(userId, userName, "sess-dup"))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), dup.getErrorCode());
        assertTrue(dup.getMessage().contains("already registered"), "duplicate registration must be explicit");

        // 同一 credentialId 仍只有一行
        assertEquals(1, daoProvider.daoFor(NopAuthMfaCredential.class).findAllByExample(credentialExample(userId)).size());
    }

    @Test
    void testConfirmFailCountDoesNotConsumeUntilLimit() {
        String userId = "wa-failcnt-user";
        String userName = "wa_failcnt_user";
        saveUser(userId, userName, null);

        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, null, ctx(userId, userName, "sess-fc")));
        // 空 attestation（载体缺失）→ 验证失败路径（fail-closed，不抛库异常）
        io.nop.auth.api.messages.WebAuthnAttestation bad = new io.nop.auth.api.messages.WebAuthnAttestation();

        int maxAttempts = io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_MAX_ATTEMPTS.get();
        for (int i = 0; i < maxAttempts; i++) {
            NopException fail = assertThrows(NopException.class, () -> ormTemplate.runInSession(s ->
                    userBizModel.confirmWebauthnRegistration(bind.getChallengeToken(), bad,
                            ctx(userId, userName, "sess-fc"))));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), fail.getErrorCode());
            if (i < maxAttempts - 1) {
                assertNotNull(mfaChallengeStore.peek(bind.getChallengeToken()),
                        "failure must not consume challenge before limit");
            }
        }
        // 超限作废（incrFailCount ≥ max-attempts → consume）
        assertNull(mfaChallengeStore.peek(bind.getChallengeToken()),
                "challenge must be discarded after max attempts");
    }

    @Test
    void testConfirmSessionBindingEnforced() {
        String userId = "wa-sessbind-user";
        String userName = "wa_sessbind_user";
        saveUser(userId, userName, null);

        // bind 发生在 sess-A；confirm 在 sess-B → BIND_EXPIRED（challenge 会话绑定校验）
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, null, ctx(userId, userName, "sess-A")));
        NopException ex = assertThrows(NopException.class, () -> ormTemplate.runInSession(s ->
                userBizModel.confirmWebauthnRegistration(bind.getChallengeToken(),
                        new WebAuthnTestClient().attest(bind.getCreationOptions().getChallenge(), 0L),
                        ctx(userId, userName, "sess-B"))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_BIND_EXPIRED.getErrorCode(), ex.getErrorCode());
        // setting 未被推进（仍 pending）
        assertEquals(NopAuthConstants.MFA_STATUS_PENDING,
                daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId).getStatus());
    }

    // ===================== 8. 受限会话 proof 前置继承 + 白名单裁定 =====================

    @Test
    void testRestrictedBindWebauthnInheritsProofPrecheck() {
        String userId = "wa-rs-proof-user";
        String userName = "wa_rs_proof_user";
        saveUser(userId, userName, "13800138001");
        IServiceContext restrictedCtx = restrictedCtx(userId, userName, "sess-rs");

        // 受限会话内 bindMfa(webauthn) 无 proof 票 → 登记通道发码 + CHANNEL_PROOF_REQUIRED
        // （W13 前置在类型分派之前——webauthn 路径自动继承，专项断言）
        NopException ex = assertThrows(NopException.class, () -> ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_WEBAUTHN, null, restrictedCtx)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertNotNull(smsSender.lastMessage.get(), "proof code must have been sent to the registered phone");

        // 未创建 webauthn-register challenge（proof 前置阻断，分派未到达）
        assertNull(daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId),
                "proof precheck must run before webauthn dispatch (no pending setting)");
    }

    @Test
    void testRestrictedSessionWhitelistAdjudication() {
        // confirmWebauthnRegistration 入白名单（受限会话可达——升级路径）
        IServiceContext restrictedCtx = restrictedCtx("wa-whitelist-user", "wa_whitelist_user", "sess-wl");
        assertDoesNotThrow(() -> operationMfaChecker.check("NopAuthUser__confirmWebauthnRegistration",
                restrictedCtx.getUserContext(), null),
                "confirmWebauthnRegistration must be whitelisted for restricted sessions (upgrade path)");

        // webauthnBeginVerify / credential 管理 / add-key 双端点不入白名单（裁定：受限用户 setting
        // 不可能为 webauthn——不可达死代码；A2-followup-2 add-key 同构先例）
        for (String op : new String[]{"NopAuthUser__webauthnBeginVerify",
                "NopAuthUser__removeWebauthnCredential", "NopAuthUser__renameWebauthnCredential",
                "NopAuthUser__listWebauthnCredentials",
                "NopAuthUser__webauthnBeginAddKey", "NopAuthUser__confirmWebauthnAddKey"}) {
            NopException ex = assertThrows(NopException.class, () ->
                    operationMfaChecker.check(op, restrictedCtx.getUserContext(), null));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_RESTRICTED_SESSION.getErrorCode(), ex.getErrorCode(),
                    op + " must not be whitelisted (adjudicated unreachable-for-restricted)");
        }
    }

    // ===================== 9. 未绑定 webauthn 用户全流程无感知（一期零回归内联证据） =====================

    @Test
    void testUnboundUserUnaffected() {
        String userId = "wa-plain-user";
        String userName = "wa_plain_user";
        saveUser(userId, userName, null);

        // 直接登录成功（无 MFA 拦截，无 webauthn 介入）
        LoginResult result = assertDoesNotThrow(() -> ormTemplate.runInSession(s -> doPasswordLogin(userName)));
        assertNotNull(result.getAccessToken());
        assertNull(result.getMfaRestricted());

        // bindMfa 非法类型错误消息动态拼接合法值（白名单 #2）
        NopException ex = assertThrows(NopException.class, () -> ormTemplate.runInSession(s ->
                userBizModel.bindMfa("bogus-type", null, ctx(userId, userName, "sess-plain"))));
        assertTrue(ex.getMessage().contains("totp/sms/webauthn"),
                "error message must dynamically list supported types: " + ex.getMessage());
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

    private LoginResult doMfaVerify(String challengeToken, WebAuthnAssertion assertion) {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setChallengeToken(challengeToken);
        req.setAssertion(assertion);
        IServiceContext c = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(req, c));
    }

    // ===================== Helpers: data setup =====================

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

    /** 直接落 enabled webauthn setting + credential 行（流程级测试的快速通道；ceremony 正确性由全链测试证明）。 */
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

    private NopAuthMfaCredential credentialExample(String userId) {
        NopAuthMfaCredential example = new NopAuthMfaCredential();
        example.setUserId(userId);
        return example;
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

    /** 受限会话上下文（W13 mfaRestricted 标志）。 */
    private IServiceContext restrictedCtx(String userId, String userName, String sessionId) {
        ServiceContextImpl c = new ServiceContextImpl();
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userName);
        uc.setTenantId(TENANT_ID);
        uc.setSessionId(sessionId);
        uc.setMfaRestricted(true);
        c.setUserContext(uc);
        return c;
    }

    // ===================== H2 + wiring（TestMfaUserSelfService 同型） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:mfa-webauthn-e2e-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("mfa-webauthn", CacheConfig.newConfig(100)));
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
        authTokenProvider.setEncKey("test-enc-key-mfa-webauthn");

        LocalUserContextCache userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        SHA256PasswordEncoder passwordEncoder = new SHA256PasswordEncoder();
        TOTPAuthenticator totpAuthenticator = new TOTPAuthenticator();
        mfaChallengeStore = new LocalMfaChallengeStore();
        smsCodeStore = new LocalSmsCodeStore();
        smsSender = new CapturingSmsSender();
        webAuthnAuthenticator = new WebAuthnAuthenticator();

        // 计数 verifier（接线验证）：登录级/操作级共用同一实例
        countingVerifier = new CountingVerifier();
        setField(countingVerifier, "totpAuthenticator", totpAuthenticator);
        setField(countingVerifier, "smsCodeStore", new LocalSmsCodeStore());
        setField(countingVerifier, "daoProvider", daoProvider);
        setField(countingVerifier, "jdbcTemplate", jdbcTemplate);
        setField(countingVerifier, "webAuthnAuthenticator", webAuthnAuthenticator);
        setField(countingVerifier, "auditService", new NoopAuditService());

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
        setField(loginService, "mfaFactorVerifier", countingVerifier);
        loginService.setReturnDeptName(false);

        userBizModel = new NopAuthUserBizModel();
        setField(userBizModel, "daoProvider", daoProvider);
        setField(userBizModel, "passwordEncoder", passwordEncoder);
        setField(userBizModel, "totpAuthenticator", totpAuthenticator);
        setField(userBizModel, "smsCodeStore", smsCodeStore);
        setField(userBizModel, "smsSender", smsSender);
        setField(userBizModel, "mfaChallengeStore", mfaChallengeStore);
        setField(userBizModel, "mfaFactorVerifier", countingVerifier);
        setField(userBizModel, "webAuthnAuthenticator", webAuthnAuthenticator);
        setField(userBizModel, "auditService", new NoopAuditService());

        loginApiBizModel = new LoginApiBizModel();
        setField(loginApiBizModel, "loginService", loginService);
        setField(loginApiBizModel, "mfaChallengeStore", mfaChallengeStore);
        setField(loginApiBizModel, "smsCodeStore", smsCodeStore);
        setField(loginApiBizModel, "mfaFactorVerifier", countingVerifier);
        setField(loginApiBizModel, "daoProvider", daoProvider);
        setField(loginApiBizModel, "auditService", new NoopAuditService());
        setField(loginApiBizModel, "webAuthnAuthenticator", webAuthnAuthenticator);

        operationMfaChecker = new OperationMfaCheckerImpl();
        setField(operationMfaChecker, "mfaChallengeStore", mfaChallengeStore);
        setField(operationMfaChecker, "daoProvider", daoProvider);
        setField(operationMfaChecker, "auditService", new NoopAuditService());
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
