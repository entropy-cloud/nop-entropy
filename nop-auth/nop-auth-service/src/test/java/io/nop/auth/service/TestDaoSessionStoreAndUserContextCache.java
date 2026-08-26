package io.nop.auth.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.core.login.RandomSessionIdGenerator;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthSession;
import io.nop.auth.service.login.DaoLoginSessionStore;
import io.nop.auth.service.login.DaoUserContextCache;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * check2 [P1]/[P2] 回归：DaoLoginSessionStore.getSessionInfoForUser 的未登出过滤字段
 * 与 DaoUserContextCache.checkExpired 对 lastAccessTime 为 null 行的边界。
 *
 * <p>修复前行为：
 * <ul>
 *   <li>[P1] getSessionInfoForUser 误将 LOGOUT_TYPE_NONE 设到 loginType 字段（复制粘贴错误），
 *       {@code userName=? AND loginType=0} 对真实登录会话（loginType=1/2/3/4/5/20+）恒不命中，
 *       killLoginAsync/getLoginUserContextAsync 对普通登录会话静默失效。</li>
 *   <li>[P2] checkExpired 直接 {@code session.getLastAccessTime().plus(...)}，裸 CrudBizModel
 *       直写/历史遗留的无 lastAccessTime 行在 getUserContextAsync 内 NPE。</li>
 * </ul>
 */
class TestDaoSessionStoreAndUserContextCache {

    private static final String TENANT_ID = "0";

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;
    private DaoLoginSessionStore sessionStore;
    private DaoUserContextCache daoCache;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
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
                // best-effort teardown
            }
        }
    }

    // ===================== [P1] getSessionInfoForUser 命中真实登录会话 =====================

    @Test
    void testGetSessionInfoForUserFindsActivePasswordSession() {
        String sessionId = saveActiveSession("p1-user", "p1_user", AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);

        SessionInfo info = ormTemplate.runInSession(s -> sessionStore.getSessionInfoForUser("p1_user"));
        assertNotNull(info, "active username-password session (loginType=1) must be found");
        assertEquals("p1_user", info.getUserName());
        assertEquals(sessionId, info.getSessionId());
    }

    @Test
    void testGetSessionInfoForUserFindsActiveSmsSession() {
        // loginType=5（短信验证码登录）同为真实登录形态，不得因字段误用而漏查
        String sessionId = saveActiveSession("p1-sms-user", "p1_sms_user", AuthApiConstants.LOGIN_TYPE_PHONE_SMS);

        SessionInfo info = ormTemplate.runInSession(s -> sessionStore.getSessionInfoForUser("p1_sms_user"));
        assertNotNull(info, "active sms session (loginType=5) must be found");
        assertEquals(sessionId, info.getSessionId());
    }

    @Test
    void testGetSessionInfoForUserSkipsLoggedOutSession() {
        String sessionId = saveActiveSession("p1-out-user", "p1_out_user", AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        ormTemplate.runInSession(s -> {
            sessionStore.logoutSession(sessionId, AuthApiConstants.LOGOUT_TYPE_EXPIRE, "tester");
            return null;
        });

        SessionInfo info = ormTemplate.runInSession(s -> sessionStore.getSessionInfoForUser("p1_out_user"));
        assertNull(info, "logged-out session must not be returned");
    }

    // ===================== [P2] checkExpired 对 null lastAccessTime 的边界 =====================

    @Test
    void testUserContextAsyncWithNullLastAccessTimeReturnsNullInsteadOfNpe() {
        // 模拟 NopAuthSession__save 直写/历史遗留行：logoutType=0 但 lastAccessTime 为 null
        ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthSession> dao = daoProvider.daoFor(NopAuthSession.class);
            NopAuthSession row = dao.newEntity();
            row.setSessionId("sess-null-last-access");
            row.setUserId("p2-user");
            row.setUserName("p2_user");
            row.setTenantId(TENANT_ID);
            row.setLoginTime(io.nop.api.core.time.CoreMetrics.currentTimestamp());
            row.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
            row.setLogoutType(AuthApiConstants.LOGOUT_TYPE_NONE);
            // 故意不设置 lastAccessTime（该列非 mandatory，裸 CrudBizModel 直写可产生）
            dao.saveEntity(row);
            return null;
        }));

        IUserContext ctx = ormTemplate.runInSession(s ->
                FutureHelper.syncGet(daoCache.getUserContextAsync("sess-null-last-access")));
        assertNull(ctx, "session row without lastAccessTime must be treated as expired/invalid, not NPE");
    }

    @Test
    void testUserContextAsyncWithFreshLastAccessTimeStillLoads() {
        // 对照组：正常行（lastAccessTime=now）不受 null 防御影响
        String sessionId = saveActiveSession("p2-ok-user", "p2_ok_user", AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID, () -> {
            NopAuthSession row = daoProvider.daoFor(NopAuthSession.class).getEntityById(sessionId);
            row.setCacheData(null);
            return null;
        }));

        IUserContext ctx = ormTemplate.runInSession(s ->
                FutureHelper.syncGet(daoCache.getUserContextAsync(sessionId)));
        assertNotNull(ctx, "fresh session must still load");
        assertEquals("p2-ok-user", ctx.getUserId());
    }

    // ===================== Helpers =====================

    private String saveActiveSession(String userId, String userName, int loginType) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userName);
        userContext.setTenantId(TENANT_ID);

        LoginRequest request = new LoginRequest();
        request.setLoginType(loginType);

        return ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID, () ->
                sessionStore.saveSession(userContext, request, new HashMap<>())));
    }

    // ===================== H2 + wiring（与 TestMfaRestrictedDaoCache 同构） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:dao-session-store-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("dao-session-store", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);

        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new io.nop.core.lang.sql.SQL(createSql));

        OrmDaoProvider __daoProvider = new OrmDaoProvider();
        __daoProvider.setOrmTemplate(ormTemplate);
        daoProvider = __daoProvider;
    }

    private void wireRealBeans() {
        sessionStore = new DaoLoginSessionStore();
        setField(sessionStore, "daoProvider", daoProvider);
        setField(sessionStore, "sessionIdGenerator", new RandomSessionIdGenerator());

        daoCache = new DaoUserContextCache();
        daoCache.setUserContextConfig(new UserContextConfig());
        setField(daoCache, "daoProvider", daoProvider);
        daoCache.init();
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
                throw io.nop.api.core.exceptions.NopException.adapt(e);
            }
        }
        throw new IllegalArgumentException("no field " + name + " on " + target.getClass());
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
