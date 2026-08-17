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
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthMfaTrustedDevice;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.biz.dto.TrustedDeviceInfo;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.auth.service.mfa.MfaLoginPolicyServiceImpl;
import io.nop.auth.service.mfa.MfaTrustedDeviceManager;
import io.nop.auth.service.mfa.RoleMfaPolicyEvaluator;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W15-impl Phase 2 E2E：可信设备全链（设计 §六）——登记（mfaVerify rememberDevice，仅密码类）→
 * 豁免（二次登录无 challenge）→ 固定窗口不续期 → 同 hash 复活刷新 → max-count 满员 →
 * allowTrustedDevice=false 策略跳过（行保留，放宽恢复）→ 信道路径不豁免不登记 → 恢复码不登记 →
 * 无 device-id 降级 → 撤销矩阵四触发 → OAuth 副本回归（有可信设备行仍创建 challenge）→
 * 一期零回归（无记录用户路径逐字节一致）。
 * <p>
 * Anti-Hollow：豁免/登记均经真实组件链（ServiceContext 携真实请求头 → LoginApiBizModel →
 * LoginServiceImpl.checkMfaRequired/verifySecondFactorAndComplete → MfaTrustedDeviceManager →
 * DB 行断言）。
 */
class TestTrustedDeviceE2E {

    private static final String TENANT_ID = "0";
    private static final String POLICY_ROLE = "trusted-device-policy-role";

    private static Boolean originalMfaEnabled;

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;

    private LocalMfaChallengeStore mfaChallengeStore;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;
    private NopAuthUserBizModel userBizModel;
    private MfaTrustedDeviceManager trustedDeviceManager;
    private RoleMfaPolicyEvaluator roleMfaPolicyEvaluator;

