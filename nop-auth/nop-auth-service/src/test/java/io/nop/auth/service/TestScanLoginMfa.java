package io.nop.auth.service;

import io.nop.ai.gateway.login.ChannelLoginApiBizModel;
import io.nop.ai.gateway.login.ScanLoginResult;
import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.bind.BindStartResult;
import io.nop.auth.api.bind.ChannelBindingInfo;
import io.nop.auth.api.bind.IChannelBindService;
import io.nop.auth.api.messages.AccessCodeRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.IUserContextCache;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.mfa.store.LocalSmsCodeStore;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.login.LoginServiceImpl;
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
import io.nop.integration.api.bind.ChannelBindResult;
import io.nop.integration.api.bind.ChannelBindResultStatus;
import io.nop.integration.api.bind.ChannelScanCallback;
import io.nop.integration.api.bind.IChannelBindProvider;
import io.nop.integration.api.channel.ChannelTypeCodes;
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
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6 Phase 3 test for the scan-login MFA adaptation in
 * {@link ChannelLoginApiBizModel#loginByScanAsync}. Assembles the real
 * {@link ChannelLoginApiBizModel} + real {@link LoginServiceImpl} (as
 * {@code ISessionBootstrap}) over a real in-memory H2 database, with stub
 * channel-binding collaborators to isolate the MFA-capture logic.
 *
 * <p><b>Anti-Hollow / runtime-intercept verification</b> (Minimum Rules #22 / #23):
 * {@code createSessionForUserAsync} throws {@code ERR_AUTH_MFA_REQUIRED}
 * <b>synchronously</b>; the test asserts the try/catch in {@code loginByScanAsync}
 * really intercepts it at runtime (the returned {@link ScanLoginResult} has
 * {@code mfaRequired=true} with populated challenge params), proving the capture
 * mechanism is wired — not a silent no-op where the exception bubbles unchanged.
 *
 * <p><b>No silent skip</b> (Rule #24): non-MFA error codes (disabled user) must
 * still bubble — asserted explicitly. Non-MFA users return {@code mfaRequired=false}
 * with an {@code accessCode} (zero regression).
 */
class TestScanLoginMfa {

    private static final String TENANT_ID = "0";
    private static final String CHANNEL = "feishu";
    private static Boolean originalTenantByDefault;
    private static Boolean originalMfaEnabled;

    // ---- H2 + ORM stack ----
    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;

    // ---- real beans ----
    private JwtAuthTokenProvider authTokenProvider;
    private LocalUserContextCache userContextCache;
    private LocalMfaChallengeStore mfaChallengeStore;
    private TOTPAuthenticator totpAuthenticator;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;
    private ChannelLoginApiBizModel channelLoginApi;
    private StubBindProvider bindProvider;
    private StubBindService bindService;

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
        // Defensive: AutoTestCase-based sibling tests reset VarCollector to null on teardown.
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

    // ===================== MFA user: scan → mfaRequired result → mfaVerify → accessCode =====================

    @Test
    void testScanLoginMfaUserReturnsChallengeParams() {
        String userId = "scan-mfa-user";
        String extId = "ou_scan_mfa";
        saveUser(userId, true);
        bindService.map.put(extId, userId);

        ScanLoginResult result = ormTemplate.runInSession(s -> scanLogin(extId));

        // runtime-intercept assertion: the sync-thrown MFA_REQUIRED was captured
        assertTrue(result.isMfaRequired(), "MFA user scan must return mfaRequired=true");
        assertNotNull(result.getChallengeToken(), "challengeToken must be populated");
        assertEquals(NopAuthConstants.MFA_TYPE_TOTP, result.getMfaType());
        assertEquals(ChannelTypeCodes.LOGIN_TYPE_FEISHU, result.getLoginType());
        assertNull(result.getAccessCode(), "MFA scan must NOT mint an accessCode before second factor");

        // wiring: the challenge really exists in the store (created upstream by createSessionForUserAsync)
        MfaChallenge challenge = mfaChallengeStore.peek(result.getChallengeToken());
        assertNotNull(challenge, "challenge must exist in the store after scan-login MFA interception");

        // full chain: mfaVerify(challengeToken, totp) → accessCode (channel-typed exit)
        NopAuthMfaSetting setting = daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        String plainSecret = totpAuthenticator.getCipher().decrypt(setting.getSecret());
        String code = computeTotpCode(plainSecret, System.currentTimeMillis());
        LoginResult loginResult = ormTemplate.runInSession(s -> doMfaVerify(result.getChallengeToken(), code));

        assertNotNull(loginResult.getAccessCode(), "channel-typed mfaVerify must yield accessCode");
        assertNull(loginResult.getAccessToken(), "channel-typed mfaVerify must NOT yield accessToken");
    }

    // ===================== Non-MFA user: zero regression =====================

    @Test
    void testScanLoginNonMfaUserZeroRegression() {
        String userId = "scan-plain-user";
        String extId = "ou_scan_plain";
        saveUser(userId, false);
        bindService.map.put(extId, userId);

        ScanLoginResult result = ormTemplate.runInSession(s -> scanLogin(extId));

        assertFalse(result.isMfaRequired(), "non-MFA user scan must return mfaRequired=false");
        assertNotNull(result.getAccessCode(), "non-MFA user scan must mint an accessCode");
        // non-MFA: challenge params are null (backward compat — existing callers ignore them)
        assertNull(result.getChallengeToken());
        assertNull(result.getMfaType());
    }

    // ===================== Non-MFA exception still bubbles (no silent swallow) =====================

    @Test
    void testNonMfaExceptionStillBubbles() {
        String userId = "scan-disabled-user";
        String extId = "ou_scan_disabled";
        // disabled user → createSessionForUserAsync throws USER_NOT_ALLOW_LOGIN synchronously
        saveDisabledUser(userId);
        bindService.map.put(extId, userId);

        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> scanLogin(extId)));
        // must be the USER_NOT_ALLOW_LOGIN code, NOT swallowed or mapped to mfaRequired
        assertEquals(NopAuthErrors.ERR_AUTH_USER_NOT_ALLOW_LOGIN.getErrorCode(), ex.getErrorCode(),
                "non-MFA exception must bubble unchanged (not swallowed)");
    }

    @Test
    void testBackwardCompatSerializationFieldsOptional() {
        ScanLoginResult r = new ScanLoginResult();
        // new fields default to false/null; existing callers reading only accessCode are unaffected
        assertFalse(r.isMfaRequired());
        assertNull(r.getChallengeToken());
        assertNull(r.getMfaType());
        assertNull(r.getLoginType());
        assertNull(r.getAccessCode());
        // setters round-trip
        r.setAccessCode("code");
        r.setMfaRequired(true);
        r.setChallengeToken("tok");
        r.setMfaType("totp");
        r.setLoginType(20);
        assertEquals("code", r.getAccessCode());
        assertEquals("totp", r.getMfaType());
    }

    // ===================== Helpers =====================

    private ScanLoginResult scanLogin(String extId) {
        ChannelScanCallback callback = new ChannelScanCallback();
        callback.setChannelType(CHANNEL);
        callback.setTicketId("ticket-" + extId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("open_id", extId);
        callback.setRawPayload(payload);
        return FutureHelper.syncGet(channelLoginApi.loginByScanAsync(callback, new ServiceContextImpl()));
    }

    private LoginResult doMfaVerify(String challengeToken, String code) {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setChallengeToken(challengeToken);
        req.setCode(code);
        IServiceContext ctx = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(req, ctx));
    }

    private void saveUser(String userId, boolean mfaEnabled) {
        String salt = new SHA256PasswordEncoder().generateSalt();
        String encoded = new SHA256PasswordEncoder().encodePassword(salt, "123");
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
            NopAuthUser user = dao.newEntity();
            user.setUserId(userId);
            user.setUserName("u_" + userId);
            user.setNickName("u_" + userId);
            user.setPassword(encoded);
            user.setSalt(salt);
            user.setOpenId(userId);
            user.setUserType(1);
            user.setStatus(AuthApiConstants.USER_STATUS_ACTIVE);
            user.setGender(1);
            user.setTenantId(TENANT_ID);
            dao.saveEntity(user);

            if (mfaEnabled) {
                String base32Secret = totpAuthenticator.generateSecret();
                String encrypted = totpAuthenticator.getCipher().encrypt(base32Secret);
                IEntityDao<NopAuthMfaSetting> sDao = daoProvider.daoFor(NopAuthMfaSetting.class);
                NopAuthMfaSetting setting = sDao.newEntity();
                setting.setUserId(userId);
                setting.setMfaType(NopAuthConstants.MFA_TYPE_TOTP);
                setting.setSecret(encrypted);
                setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
                setting.setTenantId(TENANT_ID);
                sDao.saveEntity(setting);
            }
            return null;
        });
    }

    private void saveDisabledUser(String userId) {
        String salt = new SHA256PasswordEncoder().generateSalt();
        String encoded = new SHA256PasswordEncoder().encodePassword(salt, "123");
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
            NopAuthUser user = dao.newEntity();
            user.setUserId(userId);
            user.setUserName("u_" + userId);
            user.setNickName("u_" + userId);
            user.setPassword(encoded);
            user.setSalt(salt);
            user.setOpenId(userId);
            user.setUserType(1);
            user.setStatus(AuthApiConstants.USER_STATUS_DISABLED);
            user.setGender(1);
            user.setTenantId(TENANT_ID);
            dao.saveEntity(user);
            return null;
        });
    }

    private static String computeTotpCode(String base32Secret, long timeMillis) {
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

    // ===================== H2 + ORM stack + wiring =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:scan-mfa-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("scan-mfa", CacheConfig.newConfig(100)));
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
        authTokenProvider = new JwtAuthTokenProvider();
        authTokenProvider.setEncKey("test-enc-key-scan-mfa");

        userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        mfaChallengeStore = new LocalMfaChallengeStore();
        totpAuthenticator = new TOTPAuthenticator();

        loginService = new LoginServiceImpl();
        setField(loginService, "daoProvider", daoProvider);
        setField(loginService, "passwordEncoder", new SHA256PasswordEncoder());
        setField(loginService, "auditService", new NoopAuditService());
        setField(loginService, "authTokenProvider", authTokenProvider);
        setField(loginService, "userContextCache", userContextCache);
        setField(loginService, "loginSessionStore", new UuidSessionStore());
        setField(loginService, "mfaChallengeStore", mfaChallengeStore);
        setField(loginService, "smsCodeStore", new LocalSmsCodeStore());
        setField(loginService, "totpAuthenticator", totpAuthenticator);
        // W12-impl：因子校验收敛至 MfaFactorVerifier（等价重构 wiring，断言零修改）
        io.nop.auth.service.mfa.MfaFactorVerifier mfaFactorVerifier = new io.nop.auth.service.mfa.MfaFactorVerifier();
        setField(mfaFactorVerifier, "totpAuthenticator", totpAuthenticator);
        setField(mfaFactorVerifier, "smsCodeStore", new LocalSmsCodeStore());
        setField(mfaFactorVerifier, "daoProvider", daoProvider);
        setField(loginService, "mfaFactorVerifier", mfaFactorVerifier);
        loginService.setReturnDeptName(false);

        loginApiBizModel = new LoginApiBizModel();
        setField(loginApiBizModel, "loginService", loginService);

        bindProvider = new StubBindProvider();
        bindService = new StubBindService();

        channelLoginApi = new ChannelLoginApiBizModel();
        setField(channelLoginApi, "authTokenProvider", authTokenProvider);
        setField(channelLoginApi, "sessionBootstrap", loginService);
        setField(channelLoginApi, "channelBindService", bindService);
        channelLoginApi.setAccessCodeExpireSeconds(300);
        channelLoginApi.registerProvider(bindProvider);
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

    /** Minimal provider: parses the callback open_id into an extId. */
    static class StubBindProvider implements IChannelBindProvider {
        @Override
        public String getChannelType() {
            return CHANNEL;
        }

        @Override
        public io.nop.integration.api.bind.BindTicket createBindTicket(String channelType, String platformUserId) {
            return new io.nop.integration.api.bind.BindTicket();
        }

        @Override
        public ChannelBindResult onChannelScanCallback(ChannelScanCallback callback) {
            ChannelBindResult r = new ChannelBindResult();
            Object openId = callback.getRawPayload().get("open_id");
            r.setExtId(openId == null ? null : openId.toString());
            r.setStatus(ChannelBindResultStatus.BINDING_COMPLETED);
            return r;
        }
    }

    /** Minimal bind service: extId → platformUserId map. */
    static class StubBindService implements IChannelBindService {
        final Map<String, String> map = new HashMap<>();

        @Override
        public BindStartResult startBinding(String channelType, String platformUserId) {
            return new BindStartResult();
        }

        @Override
        public ChannelBindingInfo completeBinding(String channelType, String platformUserId, String extId) {
            ChannelBindingInfo info = new ChannelBindingInfo();
            info.setPlatformUserId(platformUserId);
            info.setExtId(extId);
            info.setChannelType(channelType);
            return info;
        }

        @Override
        public ChannelBindingInfo findBinding(String channelType, String extId) {
            String userId = map.get(extId);
            if (userId == null) return null;
            ChannelBindingInfo info = new ChannelBindingInfo();
            info.setPlatformUserId(userId);
            info.setExtId(extId);
            info.setChannelType(channelType);
            return info;
        }

        @Override
        public List<ChannelBindingInfo> listBindings(String userId) {
            return Collections.emptyList();
        }

        @Override
        public void unbind(String bindingId) {
        }
    }

    static class NoopAuditService implements IAuditService {
        @Override
        public boolean isAllProcessed() {
            return true;
        }

        @Override
        public void saveAudit(AuditRequest request) {
        }
    }

    static class UuidSessionStore implements ILoginSessionStore {
        @Override
        public SessionInfo getSessionInfoForUser(String userName) {
            return null;
        }

        @Override
        public String saveSession(io.nop.api.core.auth.IUserContext userContext,
                                  io.nop.auth.api.messages.LoginRequest request,
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
