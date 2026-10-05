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

    @Test
    public void testJavaxCacheComputeIfAbsentSingleWinner() {
        // 用可插拔的原子Map模拟javax.cache语义：putIfAbsent/getAndPut原子
        AtomicFakeJavaxCache<String, String> inner = new AtomicFakeJavaxCache<>();
        JavaxCache<String, String> cache = new JavaxCache<>(inner);

        assertTrue(cache.putIfAbsent("a", "1"));
        assertFalse(cache.putIfAbsent("a", "2"));
        assertEquals("1", cache.getIfPresent("a"));

        AtomicInteger calls = new AtomicInteger();
        assertEquals("1", cache.computeIfAbsent("a", k -> {
            calls.incrementAndGet();
            return "other";
        }));
        assertEquals(0, calls.get());

        assertEquals("b1", cache.computeIfAbsent("b", k -> "b1"));
        assertEquals("b1", cache.computeIfAbsent("b", k -> "b2"));

        assertNull(cache.computeIfAbsent("c", k -> null));
        assertFalse(cache.containsKey("c"));
    }

    /**
     * 最小的原子fake，仅实现JavaxCache用到的方法，putIfAbsent/getAndPut用ConcurrentHashMap原子语义
     */
    static class AtomicFakeJavaxCache<K, V> implements javax.cache.Cache<K, V> {
        final java.util.concurrent.ConcurrentHashMap<K, V> map = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public V get(K key) {
            return map.get(key);
        }

        @Override
        public Map<K, V> getAll(Set<? extends K> keys) {
            Map<K, V> ret = new HashMap<>();
            keys.forEach(k -> {
                V v = map.get(k);
                if (v != null)
                    ret.put(k, v);
            });
            return ret;
        }

        @Override
        public boolean containsKey(K key) {
            return map.containsKey(key);
        }

        @Override
        public void put(K key, V value) {
            map.put(key, value);
        }

        @Override
        public V getAndPut(K key, V value) {
            return map.put(key, value);
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> map) {
            this.map.putAll(map);
        }

        @Override
        public boolean putIfAbsent(K key, V value) {
            return map.putIfAbsent(key, value) == null;
        }

        @Override
        public boolean remove(K key) {
            return map.remove(key) != null;
        }

        @Override
        public boolean remove(K key, V oldValue) {
            return map.remove(key, oldValue);
        }

        @Override
        public V getAndRemove(K key) {
            return map.remove(key);
        }

        @Override
        public boolean replace(K key, V value) {
            return map.replace(key, value) != null;
        }

        @Override
        public boolean replace(K key, V oldValue, V newValue) {
            return map.replace(key, oldValue, newValue);
        }

        @Override
        public V getAndReplace(K key, V value) {
            return map.replace(key, value);
        }

        @Override
        public void removeAll(Set<? extends K> keys) {
            keys.forEach(map::remove);
        }

        @Override
        public void removeAll() {
            map.clear();
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public <C extends javax.cache.configuration.Configuration<K, V>> C getConfiguration(Class<C> clazz) {
            throw new UnsupportedOperationException("not needed for test");
        }

        @Override
        public <T> T invoke(K key, javax.cache.processor.EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
            throw new UnsupportedOperationException("not needed for test");
        }

        @Override
        public <T> Map<K, javax.cache.processor.EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, javax.cache.processor.EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
            throw new UnsupportedOperationException("not needed for test");
        }

        @Override
        public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, javax.cache.integration.CompletionListener completionListener) {
            completionListener.onCompletion();
        }

        @Override
        public String getName() {
            return "fake";
        }

        @Override
        public javax.cache.CacheManager getCacheManager() {
            throw new UnsupportedOperationException("not needed for test");
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public <T> T unwrap(Class<T> clazz) {
            throw new UnsupportedOperationException("not needed for test");
        }

        @Override
        public void registerCacheEntryListener(javax.cache.configuration.CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        }

        @Override
        public void deregisterCacheEntryListener(javax.cache.configuration.CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        }

        @Override
        public java.util.Iterator<javax.cache.Cache.Entry<K, V>> iterator() {
            return map.entrySet().stream().<javax.cache.Cache.Entry<K, V>>map(e -> new javax.cache.Cache.Entry<K, V>() {
                @Override
                public K getKey() {
                    return e.getKey();
                }

                @Override
                public V getValue() {
                    return e.getValue();
                }

                @Override
                public <T> T unwrap(Class<T> clazz) {
                    throw new UnsupportedOperationException("not needed for test");
                }
            }).iterator();
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
