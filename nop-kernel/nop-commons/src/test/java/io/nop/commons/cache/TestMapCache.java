/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.cache;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMapCache {

    @Test
    public void testAsyncOnNonThreadSafeRunsSynchronously() throws Exception {
        MapCache<String, String> cache = new MapCache<>();
        CountDownLatch latch = new CountDownLatch(1);

        long begin = System.currentTimeMillis();
        CompletionStage<String> stage = cache.computeIfAbsentAsync("a", key -> {
            try {
                latch.await(300, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "v";
        });
        long elapsed = System.currentTimeMillis() - begin;

        // 非线程安全实例上的async方法必须在当前线程同步执行，
        // 不应把HashMap暴露给线程池中的其他线程
        assertTrue(elapsed >= 200, "elapsed=" + elapsed);
        assertTrue(stage.toCompletableFuture().isDone());
        assertEquals("v", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        assertEquals("v", cache.get("a"));
    }

    @Test
    public void testAsyncOnThreadSafeInstance() throws Exception {
        MapCache<String, String> cache = MapCache.create("thread-safe", true);
        CompletionStage<String> stage = cache.computeIfAbsentAsync("a", key -> "v");
        assertEquals("v", stage.toCompletableFuture().get(2, TimeUnit.SECONDS));
        assertEquals("v", cache.get("a"));
    }

    @Test
    public void testGetAllPresentWithNullValue() {
        MapCache<String, String> cache = new MapCache<>();
        cache.put("a", null);
        cache.put("b", "v");
        // put(key, null)的条目应与containsKey的结果保持一致：key存在，value为null
        assertTrue(cache.containsKey("a"));
        Map<String, String> all = cache.getAllPresent(Arrays.asList("a", "b", "c"));
        assertEquals(2, all.size());
        assertTrue(all.containsKey("a"));
        assertTrue(all.containsKey("b"));
    }
}
