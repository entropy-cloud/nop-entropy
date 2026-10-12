/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.cache;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证ICache接口默认实现的契约：
 * <ul>
 * <li>putIfAbsent的check-then-act原子性（javadoc明文要求：并发对同一key调用时只有一个返回true）</li>
 * <li>computeIfAbsent的单一胜者语义与null mapping处理</li>
 * </ul>
 * 故意使用不加锁的HashMap实现，且不覆写这两个方法，使默认实现被真实执行。
 */
public class TestICacheDefaults {

    /**
     * 最小实现：不覆写putIfAbsent/computeIfAbsent，让接口默认实现生效。
     * 底层用HashMap，自身无任何同步。
     */
    static class NaiveCache extends ISyncCacheAdapter<String, String> {
        final Map<String, String> map = new HashMap<>();
        final AtomicInteger putCount = new AtomicInteger();

        @Override
        public String getName() {
            return "naive";
        }

        @Override
        public long estimatedSize() {
            return map.size();
        }

        @Override
        public CacheConfig getConfig() {
            return null;
        }

        @Override
        public String getIfPresent(String key) {
            return map.get(key);
        }

        @Override
        public Map<String, String> getAllPresent(Collection<? extends String> keys) {
            Map<String, String> ret = new LinkedHashMap<>();
            for (String k : keys) {
                String v = map.get(k);
                if (v != null)
                    ret.put(k, v);
            }
            return ret;
        }

        @Override
        public void put(String key, String value) {
            putCount.incrementAndGet();
            map.put(key, value);
        }

        @Override
        public void remove(String key) {
            map.remove(key);
        }

        @Override
        public void removeAll(Collection<? extends String> keys) {
            keys.forEach(map::remove);
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public void refresh(String key) {
            map.remove(key);
        }

        @Override
        public void forEachEntry(BiConsumer<? super String, ? super String> consumer) {
            map.forEach(consumer);
        }

        @Override
        public boolean removeIfMatch(String key, String value) {
            return map.remove(key, value);
        }

        @Override
        public void refreshConfig() {
        }
    }

    @Test
    public void testPutIfAbsentSequentialContract() {
        NaiveCache cache = new NaiveCache();
        assertTrue(cache.putIfAbsent("a", "1"));
        assertFalse(cache.putIfAbsent("a", "2"));
        assertEquals("1", cache.getIfPresent("a"));
    }

    /**
     * 并发单一胜者：N个线程对同一key并发putIfAbsent，必须恰好一个返回true。
     * 修复前（无同步的两步默认实现）该测试以高概率失败；修复后由synchronized保证确定通过。
     */
    @Test
    public void testPutIfAbsentConcurrentSingleWinner() throws Exception {
        int threads = 16;
        for (int round = 0; round < 50; round++) {
            NaiveCache cache = new NaiveCache();
            CyclicBarrier barrier = new CyclicBarrier(threads);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger trueCount = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                final String value = "v" + i;
                new Thread(() -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        // ignore
                    }
                    if (cache.putIfAbsent("key", value))
                        trueCount.incrementAndGet();
                    done.countDown();
                }).start();
            }
            done.await();

