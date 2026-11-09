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
     * <b>行为依实现/配置分叉（1.7-C3-009 / plan 2547 裁定 F，按 live 实况逐个登记）</b>：
     * <table>
     *   <tr><th>实现 + 配置</th><th>未命中 key 的行为</th></tr>
     *   <tr><td>{@code LocalCache} + {@code async=true}</td>
     *       <td><b>整体失败</b>：以 {@code k -> null} 调用 Caffeine 的 {@code AsyncCache.getAll}，
     *           Caffeine 拒绝 null 值映射，抛 {@code NullMapCompletionException}
     *           （以 {@code CompletionStage} 的 {@code ExecutionException} 呈现）。
     *           <b>不加载、也不返回部分结果</b>。</td></tr>
     *   <tr><td>{@code LocalCache} + {@code async=false} + <b>有</b> loader</td>
     *       <td><b>加载缺失 key</b> 并返回完整 map（{@code Caffeine.getAll} 语义）。</td></tr>
     *   <tr><td>{@code LocalCache} + {@code async=false} + <b>无</b> loader；
     *           {@code MapCache}；{@code ISyncCache} 的默认实现
     *           （如 {@code EhcacheCache}/{@code HazelcastCache}）</td>
     *       <td><b>仅返回已缓存条目</b>（缺席 map，未命中 key 不在结果中）。</td></tr>
     * </table>
     * <p>
     * <b>调用方指引</b>：需要「miss 即加载」→ 用 {@link #computeIfAbsentAsync} 或
     * {@link #getAsync}；只想要已缓存条目且不希望触发任何加载 → 优先用
     * {@link ICache#getAllPresent(Collection)}（语义最明确，与实现无关）。
     * <p>
     * <b>契约变更说明</b>：原 javadoc 声称 "creating or retrieving those values if necessary"，
     * 与 {@code LocalCache} 的 async 分支相反（既不加载、也不返回缺席 map，而是整体失败）。
     * 本声明**按实况逐实现登记**，不再给出与实现无关的普遍承诺。
     * 拒绝方案：①统一改为真加载（改变既有 async 契约）；②统一改为缺席 map（需换
     * {@code getAllPresent} 语义，属行为变更，需另立判定）。
     *
     * @param keys the keys whose associated values are to be returned
     * @return a future of the map produced by the concrete implementation per the table above
     *         (may fail with {@code NullMapCompletionException} on {@code LocalCache}+async for
     *         any uncached key; may contain fewer entries than {@code keys})
     * @throws NullPointerException if the specified collection is null or contains a null element
     */
    @Nonnull
    CompletionStage<Map<K, V>> getAllAsync(Collection<? extends K> keys);
}