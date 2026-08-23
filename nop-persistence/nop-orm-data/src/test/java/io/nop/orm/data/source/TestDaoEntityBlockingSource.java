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
