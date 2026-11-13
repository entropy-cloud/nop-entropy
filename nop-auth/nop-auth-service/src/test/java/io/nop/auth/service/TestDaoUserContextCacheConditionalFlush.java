package io.nop.auth.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.core.login.UserContextConfig;
import io.nop.auth.dao.entity.NopAuthSession;
import io.nop.auth.service.login.DaoUserContextCache;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.concurrent.lock.impl.LocalResourceLockManager;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C1-010（R4-33 移交接收轨，plan 2568）回归钉：DaoUserContextCache 并发 flush 的
 * DB 层丢失更新——过期副本回写旧 refresh_token 会回滚令牌旋转（SSO 部署下刷新链断裂）。
 *
 * <p>修复前行为：saveUserContextAsync 走 getEntityById 读-改-写（无 version 列、无条件 UPDATE），
 * 过期副本无条件覆盖 DB 行 → 红① 红④。
 *
 * <p>修复形态：读时基线（refreshToken/lastAccessTime 的 DB 行值）条件 UPDATE + affected-row 判定
 * + per-session 单飞（IResourceLockManager）+ clearDirty 后置于落库成功。
 */
class TestDaoUserContextCacheConditionalFlush {

    private static final String TENANT_ID = "0";

    private static final String RT0 = "rt-zero-baseline";
    private static final String RT1 = "rt-one-rotated";
    private static final String AT0 = "at-zero-baseline";

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;
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

    // ===================== 红① 过期副本不得回滚旋转 token =====================

