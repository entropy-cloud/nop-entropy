/*
 * Copyright 2018 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.cache;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A semi-persistent mapping from keys to values. Cache entries are manually added using {@link #get(Object, Function)}
 * or {@link #put(Object, CompletableFuture)}, and are stored in the cache until either evicted or manually invalidated.
 * <p>
 * Implementations of this interface are expected to be thread-safe, and can be safely accessed by multiple concurrent
 * threads.
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of mapped values
 * @author ben.manes@gmail.com (Ben Manes)
 */
public interface IAsyncCache<K, V> extends ICacheManagement<K> {

    /**
     * Returns the future associated with {@code key} in this cache, or {@code null} if there is no cached future for
     * {@code key}.
     *
     * @param key key whose associated value is to be returned
     * @return the current (existing or computed) future value to which the specified key is mapped, or {@code null} if
     * this map contains no mapping for the key
     * @throws NullPointerException if the specified key is null
     */
    @Nullable
    CompletionStage<V> getAsync(@Nonnull K key);

    /**
     * Returns the future associated with {@code key} in this cache, obtaining that value from {@code mappingFunction}
     * if necessary. This method provides a simple substitute for the conventional "if cached, return; otherwise create,
     * cache and return" pattern.
     * <p>
     * If the specified key is not already associated with a value, attempts to compute its value asynchronously and
     * enters it into this cache unless {@code null}. The entire method invocation is performed atomically, so the
     * function is applied at most once per key. If the asynchronous computation fails, the entry will be automatically
     * removed from this cache.
     * <p>
     * <b>Warning:</b> as with {@link CacheLoader#load}, {@code mappingFunction} <b>must not</b> attempt to update any
     * other mappings of this cache.
     *
     * @param key             key with which the specified value is to be associated
     * @param mappingFunction the function to asynchronously compute a value
     * @return the current (existing or computed) future value associated with the specified key
     * @throws NullPointerException if the specified key or mappingFunction is null
     */
    @Nonnull
    CompletionStage<V> computeIfAbsentAsync(@Nonnull K key, @Nonnull Function<? super K, ? extends V> mappingFunction);

    /**
     * Returns the future of a map of the values associated with {@code keys}.
     * <p>
     * <b>契约（1.7-C3-009 / plan 2547 裁定 F，收窄声明）</b>：未命中的 key <b>不会触发加载</b>——
     * 返回的 map 只包含已缓存的条目；未命中的 key 在结果 map 中<b>缺席</b>（{@code map.get(k)==null}）。
     * 需要「miss 即加载」语义时请使用 {@link #computeIfAbsentAsync} 或同步
     * {@link ICache#getAll(Collection)}。
     * <p>
     * <b>契约变更说明</b>：原 javadoc 声称 "creating or retrieving those values if necessary"，与
     * {@code LocalCache} 的异步实现（以 {@code k -> null} 调用底层）不符——本声明为<b>契约收窄</b>
     * （承认实现现状），不是行为变更。拒绝方案：改实现为真加载（会改变既有 async 缓存语义，
     * 且与 Caffeine {@code AsyncCache} 的「不存在即缺席」模型冲突）。
     * <p>
     * Note that duplicate elements in {@code keys}, as determined by {@link Object#equals}, will be ignored.
     *
     * @param keys the keys whose associated values are to be returned
     * @return the future containing an unmodifiable mapping of the <b>already-cached</b> keys to values;
     *         uncached keys are absent from the map
     * @throws NullPointerException if the specified collection is null or contains a null element
     */
    @Nonnull
    CompletionStage<Map<K, V>> getAllAsync(Collection<? extends K> keys);
}