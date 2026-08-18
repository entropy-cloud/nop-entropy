package io.nop.auth.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.store.LocalMfaChallengeStore;
import io.nop.auth.core.mfa.store.LocalSmsCodeStore;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.auth.service.mfa.RoleMfaPolicyEvaluator;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W13-impl Phase 2：三层判定矩阵（设计 §4.3 完整真值表）+ 受限签发 + LoginResult 契约。
 * <p>
 * 矩阵五行（"一期行为"= 现行 checkMfaRequired 路径逐字节等价）：
 * <ol>
 *   <li>全局开关 off → 一期旁路（直接放行，策略不介入）。</li>
 *   <li>无策略（无行）→ 一期判定不变（未启用 → 直接放行）。</li>
 *   <li>有策略 + enabled 且 level(因子) ≥ L → 一期 challenge 路径（ERR_AUTH_MFA_REQUIRED
 *       + challengeToken/mfaType，表达与一期逐字节一致）。</li>
 *   <li>有策略 + enabled 弱因子（level &lt; L）→ 直接受限（不建 challenge）。</li>
 *   <li>有策略 + 未启用 → 直接受限。</li>
 * </ol>
 * 受限签发：LoginResult.mfaRestricted=TRUE + IUserContext.isMfaRestricted()=true；
 * 正常登录 mfaRestricted 缺省不出现（null——可选字段向后兼容）。
 */
class TestMfaRestrictedLoginE2E {

    private static final String TENANT_ID = "0";
    private static final String POLICY_ROLE = "restricted-policy-role";

    private static Boolean originalMfaEnabled;

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;

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
        if (VarCollector.instance() == null) {
            VarCollector.registerInstance(new VarCollector());
        }
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

    // ===================== 矩阵行 1：全局开关 off → 一期旁路 =====================

    @Test
    void testMatrixGlobalSwitchOffBypassesPolicy() {
        // 未启用 MFA + 角色策略 level 2 —— 开关 off 时必须直接放行（策略不介入）
        saveUserWithPolicyRole("switch-off-user", "switch_off_user", null, null);
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.mfa.enabled", false);
        try {
            LoginResult result = ormTemplate.runInSession(s -> doPasswordLogin("switch_off_user"));
            assertNotNull(result.getAccessToken(), "global switch off => direct pass (一期旁路)");
            assertNull(result.getMfaRestricted(), "restricted flag must not appear when MFA subsystem bypassed");
        } finally {
            provider.assignConfigValue("nop.auth.mfa.enabled", true);
        }
    }

    // ===================== 矩阵行 2：无策略 → 一期判定 =====================

    @Test
    void testMatrixNoPolicyRowIsPhaseOneBehavior() {
        // 用户不挂策略角色、未启用 MFA → 直接放行（一期行为，且无受限标志）
        saveUser("nopolicy-user", "nopolicy_user", null, null);
        LoginResult result = ormTemplate.runInSession(s -> doPasswordLogin("nopolicy_user"));
        assertNotNull(result.getAccessToken(), "no policy row => phase-one direct pass");
        assertNull(result.getMfaRestricted(), "normal login must not carry mfaRestricted");
    }

    // ===================== 矩阵行 3：达标 → 一期 challenge 路径（表达逐字节一致） =====================

