/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.cache;

import io.nop.commons.util.CollectionHelper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCache {
    /**
     * Map的速度大概是Cache的两倍 n = 100,000, map=357ms, cache=642ms
     */
    @Test
    public void testSpeed() {
        ICache<String, Object> cache = LocalCache.newCache("a", new CacheConfig().weakKeys());
        Map<String, Object> map = CollectionHelper.newConcurrentWeakMap();

        int n = 100000;

        run("map", () -> {
            for (int i = 0; i < n; i++) {
                String key = String.valueOf(i);
                map.put(key, "bbbb");
                map.get(key);
            }
        });

        run("cache", () -> {
            for (int i = 0; i < n; i++) {
                String key = String.valueOf(i);
                cache.put(key, "bbbb");
                cache.get(key);
            }
        });

    }

    @Test
    public void testPutIfAbsentAtomic() throws InterruptedException {
        ICache<String, String> cache = LocalCache.newCache("putIfAbsent", new CacheConfig());
        int rounds = 200;

        for (int round = 0; round < rounds; round++) {
            String key = "key-" + round;
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicInteger trueCount = new AtomicInteger();

            Thread t1 = new Thread(() -> {
                awaitBarrier(barrier);
                if (cache.putIfAbsent(key, "v1"))
                    trueCount.incrementAndGet();
            });
            Thread t2 = new Thread(() -> {
                awaitBarrier(barrier);
                if (cache.putIfAbsent(key, "v2"))
                    trueCount.incrementAndGet();
            });
            t1.start();
            t2.start();
            t1.join();
            t2.join();

            // 并发putIfAbsent同一key时，只能有一个调用返回true
            assertEquals(1, trueCount.get(), "round=" + round);
            // 缓存中必然存在该key，值是两个竞争者之一
            String value = cache.get(key);
            assertTrue("v1".equals(value) || "v2".equals(value));
        }
    }

    @Test
    public void testPutIfAbsentSingleThread() {
        ICache<String, String> cache = LocalCache.newCache("putIfAbsent2", new CacheConfig());
        assertTrue(cache.putIfAbsent("a", "1"));
        assertTrue(!cache.putIfAbsent("a", "2"));
        assertEquals("1", cache.get("a"));
    }

    void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            // 恢复中断标记，不影响putIfAbsent的原子性断言
            Thread.currentThread().interrupt();
        }
    }

    void run(String title, Runnable task) {
        long beginTime = System.currentTimeMillis();
        task.run();
        long endTime = System.currentTimeMillis();
        System.out.println(title + "=" + (endTime - beginTime) + "ms");
    }
}