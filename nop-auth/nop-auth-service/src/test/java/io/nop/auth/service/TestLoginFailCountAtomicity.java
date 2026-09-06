package io.nop.auth.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.core.jwt.JwtAuthTokenProvider;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.password.SHA256PasswordEncoder;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
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
import org.junit.jupiter.api.Timeout;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 [P1] 回归：登录失败计数 read-modify-write 原子性。
 *
 * <p>修复前行为：失败路径 {@code getLoginFailCountForUser} + {@code setLoginFailCountForUser}
 * 两步之间无原子性（底层为 JVM 本地 Caffeine），N 个并发失败请求同时读到同一旧值各自写回，
 * 计数丢失更新 → 远慢于实际尝试次数，{@code max-login-fail-count} 锁号阈值被并发暴绕过。
 *
 * <p>本测试用 barrier 对齐的并发错误密码登录断言：N 次失败后计数必须恰好为 N。
 */
@Timeout(120)
class TestLoginFailCountAtomicity {

    private static final String TENANT_ID = "0";
    private static final int CONCURRENCY = 32;

    private static Boolean originalMfaEnabled;

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;
    private LocalUserContextCache userContextCache;
    private LoginServiceImpl loginService;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalMfaEnabled = provider.getConfigValue("nop.auth.mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.mfa.enabled", false);
    }

    @AfterAll
    static void restoreConfig() {
        IConfigProvider provider = AppConfig.getConfigProvider();
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
            } catch (Exception ignored) {
                // ignore teardown errors — test assertion already passed
            }
        }
    }

    @Test
    void testConcurrentFailedLoginsAreAllCounted() throws InterruptedException {
        saveUser("atomic-user", "atomic_user");

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CyclicBarrier barrier = new CyclicBarrier(CONCURRENCY);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);
        AtomicInteger rejected = new AtomicInteger();
        try {
            for (int i = 0; i < CONCURRENCY; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        ormTemplate.runInSession(session -> {
                            FutureHelper.syncGet(loginService.loginAsync(wrongPasswordRequest("atomic_user"), new HashMap<>()));
                            return null;
                        });
                    } catch (NopException expected) {
                        rejected.incrementAndGet();
                    } catch (Throwable t) {
                        // 其它异常在断言阶段暴露（loginAsync 失败路径应为 NopException reject）
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(60, TimeUnit.SECONDS), "all login attempts must finish");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(CONCURRENCY, rejected.get(), "every wrong-password attempt must be rejected");
        assertEquals(CONCURRENCY, userContextCache.getLoginFailCountForUser("atomic_user"),
                "no lost updates: concurrent fail count must equal actual attempt count");
    }

    /** 对照组：串行失败计数的既有语义不受影响。 */
    @Test
    void testSequentialFailedLoginsCounted() {
        saveUser("seq-user", "seq_user");

        for (int i = 0; i < 3; i++) {
            int round = i;
            ormTemplate.runInSession(session -> {
                try {
                    FutureHelper.syncGet(loginService.loginAsync(wrongPasswordRequest("seq_user"), new HashMap<>()));
                } catch (NopException expected) {
                    // ignored
                }
                return round;
            });
        }
        assertEquals(3, userContextCache.getLoginFailCountForUser("seq_user"));
    }

    // ===================== Helpers =====================

    private LoginRequest wrongPasswordRequest(String userName) {
        LoginRequest req = new LoginRequest();
        req.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        req.setPrincipalId(userName);
        req.setPrincipalSecret("wrong-password");
        return req;
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
            user.setStatus(AuthApiConstants.USER_STATUS_ACTIVE);
            user.setGender(1);
            user.setTenantId(TENANT_ID);
            dao.saveEntity(user);
            return null;
        });
    }

    // ===================== H2 + ORM stack（与 TestLoginCredentialCheckWhenLockoutDisabled 同构） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:login-fail-atomic-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("login-fail-atomic", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);

        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new SQL(createSql));

        OrmDaoProvider __daoProvider = new OrmDaoProvider();
        __daoProvider.setOrmTemplate(ormTemplate);
        daoProvider = __daoProvider;
    }

    private void wireRealBeans() {
        JwtAuthTokenProvider authTokenProvider = new JwtAuthTokenProvider();
        authTokenProvider.setEncKey("test-enc-key-for-login-fail-atomic");

        userContextCache = new LocalUserContextCache();
        userContextCache.setUserContextConfig(new UserContextConfig());
        userContextCache.init();

        loginService = new LoginServiceImpl();
        setField(loginService, "daoProvider", daoProvider);
        setField(loginService, "passwordEncoder", new SHA256PasswordEncoder());
        setField(loginService, "auditService", new NoopAuditService());
        setField(loginService, "authTokenProvider", authTokenProvider);
        setField(loginService, "userContextCache", userContextCache);
        setField(loginService, "loginSessionStore", new UuidSessionStore());
        loginService.setReturnDeptName(false);
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
