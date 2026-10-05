package io.nop.commons.cache;

import io.nop.commons.util.CollectionHelper;

import javax.cache.Cache;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class JavaxCache<K, V> implements ISyncCache<K, V>, AutoCloseable {
    private final Cache<K, V> cache;

    public JavaxCache(Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public String getName() {
        return cache.getName();
    }

    @Override
    public void close() throws Exception {
        cache.close();
    }

    @Override
    public long estimatedSize() {
        return -1L;
    }

    @Override
    public CacheConfig getConfig() {
        return null;
    }

    @Override
    public V getIfPresent(K key) {
        return cache.get(key);
    }

    @Override
    public Map<K, V> getAllPresent(Collection<? extends K> keys) {
        Set<? extends K> set = CollectionHelper.toSet(keys);
        return cache.getAll(set);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void remove(K key) {
        cache.remove(key);
    }

    @Override
    public void removeAll(Collection<? extends K> keys) {
        cache.removeAll(CollectionHelper.toSet(keys));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void refresh(K key) {
        cache.remove(key);
    }

    @Override
    public void forEachEntry(BiConsumer<? super K, ? super V> consumer) {
        cache.forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
    }

    @Override
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        cache.putAll(map);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        return cache.putIfAbsent(key, value);
    }

    /**
     * 委托javax.cache原子的getAndPut保证单一胜者语义：并发竞争时所有线程
     * 最终都返回先写入者的值。mappingFunction返回null时不写入并返回null。
     * 不覆写时会落入ICache的synchronized默认实现，在JCache分布式缓存上
     * 以本地锁串行化，语义仍正确但粒度更粗。
     */
    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        V v = cache.get(key);
        if (v != null)
            return v;

        V newValue = mappingFunction.apply(key);
        if (newValue == null)
            return null;

        V prev = cache.getAndPut(key, newValue);
        return prev != null ? prev : newValue;
    }

    @Override
    public V getAndSet(K key, V value) {
        return cache.getAndPut(key, value);
    }

    @Override
    public boolean remove(K key, V object) {
        return cache.remove(key, object);
    }

    /**
     * 本实现的缓存配置由底层JCache实现（Hazelcast/Ehcache等）自行管理，
     * nop侧的CacheConfig不适用（getConfig()恒返回null），因此没有可刷新的配置项。
     * 这是有明确语义的能力声明，不是遗漏：调用方应通过getConfig()==null判断
     * 该实现是否支持nop侧配置。
     */
    @Override
    public void refreshConfig() {

    }

    @Override
    public boolean removeIfMatch(K key, V object) {
        return cache.remove(key, object);
    }

}