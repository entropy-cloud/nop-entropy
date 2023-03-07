/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.cache;

import io.nop.api.core.util.FutureHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class MapCache<K, V> implements ICache<K, V> {
    private final String name;
    private final Map<K, V> map;

    public MapCache(String name, boolean threadSafe) {
        this.name = name;
        this.map = threadSafe ? new ConcurrentHashMap<>() : new HashMap<>();
    }

    public static <K, V> MapCache<K, V> create(String name, boolean threadSafe) {
        return new MapCache<>(name, threadSafe);
    }

    @Nullable
    @Override
    public CompletionStage<V> getAsync(@Nonnull K key) {
        return FutureHelper.futureCall(() -> get(key));
    }

    @Nonnull
    @Override
    public CompletionStage<V> computeIfAbsentAsync(@Nonnull K key,
                                                   @Nonnull Function<? super K, ? extends V> mappingFunction) {
        return FutureHelper.futureCall(() -> computeIfAbsent(key, mappingFunction));
    }

    @Nonnull
    @Override
    public CompletionStage<Map<K, V>> getAllAsync(Collection<? extends K> keys) {
        return FutureHelper.futureCall(() -> getAll(keys));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long estimatedSize() {
        return map.size();
    }

    @Override
    public V getIfPresent(K key) {
        return map.get(key);
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        return map.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public boolean remove(K key, V object) {
        return map.remove(key, object);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        V old = map.putIfAbsent(key, value);
        return old == null;
    }

    @Override
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    @Override
    public V getAndSet(K key, V value) {
        return map.put(key, value);
    }

    @Override
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        this.map.putAll(map);
    }

    @Override
    public Map<K, V> getAllPresent(Collection<? extends K> keys) {
        Map<K, V> ret = new HashMap<>();
        for (K key : keys) {
            V value = getIfPresent(key);
            if (value != null) {
                ret.put(key, value);
            }
        }
        return ret;
    }

    @Override
    public void put(K key, V value) {
        map.put(key, value);
    }

    @Override
    public void remove(K key) {
        map.remove(key);
    }

    @Override
    public void removeAll(Collection<? extends K> keys) {
        map.keySet().removeAll(keys);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public void refresh(K key) {

    }

    @Override
    public void forEachEntry(BiConsumer<? super K, ? super V> consumer) {
        map.forEach(consumer);
    }

    @Override
    public CompletionStage<Boolean> containsKeyAsync(K key) {
        return FutureHelper.futureCall(() -> containsKey(key));
    }

    @Override
    public CompletionStage<Void> putAsync(K key, V value) {
        return FutureHelper.futureRun(() -> put(key, value));
    }

    @Override
    public CompletionStage<Void> putAllAsync(Map<? extends K, ? extends V> map) {
        return FutureHelper.futureRun(() -> putAll(map));
    }

    @Override
    public CompletionStage<Boolean> putIfAbsentAsync(K key, V value) {
        return FutureHelper.futureCall(() -> putIfAbsent(key, value));
    }

    @Override
    public CompletionStage<V> getAndSetAsync(K key, V value) {
        return FutureHelper.futureCall(() -> getAndSet(key, value));
    }

    @Override
    public CompletionStage<Void> removeAsync(K key) {
        return FutureHelper.futureRun(() -> remove(key));
    }

    @Override
    public CompletionStage<Boolean> removeIfMatchAsync(K key, V object) {
        return FutureHelper.futureCall(() -> remove(key, object));
    }

    @Override
    public boolean removeIfMatch(K key, V object) {
        return map.remove(key, object);
    }

    @Override
    public CompletionStage<Void> removeAllAsync(Collection<? extends K> keys) {
        return FutureHelper.futureRun(() -> removeAll(keys));
    }

    @Override
    public CompletionStage<Void> clearAsync() {
        return FutureHelper.futureRun(() -> clear());
    }

    @Override
    public CompletionStage<Void> forEachEntryAsync(BiConsumer<? super K, ? super V> consumer) {
        return FutureHelper.futureRun(() -> forEachEntry(consumer));
    }

    @Override
    public CacheStats stats() {
        return null;
    }
}