    @Test
    void testMatrixSatisfiedPolicyGoesPhaseOneChallengePath() {
        // totp（level 2）≥ 策略 2 → 一期 challenge 拦截（不 restricted）
        saveUserWithPolicyRole("satisfied-user", "satisfied_user", "totp", null);
        NopException ex = assertThrows(NopException.class, () ->
                ormTemplate.runInSession(s -> doPasswordLogin("satisfied_user")));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), ex.getErrorCode(),
                "satisfied policy => phase-one ERR_AUTH_MFA_REQUIRED (byte-identical expression)");
        assertNotNull(ex.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN), "errorParams.challengeToken (一期表达)");
        assertEquals(NopAuthConstants.MFA_TYPE_TOTP, ex.getParam(NopAuthErrors.ARG_MFA_TYPE));
        assertEquals(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD,
                ex.getParam(NopAuthErrors.ARG_LOGIN_TYPE));
    }

    // ===================== 矩阵行 4：弱因子 → 直接受限（不建 challenge） =====================

    @Test
    void testMatrixWeakFactorRestrictedWithoutChallenge() {
        saveUserWithPolicyRole("weak-user", "weak_user", "sms", "13800138000");
        // 不建 challenge（结论 9）：受限签发是成功登录非异常——若走 challenge 路径会抛
        // ERR_AUTH_MFA_REQUIRED（登录成功 + mfaRestricted=TRUE 即证明 challenge 创建分支未到达）
        LoginResult result = assertDoesNotThrow(() ->
                ormTemplate.runInSession(s -> doPasswordLogin("weak_user")),
                "restricted issuance is a successful login, not an exception");
        assertNotNull(result.getAccessToken(), "restricted session still carries tokens");
        assertEquals(Boolean.TRUE, result.getMfaRestricted(), "weak factor (level 1 < policy 2) => restricted");
    }

    // ===================== 矩阵行 5：未启用 → 直接受限 =====================

    @Test
    void testMatrixNotEnabledRestricted() {
        saveUserWithPolicyRole("disabled-user", "disabled_user", null, null);
        LoginResult result = assertDoesNotThrow(() ->
                ormTemplate.runInSession(s -> doPasswordLogin("disabled_user")));
        assertEquals(Boolean.TRUE, result.getMfaRestricted(), "not enabled + policy => restricted");
        assertNotNull(result.getAccessToken());
    }

    // ===================== 受限会话上下文 + LoginResult 序列化契约 =====================

    @Test
    void testRestrictedContextFlagAndOptionalJsonContract() {
        saveUserWithPolicyRole("ctx-user", "ctx_user", null, null);
        IUserContext ctx = ormTemplate.runInSession(s ->
                FutureHelper.syncGet(loginService.loginAsync(loginRequest("ctx_user"), new HashMap<>())));
        assertTrue(ctx.isMfaRestricted(), "issued context carries mfaRestricted=true");

        // LoginResult 序列化：正常登录不出现 mfaRestricted 键（可选字段向后兼容）
        LoginResult normal = new LoginResult();
        normal.setAccessToken("t");
        String json = JsonTool.stringify(normal);
        assertFalse(json.contains("mfaRestricted"), "normal LoginResult JSON must not carry mfaRestricted");
        LoginResult restricted = new LoginResult();
        restricted.setMfaRestricted(Boolean.TRUE);
        assertTrue(JsonTool.stringify(restricted).contains("mfaRestricted"));
    }

    // ===================== Helpers =====================

    private LoginRequest loginRequest(String userName) {
        LoginRequest req = new LoginRequest();
        req.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        req.setPrincipalId(userName);
        req.setPrincipalSecret("123");
        return req;
    }

    private LoginResult doPasswordLogin(String userName) {
        IServiceContext c = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.loginAsync(loginRequest(userName), c));
    }

    private void savePolicyRole(String roleId, int minMfaLevel) {
        io.nop.api.core.context.ContextProvider.runWithTenant(TENANT_ID, () -> {
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

    private void saveUser(String userId, String userName, String mfaType, String phone) {
        SHA256PasswordEncoder encoder = new SHA256PasswordEncoder();
        String salt = encoder.generateSalt();
        io.nop.api.core.context.ContextProvider.runWithTenant(TENANT_ID, () -> {
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

            if (mfaType != null) {
                IEntityDao<NopAuthMfaSetting> settingDao = daoProvider.daoFor(NopAuthMfaSetting.class);
                NopAuthMfaSetting setting = settingDao.newEntity();
                setting.setUserId(userId);
                setting.setMfaType(mfaType);
                setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
                setting.setTenantId(TENANT_ID);
                if (phone != null) {
                    setting.setPhone(phone);
                }
                if (NopAuthConstants.MFA_TYPE_TOTP.equals(mfaType)) {
                    setting.setSecret(new TOTPAuthenticator().getCipher().encrypt(
                            new TOTPAuthenticator().generateSecret()));
                }
                settingDao.saveEntity(setting);
            }
            return null;
        });
    }

    private void saveUserWithPolicyRole(String userId, String userName, String mfaType, String phone) {
        saveUser(userId, userName, mfaType, phone);
        io.nop.api.core.context.ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUserRole> mappingDao = daoProvider.daoFor(NopAuthUserRole.class);
            NopAuthUserRole mapping = mappingDao.newEntity();
            mapping.setUserId(userId);
            mapping.setRoleId(POLICY_ROLE);
            mappingDao.saveEntity(mapping);
            return null;
        });
    }

    // ===================== H2 + wiring（TestMfaUserSelfService 同型） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:mfa-restricted-e2e-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("mfa-restricted", CacheConfig.newConfig(100)));
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
        authTokenProvider.setEncKey("test-enc-key-mfa-restricted");

        LocalUserContextCache userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        TOTPAuthenticator totpAuthenticator = new TOTPAuthenticator();
        SHA256PasswordEncoder passwordEncoder = new SHA256PasswordEncoder();

        MfaFactorVerifier mfaFactorVerifier = new MfaFactorVerifier();
        setField(mfaFactorVerifier, "totpAuthenticator", totpAuthenticator);
        setField(mfaFactorVerifier, "smsCodeStore", new LocalSmsCodeStore());
        setField(mfaFactorVerifier, "daoProvider", daoProvider);

        RoleMfaPolicyEvaluator evaluator = new RoleMfaPolicyEvaluator();
        setField(evaluator, "daoProvider", daoProvider);

        loginService = new LoginServiceImpl();
        setField(loginService, "daoProvider", daoProvider);
        setField(loginService, "passwordEncoder", passwordEncoder);
        setField(loginService, "auditService", new NoopAuditService());
        setField(loginService, "authTokenProvider", authTokenProvider);
        setField(loginService, "userContextCache", userContextCache);
        setField(loginService, "loginSessionStore", new UuidSessionStore());
        setField(loginService, "mfaChallengeStore", new LocalMfaChallengeStore());
        setField(loginService, "totpAuthenticator", totpAuthenticator);
        setField(loginService, "mfaFactorVerifier", mfaFactorVerifier);
        setField(loginService, "roleMfaPolicyEvaluator", evaluator);
        loginService.setReturnDeptName(false);

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
