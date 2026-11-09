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

    /**
     * plan 2547 R4-13（C1-004）pin0104a：同一 key 连续两次 debounce（delay 足够长）→
     * <b>只执行最后一次</b>（debounce 的核心契约）。
     * <p>
     * <b>closure audit R1 B-1 / M-6 勘误</b>：首版修复把本地新建的 {@code CompletableFuture} 存进
     * map，而底层调度实际上可被 cancel（{@code ExecutorHelper.schedule:271}
     * {@code bindCancel(future, f)}）——因此首版使 debounce 退化为「两次都执行」。
     * 本钉是该回归的**判别性载体**：首版实现下必红。
     */
    @Test
    public void pin0104a_debounceExecutesOnlyLastTask() throws Exception {
        AtomicInteger executed = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        limiter.debounce("k-debounce", 250, () -> {
            executed.incrementAndGet();
            done.countDown();
        });
        Thread.sleep(60);   // 确保第一次已提交、尚未触发
        limiter.debounce("k-debounce", 250, () -> {
            executed.incrementAndGet();
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS), "第二次任务必须执行");
        Thread.sleep(400);  // 等过第一次的原定触发时刻
        assertEquals(1, executed.get(),
                "debounce 必须只执行最后一次（缺陷/首版回归态=2），实际=" + executed.get());
    }

    /** pin0104a2：并发 debounce 同一 key 后 map 只保留一个条目（非原子缺陷面）。 */
    @Test
    public void pin0104a2_concurrentDebounceKeepsSingleEntry() throws Exception {
        int n = 8;
        CountDownLatch all = new CountDownLatch(1);
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(() -> {
                try {
                    all.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                limiter.debounce("k1", 200, () -> {
                });
            });
            threads.add(t);
            t.start();
        }
        all.countDown();
        for (Thread t : threads)
            t.join(5000);

        java.lang.reflect.Field f = RateLimitExecutorImpl.class.getDeclaredField("promiseMap");
        f.setAccessible(true);
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) f.get(limiter);
        assertEquals(1, map.size(), "并发去抖后 map 中同 key 只应有一个条目，实际=" + map.size());
    }

    /**
     * plan 2547 R4-13（C1-004）pin0104b：delay=0 时任务结束后 promiseMap 无陈旧条目。
     * 缺陷态：holder[0] 提交后写，delay=0 时定时线程读到 null → CHM.remove(key,null) NPE 被吞，
     * 条目永久残留。
     */
    @Test
    public void pin0104b_noStaleEntryAfterZeroDelayTask() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        limiter.debounce("k0", 0, done::countDown);
        assertTrue(done.await(5, TimeUnit.SECONDS));

        // 再触发一次，验证不会被上一次的陈旧条目错误取消
        CountDownLatch done2 = new CountDownLatch(1);
        limiter.debounce("k0", 0, done2::countDown);
        assertTrue(done2.await(5, TimeUnit.SECONDS), "第二次 debounce 必须能正常执行");
    }
}