    private final Map<String, String> secretByUser = new HashMap<>();

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalMfaEnabled = provider.getConfigValue("nop.auth.mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.mfa.enabled", true);
    }

    @AfterAll
    static void destroyCore() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.mfa.enabled",
                originalMfaEnabled != null ? originalMfaEnabled : Boolean.FALSE);
        // trusted-device 配置恢复缺省（个别用例临时调整）
        provider.assignConfigValue("nop.auth.mfa.trusted-device.max-count", 5);
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

    // ===================== 全链：登记 → 豁免 =====================

    @Test
    void testRegisterAndSecondLoginExempted() {
        String userId = "td-full-user";
        String userName = "td_full_user";
        String secret = setupTotpUser(userId, userName);

        // 1. 密码登录 → challenge；mfaVerify(rememberDevice=true + device headers) → 登记 true
        String challengeToken = loginChallenge(userName);
        LoginResult verified = mfaVerifyRemember(challengeToken, secret, userId, deviceHeaders("device-A"));
        assertNotNull(verified.getAccessToken());
        assertEquals(Boolean.TRUE, verified.getTrustedDeviceRegistered(),
                "rememberDevice=true with device-id must register and report true");

        NopAuthMfaTrustedDevice row = singleRow(userId);
        assertNotNull(row, "a trusted device row must be created");
        assertEquals(MfaTrustedDeviceManager.fingerprint(deviceHeaders("device-A")), row.getDeviceHash());
        assertNotNull(row.getDeviceName(), "default device name (UA digest) must be populated");

        // 2. 同指纹二次登录 → 无 challenge 直接 accessToken（豁免 = 一期"放行"同路径）
        LoginResult second = assertDoesNotThrow(() ->
                ormTemplate.runInSession(s -> doPasswordLogin(userName, deviceHeaders("device-A"))));
        assertNotNull(second.getAccessToken(), "second login with the same fingerprint must be exempted");

        // 3. lastUsedAt 更新但 expireAt 不续（固定窗口）
        Timestamp expireBefore = row.getExpireAt();
        Timestamp usedAt = singleRow(userId).getLastUsedAt();
        assertNotNull(usedAt);
        assertEquals(expireBefore.getTime(), singleRow(userId).getExpireAt().getTime(),
                "exemption hit must NOT renew expireAt (fixed window)");
    }

    @Test
    void testRegistrationWithoutDeviceIdReportsFalse() {
        String userId = "td-nodevice-user";
        String secret = setupTotpUser(userId, "td_nodevice_user");

        String challengeToken = loginChallenge("td_nodevice_user");
        // headers 无 device-id → 登记结果显式 false（提示非静默），无行
        LoginResult verified = mfaVerifyRemember(challengeToken, secret, userId, new HashMap<>());
        assertEquals(Boolean.FALSE, verified.getTrustedDeviceRegistered(),
                "rememberDevice=true without device-id must explicitly report false");
        assertEquals(0, trustedDeviceManager.listForUser(userId).size(), "no row without device-id");

        // 下次登录正常 MFA（降级非错误）
        assertThrows(NopException.class, () -> ormTemplate.runInSession(s ->
                doPasswordLogin("td_nodevice_user", new HashMap<>())));
    }

    @Test
    void testDifferentFingerprintNotExempted() {
        String userId = "td-diff-user";
        String secret = setupTotpUser(userId, "td_diff_user");
        String token = loginChallenge("td_diff_user");
        assertEquals(Boolean.TRUE, mfaVerifyRemember(token, secret, userId, deviceHeaders("device-A"))
                .getTrustedDeviceRegistered());

        // 不同 device-id → 不同指纹 → 不豁免（challenge 路径）
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin("td_diff_user", deviceHeaders("device-B"))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
    }

    // ===================== 固定窗口：命中不续期 / 过期不再豁免 / 同 hash 复活刷新 =====================

    @Test
    void testExpiredRowNotExemptedAndReviveRefresh() {
        String userId = "td-expire-user";
        String secret = setupTotpUser(userId, "td_expire_user");
        String token = loginChallenge("td_expire_user");
        mfaVerifyRemember(token, secret, userId, deviceHeaders("device-A"));
        String sid = singleRow(userId).getSid();

        // 过期行不豁免（惰性失效，行保留审计）
        expireAllRows(userId);
        NopException expired = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin("td_expire_user", deviceHeaders("device-A"))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), expired.getErrorCode(),
                "expired trusted row must not exempt (lazy invalidation)");
        assertEquals(1, trustedDeviceManager.listForUser(userId).size(), "expired row must be retained for audit");

        // 同 hash 复活刷新：再次 mfaVerify(rememberDevice) → 同一行（sid 不变）expireAt 重算
        String token2 = (String) expired.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
        LoginResult revived = mfaVerifyRemember(token2, secret, userId, deviceHeaders("device-A"));
        assertEquals(Boolean.TRUE, revived.getTrustedDeviceRegistered());
        NopAuthMfaTrustedDevice row = singleRow(userId);
        assertEquals(sid, row.getSid(), "revive must refresh the same (userId, deviceHash) row");
        assertTrue(row.getExpireAt().getTime() > System.currentTimeMillis(), "expireAt must be recalculated");

        // 复活后再登录豁免恢复
        LoginResult after = assertDoesNotThrow(() ->
                ormTemplate.runInSession(s -> doPasswordLogin("td_expire_user", deviceHeaders("device-A"))));
        assertNotNull(after.getAccessToken());
    }

    // ===================== max-count 满员 =====================

    @Test
    void testMaxCountFullReportsFalse() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.mfa.trusted-device.max-count", 1);
        try {
            String userId = "td-max-user";
            String secret = setupTotpUser(userId, "td_max_user");

            String token = loginChallenge("td_max_user");
            assertEquals(Boolean.TRUE, mfaVerifyRemember(token, secret, userId, deviceHeaders("device-A"))
                    .getTrustedDeviceRegistered());

            // 第二台设备：满员（仅计未过期行）→ 显式 false，不阻断登录
            String token2 = loginChallenge("td_max_user");
            LoginResult full = mfaVerifyRemember(token2, secret, userId, deviceHeaders("device-B"));
            assertNotNull(full.getAccessToken(), "full registration must not block login");
            assertEquals(Boolean.FALSE, full.getTrustedDeviceRegistered(),
                    "max-count reached must explicitly report false (no silent LRU eviction)");
            assertEquals(1, trustedDeviceManager.listForUser(userId).size());
        } finally {
            provider.assignConfigValue("nop.auth.mfa.trusted-device.max-count", 5);
        }
    }

    // ===================== allowTrustedDevice=false 策略：跳过豁免、行保留、放宽恢复 =====================

    @Test
    void testPolicyDisallowSkipsExemptionAndRowRetained() {
        String userId = "td-policy-user";
        String secret = setupTotpUser(userId, "td_policy_user");
        String token = loginChallenge("td_policy_user");
        mfaVerifyRemember(token, secret, userId, deviceHeaders("device-A"));

        // 挂策略角色：minMfaLevel=1（totp=2 达标）+ allowTrustedDevice=false
        savePolicyRole(1, (byte) 0);
        assignRole(userId, POLICY_ROLE);

        // 跳过豁免：仍创建 challenge；行保留（策略放宽后恢复）
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin("td_policy_user", deviceHeaders("device-A"))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode(),
                "allowTrustedDevice=false must skip the exemption (AND merge, fail-safe)");
        assertEquals(1, trustedDeviceManager.listForUser(userId).size(), "row must be retained while policy disallows");

        // 策略放宽（allow=true）→ 豁免恢复（行未被删除）
        savePolicyRole(1, (byte) 1);
        LoginResult restored = assertDoesNotThrow(() ->
                ormTemplate.runInSession(s -> doPasswordLogin("td_policy_user", deviceHeaders("device-A"))));
        assertNotNull(restored.getAccessToken(), "exemption must recover after policy relaxes");
    }

    // ===================== 信道路径：不豁免不登记 =====================

    @Test
    void testChannelPathNotExemptedAndNoRegistration() {
        String userId = "td-channel-user";
        String secret = setupTotpUser(userId, "td_channel_user");
        // 先登记一台设备（密码路径）
        String token = loginChallenge("td_channel_user");
        mfaVerifyRemember(token, secret, userId, deviceHeaders("device-A"));

        // 信道路径（createSessionForUserAsync，loginType=20）：有未过期行仍创建 challenge（headers=null 结构性跳过）
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> {
                    FutureHelper.syncGet(loginService.createSessionForUserAsync(userId,
                            io.nop.integration.api.channel.ChannelTypeCodes.LOGIN_TYPE_FEISHU));
                    return null;
                }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode(),
                "channel path must not be exempted (no headers)");

        // 信道 challenge 的 mfaVerify(rememberDevice=true) 不登记（loginType=20 非密码类）
        String channelToken = (String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
        int before = trustedDeviceManager.listForUser(userId).size();
        LoginResult channelResult = mfaVerifyRemember(channelToken, secret, userId, deviceHeaders("device-C"));
        assertNull(channelResult.getAccessToken(), "channel login yields accessCode only");
        assertNotNull(channelResult.getAccessCode());
        assertNull(channelResult.getTrustedDeviceRegistered(),
                "channel-type mfaVerify must not report registration (field absent)");
        assertEquals(before, trustedDeviceManager.listForUser(userId).size(),
                "channel-type mfaVerify must not register a trusted device");
    }

    // ===================== 恢复码不登记 =====================

    @Test
    void testRecoveryCodeDoesNotRegister() {
        String userId = "td-recovery-user";
        setupTotpUserWithRecovery(userId, "td_recovery_user", "1234567890");

        String token = loginChallenge("td_recovery_user");
        // 恢复码分支 + rememberDevice=true → 不登记（应急通道不产生长期豁免）
        LoginResult result = ormTemplate.runInSession(s -> doMfaVerifyRecovery(token, "1234567890", deviceHeaders("device-A")));
        assertNotNull(result.getAccessToken());
        assertNull(result.getTrustedDeviceRegistered(),
                "recovery-code login must not register a trusted device (structural exclusion)");
        assertEquals(0, trustedDeviceManager.listForUser(userId).size());
    }

    // ===================== 撤销矩阵四触发 =====================

    @Test
    void testRevocationMatrix() {
        // (a) 自助移除（物理删除 + 越权归一"不存在"）
        String userId = "td-revoke-self";
        String secret = setupTotpUser(userId, "td_revoke_self");
        String token = loginChallenge("td_revoke_self");
        mfaVerifyRemember(token, secret, userId, deviceHeaders("device-A"));

        String otherUserId = "td-revoke-other";
        String otherSecret = setupTotpUser(otherUserId, "td_revoke_other");
        String otherToken = loginChallenge("td_revoke_other");
        mfaVerifyRemember(otherToken, otherSecret, otherUserId, deviceHeaders("device-X"));

        String otherSid = singleRow(otherUserId).getSid();
        NopException notMine = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(() -> userBizModel.removeTrustedDevice(otherSid, ctx(userId))));
        assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), notMine.getErrorCode(),
                "removing another user's device must be normalized to not-found");
        assertEquals(1, trustedDeviceManager.listForUser(otherUserId).size(), "other user's row must survive");

        List<TrustedDeviceInfo> mine = new java.util.ArrayList<>(
                trustedDeviceManager.listForUser(userId).stream().map(r -> {
                    TrustedDeviceInfo info = new TrustedDeviceInfo();
                    info.setSid(r.getSid());
                    info.setDeviceName(r.getDeviceName());
                    info.setExpireAt(r.getExpireAt());
                    info.setLastUsedAt(r.getLastUsedAt());
                    info.setCreateTime(r.getCreateTime());
                    info.setExpired(r.getExpireAt() != null
                            && r.getExpireAt().getTime() <= System.currentTimeMillis());
                    return info;
                }).collect(java.util.stream.Collectors.toList()));
        assertEquals(1, mine.size());
        assertFalse(mine.get(0).isExpired());
        ormTemplate.runInSession(() -> userBizModel.listTrustedDevices(ctx(userId))); // BizModel 面 wiring 烟测
        ormTemplate.runInSession(() -> userBizModel.removeTrustedDevice(mine.get(0).getSid(), ctx(userId)));
        assertEquals(0, trustedDeviceManager.listForUser(userId).size(), "self removal must physically delete");

        // (b) unbindMfa 成功 → 全量删除
        String unbindUser = "td-revoke-unbind";
        String unbindSecret = setupTotpUser(unbindUser, "td_revoke_unbind");
        String t1 = loginChallenge("td_revoke_unbind");
        mfaVerifyRemember(t1, unbindSecret, unbindUser, deviceHeaders("device-A"));
        assertEquals(1, trustedDeviceManager.listForUser(unbindUser).size());
        ormTemplate.runInSession(s -> {
            // 解绑需验证当前因子：发 totp 码（下一窗口）
            String code = nextTotpCode(unbindSecret, unbindUser);
            userBizModel.unbindMfa(code, ctx(unbindUser));
            return null;
        });
        assertEquals(0, trustedDeviceManager.listForUser(unbindUser).size(),
                "unbindMfa success must revoke all trusted devices");

        // (c) confirmMfa 成功（换绑判定点）→ 全量删除
        String rebindUser = "td-revoke-rebind";
        String rebindSecret = setupTotpUser(rebindUser, "td_revoke_rebind");
        String t2 = loginChallenge("td_revoke_rebind");
        mfaVerifyRemember(t2, rebindSecret, rebindUser, deviceHeaders("device-A"));
        assertEquals(1, trustedDeviceManager.listForUser(rebindUser).size());
        // 解绑（解绑即撤销——(b) 已覆盖）；随后直接经 manager 补一行（模拟撤销后再次信任），
        // 换绑 confirm 成功点应再次全量撤销（矩阵两触发点各自独立验证）
        ormTemplate.runInSession(s -> {
            userBizModel.unbindMfa(nextTotpCode(rebindSecret, rebindUser), ctx(rebindUser));
            return null;
        });
        assertEquals(0, trustedDeviceManager.listForUser(rebindUser).size());
        ormTemplate.runInSession(s -> trustedDeviceManager.register(rebindUser, deviceHeaders("device-B")));
        assertEquals(1, trustedDeviceManager.listForUser(rebindUser).size());
        ormTemplate.runInSession(s -> {
            io.nop.auth.service.biz.dto.MfaBindResult bind =
                    userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_TOTP, ctx(rebindUser));
            String bindSecret = extractSecret(bind.getProvisioningUri());
            secretByUser.put(rebindUser, bindSecret);
            userBizModel.confirmMfa(bind.getBindToken(), currentTotp(bindSecret), ctx(rebindUser));
            return null;
        });
        assertEquals(0, trustedDeviceManager.listForUser(rebindUser).size(),
                "confirmMfa success (factor change) must revoke all trusted devices");

        // (d) resetUserMfa（管理员重置）→ 全量删除
        String resetUser = "td-revoke-reset";
        String resetSecret = setupTotpUser(resetUser, "td_revoke_reset");
        String t4 = loginChallenge("td_revoke_reset");
        mfaVerifyRemember(t4, resetSecret, resetUser, deviceHeaders("device-A"));
        assertEquals(1, trustedDeviceManager.listForUser(resetUser).size());
        ormTemplate.runInSession(() -> userBizModel.resetUserMfa(resetUser, adminCtx("td-revoke-admin")));
        assertEquals(0, trustedDeviceManager.listForUser(resetUser).size(),
                "resetUserMfa must revoke all trusted devices");
    }

    // ===================== OAuth 副本回归（W13 同步不变式履行） =====================

    @Test
    void testOAuthCopyStillCreatesChallengeDespiteTrustedRow() {
        String userId = "td-oauth-user";
        String secret = setupTotpUser(userId, "td_oauth_user");
        String token = loginChallenge("td_oauth_user");
        mfaVerifyRemember(token, secret, userId, deviceHeaders("device-A"));

        // MfaLoginPolicyServiceImpl（OAuth/SSO 入口同构副本，无豁免分支——W15 裁定）：
        // 有未过期可信设备行仍创建 challenge
        MfaLoginPolicyServiceImpl policyService = new MfaLoginPolicyServiceImpl();
        setField(policyService, "daoProvider", daoProvider);
        setField(policyService, "mfaChallengeStore", mfaChallengeStore);
        setField(policyService, "userContextCache", new LocalUserContextCache());
        setField(policyService, "roleMfaPolicyEvaluator", roleMfaPolicyEvaluator);

        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> policyService.checkMfaForUserName("td_oauth_user",
                        AuthApiConstants.LOGIN_TYPE_SSO)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode(),
                "OAuth entry must still create a challenge despite an unexpired trusted device row (copy has no exemption branch)");
        assertNotNull(ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN));
    }

    // ===================== 一期零回归 =====================

    @Test
    void testZeroRegressionUserWithoutRecord() {
        String userId = "td-zeroreg-user";
        setupTotpUser(userId, "td_zeroreg_user");
        // 带 device-id 头登录但无记录：豁免分支短路（查库无行）→ challenge 路径与一期一致
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin("td_zeroreg_user", deviceHeaders("device-A"))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode(),
                "no-record user must hit the phase-one challenge path (byte-identical)");
        assertNotNull(mfaChallengeStore.peek((String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN)));

        // 不勾选 rememberDevice 的 mfaVerify → 无登记、响应字段缺省不出现
        String secret = secretByUser.get(userId);
        String token = (String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
        LoginResult result = mfaVerifyRemember(token, secret, userId, null, deviceHeaders("device-A"));
        assertNull(result.getTrustedDeviceRegistered(),
                "field must be absent when rememberDevice is not requested");
        assertEquals(0, trustedDeviceManager.listForUser(userId).size());
    }

    // ===================== Helpers: login operations =====================

    private static Map<String, Object> deviceHeaders(String deviceId) {
        Map<String, Object> h = new HashMap<>();
        h.put("X-Nop-Mfa-Device-Id", deviceId);
        h.put("User-Agent", "UnitTestAgent/1.0");
        h.put("Accept-Language", "zh-CN");
        return h;
    }

    private LoginResult doPasswordLogin(String userName, Map<String, Object> headers) {
        LoginRequest req = new LoginRequest();
        req.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        req.setPrincipalId(userName);
        req.setPrincipalSecret("123");
        IServiceContext ctx = new ServiceContextImpl();
        ctx.setRequestHeaders(headers);
        return FutureHelper.syncGet(loginApiBizModel.loginAsync(req, ctx));
    }

    /** 密码登录并返回 challengeToken（断言 MFA_REQUIRED）。 */
    private String loginChallenge(String userName) {
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin(userName, new HashMap<>())));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        return (String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
    }

    /** mfaVerify(rememberDevice=true, device headers)——使用下一有效 TOTP 窗口码。 */
    private LoginResult mfaVerifyRemember(String challengeToken, String secret, String userId,
                                          Map<String, Object> headers) {
        return mfaVerifyRemember(challengeToken, secret, userId, Boolean.TRUE, headers);
    }

    private LoginResult mfaVerifyRemember(String challengeToken, String secret, String userId,
                                          Boolean remember, Map<String, Object> headers) {
        return ormTemplate.runInSession(s -> {
            MfaVerifyRequest req = new MfaVerifyRequest();
            req.setChallengeToken(challengeToken);
            req.setCode(nextTotpCode(secret, userId));
            req.setRememberDevice(remember);
            IServiceContext ctx = new ServiceContextImpl();
            ctx.setRequestHeaders(headers);
            return FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(req, ctx));
        });
    }

    private LoginResult doMfaVerifyRecovery(String challengeToken, String recoveryCode, Map<String, Object> headers) {
        return ormTemplate.runInSession(s -> {
            MfaVerifyRequest req = new MfaVerifyRequest();
            req.setChallengeToken(challengeToken);
            req.setRecoveryCode(recoveryCode);
            req.setRememberDevice(Boolean.TRUE);
            IServiceContext ctx = new ServiceContextImpl();
            ctx.setRequestHeaders(headers);
            return FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(req, ctx));
        });
    }

    // ===================== Helpers: users / policies =====================

    private String setupTotpUser(String userId, String userName) {
        saveUser(userId, userName);
        TOTPAuthenticator totp = new TOTPAuthenticator();
        String base32Secret = totp.generateSecret();
        String encrypted = totp.getCipher().encrypt(base32Secret);
        secretByUser.put(userId, base32Secret);
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
        return base32Secret;
    }

    private void setupTotpUserWithRecovery(String userId, String userName, String recoveryCode) {
        setupTotpUser(userId, userName);
        SHA256PasswordEncoder encoder = new SHA256PasswordEncoder();
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<io.nop.auth.dao.entity.NopAuthMfaRecoveryCode> dao =
                    daoProvider.daoFor(io.nop.auth.dao.entity.NopAuthMfaRecoveryCode.class);
            io.nop.auth.dao.entity.NopAuthMfaRecoveryCode rc = dao.newEntity();
            rc.setUserId(userId);
            String salt = encoder.generateSalt();
            rc.setCodeHash(salt + ":" + encoder.encodePassword(salt, recoveryCode));
            rc.setUsed((byte) 0);
            dao.saveEntity(rc);
            return null;
        });
    }

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

    private void savePolicyRole(int minMfaLevel, byte allowTrustedDevice) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthRole> roleDao = daoProvider.daoFor(NopAuthRole.class);
            if (roleDao.getEntityById(POLICY_ROLE) == null) {
                NopAuthRole role = roleDao.newEntity();
                role.setRoleId(POLICY_ROLE);
                role.setRoleName(POLICY_ROLE);
                roleDao.saveEntity(role);
            }
            IEntityDao<NopAuthRoleMfaPolicy> policyDao = daoProvider.daoFor(NopAuthRoleMfaPolicy.class);
            NopAuthRoleMfaPolicy policy = policyDao.getEntityById(POLICY_ROLE);
            if (policy == null) {
                policy = policyDao.newEntity();
                policy.setRoleId(POLICY_ROLE);
                policy.setMinMfaLevel(minMfaLevel);
                policy.setAllowTrustedDevice(allowTrustedDevice);
                policyDao.saveEntity(policy);
            } else {
                // 放宽/收紧：显式更新既有行（无外层事务时 tracked entity 不保证 flush）
                policy.setMinMfaLevel(minMfaLevel);
                policy.setAllowTrustedDevice(allowTrustedDevice);
                policyDao.updateEntityDirectly(policy);
            }
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

    private void expireAllRows(String userId) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            for (NopAuthMfaTrustedDevice row : trustedDeviceManager.listForUser(userId)) {
                row.setExpireAt(new Timestamp(System.currentTimeMillis() - 1000));
                daoProvider.daoFor(NopAuthMfaTrustedDevice.class).updateEntityDirectly(row);
            }
            return null;
        });
    }

    private NopAuthMfaTrustedDevice singleRow(String userId) {
        List<NopAuthMfaTrustedDevice> rows = trustedDeviceManager.listForUser(userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ===================== Helpers: TOTP =====================

    /** 当前窗口码。 */
    private static String currentTotp(String base32Secret) {
        return computeTotpAt(base32Secret, System.currentTimeMillis());
    }

    /** 下一有效窗口码（严格大于 lastVerifiedWindow——防重放推进语义）。 */
    private String nextTotpCode(String secret, String userId) {
        return ormTemplate.runInSession(s -> {
            NopAuthMfaSetting setting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
            long last = setting == null || setting.getLastVerifiedWindow() == null ? -1L : setting.getLastVerifiedWindow();
            long now = System.currentTimeMillis() / 1000L / TOTPAuthenticator.PERIOD_SECONDS;
            long window = Math.max(now, last + 1);
            return computeTotpAt(secret, window * TOTPAuthenticator.PERIOD_SECONDS * 1000L);
        });
    }

    private static String computeTotpAt(String base32Secret, long timeMillis) {
        byte[] secretBytes = base32Decode(base32Secret);
        long window = timeMillis / 1000L / TOTPAuthenticator.PERIOD_SECONDS;
        byte[] counterBytes = new byte[8];
        long t = window;
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (t & 0xFF);
            t >>>= 8;
        }
        byte[] hash = HashHelper.hmac(TOTPAuthenticator.HMAC_ALGORITHM, counterBytes, secretBytes);
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

    private static String extractSecret(String uri) {
        int idx = uri.indexOf("secret=");
        int end = uri.indexOf("&", idx);
        return uri.substring(idx + "secret=".length(), end > 0 ? end : uri.length());
    }

    private IServiceContext ctx(String userId) {
        ServiceContextImpl c = new ServiceContextImpl();
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userId);
        uc.setTenantId(TENANT_ID);
        Set<String> roles = new HashSet<>();
        roles.add(NopAuthConstants.ROLE_USER);
        uc.setRoles(roles);
        c.setUserContext(uc);
        return c;
    }

    private IServiceContext adminCtx(String userId) {
        ServiceContextImpl c = new ServiceContextImpl();
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userId);
        uc.setTenantId(TENANT_ID);
        Set<String> roles = new HashSet<>();
        roles.add(NopAuthConstants.ROLE_ADMIN);
        uc.setRoles(roles);
        c.setUserContext(uc);
        return c;
    }

    // ===================== H2 + wiring（TestMfaLoginE2E 同型） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:mfa-trusted-e2e-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("mfa-trusted", CacheConfig.newConfig(100)));
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
        authTokenProvider.setEncKey("test-enc-key-mfa-trusted");

        LocalUserContextCache userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        mfaChallengeStore = new LocalMfaChallengeStore();
        SHA256PasswordEncoder passwordEncoder = new SHA256PasswordEncoder();

        trustedDeviceManager = new MfaTrustedDeviceManager();
        setField(trustedDeviceManager, "daoProvider", daoProvider);
        setField(trustedDeviceManager, "auditService", new NoopAuditService());

        roleMfaPolicyEvaluator = new RoleMfaPolicyEvaluator();
        setField(roleMfaPolicyEvaluator, "daoProvider", daoProvider);

        MfaFactorVerifier mfaFactorVerifier = new MfaFactorVerifier();
        setField(mfaFactorVerifier, "totpAuthenticator", new TOTPAuthenticator());
        setField(mfaFactorVerifier, "daoProvider", daoProvider);

        loginService = new LoginServiceImpl();
        setField(loginService, "daoProvider", daoProvider);
        setField(loginService, "passwordEncoder", passwordEncoder);
        setField(loginService, "auditService", new NoopAuditService());
        setField(loginService, "authTokenProvider", authTokenProvider);
        setField(loginService, "userContextCache", userContextCache);
        setField(loginService, "loginSessionStore", new UuidSessionStore());
        setField(loginService, "mfaChallengeStore", mfaChallengeStore);
        setField(loginService, "totpAuthenticator", new TOTPAuthenticator());
        setField(loginService, "mfaFactorVerifier", mfaFactorVerifier);
        setField(loginService, "roleMfaPolicyEvaluator", roleMfaPolicyEvaluator);
        setField(loginService, "trustedDeviceManager", trustedDeviceManager);
        loginService.setReturnDeptName(false);

        userBizModel = new NopAuthUserBizModel();
        setField(userBizModel, "daoProvider", daoProvider);
        setField(userBizModel, "passwordEncoder", passwordEncoder);
        setField(userBizModel, "totpAuthenticator", new TOTPAuthenticator());
        setField(userBizModel, "mfaFactorVerifier", mfaFactorVerifier);
        setField(userBizModel, "trustedDeviceManager", trustedDeviceManager);

        loginApiBizModel = new LoginApiBizModel();
        setField(loginApiBizModel, "loginService", loginService);
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
