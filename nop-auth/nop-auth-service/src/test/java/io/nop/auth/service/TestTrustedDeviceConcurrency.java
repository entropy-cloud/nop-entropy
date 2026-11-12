package io.nop.auth.service;

import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.dao.entity.NopAuthMfaTrustedDevice;
import io.nop.auth.service.mfa.MfaTrustedDeviceManager;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.commons.concurrent.lock.impl.LocalResourceLockManager;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
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

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.1-MA-04（plan 2569）并发纪律回归钉：
 * <ul>
 *   <li>红①（C2-004）：max-count check-then-insert 无锁——并发不同 deviceHash 新行双双越过
 *       count 检查 → max+1 行。红证形态 = deadline 内循环并发批次（单批次概率性，循环收敛）。</li>
 *   <li>红②（C2-005 子面②）：isExempted 对并发删除行的 updateEntityDirectly 曾抛
 *       ERR_ORM_UPDATE_ENTITY_NOT_FOUND（500 面）——并发批次概率性红；绿证 = 条件 UPDATE
 *       形态同批次零异常 + affected=0 静默 false（确定性）。</li>
 * </ul>
 */
class TestTrustedDeviceConcurrency {

    private static final String TENANT_ID = "0";
    private static final String USER_ID = "td-conc-user";
    private static final int MAX_COUNT = 5;

    private SimpleDataSource dataSource;
    private OrmSessionFactoryBean factoryBean;
    private OrmTemplateImpl ormTemplate;
    private IDaoProvider daoProvider;
    private MfaTrustedDeviceManager manager;

