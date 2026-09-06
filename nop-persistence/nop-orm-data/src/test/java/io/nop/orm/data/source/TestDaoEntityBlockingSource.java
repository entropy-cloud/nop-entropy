/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.data.source;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestDaoEntityBlockingSource {

    private DaoEntityBlockingSource<IOrmEntity> source;
    private IEntityDao<IOrmEntity> dao;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        source = new DaoEntityBlockingSource<>();
        source.setEntityName("io.test.EntityQ");

        IDaoProvider daoProvider = mock(IDaoProvider.class);
        dao = (IEntityDao<IOrmEntity>) mock(IEntityDao.class);
        lenient().when(daoProvider.<IOrmEntity>dao("io.test.EntityQ")).thenReturn(dao);
        source.setDaoProvider(daoProvider);
    }

    private void returnItems(List<IOrmEntity> items) {
        when(dao.findAllByQuery(any(QueryBean.class))).thenReturn(items);
    }

    @Test
    public void testTakeReturnsItem() throws InterruptedException {
        IOrmEntity entity = mock(IOrmEntity.class);
        source.setQueryBuilder(ctx -> new QueryBean());
        returnItems(List.of(entity));

        // 修复前 take() -> drainTo(maxWait=-1) -> Guard.positiveLong(-1) 直接抛 IllegalArgumentException
        IOrmEntity taken = source.take();
        assertSame(entity, taken);
    }

    @Test
    public void testDrainToReturnsTransferredCountOnly() throws InterruptedException {
        IOrmEntity entity = mock(IOrmEntity.class);
        source.setQueryBuilder(ctx -> new QueryBean());
        returnItems(List.of(entity));

        List<Object> c = new ArrayList<>();
        c.add(new Object()); // 调用前集合中已有元素，不应计入返回值

        // 走等待路径（数据立即可得），修复前返回 c.size() 把已有元素计入
        int n = source.drainTo(c, 1, 100, 100);
        assertEquals(1, n);
        assertEquals(2, c.size());
    }

    @Test
    public void testDrainToAccumulatesUntilMaxElements() throws InterruptedException {
        IOrmEntity e1 = mock(IOrmEntity.class);
        IOrmEntity e2 = mock(IOrmEntity.class);
        IOrmEntity e3 = mock(IOrmEntity.class);
        source.setQueryBuilder(ctx -> new QueryBean());
        // 第一次查询只有 1 条，100ms 后的第二批再补 2 条
        when(dao.findAllByQuery(any(QueryBean.class))).thenReturn(List.of(e1), List.of(e2, e3));

        List<Object> c = new ArrayList<>();
        long begin = System.currentTimeMillis();
        int n = source.drainTo(c, 3, 2000, 2000);

        // 修复前拿到任意条数就返回（n=1）；按攒批契约应累计到 maxElements
        assertEquals(3, n);
        assertEquals(3, c.size());
        // 未超 maxWait 即拿满返回，不需要等满 minWait
        assertTrue(System.currentTimeMillis() - begin < 2000, "should return as soon as maxElements collected");
    }

    @Test
    public void testDrainToReturnsPartialWhenMinWaitExpired() throws InterruptedException {
        IOrmEntity e1 = mock(IOrmEntity.class);
        source.setQueryBuilder(ctx -> new QueryBean());
        // 首批拿到 1 条，后续轮询不再有新数据（acquire 状态机制保证不会重复获取）
        when(dao.findAllByQuery(any(QueryBean.class))).thenReturn(List.of(e1), Collections.emptyList());

        List<Object> c = new ArrayList<>();
        long begin = System.currentTimeMillis();
        int n = source.drainTo(c, 3, 300, 2000);

        // 攒批窗口（minWait）用尽后应带着部分数据返回，而不是一直等到 maxWait
        assertEquals(1, n);
        long elapsed = System.currentTimeMillis() - begin;
        assertTrue(elapsed < 2000, "should return after minWait window, elapsed=" + elapsed);
        assertTrue(elapsed >= 250, "should wait at least minWait before giving up, elapsed=" + elapsed);
    }

    @Test
    public void testDrainToMaxElementsNotPositiveSkipsQuery() throws InterruptedException {
        source.setQueryBuilder(ctx -> new QueryBean());

        List<Object> c = new ArrayList<>();
        assertEquals(0, source.drainTo(c, 0, 0, 0));
        // 修复前 maxElements=0 仍会以 limit=0 查询数据库（语义未定义）
        verify(dao, never()).findAllByQuery(any());
    }

    @Test
    public void testDrainToThrowsOnInterrupt() throws Exception {
        source.setQueryBuilder(ctx -> new QueryBean());
        // 始终查不到数据，使工作线程停留在等待循环中
        returnItems(Collections.emptyList());

        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            started.countDown();
            try {
                source.drainTo(new ArrayList<>(), 1, 5000, 5000);
            } catch (Throwable e) {
                error.set(e);
            }
        });
        worker.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        Thread.sleep(300);
        worker.interrupt();
        worker.join(5000);

        // 修复前中断被吞掉，方法正常返回
        assertTrue(error.get() instanceof InterruptedException, String.valueOf(error.get()));
    }
}
