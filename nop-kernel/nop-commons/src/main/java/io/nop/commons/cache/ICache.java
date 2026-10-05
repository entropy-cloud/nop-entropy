/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.cache;

import io.nop.api.core.config.IConfigRefreshable;
import io.nop.commons.collections.IAsyncMap;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface ICache<K, V> extends IAsyncCache<K, V>, IAsyncMap<K, V>, IConfigRefreshable {
    String getName();

    long estimatedSize();

    CacheConfig getConfig();

    default V get(K key) {
        return getIfPresent(key);
    }

    V getIfPresent(K key);

    /**
     * 若缓存中不存在该key，则通过mappingFunction计算值并写入。
     * <p>
     * 写入采用putIfAbsent的原子语义：并发竞争时只有一个线程计算出的值会被写入，
     * 其余线程返回先写入者的值。并发下mappingFunction可能被多个线程执行，
     * 但仅有一个结果生效。mappingFunction返回null时不写入，返回null。
     * <p>
     * 注意：本默认实现通过与putIfAbsent相同的锁保证插入原子性，
     * 性能敏感的实现应自行覆写（如LocalCache委托Caffeine）。
     */
    default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V v = get(key);
        if (v != null)
            return v;

        V newValue = mappingFunction.apply(key);
        if (newValue == null)
            return null;

        if (putIfAbsent(key, newValue))
            return newValue;
        // 并发竞争失败：返回先写入者的值
        return get(key);
    }

    default Map<K, V> getAll(Collection<? extends K> keys) {
        return getAllPresent(keys);
    }

    Map<K, V> getAllPresent(Collection<? extends K> keys);

    default boolean containsKey(K key) {
        return getIfPresent(key) != null;
    }

    void put(K key, V value);

    default void putAll(Map<? extends K, ? extends V> map) {
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 仅当key不存在时才插入。实现方应保证check-then-act的原子性：
     * 并发对同一key调用时，只有一个调用返回true。
     * <p>
     * 本默认实现通过synchronized保证上述原子性契约。性能敏感的实现应自行覆写
     * （如LocalCache委托Caffeine的asMap().putIfAbsent）。
     */
    default boolean putIfAbsent(K key, V value) {
        synchronized (this) {
            if (containsKey(key))
                return false;

            put(key, value);
            return true;
        }
    }

    default V getAndSet(K key, V value) {
        V old = getIfPresent(key);
        put(key, value);
        return old;
    }

    void remove(K key);

    default Set<K> getAllKeys() {
        Set<K> keys = new LinkedHashSet<>();
        forEachEntry((k, v) -> keys.add(k));
        return keys;
    }

    default boolean remove(K key, V object) {
        V value = getIfPresent(key);
        if (value == object) {
            remove(key);
            return true;
        }
        return false;
    }

    void removeAll(Collection<? extends K> keys);

    void clear();

    /**
     * 对于LoadingCache, 主动刷新缓存
     *
     * @param key
     */
    void refresh(K key);

    void forEachEntry(BiConsumer<? super K, ? super V> consumer);

    @Override
    default CacheStats stats() {
        return null;
    }
}