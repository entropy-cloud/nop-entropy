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
import java.util.Map;
import java.util.Objects;
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

    default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V v;
        if ((v = get(key)) == null) {
            V newValue;
            if ((newValue = mappingFunction.apply(key)) != null) {
                put(key, newValue);
                return newValue;
            }
        }

        return v;
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

    default boolean putIfAbsent(K key, V value) {
        if (containsKey(key))
            return false;

        put(key, value);
        return true;
    }

    default V getAndSet(K key, V value) {
        V old = getIfPresent(key);
        put(key, value);
        return old;
    }

    void remove(K key);

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