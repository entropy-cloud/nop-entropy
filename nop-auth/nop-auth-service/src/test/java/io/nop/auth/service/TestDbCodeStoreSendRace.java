package io.nop.auth.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.mfa.store.EmailCodeStoreConfig;
import io.nop.auth.core.mfa.store.SmsCodeStoreConfig;
import io.nop.auth.dao.entity.NopAuthEmailCode;
import io.nop.auth.dao.entity.NopAuthSmsCode;
import io.nop.auth.service.mfa.store.DbEmailCodeStore;
import io.nop.auth.service.mfa.store.DbSmsCodeStore;
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 [P2] 回归：DbSmsCodeStore/DbEmailCodeStore.send 首发插入的 read-then-insert 竞态。
 *
 * <p>修复前行为：{@code send} 先 getEntityById 再 saveEntityDirectly，两步之间无原子性。
 * 多节点部署（本地限流为 JVM 级，不跨节点互斥）下两节点同时对同一 key 首发 → 双 INSERT →
 * 一方主键冲突抛未归一异常（JdbcException/ERR_SQL_DUPLICATE_KEY），用户侧 500。
 *
 * <p>本测试用 JDK 动态代理确定性复现竞态窗口：首次 getEntityById 强制返回 null（模拟对手
 * 节点尚未插入），saveEntityDirectly 前先插入同 key 竞争行（模拟对手抢先落库）。修复后
 * send 应捕获主键冲突并回退为更新（与单节点重发语义一致），不再向上抛错。
 */
class TestDbCodeStoreSendRace {

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @BeforeEach
    void setUp() {
        buildH2Stack();
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

    @Test
    void testSmsSendDuplicateKeyFallsBackToUpdate() {
        DbSmsCodeStore store = new DbSmsCodeStore();
        store.setConfig(new SmsCodeStoreConfig());
        IEntityDao<NopAuthSmsCode> realDao = daoProvider.daoFor(NopAuthSmsCode.class);

        String key = "login:13800138000";
        // 竞争行：对手节点抢先插入的旧码（failCount=7 用于断言回退路径重置计数）
        NopAuthSmsCode rival = realDao.newEntity();
        rival.setCodeKey(key);
        rival.setPhone("13800138000");
        rival.setCode("111111");
        rival.setExpireAt(System.currentTimeMillis() - 1);
        rival.setFailCount(7);

        AtomicBoolean readMasked = new AtomicBoolean(true);
        IEntityDao<NopAuthSmsCode> racingDao = proxyDao(realDao, () -> {
            realDao.saveEntityDirectly(rival);
            readMasked.set(false);
        }, readMasked);
        IDaoProvider racingProvider = proxyProvider(racingDao, NopAuthSmsCode.class);

        setField(store, "daoProvider", racingProvider);
        setField(store, "ormTemplate", ormTemplate);

        String code = ormTemplate.runInSession(s -> store.send(key));

        assertNotNull(code);
        assertTrue(code.matches("\\d{6}"), "code must remain 6 digits: " + code);

        NopAuthSmsCode row = ormTemplate.runInSession(s -> daoProvider.daoFor(NopAuthSmsCode.class).getEntityById(key));
        assertNotNull(row, "row must exist after race resolution");
        assertEquals(code, row.getCode(), "fallback update must overwrite rival code with the new code");
        assertEquals(0, row.getFailCount(), "fallback update must reset failCount (resend semantics)");
        assertTrue(row.getExpireAt() > System.currentTimeMillis(), "fallback update must refresh TTL");
    }

    @Test
    void testEmailSendDuplicateKeyFallsBackToUpdate() {
        DbEmailCodeStore store = new DbEmailCodeStore();
        store.setConfig(new EmailCodeStoreConfig());
        IEntityDao<NopAuthEmailCode> realDao = daoProvider.daoFor(NopAuthEmailCode.class);

        String key = "mfa-email:race-user";
        NopAuthEmailCode rival = realDao.newEntity();
        rival.setCodeKey(key);
        rival.setCode("222222");
        rival.setExpireAt(System.currentTimeMillis() - 1);
        rival.setFailCount(5);

        AtomicBoolean readMasked = new AtomicBoolean(true);
        IEntityDao<NopAuthEmailCode> racingDao = proxyDao(realDao, () -> {
            realDao.saveEntityDirectly(rival);
            readMasked.set(false);
        }, readMasked);
        IDaoProvider racingProvider = proxyProvider(racingDao, NopAuthEmailCode.class);

        setField(store, "daoProvider", racingProvider);
        setField(store, "ormTemplate", ormTemplate);

        String code = ormTemplate.runInSession(s -> store.send(key));

        assertNotNull(code);
        assertTrue(code.matches("\\d{6}"), "code must remain 6 digits: " + code);

        NopAuthEmailCode row = ormTemplate.runInSession(s ->
                daoProvider.daoFor(NopAuthEmailCode.class).getEntityById(key));
        assertNotNull(row, "row must exist after race resolution");
        assertEquals(code, row.getCode(), "fallback update must overwrite rival code with the new code");
        assertEquals(0, row.getFailCount(), "fallback update must reset failCount (resend semantics)");
    }

    // ===================== 竞态模拟 proxy =====================

    /**
     * 首次 getEntityById 返回 null（模拟竞态窗口内对手行不可见）；saveEntityDirectly 前
     * 先落竞争行（模拟对手抢先 INSERT）；此后全部透传真实 dao。
     */
    @SuppressWarnings("unchecked")
    private static <T extends io.nop.dao.api.IDaoEntity> IEntityDao<T> proxyDao(IEntityDao<T> realDao, Runnable insertRival, AtomicBoolean readMasked) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getEntityById".equals(name) && readMasked.get()) {
                return null;
            }
            if ("saveEntityDirectly".equals(name) && readMasked.get()) {
                insertRival.run();
                return delegate(realDao, method, args);
            }
            return delegate(realDao, method, args);
        };
        return (IEntityDao<T>) Proxy.newProxyInstance(DbSmsCodeStore.class.getClassLoader(),
                new Class[]{IEntityDao.class}, handler);
    }

    @SuppressWarnings("unchecked")
    private IDaoProvider proxyProvider(IEntityDao<?> racingDao, Class<?> entityClass) {
        IDaoProvider real = this.daoProvider;
        InvocationHandler handler = (proxy, method, args) -> {
            if ("daoFor".equals(method.getName()) && entityClass.equals(args[0])) {
                return racingDao;
            }
            return delegate(real, method, args);
        };
        return (IDaoProvider) Proxy.newProxyInstance(DbSmsCodeStore.class.getClassLoader(),
                new Class[]{IDaoProvider.class}, handler);
    }

    private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    // ===================== H2 stack =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:db-code-store-race-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("db-code-store-race", CacheConfig.newConfig(100)));
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
