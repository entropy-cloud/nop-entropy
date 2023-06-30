/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CaffeineCacheLoader<K, V> implements CacheLoader<K, V> {
    private final ICacheLoader<K, V> loader;

    public CaffeineCacheLoader(ICacheLoader<K, V> loader) {
        this.loader = loader;
    }

    @Nullable
    @Override
    public V load(@NonNull K k) throws Exception {
        return loader.load(k);
    }

    @Override
    public @NonNull Map<K, V> loadAll(@NonNull Iterable<? extends K> keys) throws Exception {
        return loader.loadAll(keys);
    }

    @Override
    public @NonNull CompletableFuture<V> asyncLoad(@NonNull K key, @NonNull Executor executor) {
        return loader.asyncLoad(key, executor);
    }

    @Override
    public @NonNull CompletableFuture<Map<K, V>> asyncLoadAll(@NonNull Iterable<? extends K> keys,
                                                              @NonNull Executor executor) {
        return loader.asyncLoadAll(keys, executor);
    }

    @Nullable
    @Override
    public V reload(@NonNull K key, @NonNull V oldValue) throws Exception {
        return loader.reload(key, oldValue);
    }

    @Override
    public @NonNull CompletableFuture<V> asyncReload(@NonNull K key, @NonNull V oldValue, @NonNull Executor executor) {
        return loader.asyncReload(key, oldValue, executor);
    }
}
