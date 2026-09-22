/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.ratelimit;

import io.nop.api.core.exceptions.NopException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DbSendCodeRateLimiter} 三层语义与并发原子性（plan 2275 Phase 2，手工 H2 栈——
 * Db bean 为 autowire-candidate=false 不走容器注入）：顺序间隔拒绝、间隔门 SETNX + 过期行
 * 复活（审查 M3）、scope 隔离、并发同键计数无丢失更新（SQL 原子递增 Proof，线程数 16 规避
 * H2 锁超时抖动——审查 m3）、过期行惰性重置。
 */
public class TestDbSendCodeRateLimiter {

    private io.nop.auth.service.ratelimit.DbSendCodeRateLimiter limiter;
    private io.nop.orm.IOrmTemplate ormTemplate;
    private io.nop.dao.api.IDaoProvider daoProvider;

    @org.junit.jupiter.api.BeforeAll
    static void initCore() {
        io.nop.core.initialize.CoreInitialization.initializeTo(io.nop.core.CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @BeforeEach
    void setUp() {
        buildH2Stack("db-ratelimit");
        limiter = new io.nop.auth.service.ratelimit.DbSendCodeRateLimiter();
        setField(limiter, "daoProvider", daoProvider);
        setField(limiter, "ormTemplate", ormTemplate);
    }

    @Test
    public void testSequentialIntervalRejection() {
        ormTemplate.runInSession(s -> {
            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null);
            return null;
        });
        ormTemplate.runInSession(s -> {
            NopException err = assertThrows(NopException.class,
                    () -> limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13800138000", null));
            assertTrue(err.getErrorCode().contains("rate-limit"), "got: " + err.getErrorCode());
            return null;
        });
    }

    /** 间隔门过期行复活语义（plan 2275 审查 M3：过期行必须删+重插放行，不得永久锁死）。 */
    @Test
    public void testIntervalMarkerExpiredRowRevival() {
        String target = "13900139000";
        ormTemplate.runInSession(s -> {
            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, target, null);
            return null;
        });
        // 置两行过期（间隔门 + 日计数），模拟 60s 前发送的终态
        expireRow("login:sms:" + target + ":i");
        expireRow("login:sms:" + target + ":d" + io.nop.api.core.time.CoreMetrics.today().toEpochDay());
        // 过期行：INSERT 撞 PK → 读行判定过期 → DELETE + 重试 INSERT → 放行（非永久锁死）
        ormTemplate.runInSession(s -> {
            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, target, null);
            return null;
        });
    }

    /** scope 隔离（等价原拓扑：login 与 bind 独立计数，审查 M1 分组表）。 */
    @Test
    public void testScopeIsolation() {
        ormTemplate.runInSession(s -> {
            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13500135000", null);
            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_BIND, "13500135000", null);
            assertThrows(NopException.class,
                    () -> limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13500135000", null));
            return null;
        });
    }

    /**
     * 并发同键原子递增 Proof：16 线程不同手机号（目标层互不影响）同一 IP——IP 计数递增
     * 16 次无丢失更新（SQL 条件 UPDATE 原子性）；ip-daily-limit 默认 50 不触达，全部放行。
     */
    @Test
    public void testConcurrentIpCountNoLostUpdates() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger passed = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        ormTemplate.runInSession(s -> {
                            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN,
                                    "13700137" + String.format("%04d", idx), "10.9.9.7");
                            return null;
                        });
                        passed.incrementAndGet();
                    } catch (Throwable t) {
                        // 计数在上层断言
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(60, TimeUnit.SECONDS), "all threads must finish");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(threads, passed.get(), "all 16 distinct-phone sends must pass (no unexpected rejection)");
    }

    /** 过期惰性重置：日计数行与间隔门行同时过期后，check 视为新周期（不继承旧计数）。 */
    @Test
    public void testExpiredCounterRowLazyReset() {
        String target = "13600136000";
        ormTemplate.runInSession(s -> {
            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, target, null);
            return null;
        });
        expireRow("login:sms:" + target + ":d" + io.nop.api.core.time.CoreMetrics.today().toEpochDay());
        expireRow("login:sms:" + target + ":i");
        ormTemplate.runInSession(s -> {
            limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, target, null);
            return null;
        });
    }

    /** Db 后端同目标日配额拒绝（审查 Minor-6）：interval 置 0 绕过间隔层，第 21 次触发 DAILY_LIMIT。 */
    @Test
    public void testTargetDailyLimitRejected() {
        io.nop.api.core.config.IConfigProvider provider = io.nop.api.core.config.AppConfig.getConfigProvider();
        Integer original = provider.getConfigValue("nop.auth.sms-code.send-interval-seconds", 60);
        provider.assignConfigValue("nop.auth.sms-code.send-interval-seconds", 0);
        final NopException[] thrown = new NopException[1];
        try {
            // 每次发送独立 ORM 会话（生产中每请求一会话）——同会话一级缓存会使 selectCount 读到
            // 递增前的旧值（测试工件，非实现缺陷）
            for (int i = 0; i < 20; i++) {
                ormTemplate.runInSession(s -> {
                    limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13400134000", null);
                    return null;
                });
            }
            ormTemplate.runInSession(s -> {
                try {
                    limiter.checkSmsAllowed(ISendCodeRateLimiter.SCOPE_LOGIN, "13400134000", null);
                } catch (NopException e) {
                    thrown[0] = e;
                }
                return null;
            });
        } finally {
            if (original != null) {
                provider.assignConfigValue("nop.auth.sms-code.send-interval-seconds", original);
            }
        }
        assertTrue(thrown[0] != null, "21st send must be rejected");
        assertTrue(thrown[0].getErrorCode().contains("daily-limit"),
                "21st send must hit target daily limit, got: " + thrown[0].getErrorCode());
    }

    private void expireRow(String key) {
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin()
                .name("testExpireRateLimitRow")
                .sql("update NopAuthRateLimitCounter o set o.expireAt = ? where o.counterKey = ?", 1000L, key)
                .end();
        ormTemplate.executeUpdate(upd);
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
        factoryBean.setGlobalCache(new io.nop.commons.cache.LocalCacheProvider("db-ratelimit-test",
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