            assertEquals(1, trueCount.get(), "并发putIfAbsent必须恰好一个返回true，round=" + round);
            assertTrue(cache.getIfPresent("key") != null);
        }
    }

    @Test
    public void testComputeIfAbsentCachesResult() {
        NaiveCache cache = new NaiveCache();
        AtomicInteger mappingCalls = new AtomicInteger();

        String v1 = cache.computeIfAbsent("a", k -> {
            mappingCalls.incrementAndGet();
            return "computed";
        });
        assertEquals("computed", v1);

        // 第二次命中缓存，mapping不再执行
        String v2 = cache.computeIfAbsent("a", k -> {
            mappingCalls.incrementAndGet();
            return "other";
        });
        assertEquals("computed", v2);
        assertEquals(1, mappingCalls.get());
    }

    @Test
    public void testComputeIfAbsentNullMappingNotCached() {
        NaiveCache cache = new NaiveCache();
        AtomicInteger mappingCalls = new AtomicInteger();

        assertNull(cache.computeIfAbsent("a", k -> {
            mappingCalls.incrementAndGet();
            return null;
        }));
        // null不写入，后续调用会重新执行mapping
        assertNull(cache.computeIfAbsent("a", k -> {
            mappingCalls.incrementAndGet();
            return null;
        }));
        assertEquals(2, mappingCalls.get());
        assertEquals(0, cache.map.size());
    }

    /**
     * 并发单一胜者：所有线程必须拿到同一个值（先写入者的值），且缓存中只有一个条目。
     */
    @Test
    public void testComputeIfAbsentConcurrentSingleWinner() throws Exception {
        int threads = 16;
        for (int round = 0; round < 50; round++) {
            NaiveCache cache = new NaiveCache();
            CyclicBarrier barrier = new CyclicBarrier(threads);
            CountDownLatch done = new CountDownLatch(threads);
            Map<String, Integer> results = new HashMap<>();

            for (int i = 0; i < threads; i++) {
                final String value = "v" + i;
                final int idx = i;
                new Thread(() -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        // ignore
                    }
                    String r = cache.computeIfAbsent("key", k -> value);
                    synchronized (results) {
                        results.put(r, idx);
                    }
                    done.countDown();
                }).start();
            }
            done.await();

            assertEquals(1, results.size(), "所有线程必须拿到同一个值，round=" + round);
            assertEquals(1, cache.map.size());
        }
    }

    /**
     * WI14 plan 2448：EhcacheCache 真实行为测试（真实 CacheManager + on-heap cache）。
     * 语义点：putIfAbsent 原子（返回 boolean）；computeIfAbsent 走 ICache 锁化 default
     * （putIfAbsent 兜底，与 2448 Ruling 1 裁定一致）；getAndSet 为 get+put 顺序语义
     * （Ehcache 无原子 getAndPut——继承 default 是显式裁决的语义损失）。
     */
    @Test
    public void testEhcacheCacheBehavior() {
        org.ehcache.CacheManager manager = org.ehcache.config.builders.CacheManagerBuilder
                .newCacheManagerBuilder()
                .withCache("test-icache", org.ehcache.config.builders.CacheConfigurationBuilder
                        .newCacheConfigurationBuilder(String.class, String.class,
                                org.ehcache.config.builders.ResourcePoolsBuilder.heap(100)))
                .build(true);
        try {
            org.ehcache.Cache<String, String> inner = manager.getCache("test-icache", String.class, String.class);
            EhcacheCache<String, String> cache = new EhcacheCache<>("test-icache", inner);

            assertTrue(cache.putIfAbsent("a", "1"));
            assertFalse(cache.putIfAbsent("a", "2"));
            assertEquals("1", cache.getIfPresent("a"));

            // 已有值时 mapping 不执行（default 的 get 短路路径）
            AtomicInteger calls = new AtomicInteger();
            assertEquals("1", cache.computeIfAbsent("a", k -> {
                calls.incrementAndGet();
                return "other";
            }));
            assertEquals(0, calls.get());

            // getAndSet 顺序语义：返回前值
            assertEquals("1", cache.getAndSet("a", "3"));
            assertEquals("3", cache.getIfPresent("a"));

            assertEquals("b1", cache.computeIfAbsent("b", k -> "b1"));
            assertEquals("b1", cache.computeIfAbsent("b", k -> "b2"));

            // null mapping 不落缓存
            assertNull(cache.computeIfAbsent("c", k -> null));
            assertFalse(cache.containsKey("c"));

            // forEachEntry 遍历真实条目
            Map<String, String> seen = new LinkedHashMap<>();
            cache.forEachEntry(seen::put);
            assertEquals("3", seen.get("a"));
            assertEquals("b1", seen.get("b"));

            cache.remove("a");
            assertFalse(cache.containsKey("a"));
        } finally {
            manager.close();
        }
    }

    /**
     * ISyncCache只桥接async变体，sync侧的putIfAbsent/computeIfAbsent保持ICache默认实现，
     * 子类不覆写它们即可让默认实现被真实执行。
     */
    static class ISyncCacheAdapter<K, V> implements ISyncCache<K, V> {
        @Override
        public String getName() {
            return "adapter";
        }

        @Override
        public long estimatedSize() {
            return -1;
        }

        @Override
        public CacheConfig getConfig() {
            return null;
        }

        @Override
        public V getIfPresent(K key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<K, V> getAllPresent(Collection<? extends K> keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void put(K key, V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(K key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeAll(Collection<? extends K> keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void refresh(K key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forEachEntry(BiConsumer<? super K, ? super V> consumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeIfMatch(K key, V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void refreshConfig() {
        }
    }
}
