package io.nop.commons.cache;

import io.nop.commons.util.CollectionHelper;

import javax.cache.Cache;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class JavaxCache<K, V> implements ISyncCache<K, V> {
    private final Cache<K, V> cache;

    public JavaxCache(Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public String getName() {
        return cache.getName();
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

    @Override
    public V getAndSet(K key, V value) {
        return cache.getAndPut(key, value);
    }

    @Override
    public boolean remove(K key, V object) {
        return cache.remove(key, object);
    }

    @Override
    public void refreshConfig() {

    }


    @Override
    public boolean removeIfMatch(K key, V object) {
        return cache.remove(key, object);
    }

}