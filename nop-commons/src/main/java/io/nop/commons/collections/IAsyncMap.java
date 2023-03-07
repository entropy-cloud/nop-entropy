/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface IAsyncMap<K, V> {
    CompletionStage<V> getAsync(K key);

    V get(K key);

    CompletionStage<V> computeIfAbsentAsync(K key, Function<? super K, ? extends V> mappingFunction);

    V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

    CompletionStage<Map<K, V>> getAllAsync(Collection<? extends K> keys);

    Map<K, V> getAll(Collection<? extends K> keys);

    CompletionStage<Boolean> containsKeyAsync(K key);

    boolean containsKey(K key);

    CompletionStage<Void> putAsync(K key, V value);

    void put(K key, V value);

    CompletionStage<Void> putAllAsync(Map<? extends K, ? extends V> map);

    void putAll(Map<? extends K, ? extends V> map);

    CompletionStage<Boolean> putIfAbsentAsync(K key, V value);

    boolean putIfAbsent(K key, V value);

    CompletionStage<V> getAndSetAsync(K key, V value);

    V getAndSet(K key, V value);

    CompletionStage<Void> removeAsync(K key);

    void remove(K key);

    CompletionStage<Boolean> removeIfMatchAsync(K key, V object);

    boolean removeIfMatch(K key, V object);

    CompletionStage<Void> removeAllAsync(Collection<? extends K> keys);

    void removeAll(Collection<? extends K> keys);

    CompletionStage<Void> clearAsync();

    void clear();

    CompletionStage<Void> forEachEntryAsync(BiConsumer<? super K, ? super V> consumer);

    void forEachEntry(BiConsumer<? super K, ? super V> consumer);
}
