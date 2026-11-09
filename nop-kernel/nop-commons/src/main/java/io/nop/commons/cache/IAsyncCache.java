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
     * <b>契约（1.7-C3-009 / plan 2547 裁定 F，**按 live 实况收窄**）</b>：未命中的 key
     * <b>不支持</b>——本方法<b>不做部分命中/缺席语义</b>。{@code LocalCache} 的异步实现以
     * {@code k -> null} 调用底层 {@code AsyncCache.getAll}，而 Caffeine 拒绝 null 值映射，
     * 因此**只要存在任一未命中 key，整个调用即以 {@code NullMapCompletionException}（包装后为
     * {@code ExecutionException}）失败**，不会返回"部分结果"。
     * <p>
     * **调用方用法**：
     * <ul>
     *   <li>只取已缓存条目：先用 {@link #getIfPresent} 过滤，或使用同步 {@link ICache#getAll(Collection)}；</li>
     *   <li>需要 miss 即加载：使用 {@link #computeIfAbsentAsync}（逐 key）或 {@link #getAsync}。</li>
     * </ul>
     * <p>
     * <b>契约变更说明</b>：原 javadoc 声称 "creating or retrieving those values if necessary"，
     * 与实现相反（既不加载、也不返回缺席 map，而是整体失败）。本声明为**按实况订正**（不是行为变更）。
     * 拒绝了两个备选：①改实现为真加载（改变既有 async 契约）；
     * ②改实现为「返回缺席 map」（需换 {@code getAllPresent} 语义，属行为变更，需另立判定）。
     * <p>
     * Note that duplicate elements in {@code keys}, as determined by {@link Object#equals}, will be ignored.
     *
     * @param keys the keys whose associated values are to be returned
     * @return the future containing the already-cached keys' values; <b>if any requested key is
     *         uncached the whole call fails</b> with {@code NullMapCompletionException}
     *         (surfaced as {@code ExecutionException}) — 未命中不支持部分结果
     * @throws NullPointerException if the specified collection is null or contains a null element
     */
    @Nonnull
    CompletionStage<Map<K, V>> getAllAsync(Collection<? extends K> keys);
}