    @Test
    void staleContextSaveMustNotRollBackRotatedRefreshToken() {
        String sessionId = createSessionRow("c1-user", "c1_user", RT0, AT0);

        // 段1：独立会话 load 副本 A（基线 RT0）
        IUserContext copyA = loadContext(sessionId);
        assertNotNull(copyA);
        assertEquals(RT0, copyA.getRefreshToken());

        // 段2：独立会话直写旋转 RT0 -> RT1（模拟另一请求/另一副本完成 IdP 刷新并落库）
        ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthSession> dao = daoProvider.daoFor(NopAuthSession.class);
            NopAuthSession row = dao.getEntityById(sessionId);
            row.setRefreshToken(RT1);
            return null;
        }));
        assertEquals(RT1, readDbRefreshToken(sessionId), "rotation must be committed");

        // 段3：过期副本 A 再次 save —— 不得把 RT1 回滚成 RT0
        ormTemplate.runInSession(s ->
                FutureHelper.syncGet(daoCache.saveUserContextAsync(copyA)));

        assertEquals(RT1, readDbRefreshToken(sessionId),
                "stale copy flush must not roll back rotated refresh token (2.3-C1-010)");
    }

    // ===================== 绿① logout 后 save 放弃 =====================

    @Test
    void saveAfterLogoutMustNotResurrectRow() {
        String sessionId = createSessionRow("c1-out-user", "c1_out_user", RT0, AT0);
        IUserContext copy = loadContext(sessionId);
        assertNotNull(copy);

        ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthSession> dao = daoProvider.daoFor(NopAuthSession.class);
            NopAuthSession row = dao.getEntityById(sessionId);
            row.setLogoutType(AuthApiConstants.LOGOUT_TYPE_EXPIRE);
            row.setLogoutTime(CoreMetrics.currentTimestamp());
            return null;
        }));

        ormTemplate.runInSession(s ->
                FutureHelper.syncGet(daoCache.saveUserContextAsync(copy)));

        // 行保持登出态，token 不被复活改写
        AtomicReference<NopAuthSession> ref = new AtomicReference<>();
        ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID, () -> {
            ref.set(daoProvider.daoFor(NopAuthSession.class).getEntityById(sessionId));
            return null;
        }));
        assertEquals(AuthApiConstants.LOGOUT_TYPE_EXPIRE, ref.get().getLogoutType());
        assertEquals(RT0, ref.get().getRefreshToken(), "logout row token must not be rewritten");
    }

    // ===================== 绿② 正常 save 全字段落库 + clearDirty =====================

    @Test
    void normalSavePersistsAllFieldsAndClearsDirty() {
        String sessionId = createSessionRow("c1-ok-user", "c1_ok_user", RT0, AT0);
        IUserContext copy = loadContext(sessionId);
        assertNotNull(copy);

        copy.setRefreshToken(RT1);
        copy.setAccessToken("at-one-rotated");
        ((io.nop.auth.core.login.UserContextImpl) copy).setNickName("renamed");

        ormTemplate.runInSession(s ->
                FutureHelper.syncGet(daoCache.saveUserContextAsync(copy)));

        AtomicReference<NopAuthSession> ref = new AtomicReference<>();
        ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID, () -> {
            ref.set(daoProvider.daoFor(NopAuthSession.class).getEntityById(sessionId));
            return null;
        }));
        assertEquals(RT1, ref.get().getRefreshToken());
        assertEquals("at-one-rotated", ref.get().getAccessToken());
        assertNotNull(ref.get().getCacheData());
        assertTrue(ref.get().getCacheData().contains("renamed"), "cacheData must be persisted");
        assertFalse(copy.dirty(), "clearDirty must run only after successful persistence");
        assertEquals(RT1, readDbRefreshToken(sessionId));
    }

    // ===================== 白名单钉：基线字段不得泄漏进 cacheData =====================

    @Test
    void baselineFieldsMustNotLeakIntoCacheData() {
        String sessionId = createSessionRow("c1-leak-user", "c1_leak_user", RT0, AT0);
        IUserContext copy = loadContext(sessionId);
        assertNotNull(copy);

        ormTemplate.runInSession(s ->
                FutureHelper.syncGet(daoCache.saveUserContextAsync(copy)));

        AtomicReference<String> cacheData = new AtomicReference<>();
        ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID, () -> {
            cacheData.set(daoProvider.daoFor(NopAuthSession.class).getEntityById(sessionId).getCacheData());
            return null;
        }));
        assertNotNull(cacheData.get());
        Map<String, Object> map = (Map<String, Object>) JsonTool.parse(cacheData.get());
        assertFalse(map.containsKey("baselineRefreshToken"), "baseline field must not leak into cacheData");
        assertFalse(map.containsKey("baselineLastAccessTime"), "baseline field must not leak into cacheData");
    }

    // ===================== 红④ 并发过期副本 flush：RT 不回滚 =====================

    @Test
    void concurrentStaleFlushesMustNotRollBackRotatedRefreshToken() throws Exception {
        String sessionId = createSessionRow("c1-conc-user", "c1_conc_user", RT0, AT0);

        // 8 个副本全部在旋转前 load（基线同为 RT0）
        int n = 8;
        List<IUserContext> copies = new CopyOnWriteArrayList<>();
        for (int i = 0; i < n; i++) {
            copies.add(loadContext(sessionId));
        }

        // 旋转落库 RT0 -> RT1
        ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthSession> dao = daoProvider.daoFor(NopAuthSession.class);
            dao.getEntityById(sessionId).setRefreshToken(RT1);
            return null;
        }));

        // 8 线程并发 save 过期副本
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        for (IUserContext copy : copies) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            futures.add(f);
            pool.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    ormTemplate.runInSession(s ->
                            FutureHelper.syncGet(daoCache.saveUserContextAsync(copy)));
                    f.complete(null);
                } catch (Throwable t) {
                    errors.add(t);
                    f.completeExceptionally(t);
                }
            });
        }
        start.countDown();
        for (CompletableFuture<Void> f : futures) {
            FutureHelper.syncGet(f);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty(), "concurrent flushes must not throw: " + errors);

        assertEquals(RT1, readDbRefreshToken(sessionId),
                "concurrent stale flushes must not roll back rotated refresh token");
    }

    // ===================== Helpers =====================

    private String createSessionRow(String userId, String userName, String refreshToken, String accessToken) {
        return ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthSession> dao = daoProvider.daoFor(NopAuthSession.class);
            NopAuthSession row = dao.newEntity();
            row.setSessionId("sess-" + userName);
            row.setUserId(userId);
            row.setUserName(userName);
            row.setTenantId(TENANT_ID);
            row.setLoginTime(CoreMetrics.currentTimestamp());
            row.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
            row.setLogoutType(AuthApiConstants.LOGOUT_TYPE_NONE);
            row.setLastAccessTime(CoreMetrics.currentDateTime());
            row.setRefreshToken(refreshToken);
            row.setAccessToken(accessToken);
            dao.saveEntity(row);
            return row.getSessionId();
        }));
    }

    private IUserContext loadContext(String sessionId) {
        return ormTemplate.runInSession(s ->
                ContextProvider.runWithTenant(TENANT_ID, () ->
                        FutureHelper.syncGet(daoCache.getUserContextAsync(sessionId))));
    }

    private String readDbRefreshToken(String sessionId) {
        // EQL 标量投影直查 DB（绕一级缓存，读到已提交值）
        SQL select = SQL.begin().name("craFlushReadRefreshToken")
                .sql("select o.refreshToken from NopAuthSession o where o.sessionId = ?", sessionId)
                .end();
        return ormTemplate.findString(select, null);
    }

    // ===================== H2 + wiring（与 TestDaoSessionStoreAndUserContextCache 同构） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:cra-flush-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("cra-flush", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);

        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new SQL(createSql));

        OrmDaoProvider ormDaoProvider = new OrmDaoProvider();
        ormDaoProvider.setOrmTemplate(ormTemplate);
        daoProvider = ormDaoProvider;
    }

    private void wireRealBeans() {
        daoCache = new DaoUserContextCache();
        daoCache.setUserContextConfig(new UserContextConfig());
        setField(daoCache, "daoProvider", daoProvider);
        // 2.3-C1-010 修复（plan 2568）新增字段：红证阶段（修复前代码）字段尚不存在，容忍缺位；
        // 修复后红①/红④ 经条件 UPDATE 路径必须真实用到 ormTemplate/lockManager（变异探针覆盖）
        setFieldIfPresent(daoCache, "ormTemplate", ormTemplate);
        setFieldIfPresent(daoCache, "lockManager", new LocalResourceLockManager());
        daoCache.init();
    }

    private static void setFieldIfPresent(Object target, String name, Object value) {
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
        // 字段不存在：修复前代码形态，跳过
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
