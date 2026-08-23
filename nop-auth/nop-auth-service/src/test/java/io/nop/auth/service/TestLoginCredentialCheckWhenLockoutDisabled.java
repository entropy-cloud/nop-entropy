package io.nop.auth.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
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
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 回归（nop-auth 检查报告 [P0]）：{@code nop.auth.login.max-login-fail-count} 配置为
 * 0/负数时，登录链路不得跳过凭证校验（密码比对 / isAllowLogin 账号状态检查）。
 *
 * <p>装配方式与 {@code TestMfaLoginE2E} 相同：真实 {@link LoginServiceImpl} +
 * {@link LoginApiBizModel} 跑在独立 H2 + ORM 栈上，仅验证用户名密码登录路径
 * （MFA 关闭、无短信/验证码依赖）。
 *
 * <p>修复前行为：max-login-fail-count=0 时错误密码/被禁用账号直接通过校验进入
 * completeLogin 签发 token（认证绕过）。
 */
class TestLoginCredentialCheckWhenLockoutDisabled {

    private static final String TENANT_ID = "0";
    private static final String CONFIG_KEY = "nop.auth.login.max-login-fail-count";

    private static Integer originalMaxFailCount;
    private static Boolean originalMfaEnabled;

    // ---- H2 + ORM stack ----
    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;

    // ---- real beans under test ----
    private SHA256PasswordEncoder passwordEncoder;
    private LoginServiceImpl loginService;
    private LoginApiBizModel loginApiBizModel;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalMaxFailCount = provider.getConfigValue(CONFIG_KEY, Integer.valueOf(
                NopAuthConfigs.CFG_AUTH_MAX_LOGIN_FAIL_COUNT.getDefaultValue()));
        originalMfaEnabled = provider.getConfigValue("nop.auth.mfa.enabled", Boolean.FALSE);
        // 本测试只覆盖用户名密码登录路径：显式关闭 MFA，避免共享 surefire JVM 中
        // 兄弟测试的配置覆盖影响判定
        provider.assignConfigValue("nop.auth.mfa.enabled", false);
    }

    @AfterAll
    static void restoreConfig() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue(CONFIG_KEY, originalMaxFailCount);
        provider.assignConfigValue("nop.auth.mfa.enabled",
                originalMfaEnabled != null ? originalMfaEnabled : Boolean.FALSE);
    }

    @BeforeEach
    void setUp() {
        buildH2Stack();
        wireRealBeans();
    }

    @AfterEach
    void tearDown() {
        if (factoryBean != null) {
            try {
                factoryBean.destroy();
            } catch (Exception e) {
                // ignore teardown errors — test assertion already passed
            }
        }
    }

    @Test
    void testWrongPasswordRejectedWhenMaxFailCountZero() {
        withMaxFailCount(0, () -> {
            saveUser("lockout-zero-user", "lockout_zero", AuthApiConstants.USER_STATUS_ACTIVE);

            NopException ex = assertThrows(NopException.class, () ->
                    ormTemplate.runInSession(session -> doPasswordLogin("lockout_zero", "wrong-password")),
                    "max-login-fail-count=0 must NOT disable password verification");
            assertEquals(NopAuthErrors.ERR_AUTH_LOGIN_CHECK_FAIL.getErrorCode(), ex.getErrorCode());
        });
    }

    @Test
    void testDisabledUserRejectedWhenMaxFailCountZero() {
        withMaxFailCount(0, () -> {
            // 正确密码 + 被禁用账号：isAllowLogin 必须拒绝
            saveUser("lockout-zero-disabled", "lockout_disabled", AuthApiConstants.USER_STATUS_DISABLED);

            NopException ex = assertThrows(NopException.class, () ->
                    ormTemplate.runInSession(session -> doPasswordLogin("lockout_disabled", "123")),
                    "max-login-fail-count=0 must NOT disable isAllowLogin account status check");
            assertEquals(NopAuthErrors.ERR_AUTH_USER_NOT_ALLOW_LOGIN.getErrorCode(), ex.getErrorCode());
        });
    }

    @Test
    void testCorrectPasswordStillSucceedsWhenMaxFailCountZero() {
        withMaxFailCount(0, () -> {
            saveUser("lockout-zero-ok", "lockout_ok", AuthApiConstants.USER_STATUS_ACTIVE);

            LoginResult result = ormTemplate.runInSession(session -> doPasswordLogin("lockout_ok", "123"));
            assertNotNull(result.getAccessToken(),
                    "legit user must still login when max-login-fail-count=0 (lockout disabled, not login)");
        });
    }

    /** 对照组：默认配置（>0）下错误密码仍被拒绝，钉定修复不改变常规路径行为。 */
    @Test
    void testWrongPasswordRejectedWithDefaultMaxFailCount() {
        withMaxFailCount(NopAuthConfigs.CFG_AUTH_MAX_LOGIN_FAIL_COUNT.getDefaultValue(), () -> {
            saveUser("lockout-default-user", "lockout_default", AuthApiConstants.USER_STATUS_ACTIVE);

            NopException ex = assertThrows(NopException.class, () ->
                    ormTemplate.runInSession(session -> doPasswordLogin("lockout_default", "wrong-password")));
            assertEquals(NopAuthErrors.ERR_AUTH_LOGIN_CHECK_FAIL.getErrorCode(), ex.getErrorCode());
        });
    }

    // ===================== Helpers =====================

    private static void withMaxFailCount(int value, Runnable body) {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue(CONFIG_KEY, value);
        try {
            body.run();
        } finally {
            provider.assignConfigValue(CONFIG_KEY, originalMaxFailCount);
        }
    }

    private LoginResult doPasswordLogin(String userName, String secret) {
        LoginRequest req = new LoginRequest();
        req.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        req.setPrincipalId(userName);
        req.setPrincipalSecret(secret);
        IServiceContext ctx = new ServiceContextImpl();
        return FutureHelper.syncGet(loginApiBizModel.loginAsync(req, ctx));
    }

    private void saveUser(String userId, String userName, int status) {
        String salt = passwordEncoder.generateSalt();
        String encodedPassword = passwordEncoder.encodePassword(salt, "123");
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
            NopAuthUser user = dao.newEntity();
            user.setUserId(userId);
            user.setUserName(userName);
            user.setNickName(userName);
            user.setPassword(encodedPassword);
            user.setSalt(salt);
            user.setOpenId(userId);
            user.setUserType(1);
            user.setStatus(status);
            user.setGender(1);
            user.setTenantId(TENANT_ID);
            dao.saveEntity(user);
            return null;
        });
    }

    // ===================== H2 + ORM stack（与 TestMfaLoginE2E 同构） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:login-lockout-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("login-lockout", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);

        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new SQL(createSql));

        daoProvider = new OrmDaoProvider(ormTemplate);
    }

    private void wireRealBeans() {
        JwtAuthTokenProvider authTokenProvider = new JwtAuthTokenProvider();
        authTokenProvider.setEncKey("test-enc-key-for-login-lockout");

        LocalUserContextCache userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        passwordEncoder = new SHA256PasswordEncoder();

        loginService = new LoginServiceImpl();
        setField(loginService, "daoProvider", daoProvider);
        setField(loginService, "passwordEncoder", passwordEncoder);
        setField(loginService, "auditService", new NoopAuditService());
        setField(loginService, "authTokenProvider", authTokenProvider);
        setField(loginService, "userContextCache", userContextCache);
        setField(loginService, "loginSessionStore", new UuidSessionStore());
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

    // ===================== Stubs =====================

    /** No-op audit service（登录失败路径会写审计，本测试只关心错误码）。 */
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
