/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.data.source;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * plan 2517 R2-14 红⑬（C6-001/C3-006，C10-010）回归钉：drainTo 等待窗口出事务。
 * <p>缺陷态：方法级 REQUIRES_NEW 横跨无限等待窗口（K>=8 消费者空队列稳态持连持锁 → DB 饥饿）→
 * 每轮轮询不在任何逐轮短事务内（runInTransaction 调用数 0）→ 红；
 * 修复后：REQUIRES_NEW 摘除，循环体内逐轮 runInTransaction 短事务包络（N4：loadItems 含 tryClaim
 * 与轮询读同界）→ 每轮一次 runInTransaction 调用 → 绿。接线验证（rule 23）：短事务包络真实包裹
 * loadItems（事务回调内完成轮询读）。「空闲持连」守卫指标断言载体 = 事务边界不再覆盖整个等待窗口。</p>
 */
public class TestDaoEntityBlockingSourceDrainToCra2517 {

    private DaoEntityBlockingSource<IOrmEntity> source;
    private IEntityDao<IOrmEntity> dao;
    private IDaoProvider daoProvider;
    private ITransactionTemplate txnTemplate;
    private final AtomicInteger txnCount = new AtomicInteger();
    private final AtomicInteger loadsInsideTxn = new AtomicInteger();
    private final AtomicInteger totalLoads = new AtomicInteger();
    private final List<IOrmEntity> round1 = new ArrayList<>();
    private final List<IOrmEntity> round2 = new ArrayList<>();

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setUp() throws Exception {
        source = new DaoEntityBlockingSource<>();
        source.setEntityName("io.test.EntityQDrain");
        source.setPollInterval(1L);
        source.setQueryBuilder(ctx -> new QueryBean());

        dao = (IEntityDao<IOrmEntity>) mock(IEntityDao.class);
        daoProvider = mock(IDaoProvider.class);
        when(daoProvider.<IOrmEntity>dao("io.test.EntityQDrain")).thenReturn(dao);
        source.setDaoProvider(daoProvider);

        // 无抢占字段配置 → loadItems 不走 tryClaim，专注事务边界断言
        txnCount.set(0);
        loadsInsideTxn.set(0);
        totalLoads.set(0);
        IOrmEntity item = mock(IOrmEntity.class);
        round1.clear();
        round2.clear();
        round2.add(item);
        AtomicInteger round = new AtomicInteger();
        when(dao.findAllByQuery(any(QueryBean.class))).thenAnswer(inv -> {
            totalLoads.incrementAndGet();
            if (txnCount.get() > 0)
                loadsInsideTxn.incrementAndGet();
            return round.incrementAndGet() == 1 ? round1 : round2;
        });
        org.mockito.Mockito.doAnswer(inv -> null).when(dao).flushSession();

        txnTemplate = mock(ITransactionTemplate.class);
        when(txnTemplate.runInTransaction(any(), any(), any())).thenAnswer(inv -> {
            txnCount.incrementAndGet();
            java.util.function.Function<ITransaction, Object> task = inv.getArgument(2);
            return task.apply(mock(ITransaction.class));
        });

        // 反射注入（缺陷态无 setTransactionTemplate 方法即红——变异论证）
        Method setter = DaoEntityBlockingSource.class.getMethod("setTransactionTemplate", ITransactionTemplate.class);
        setter.invoke(source, txnTemplate);
    }

    /**
     * 红⑬核心：take() 空队列→有数据两轮轮询，每轮必须各自处于独立短事务内。
     * 缺陷态（方法级 REQUIRES_NEW）：轮询体不在逐轮事务内（0 次 runInTransaction）→ 红。
     */
    @Test
    public void testCra2517_C6_001_eachPollRoundRunsInShortTransaction() throws InterruptedException {
        List<Object> items = new ArrayList<>();
        int n = source.drainTo(items, 1, -1L, -1L);

        assertEquals(1, n);
        assertSame(round2.get(0), items.get(0));

        assertTrue(txnCount.get() >= 2,
                "缺陷态：等待窗口被方法级 REQUIRES_NEW 整体覆盖、轮询体无逐轮短事务（runInTransaction="
                        + txnCount.get() + "）→ 红；修复后每轮独立短事务 → 绿");
        assertEquals(totalLoads.get(), loadsInsideTxn.get(),
                "N4 实现约束：轮询读（loadItems）必须全部位于短事务回调内（短事务包络真实包裹 tryClaim+loadItems）");
    }

    /**
     * maxWait==0 即时路径同口径：单轮短事务包络。
     */
    @Test
    public void testCra2517_C6_001_immediatePathAlsoEnvelopedInTransaction() throws InterruptedException {
        when(dao.findAllByQuery(any(QueryBean.class))).thenReturn(round2);

        List<Object> items = new ArrayList<>();
        int n = source.drainTo(items, 1, 0, 0);

        assertEquals(1, n);
        assertTrue(txnCount.get() >= 1, "即时路径同样必须经逐轮短事务包络");
    }

    /**
     * 事务模板缺位（@Transactional 拦截器未装配部署形态）显式降级不阻塞：无事务执行（既有行为保持）。
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testCra2517_C6_001_missingTransactionTemplateDegradesExplicitly() throws Exception {
        DaoEntityBlockingSource<IOrmEntity> bare = new DaoEntityBlockingSource<>();
        bare.setEntityName("io.test.EntityQDrain");
        bare.setQueryBuilder(ctx -> new QueryBean());
        bare.setDaoProvider(daoProvider);
        bare.setOrmTemplate(new io.nop.orm.impl.OrmTemplateImpl(
                mock(io.nop.orm.IOrmSessionFactory.class)));
        // 不注入 transactionTemplate：显式降级路径（无事务执行，既有未装配行为保持）
        org.mockito.Mockito.lenient().when(dao.findAllByQuery(any(QueryBean.class))).thenReturn(round2);

        List<Object> items = new ArrayList<>();
        int n = bare.drainTo(items, 1, 0, 0);
        assertEquals(1, n);
        assertEquals(0, txnCount.get());
        assertNotNull(ContextProvider.getOrCreateContext());
    }
}
