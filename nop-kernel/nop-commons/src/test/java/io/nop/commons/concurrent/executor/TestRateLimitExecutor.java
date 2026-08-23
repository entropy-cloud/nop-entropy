/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRateLimitExecutor {
    private DefaultScheduledExecutor executor;
    private RateLimitExecutorImpl limiter;

    @BeforeEach
    public void setUp() {
        executor = DefaultScheduledExecutor.newSingleThreadTimer("test-rate-limit");
        limiter = new RateLimitExecutorImpl(executor);
    }

    @AfterEach
    public void tearDown() {
        if (executor != null)
            executor.destroy();
    }

    @Test
    public void testThrottle() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch firstDone = new CountDownLatch(1);

        // 第一次调用触发调度
        limiter.throttle("k", 100, () -> {
            count.incrementAndGet();
            firstDone.countDown();
        });
        assertTrue(firstDone.await(2, TimeUnit.SECONDS));
        assertEquals(1, count.get());

        // 首个任务已执行完毕后，新的节流窗口应重新生效：
        // pending窗口内的其他throttle调用应被跳过
        CountDownLatch secondDone = new CountDownLatch(1);
        limiter.throttle("k", 150, () -> {
            count.incrementAndGet();
            secondDone.countDown();
        });
        Thread.sleep(30);
        limiter.throttle("k", 150, count::incrementAndGet);
        Thread.sleep(30);
        limiter.throttle("k", 150, count::incrementAndGet);

        assertTrue(secondDone.await(2, TimeUnit.SECONDS));
        Thread.sleep(400);
        // 整个过程只执行2次任务：第二次调度的任务执行，pending窗口内的两次调用被跳过
        assertEquals(2, count.get());
    }

    @Test
    public void testDebounce() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        limiter.debounce("k", 50, () -> {
            count.incrementAndGet();
            done.countDown();
        });
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(1, count.get());

        // debounce任务执行完毕后条目应被清理，不应残留在map中
        Thread.sleep(100);
        assertFalse(getPromiseMap().containsKey("k"));
    }

    @Test
    public void testThrottleCleanup() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        limiter.throttle("k", 50, done::countDown);
        assertTrue(done.await(2, TimeUnit.SECONDS));

        // throttle任务执行完毕后条目应被清理
        Thread.sleep(100);
        assertFalse(getPromiseMap().containsKey("k"));
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<Object, ? extends Future<?>> getPromiseMap() throws Exception {
        Field field = RateLimitExecutorImpl.class.getDeclaredField("promiseMap");
        field.setAccessible(true);
        return (ConcurrentMap<Object, ? extends Future<?>>) (Map<?, ?>) field.get(limiter);
    }
}
