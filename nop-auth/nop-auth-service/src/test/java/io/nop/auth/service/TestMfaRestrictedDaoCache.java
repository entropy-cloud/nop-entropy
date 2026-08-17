package io.nop.auth.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.RandomSessionIdGenerator;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.MfaLoginDecision;
import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.auth.service.login.DaoLoginSessionStore;
import io.nop.auth.service.login.DaoUserContextCache;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.auth.service.mfa.MfaLoginPolicyServiceImpl;
import io.nop.auth.service.mfa.RoleMfaPolicyEvaluator;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.api.core.context.ContextProvider;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W13-impl Phase 2：mfaRestricted 持久化 Dao-cache 路径 round-trip（fail-open 防护测试，
 * 设计 §4.3 持久化机制——Local cache 对象引用语义不能暴露白名单缺漏）+ OAuth 接入
 * （MfaLoginPolicyServiceImpl 三路径 + attrs 保留专项断言）。
 * <p>
 * Dao-cache 事实：每请求从 {@code NopAuthSession.cacheData} 反序列化全新对象
 * （JsonTool.parse + BeanTool.setProperties）——序列化是手工白名单。本测试用真实
 * {@link DaoUserContextCache} 断言"受限签发后第二请求标志仍在"。
 */
class TestMfaRestrictedDaoCache {

    private static final String TENANT_ID = "0";
    private static final String POLICY_ROLE = "daocache-policy-role";

