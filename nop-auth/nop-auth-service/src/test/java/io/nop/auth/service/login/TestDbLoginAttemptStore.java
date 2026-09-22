package io.nop.auth.service.login;

import io.nop.auth.core.login.ILoginAttemptStore;
import io.nop.auth.core.login.UserContextConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DbLoginAttemptStore} 并发原子性与 TTL 语义（plan 2275 Phase 2，手工 H2 栈——
 * Db bean 为 autowire-candidate=false 不走容器注入）：并发递增无丢失更新（SQL 条件 UPDATE
 * 原子性 Proof——`TestLoginFailCountAtomicity` 的 DB 后端对应，线程数 16 规避 H2 锁超时
 * 抖动——审查 m3）、user/ip 键维度隔离、过期读 0 + 惰性删除、reset 删除。
 */
public class TestDbLoginAttemptStore {

    private io.nop.auth.service.login.DbLoginAttemptStore store;
    private io.nop.orm.IOrmTemplate ormTemplate;
    private io.nop.dao.api.IDaoProvider daoProvider;

    @org.junit.jupiter.api.BeforeAll
    static void initCore() {
        io.nop.core.initialize.CoreInitialization.initializeTo(io.nop.core.CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @BeforeEach
    void setUp() {
        buildH2Stack("db-attempt");
        store = new io.nop.auth.service.login.DbLoginAttemptStore();
        setField(store, "daoProvider", daoProvider);
        setField(store, "ormTemplate", ormTemplate);
        setField(store, "config", new UserContextConfig());
    }

    @Test
    public void testConcurrentIncrementNoLostUpdates() throws Exception {
        String key = ILoginAttemptStore.userKey("db-atomic-user");
        store.resetLoginFailCount(key);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        ormTemplate.runInSession(s -> {
                            store.incrementLoginFailCount(key);
                            return null;
                        });
                    } catch (Throwable t) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(60, TimeUnit.SECONDS), "all threads must finish");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, errors.get(), "no thread errors");
        assertEquals(threads, store.getLoginFailCount(key),
                "no lost updates: N concurrent DB increments must yield count N");
    }

    @Test
    public void testUserAndIpKeysAreDistinctDimensions() {
        String user = ILoginAttemptStore.userKey("db-dim-user");
        String ip = ILoginAttemptStore.ipKey("10.8.8.1");
        store.resetLoginFailCount(user);
        store.resetLoginFailCount(ip);
        store.setLoginFailCount(user, 3);
        assertEquals(0, store.getLoginFailCount(ip), "user/ip dimensions must be isolated");
        store.setLoginFailCount(ip, 5);
        assertEquals(5, store.getLoginFailCount(ip));
        store.resetLoginFailCount(ip);
        assertEquals(0, store.getLoginFailCount(ip));
        assertEquals(3, store.getLoginFailCount(user), "reset of ip key must not affect user key");
    }

    /** 过期读 0 + 惰性删除：expire_at 置为过去后 get 返回 0，重新递增从 1 开始（固定窗口新周期）。 */
    @Test
    public void testExpiredRowReadsZeroAndRevives() {
        String key = ILoginAttemptStore.userKey("db-ttl-user");
        store.resetLoginFailCount(key);
        assertEquals(1, store.incrementLoginFailCount(key));
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin()
                .name("testExpireAttemptRow")
                .sql("update NopAuthLoginAttempt o set o.expireAt = ? where o.attemptKey = ?", 1000L, key)
                .end();
        ormTemplate.executeUpdate(upd);
        assertEquals(0, store.getLoginFailCount(key), "expired row must read as 0");
        assertEquals(1, store.incrementLoginFailCount(key),
                "increment on expired row must start a fresh window at 1 (not inherit old count)");
    }

    @Test
    public void testResetRemovesEntry() {
        String key = ILoginAttemptStore.userKey("db-reset-user");
        store.incrementLoginFailCount(key);
        assertTrue(store.getLoginFailCount(key) > 0);
        store.resetLoginFailCount(key);
        assertEquals(0, store.getLoginFailCount(key));
    }

    // ===================== 手工 H2 栈（TestLoginFailCountAtomicity 同构） =====================

    private io.nop.dao.jdbc.datasource.SimpleDataSource dataSource;
    private io.nop.orm.factory.OrmSessionFactoryBean factoryBean;

    private void buildH2Stack(String dbName) {
        dataSource = new io.nop.dao.jdbc.datasource.SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + "-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        io.nop.dao.jdbc.impl.JdbcFactory factory = new io.nop.dao.jdbc.impl.JdbcFactory();
        io.nop.dao.txn.ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        io.nop.dao.jdbc.IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new io.nop.orm.factory.OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new io.nop.api.core.ioc.IBeanProvider() {
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
        });
        factoryBean.setGlobalCache(new io.nop.commons.cache.LocalCacheProvider("db-attempt-test",
                io.nop.commons.cache.CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new io.nop.orm.factory.DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        io.nop.orm.IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new io.nop.orm.impl.OrmTemplateImpl(sessionFactory);

        java.util.Collection<? extends io.nop.orm.model.IEntityModel> tables =
                sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new io.nop.orm.ddl.DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new io.nop.core.lang.sql.SQL(createSql));

        io.nop.orm.dao.OrmDaoProvider p = new io.nop.orm.dao.OrmDaoProvider();
        p.setOrmTemplate(ormTemplate);
        daoProvider = p;
    }

    @AfterEach
    void tearDown() {
        if (factoryBean != null) {
            try {
                factoryBean.destroy();
            } catch (Exception ignored) {
                // teardown 阶段 H2 内存库随进程销毁，释放失败不影响测试结论
            }
        }
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
}