    @BeforeAll
    static void initCore() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @BeforeEach
    void setUp() {
        buildH2Stack();
        wireManager();
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

    // ===================== 红① 并发不同 hash register 不越 max-count =====================

    @Test
    void concurrentRegisterDistinctHashesMustNotExceedMaxCount() throws Exception {
        // max-count 收窄为 1（窗口内确定性收紧：任一越界行即溢出）
        io.nop.api.core.config.AppConfig.getConfigProvider()
                .assignConfigValue("nop.auth.mfa.trusted-device.max-count", 1);
        try {
            doConcurrentRegisterRace();
        } finally {
            io.nop.api.core.config.AppConfig.getConfigProvider()
                    .assignConfigValue("nop.auth.mfa.trusted-device.max-count", MAX_COUNT);
        }
    }

    private void doConcurrentRegisterRace() throws Exception {
        // 预置 MAX_COUNT 个未过期行（不同 hash）
        for (int i = 0; i < MAX_COUNT; i++) {
            insertRow(USER_ID, "hash-full-" + i);
        }

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        SpyLockManager spy = new SpyLockManager();
        setField(manager, "lockManager", spy);
        try {
            long deadline = System.currentTimeMillis() + 10_000;
            int batch = 0;
            boolean overflowSeen = false;
            while (System.currentTimeMillis() < deadline && !overflowSeen) {
                batch++;
                CountDownLatch start = new CountDownLatch(1);
                List<String> results = new CopyOnWriteArrayList<>();
                List<Thread> ts = new java.util.ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    String hash = "hash-race-" + batch + "-" + i;
                    Thread t = new Thread(() -> raceRegister(start, results, hash));
                    ts.add(t);
                    t.start();
                }
                start.countDown();
                for (Thread t : ts) {
                    t.join(8_000);
                }

                long unexpired = manager.countUnexpired(USER_ID);
                if (unexpired > MAX_COUNT) {
                    overflowSeen = true;
                }
                // 清理本轮 race 行，恢复满员基线
                ormTemplate.runInSession(s -> {
                    IEntityDao<NopAuthMfaTrustedDevice> dao = daoProvider.daoFor(NopAuthMfaTrustedDevice.class);
                    NopAuthMfaTrustedDevice example = dao.newEntity();
                    example.setUserId(USER_ID);
                    for (NopAuthMfaTrustedDevice row : dao.findAllByExample(example)) {
                        if (row.getDeviceHash() != null && row.getDeviceHash().startsWith("hash-race-")) {
                            dao.deleteEntity(row);
                        }
                    }
                    return null;
                });
            }
            assertFalse(overflowSeen,
                    "concurrent distinct-hash registers must not exceed max-count (C2-004, batches=" + batch + ")");
            assertEquals(1, spy.maxConcurrent.get(),
                    "per-user register sections must be serialized by the lock (C2-004 structural pin)");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 结构钉（plan 2539/2540 概率腿未命中先例承载形态）：并发同资源 acquisitions 在锁存在时
     * 串行化（maxConcurrent==1，确定性），锁被摘除时并发重叠（maxConcurrent>=2，确定性承载红）。
     */
    static class SpyLockManager extends LocalResourceLockManager {
        final java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger maxConcurrent = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public <T> T callWithLock(String resourceId, String holderId, String lockReason,
                                  java.util.function.Function<io.nop.commons.concurrent.lock.IResourceLock, T> task) {
            // 仅统计持锁段内的重叠（锁获取前计数会把排队等待也计入，无信号意义）
            return super.callWithLock(resourceId, holderId, lockReason, lock -> {
                int cur = inFlight.incrementAndGet();
                maxConcurrent.accumulateAndGet(cur, Math::max);
                try {
                    return task.apply(lock);
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        }
    }

    private void raceRegister(CountDownLatch start, List<String> results, String hash) {
        try {
            start.await();
            Map<String, Object> headers = new HashMap<>();
            headers.put(MfaTrustedDeviceManager.HEADER_DEVICE_ID, hash);
            results.add(manager.register(USER_ID, headers));
        } catch (Exception e) {
            results.add("error:" + e.getClass().getSimpleName());
        }
    }

    // ===================== 红② isExempted vs 并发删除：零异常 =====================

    @Test
    void isExemptedConcurrentWithRemoveMustNotThrow() throws Exception {
        insertRow(USER_ID, "hash-del-race");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            long deadline = System.currentTimeMillis() + 4_000;
            AtomicInteger exemptions = new AtomicInteger();
            AtomicInteger misses = new AtomicInteger();
            List<Throwable> errors = new CopyOnWriteArrayList<>();
            while (System.currentTimeMillis() < deadline) {
                CountDownLatch start = new CountDownLatch(1);
                Thread exemptor = new Thread(() -> {
                    try {
                        start.await();
                        Map<String, Object> headers = new HashMap<>();
                        headers.put(MfaTrustedDeviceManager.HEADER_DEVICE_ID, "hash-del-race");
                        String hash = MfaTrustedDeviceManager.fingerprint(headers);
                        if (manager.isExempted(USER_ID, hash))
                            exemptions.incrementAndGet();
                        else
                            misses.incrementAndGet();
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                });
                Thread remover = new Thread(() -> {
                    try {
                        start.await();
                        // 删除唯一行（按 sid 查找）
                        ormTemplate.runInSession(s -> {
                            IEntityDao<NopAuthMfaTrustedDevice> dao = daoProvider.daoFor(NopAuthMfaTrustedDevice.class);
                            NopAuthMfaTrustedDevice example = dao.newEntity();
                            example.setUserId(USER_ID);
                            for (NopAuthMfaTrustedDevice row : dao.findAllByExample(example)) {
                                dao.deleteEntity(row);
                            }
                            return null;
                        });
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                });
                exemptor.start();
                remover.start();
                start.countDown();
                exemptor.join(3_000);
                remover.join(3_000);
                // 恢复行，进入下一轮
                insertRow(USER_ID, "hash-del-race");
            }
            assertTrue(errors.isEmpty(), "isExempted must not throw on concurrently deleted row (C2-005): " + errors);
            assertTrue(exemptions.get() + misses.get() > 0, "race batches must have executed");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void exemptedExpiredRowReturnsFalseWithoutUpdate() {
        // 过期行：条件 UPDATE affected=0 → 静默 false（既有契约保持）
        insertRow(USER_ID, "hash-expired", true);
        Map<String, Object> headers = new HashMap<>();
        headers.put(MfaTrustedDeviceManager.HEADER_DEVICE_ID, "hash-expired");
        String hash = MfaTrustedDeviceManager.fingerprint(headers);
        assertFalse(manager.isExempted(USER_ID, hash));
    }

    // ===================== Helpers =====================

    private void insertRow(String userId, String deviceHash) {
        insertRow(userId, deviceHash, false);
    }

    private void insertRow(String userId, String deviceHash, boolean expired) {
        ormTemplate.runInSession(s -> {
            IEntityDao<NopAuthMfaTrustedDevice> dao = daoProvider.daoFor(NopAuthMfaTrustedDevice.class);
            NopAuthMfaTrustedDevice row = dao.newEntity();
            row.setSid("td" + Long.toHexString(System.nanoTime()) + Math.abs(deviceHash.hashCode() % 100));
            row.setUserId(userId);
            row.setDeviceHash(deviceHash);
            row.setTenantId(TENANT_ID);
            long ttl = 30L * 24 * 3600_000L;
            row.setExpireAt(new Timestamp(CoreMetrics.currentTimeMillis() + (expired ? -ttl : ttl)));
            row.setLastUsedAt(new Timestamp(CoreMetrics.currentTimeMillis()));
            dao.saveEntity(row);
            return null;
        });
    }

    private void wireManager() {
        manager = new MfaTrustedDeviceManager();
        setField(manager, "daoProvider", daoProvider);
        setField(manager, "auditService", null);
        setField(manager, "lockManager", new LocalResourceLockManager());
        setField(manager, "ormTemplate", ormTemplate);
    }

    private void setField(Object target, String name, Object value) {
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

    // ===================== H2 stack（与 TestDaoSessionStoreAndUserContextCache 同构） =====================

    private void buildH2Stack() {
        dataSource = new SimpleDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:td-conc-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        IJdbcTemplate jdbcTemplate = factory.newJdbcTemplate(txn);

        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MinimalBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("td-conc", CacheConfig.newConfig(100)));
        factoryBean.setSequenceGenerator(new io.nop.dao.seq.UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.init();

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        ormTemplate = new OrmTemplateImpl(sessionFactory);

        Collection<? extends IEntityModel> tables = sessionFactory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null))
                .createTables(tables, false);
        jdbcTemplate.executeMultiSql(new io.nop.core.lang.sql.SQL(createSql));

        OrmDaoProvider ormDaoProvider = new OrmDaoProvider();
        ormDaoProvider.setOrmTemplate(ormTemplate);
        daoProvider = ormDaoProvider;
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