    private static Boolean originalMfaEnabled;

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;
    private DaoUserContextCache daoCache;
    private LoginServiceImpl loginService;
    private MfaLoginPolicyServiceImpl oauthPolicyService;

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
    }

    @BeforeEach
    void setUp() {
        buildH2Stack();
        wireRealBeans();
        savePolicyRole(POLICY_ROLE, 2);
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

    // ===================== Dao-cache round-trip：受限签发 → 第二请求标志存活 =====================

    @Test
    void testRestrictedFlagSurvivesDaoCacheRoundTrip() {
        saveUserWithPolicyRole("daocache-user", "daocache_user");

        IUserContext issued = ormTemplate.runInSession(s -> FutureHelper.syncGet(
                loginService.loginAsync(loginRequest("daocache_user"), new HashMap<>())));
        assertTrue(issued.isMfaRestricted(), "issued context carries the flag");
        String sessionId = issued.getSessionId();
        assertNotNull(sessionId);

        // 第二请求：DaoUserContextCache 从 NopAuthSession.cacheData 反序列化全新对象
        // （必须仍受限——否则第二请求起标志丢失 = fail-open）
        UserContextImpl second = ormTemplate.runInSession(s ->
                (UserContextImpl) FutureHelper.syncGet(daoCache.getUserContextAsync(sessionId)));
        assertNotNull(second, "session row + cacheData must be persisted by completeLogin restricted variant");
        assertTrue(second.isMfaRestricted(), "mfaRestricted must survive Dao-cache deserialization (第二请求)");
        assertEquals("daocache-user", second.getUserId());
    }

    @Test
    void testNormalLoginHasNoFlagInDaoCacheRoundTrip() {
        saveUser("normal-user", "normal_user");

        IUserContext issued = ormTemplate.runInSession(s -> FutureHelper.syncGet(
                loginService.loginAsync(loginRequest("normal_user"), new HashMap<>())));
        assertFalse(issued.isMfaRestricted());
        String sessionId = issued.getSessionId();

        UserContextImpl second = ormTemplate.runInSession(s ->
                (UserContextImpl) FutureHelper.syncGet(daoCache.getUserContextAsync(sessionId)));
        assertNotNull(second);
        assertFalse(second.isMfaRestricted(), "normal sessions stay unrestricted across Dao-cache round-trips");
    }

    // ===================== OAuth（外部身份）三路径 + attrs 保留 =====================

    @Test
    void testOAuthPassThroughWhenNoPolicyUser() {
        // 无本地用户映射 → 空策略 → 放行（一期行为；migration note 预期行为变更的范围之外）
        assertNull(ormTemplate.runInSession(s ->
                oauthPolicyService.checkMfaForUserName("no-such-local-user", AuthApiConstants.LOGIN_TYPE_SSO)));
    }

    @Test
    void testOAuthPassThroughWhenPolicySatisfied() {
        // 达标（totp level 2 ≥ 策略 2）→ checkMfaForUserName 抛 ERR_AUTH_MFA_REQUIRED（challenge 分支，
        // 与一期表达一致）——注意：challenge 分支不是"放行"，达标用户在 OAuth 入口同样被 MFA 拦截
        saveUserWithPolicyRoleAndTotp("oauth-ok-user", "oauth_ok_user");
        NopException ex = assertThrows(NopException.class, () -> ormTemplate.runInSession(s ->
                oauthPolicyService.checkMfaForUserName("oauth_ok_user", AuthApiConstants.LOGIN_TYPE_SSO)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertNotNull(ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN));
        assertEquals(NopAuthConstants.MFA_TYPE_TOTP, ex.getParam(NopAuthErrors.ARG_MFA_TYPE));
        assertEquals(AuthApiConstants.LOGIN_TYPE_SSO, ex.getParam(NopAuthErrors.ARG_LOGIN_TYPE));
    }

    @Test
    void testOAuthRestrictedPathPreservesAttrsAndSurvivesDaoCache() {
        // 未启用 + 策略 2 → 受限；completeRestricted：先设后存 + 会话行保障 + attrs 不丢失
        saveUserWithPolicyRole("oauth-restricted-user", "oauth_restricted_user");

        MfaLoginDecision decision = ormTemplate.runInSession(s ->
                oauthPolicyService.checkMfaForUserName("oauth_restricted_user", AuthApiConstants.LOGIN_TYPE_SSO));
        assertNotNull(decision);
        assertTrue(decision.isRestricted(), "not enabled + policy => restricted decision");

        // OAuth 一期口径的上下文（userId=userName、attrs 携带 IdP tokens、sessionId=sessionState）
        UserContextImpl oauthContext = new UserContextImpl();
        oauthContext.setUserId("oauth_restricted_user");
        oauthContext.setUserName("oauth_restricted_user");
        oauthContext.setSessionId("idp-session-state-1");
        oauthContext.setAttr("accessToken", "idp-access-token");
        oauthContext.setAttr("refreshToken", "idp-refresh-token");

        UserContextImpl completed = ormTemplate.runInSession(s ->
                (UserContextImpl) FutureHelper.syncGet(oauthPolicyService.completeRestricted(oauthContext)));
        assertTrue(completed.isMfaRestricted(), "completeRestricted sets the flag (先设后存)");

        // OAuth attrs（accessToken/refreshToken）不得丢失（专项断言）
        assertEquals("idp-access-token", completed.getAttr("accessToken"));
        assertEquals("idp-refresh-token", completed.getAttr("refreshToken"));

        // Dao-cache round-trip：受限标志 + attrs 第二请求仍在（受限签发基座裁定）
        UserContextImpl second = ormTemplate.runInSession(s ->
                (UserContextImpl) FutureHelper.syncGet(daoCache.getUserContextAsync("idp-session-state-1")));
        assertNotNull(second, "completeRestricted must ensure the NopAuthSession row (Dao-cache 基座)");
        assertTrue(second.isMfaRestricted(), "OAuth restricted flag survives Dao-cache round-trip");
        assertEquals("idp-access-token", second.getAttr("accessToken"),
                "OAuth attrs must survive Dao-cache round-trip (不得丢失)");
        assertEquals("idp-refresh-token", second.getAttr("refreshToken"));
    }

    @Test
    void testOAuthGlobalSwitchOffBypass() {
        saveUserWithPolicyRole("oauth-off-user", "oauth_off_user");
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.mfa.enabled", false);
        try {
            assertNull(ormTemplate.runInSession(s ->
                    oauthPolicyService.checkMfaForUserName("oauth_off_user", AuthApiConstants.LOGIN_TYPE_SSO)),
                    "global switch off => pass-through (一期旁路)");
        } finally {
            provider.assignConfigValue("nop.auth.mfa.enabled", true);
        }
    }

    @Test
    void testCompleteRestrictedWithoutSessionIdIsSafe() {
        // sessionId 为空的上下文：ensureSessionRow no-op，不抛错（防御性）
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId("nosess-user");
        uc.setUserName("nosess_user");
        assertDoesNotThrow(() -> ormTemplate.runInSession(s ->
                FutureHelper.syncGet(oauthPolicyService.completeRestricted(uc))));
        assertTrue(uc.isMfaRestricted());
    }

    // ===================== Helpers =====================

    private LoginRequest loginRequest(String userName) {
        LoginRequest req = new LoginRequest();
        req.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        req.setPrincipalId(userName);
        req.setPrincipalSecret("123");
        return req;
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

    private void saveUserWithPolicyRole(String userId, String userName) {
        saveUser(userId, userName);
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUserRole> mappingDao = daoProvider.daoFor(NopAuthUserRole.class);
            NopAuthUserRole mapping = mappingDao.newEntity();
            mapping.setUserId(userId);
            mapping.setRoleId(POLICY_ROLE);
            mappingDao.saveEntity(mapping);
            return null;
        });
    }

    private void saveUserWithPolicyRoleAndTotp(String userId, String userName) {
        saveUserWithPolicyRole(userId, userName);
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<io.nop.auth.dao.entity.NopAuthMfaSetting> settingDao =
                    daoProvider.daoFor(io.nop.auth.dao.entity.NopAuthMfaSetting.class);
            io.nop.auth.dao.entity.NopAuthMfaSetting setting = settingDao.newEntity();
            setting.setUserId(userId);
            setting.setMfaType(NopAuthConstants.MFA_TYPE_TOTP);
            setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
            setting.setTenantId(TENANT_ID);
            setting.setSecret(new io.nop.auth.core.totp.TOTPAuthenticator().getCipher().encrypt(
                    new io.nop.auth.core.totp.TOTPAuthenticator().generateSecret()));
            settingDao.saveEntity(setting);
            return null;
        });
    }

    // ===================== H2 + wiring（真实 DaoUserContextCache/DaoLoginSessionStore） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:mfa-dao-cache-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("mfa-dao-cache", CacheConfig.newConfig(100)));
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
        authTokenProvider.setEncKey("test-enc-key-mfa-dao-cache");

        // 真实 DaoUserContextCache（每请求从 cacheData 反序列化全新对象）
        daoCache = new DaoUserContextCache();
        daoCache.setUserContextConfig(new UserContextConfig());
        setField(daoCache, "daoProvider", daoProvider);
        daoCache.init();

        // 真实 DaoLoginSessionStore（completeLogin saveSession 落 NopAuthSession 行）
        DaoLoginSessionStore sessionStore = new DaoLoginSessionStore();
        setField(sessionStore, "daoProvider", daoProvider);
        setField(sessionStore, "sessionIdGenerator", new RandomSessionIdGenerator());

        RoleMfaPolicyEvaluator evaluator = new RoleMfaPolicyEvaluator();
        setField(evaluator, "daoProvider", daoProvider);

        loginService = new LoginServiceImpl();
        setField(loginService, "daoProvider", daoProvider);
        setField(loginService, "passwordEncoder", new SHA256PasswordEncoder());
        setField(loginService, "auditService", new NoopAuditService());
        setField(loginService, "authTokenProvider", authTokenProvider);
        setField(loginService, "userContextCache", daoCache);
        setField(loginService, "loginSessionStore", sessionStore);
        setField(loginService, "mfaChallengeStore", new LocalMfaChallengeStore());
        setField(loginService, "roleMfaPolicyEvaluator", evaluator);
        loginService.setReturnDeptName(false);

        // OAuth 判定 SPI 实现（真实组件）
        oauthPolicyService = new MfaLoginPolicyServiceImpl();
        setField(oauthPolicyService, "daoProvider", daoProvider);
        setField(oauthPolicyService, "mfaChallengeStore", new LocalMfaChallengeStore());
        setField(oauthPolicyService, "userContextCache", daoCache);
        setField(oauthPolicyService, "roleMfaPolicyEvaluator", evaluator);
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

    static class NoopAuditService implements io.nop.api.core.audit.IAuditService {
        @Override
        public boolean isAllProcessed() {
            return true;
        }

        @Override
        public void saveAudit(io.nop.api.core.audit.AuditRequest request) {
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
