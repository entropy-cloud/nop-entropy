package io.nop.auth.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.IUserContextCache;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.mfa.store.LocalSmsCodeStore;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.biz.dto.MfaBindResult;
import io.nop.auth.service.biz.dto.MfaStatusResult;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
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

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6 Phase 1 + Phase 2 tests for the user self-service MFA management
 * (bind/confirm/unbind/recovery/status) and admin reset, exercised against the
 * real {@link NopAuthUserBizModel} over a real in-memory H2 database with the
 * W4/W5 components ({@link TOTPAuthenticator}, {@link LocalSmsCodeStore},
 * {@link IPasswordEncoder}).
 *
 * <p><b>Anti-Hollow / Wiring verification</b> (Minimum Rules #22 / #23): the
 * BizModel is wired with the SAME stores used by {@link LoginServiceImpl}, and
 * cross-component round-trip tests assert that W6-produced artifacts (encrypted
 * secret, recovery-code codeHash) are consumable by the W5 login/mfaVerify path.
 *
 * <p><b>No silent skip</b> (Rule #24): negative cases (already-enabled bind,
 * expired bindToken, not-enabled unbind, non-admin reset) all throw explicit
 * exceptions.
 */
class TestMfaUserSelfService {

    private static final String TENANT_ID = "0";
    private static Boolean originalTenantByDefault;
    private static Boolean originalMfaEnabled;

    // ---- H2 + ORM stack ----
    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;
    private IJdbcTemplate jdbcTemplate;

    // ---- real beans ----
    private JwtAuthTokenProvider authTokenProvider;
    private LocalUserContextCache userContextCache;
    private LocalMfaChallengeStore mfaChallengeStore;
    private LocalSmsCodeStore smsCodeStore;
    private TOTPAuthenticator totpAuthenticator;
    private CapturingSmsSender smsSender;
    private SHA256PasswordEncoder passwordEncoder;
    private NopAuthUserBizModel userBizModel;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalMfaEnabled = provider.getConfigValue("nop.auth.mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.mfa.enabled", true);
        originalTenantByDefault = provider.getConfigValue("nop.orm.enable-tenant-by-default", Boolean.FALSE);
        // Do NOT toggle nop.orm.enable-tenant-by-default globally: assignConfigValue'd
        // refs survive NopJunitExtension's reset() and leak tenant columns into sibling
        // tests in the shared surefire JVM (nop.err.orm.missing-tenant-id).
    }

    @AfterAll
    static void destroyCore() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.mfa.enabled",
                originalMfaEnabled != null ? originalMfaEnabled : Boolean.FALSE);
        provider.assignConfigValue("nop.orm.enable-tenant-by-default",
                originalTenantByDefault != null ? originalTenantByDefault : Boolean.FALSE);
    }

    @BeforeEach
    void setUp() {
        // Defensive: AutoTestCase-based sibling tests legitimately reset the global
        // VarCollector singleton to null on teardown. This test calls the real
        // LoginApiBizModel (which uses VarCollector.instance()), so ensure a non-null
        // collector exists when this class runs after them. Mirrors TestChannelScanBindLoginE2E.
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

    // ===================== Phase 1: bind state machine E2E =====================

    @Test
    void testBindConfirmEnabledStateMachine() {
        String userId = "bind-user";
        String userName = "bind_user";
        saveUser(userId, userName, null);

        // bind totp → provisioning URI + bindToken
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_TOTP, ctx(userId, userName)));
        assertEquals(NopAuthConstants.MFA_TYPE_TOTP, bind.getMfaType());
        assertNotNull(bind.getProvisioningUri(), "totp bind must return provisioning URI");
        assertTrue(bind.getProvisioningUri().startsWith("otpauth://totp/"), "provisioning URI must be otpauth scheme");
        assertNotNull(bind.getBindToken(), "bind must return bindToken");

        // pending state in DB, secret encrypted
        NopAuthMfaSetting pending = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_PENDING, pending.getStatus());
        assertNotNull(pending.getSecret(), "pending secret must be persisted (encrypted)");
        assertNotEqualsBase32(bind, pending);

        // confirm with a valid TOTP code → enabled + recovery codes
        String base32Secret = extractSecretFromUri(bind.getProvisioningUri());
        String code = computeTotpCode(base32Secret, System.currentTimeMillis());
        List<String> recoveryCodes = ormTemplate.runInSession(s ->
                userBizModel.confirmMfa(bind.getBindToken(), code, ctx(userId, userName)));

        assertEquals(10, recoveryCodes.size(), "confirm must generate 10 recovery codes");
        NopAuthMfaSetting enabled = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, enabled.getStatus());
        assertNull(enabled.getBindToken(), "confirm must clear bindToken");

        // recovery codes persisted as salt:hash (not plaintext)
        List<NopAuthMfaRecoveryCode> rows = listRecoveryCodes(userId);
        assertEquals(10, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.getCodeHash() != null && r.getCodeHash().contains(":")),
                "recovery codeHash must be salt:hash format");

        // password login now intercepted by MFA_REQUIRED
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(session -> doPasswordLogin(userName)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
    }

    // ===================== Phase 1: cross-component secret round-trip =====================

    @Test
    void testTotpSecretRoundTripW6ProduceW5Consume() {
        String userId = "rt-secret-user";
        String userName = "rt_secret_user";
        saveUser(userId, userName, null);

        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_TOTP, ctx(userId, userName)));
        String base32Secret = extractSecretFromUri(bind.getProvisioningUri());

        // confirm → enabled
        String code = computeTotpCode(base32Secret, System.currentTimeMillis());
        ormTemplate.runInSession(s -> userBizModel.confirmMfa(bind.getBindToken(), code, ctx(userId, userName)));

        // W6-produced setting.secret must be decryptable + verifiable by the W5 TOTPAuthenticator.verify.
        // lastVerifiedWindow=-1 isolates the W6-produce↔W5-consume decrypt+HMAC boundary (anti-replay is W5-internal).
        NopAuthMfaSetting setting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        long window = totpAuthenticator.verify(setting.getSecret(),
                computeTotpCode(base32Secret, System.currentTimeMillis()), -1L);
        assertTrue(window >= 0, "W6-produced encrypted secret must be consumable by W5 TOTPAuthenticator.verify (round-trip)");
    }

    // ===================== Phase 1: cross-component recovery-code round-trip =====================

    @Test
    void testRecoveryCodeRoundTripW6ProduceW5Consume() {
        String userId = "rt-recovery-user";
        String userName = "rt_recovery_user";
        saveUser(userId, userName, null);
        enableTotpDirectly(userId, userName);
        List<String> codes = ormTemplate.runInSession(s ->
                userBizModel.generateRecoveryCodes(ctx(userId, userName)));
        assertEquals(10, codes.size());

        // trigger a challenge so we can mfaVerify with a recovery code
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(session -> doPasswordLogin(userName)));
        String challengeToken = (String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);

        // W6-generated recovery code must be consumable by W5 verifyRecoveryCode path (mfaVerify)
        LoginResult result = ormTemplate.runInSession(session -> doMfaVerifyRecovery(challengeToken, codes.get(0)));
        assertNotNull(result.getAccessToken(), "W6 recovery code must login via W5 mfaVerify path (round-trip)");

        // recovery login disables MFA (force rebind)
        NopAuthMfaSetting setting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_DISABLED, setting.getStatus());
    }

    // ===================== Phase 1: unbind =====================

    @Test
    void testUnbindRequiresFactorAndInvalidatesRecovery() {
        String userId = "unbind-user";
        String userName = "unbind_user";
        saveUser(userId, userName, null);
        enableTotpDirectly(userId, userName);
        ormTemplate.runInSession(s -> userBizModel.generateRecoveryCodes(ctx(userId, userName)));

        // unbind with wrong code → MFA_FAIL
        assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> { userBizModel.unbindMfa("000000", ctx(userId, userName)); return null; }));

        // unbind with valid TOTP code → disabled + recovery codes deleted
        NopAuthMfaSetting setting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        String plainSecret = totpAuthenticator.getCipher().decrypt(setting.getSecret());
        String code = computeTotpCode(plainSecret, System.currentTimeMillis());
        ormTemplate.runInSession(s -> { userBizModel.unbindMfa(code, ctx(userId, userName)); return null; });

        NopAuthMfaSetting after = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_DISABLED, after.getStatus());
        assertTrue(listRecoveryCodes(userId).isEmpty(), "unbind must delete all recovery codes");

        // login no longer intercepted (status != enabled)
        LoginResult result = assertDoesNotThrow(() -> ormTemplate.runInSession(session -> doPasswordLogin(userName)));
        assertNotNull(result.getAccessToken(), "after unbind, login must not be MFA-intercepted");
    }

    // ===================== Phase 1: recovery-code reset =====================

    @Test
    void testGenerateRecoveryCodesInvalidatesOld() {
        String userId = "reset-user";
        String userName = "reset_user";
        saveUser(userId, userName, null);
        enableTotpDirectly(userId, userName);

        List<String> first = ormTemplate.runInSession(s ->
                userBizModel.generateRecoveryCodes(ctx(userId, userName)));
        List<String> second = ormTemplate.runInSession(s ->
                userBizModel.generateRecoveryCodes(ctx(userId, userName)));

        assertEquals(10, second.size());
        assertEquals(10, listRecoveryCodes(userId).size(), "reset must keep only the new 10 codes");
        // old codes are different from new codes (re-generated)
        assertFalse(first.stream().anyMatch(second::contains), "old recovery codes must be replaced");

        // trigger challenge + verify an OLD recovery code fails
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(session -> doPasswordLogin(userName)));
        String challengeToken = (String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);
        assertThrows(NopException.class, () ->
                ormTemplate.runInSession(session -> doMfaVerifyRecovery(challengeToken, first.get(0))),
                "old recovery code must be invalid after reset");
    }

    // ===================== Phase 1: status no secret =====================

    @Test
    void testGetMfaStatusNeverReturnsSecret() {
        String userId = "status-user";
        String userName = "status_user";
        saveUser(userId, userName, "13800138000");
        enableTotpDirectly(userId, userName);

        MfaStatusResult status = ormTemplate.runInSession(s ->
                userBizModel.getMfaStatus(ctx(userId, userName)));
        assertEquals(NopAuthConstants.MFA_TYPE_TOTP, status.getMfaType());
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, status.getStatus());
        // totp setting has no phone; status must not expose secret (the core明文边界 assertion)
        assertFalse(hasSecretField(status), "MfaStatusResult must not expose a secret field");
        assertNull(getSecretValue(status), "MfaStatusResult must not carry any secret value");
    }

    private static Object getSecretValue(MfaStatusResult r) {
        try {
            java.lang.reflect.Field f = r.getClass().getDeclaredField("secret");
            f.setAccessible(true);
            return f.get(r);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @Test
    void testBindSmsSendsCodeAndConfirmEnables() {
        String userId = "sms-bind-user";
        String userName = "sms_bind_user";
        saveUser(userId, userName, "13800138999");

        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_SMS, ctx(userId, userName)));
        assertEquals(NopAuthConstants.MFA_TYPE_SMS, bind.getMfaType());
        assertTrue(bind.isSmsSent(), "sms bind must send code");
        assertNotNull(bind.getBindToken());
        assertNull(bind.getProvisioningUri(), "sms bind must not return provisioning URI");

        // ISmsSender was called; grab the code
        assertNotNull(smsSender.lastMessage.get());
        String code = smsSender.lastMessage.get().getParams().get(0);

        // confirm via SmsCodeStore key mfa:userId → enabled
        List<String> recovery = ormTemplate.runInSession(s ->
                userBizModel.confirmMfa(bind.getBindToken(), code, ctx(userId, userName)));
        assertEquals(10, recovery.size());
        NopAuthMfaSetting setting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, setting.getStatus());
        assertEquals(NopAuthConstants.MFA_TYPE_SMS, setting.getMfaType());
    }

    // ===================== Phase 1: no silent skip (negative paths) =====================

    @Test
    void testAlreadyEnabledBindRejected() {
        String userId = "already-user";
        String userName = "already_user";
        saveUser(userId, userName, null);
        enableTotpDirectly(userId, userName);

        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_TOTP, ctx(userId, userName))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_ALREADY_ENABLED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void testBindExpiredOrUnknownTokenRejected() {
        String userId = "expired-user";
        String userName = "expired_user";
        saveUser(userId, userName, null);

        // confirm with unknown bindToken (no pending record) → BIND_EXPIRED
        NopException ex1 = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> userBizModel.confirmMfa("unknown-token", "123456", ctx(userId, userName))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_BIND_EXPIRED.getErrorCode(), ex1.getErrorCode());

        // bind, then backdate the pending row's UPDATE_TIME past bind-expire-seconds → BIND_EXPIRED.
        // Deterministic expiry: setting bind-expire-seconds=0 relies on >=1ms elapsing between the
        // bind flush and the confirm expiry check; under heavy parallel load (-T 1C) both can land
        // in the same millisecond (age==0, check is strict '>') causing an intermittent mfa-fail.
        // Backdate 600s must exceed the default bind-expire-seconds=300 window.
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_TOTP, ctx(userId, userName)));
        backdatePendingUpdateTime(userId, 600_000L);
        NopException ex2 = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> userBizModel.confirmMfa(bind.getBindToken(), "123456", ctx(userId, userName))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_BIND_EXPIRED.getErrorCode(), ex2.getErrorCode());
    }

    /**
     * Backdate UPDATE_TIME of the pending MfaSetting row via direct SQL, bypassing the ORM
     * auto-stamp (onUpdate would overwrite a manually assigned updateTime at flush).
     */
    private void backdatePendingUpdateTime(String userId, long backdateMs) {
        SQL upd = SQL.begin().name("mfaBindBackdateUpdateTime")
                .sql("UPDATE NOP_AUTH_MFA_SETTING SET UPDATE_TIME = ? WHERE USER_ID = ?",
                        new Timestamp(CoreMetrics.currentTimeMillis() - backdateMs), userId)
                .end();
        jdbcTemplate.executeUpdate(upd);
    }

    @Test
    void testNotEnabledUnbindAndGenerateRejected() {
        String userId = "notenabled-user";
        String userName = "notenabled_user";
        saveUser(userId, userName, null);

        // unbind without enabled → NOT_ENABLED
        NopException ex1 = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> { userBizModel.unbindMfa("123456", ctx(userId, userName)); return null; }));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_NOT_ENABLED.getErrorCode(), ex1.getErrorCode());

        // generateRecoveryCodes without enabled → NOT_ENABLED
        NopException ex2 = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> userBizModel.generateRecoveryCodes(ctx(userId, userName))));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_NOT_ENABLED.getErrorCode(), ex2.getErrorCode());
    }

    // ===================== Phase 2: admin reset =====================

    @Test
    void testAdminResetUserMfaClearsSettingAndRecovery() {
        String userId = "admin-reset-user";
        String userName = "admin_reset_user";
        saveUser(userId, userName, null);
        enableTotpDirectly(userId, userName);
        ormTemplate.runInSession(s -> userBizModel.generateRecoveryCodes(ctx(userId, userName)));
        assertEquals(10, listRecoveryCodes(userId).size());

        // admin reset
        ormTemplate.runInSession(s -> { userBizModel.resetUserMfa(userId, adminCtx("admin-id", "admin")); return null; });

        // setting cleared (status disabled, mfaType null), recovery codes deleted
        NopAuthMfaSetting setting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_DISABLED, setting.getStatus());
        assertNull(setting.getMfaType());
        assertTrue(listRecoveryCodes(userId).isEmpty());

        // login no longer MFA-intercepted
        LoginResult result = assertDoesNotThrow(() -> ormTemplate.runInSession(session -> doPasswordLogin(userName)));
        assertNotNull(result.getAccessToken(), "after admin reset, login must not be MFA-intercepted");

        // no challenge was created (checkMfaRequired returns null for status != enabled)
        // — proven by login succeeding directly with accessToken
    }

    @Test
    void testNonAdminResetRejected() {
        String userId = "nonadmin-reset-user";
        String userName = "nonadmin_reset_user";
        saveUser(userId, userName, null);
        enableTotpDirectly(userId, userName);

        // non-admin context → rejected
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> { userBizModel.resetUserMfa(userId, ctx("plain-id", "plain_user")); return null; }));
        assertTrue(ex.getMessage().contains("admin"), "non-admin reset must be rejected: " + ex.getMessage());

        // setting untouched
        NopAuthMfaSetting setting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, setting.getStatus());
    }

    @Test
    void testAdminResetUnknownUserFailsExplicitly() {
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> { userBizModel.resetUserMfa("no-such-user", adminCtx("admin-id", "admin")); return null; }));
        assertTrue(ex.getMessage().contains("not found"), "reset of unknown user must fail explicitly");
    }

    // ===================== Phase 4: full-chain E2E + regression =====================

    /**
     * End-to-end full chain (Rule #22): bind totp → password login intercepted →
     * mfaVerify(totp) → accessToken. Proves the bind (W6) → login interception (W5) →
     * mfaVerify (W5) path is wired end-to-end across both phases' components.
     */
    @Test
    void testE2eBindTotpLoginMfaVerifyAccessToken() {
        String userId = "e2e-totp-user";
        String userName = "e2e_totp_user";
        saveUser(userId, userName, null);

        // 1. bind totp
        MfaBindResult bind = ormTemplate.runInSession(s ->
                userBizModel.bindMfa(NopAuthConstants.MFA_TYPE_TOTP, ctx(userId, userName)));
        String base32Secret = extractSecretFromUri(bind.getProvisioningUri());

        // 2. confirm → enabled
        String confirmCode = computeTotpCode(base32Secret, System.currentTimeMillis());
        ormTemplate.runInSession(s -> userBizModel.confirmMfa(bind.getBindToken(), confirmCode, ctx(userId, userName)));

        // 3. password login → intercepted with MFA_REQUIRED
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(session -> doPasswordLogin(userName)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        String challengeToken = (String) ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN);

        // 4. mfaVerify(totp) → accessToken (password-typed exit).
        //    Compute the code for the window AFTER the one consumed at confirm (anti-replay).
        NopAuthMfaSetting enabledSetting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        long nextWindow = (enabledSetting.getLastVerifiedWindow() == null ? 0 : enabledSetting.getLastVerifiedWindow()) + 1;
        String verifyCode = computeTotpCode(base32Secret, nextWindow * TOTPAuthenticator.PERIOD_SECONDS * 1000L);
        LoginResult result = ormTemplate.runInSession(session -> doMfaVerify(challengeToken, verifyCode));
        assertNotNull(result.getAccessToken(), "full chain must yield accessToken");
        assertNull(result.getAccessCode(), "password-typed mfaVerify must not yield accessCode");
    }

    /**
     * End-to-end full chain: admin reset → user login no longer intercepted (Rule #22 + #23).
     */
    @Test
    void testE2eAdminResetRestoresDirectLogin() {
        String userId = "e2e-reset-user";
        String userName = "e2e_reset_user";
        saveUser(userId, userName, null);
        enableTotpDirectly(userId, userName);

        // before reset: login is MFA-intercepted
        assertThrows(NopException.class, () -> ormTemplate.runInSession(session -> doPasswordLogin(userName)));

        // admin reset
        ormTemplate.runInSession(s -> { userBizModel.resetUserMfa(userId, adminCtx("admin-id", "admin")); return null; });

        // after reset: login succeeds directly (no MFA interception)
        LoginResult result = assertDoesNotThrow(() -> ormTemplate.runInSession(session -> doPasswordLogin(userName)));
        assertNotNull(result.getAccessToken(), "after admin reset, login must succeed directly");
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

    private LoginResult doMfaVerifyRecovery(String challengeToken, String recoveryCode) {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setChallengeToken(challengeToken);
        req.setRecoveryCode(recoveryCode);
        IServiceContext c = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(req, c));
    }

    private LoginResult doMfaVerify(String challengeToken, String code) {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setChallengeToken(challengeToken);
        req.setCode(code);
        IServiceContext c = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(req, c));
    }

    // ===================== Helpers: user/setup =====================

    private void enableTotpDirectly(String userId, String userName) {
        String base32Secret = totpAuthenticator.generateSecret();
        String encrypted = totpAuthenticator.getCipher().encrypt(base32Secret);
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

    private void saveUser(String userId, String userName, String phone) {
        String salt = passwordEncoder.generateSalt();
        String encoded = passwordEncoder.encodePassword(salt, "123");
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
            NopAuthUser user = dao.newEntity();
            user.setUserId(userId);
            user.setUserName(userName);
            user.setNickName(userName);
            user.setPassword(encoded);
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

    private List<NopAuthMfaRecoveryCode> listRecoveryCodes(String userId) {
        NopAuthMfaRecoveryCode example = new NopAuthMfaRecoveryCode();
        example.setUserId(userId);
        return daoProvider.daoFor(NopAuthMfaRecoveryCode.class).findAllByExample(example);
    }

    /** Build a self-service ServiceContext for the given user. */
    private IServiceContext ctx(String userId, String userName) {
        ServiceContextImpl c = new ServiceContextImpl();
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userName);
        uc.setTenantId(TENANT_ID);
        c.setUserContext(uc);
        return c;
    }

    /** Build an admin ServiceContext (roles contain admin). */
    private IServiceContext adminCtx(String userId, String userName) {
        ServiceContextImpl c = new ServiceContextImpl();
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userName);
        uc.setTenantId(TENANT_ID);
        Set<String> roles = new HashSet<>();
        roles.add(NopAuthConstants.ROLE_ADMIN);
        uc.setRoles(roles);
        c.setUserContext(uc);
        return c;
    }

    private static boolean hasSecretField(MfaStatusResult r) {
        try {
            return r.getClass().getDeclaredField("secret") != null;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static void assertNotEqualsBase32(MfaBindResult bind, NopAuthMfaSetting pending) {
        // pending.secret is ciphertext, must not equal the plaintext base32 in the URI
        assertFalse(bind.getProvisioningUri().contains(pending.getSecret()),
                "persisted secret must be encrypted, not the plaintext base32 in the URI");
    }

    private static String extractSecretFromUri(String uri) {
        int idx = uri.indexOf("secret=");
        int end = uri.indexOf("&", idx);
        return uri.substring(idx + "secret=".length(), end > 0 ? end : uri.length());
    }

    // ===================== TOTP code computation =====================

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

    // ===================== H2 + ORM stack =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:mfa-selfsvc-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("mfa-selfsvc", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);

        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new io.nop.core.lang.sql.SQL(createSql));

        OrmDaoProvider __daoProvider = new OrmDaoProvider(); __daoProvider.setOrmTemplate(ormTemplate); daoProvider = __daoProvider;
    }

    private void wireRealBeans() {
        authTokenProvider = new JwtAuthTokenProvider();
        authTokenProvider.setEncKey("test-enc-key-mfa-selfsvc");

        userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        mfaChallengeStore = new LocalMfaChallengeStore();
        smsCodeStore = new LocalSmsCodeStore();
        totpAuthenticator = new TOTPAuthenticator();
        smsSender = new CapturingSmsSender();
        passwordEncoder = new SHA256PasswordEncoder();

        // real NopAuthUserBizModel with W4/W5 components
        userBizModel = new NopAuthUserBizModel();
        setField(userBizModel, "daoProvider", daoProvider);
        setField(userBizModel, "passwordEncoder", passwordEncoder);
        setField(userBizModel, "totpAuthenticator", totpAuthenticator);
        setField(userBizModel, "smsCodeStore", smsCodeStore);
        setField(userBizModel, "smsSender", smsSender);

        // real LoginServiceImpl for cross-component round-trip (recovery/TOTP consumption)
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
        setField(loginService, "smsSender", smsSender);
        loginService.setReturnDeptName(false);

        // W12-impl：因子校验收敛至 MfaFactorVerifier（等价重构 wiring，断言零修改）
        io.nop.auth.service.mfa.MfaFactorVerifier mfaFactorVerifier = new io.nop.auth.service.mfa.MfaFactorVerifier();
        setField(mfaFactorVerifier, "totpAuthenticator", totpAuthenticator);
        setField(mfaFactorVerifier, "smsCodeStore", smsCodeStore);
        setField(mfaFactorVerifier, "daoProvider", daoProvider);
        setField(loginService, "mfaFactorVerifier", mfaFactorVerifier);
        setField(userBizModel, "mfaFactorVerifier", mfaFactorVerifier);

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
