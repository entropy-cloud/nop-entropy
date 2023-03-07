/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Policy;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.lang.IDestroyable;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.DestroyHelper;
import io.nop.commons.util.StringHelper;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class LocalCache<K, V> implements ICache<K, V>, IConfigRefreshable, IDestroyable {
    static final Logger LOG = LoggerFactory.getLogger(LocalCache.class);

    private final String name;
    private Cache<K, V> cache;
    private LoadingCache<K, V> loadingCache;
    private AsyncCache<K, V> asyncCache;
    private CacheConfig config;

    public LocalCache(String name, CacheConfig config, ICacheLoader<K, V> loader) {
        this.name = name;
        this.config = config;
        buildCache(config, loader);

        if (config.isUseMetrics())
            registerMetrics();
    }

    public LocalCache(String name, CacheConfig cacheConfig) {
        this(name, cacheConfig, null);
    }

    public void destroy() {
        cache.cleanUp();
    }

    public static <K, V> LocalCache<K, V> newCache(String name, CacheConfig config, ICacheLoader<K, V> loader) {
        return new LocalCache<>(name, config, loader);
    }

    public static <K, V> LocalCache<K, V> newCache(String name, CacheConfig config) {
        return new LocalCache<>(name, config);
    }

    public String getName() {
        return name;
    }

    private void registerMetrics() {
        if (!StringHelper.isEmpty(name)) {
            new CaffeineCacheMetrics(cache, name, Collections.emptyList()).bindTo(GlobalMeterRegistry.instance());
        }
    }

    private void buildCache(CacheConfig config, ICacheLoader<K, V> loader) {
        Caffeine builder = Caffeine.newBuilder();
        if (config.getMaximumSize() > 0) {
            builder.maximumSize(config.getMaximumSize());
        }

        if (config.getExpireAfterWrite() != null && config.getExpireAfterAccess() == null) {
            builder.expireAfterWrite(config.getExpireAfterWrite());
        }

        if (config.getExpireAfterAccess() != null) {
            builder.expireAfterAccess(config.getExpireAfterAccess());
        }

        if (config.isWeakKeys())
            builder.weakKeys();

        if (config.isWeakValues()) {
            builder.weakValues();
        }

        if (config.getMaximumWeight() > 0)
            builder.maximumWeight(config.getMaximumWeight());

        if (config.getRefreshAfterWrite() != null) {
            builder.refreshAfterWrite(config.getRefreshAfterWrite());
        }

        builder.recordStats();

        builder.removalListener((key, value, cause) -> {
            LOG.trace("nop.commons.cache-item-removal:cache={},key={},value={},cause={}", name, key, value, cause);
            if (config.isDestroyOnRemove())
                DestroyHelper.safeDestroy(value);
        });

        Cache<K, V> prevCache = this.cache;

        if (config.isAsync()) {
            if (loader != null) {
                AsyncLoadingCache<K, V> cache = builder.buildAsync(createCacheLoader(loader));
                this.asyncCache = cache;
                this.cache = this.loadingCache = cache.synchronous();
            } else {
                this.asyncCache = builder.buildAsync();
                this.cache = asyncCache.synchronous();
            }
        } else {
            if (loader != null) {
                this.cache = this.loadingCache = builder.build(createCacheLoader(loader));
            } else {
                this.cache = builder.build();
            }
        }
        if (prevCache != null)
            prevCache.invalidateAll();
    }

    static <K, V> CacheLoader<K, V> createCacheLoader(ICacheLoader<K, V> loader) {
        return new CaffeineCacheLoader<>(loader);
    }

    public CacheConfig getConfig() {
        return config;
    }

    @Override
    public void refreshConfig() {
        updateCache(config);
    }

    private void updateCache(CacheConfig config) {
        Policy<K, V> policy = cache.policy();
        if (config.getMaximumSize() > 0) {
            policy.eviction().map(evt -> {
                evt.setMaximum(config.getMaximumSize());
                return null;
            });
        }

        if (config.getMaximumWeight() > 0) {
            policy.eviction().map(evt -> {
                evt.setMaximum(config.getMaximumWeight());
                return null;
            });
        }

        if (config.getExpireAfterAccess() != null) {
            policy.expireAfterAccess().map(exp -> {
                exp.setExpiresAfter(config.getExpireAfterAccess());
                return null;
            });
        }

        if (config.getExpireAfterWrite() != null) {
            policy.expireAfterWrite().map(exp -> {
                exp.setExpiresAfter(config.getExpireAfterWrite());
                return null;
            });
        }

        if (config.getRefreshAfterWrite() != null) {
            policy.refreshAfterWrite().map(exp -> {
                exp.setExpiresAfter(config.getRefreshAfterWrite());
                return null;
            });
        }

        cache.cleanUp();
    }

    public CacheStats stats() {
        CacheStats stats = new CacheStats();
        com.github.benmanes.caffeine.cache.stats.CacheStats nativeStats = cache.stats();
        stats.setEvictionCount(nativeStats.evictionCount());
        stats.setEvictionWeight(nativeStats.evictionWeight());
        stats.setHitCount(nativeStats.hitCount());
        stats.setLoadFailureCount(nativeStats.loadFailureCount());
        stats.setLoadSuccessCount(nativeStats.loadSuccessCount());
        stats.setMissCount(nativeStats.missCount());
        stats.setTotalLoadTime(nativeStats.totalLoadTime());
        return stats;
    }

    @Override
    public V getAndSet(K key, V value) {
        V old = get(key);
        cache.put(key, value);
        return old;
    }

    @Override
    public V get(K key) {
        if (loadingCache != null)
            return loadingCache.get(key);
        return cache.getIfPresent(key);
    }

    @Override
    public V getIfPresent(K key) {
        return cache.getIfPresent(key);
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        return cache.get(key, mappingFunction);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        cache.putAll(map);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        V old = cache.getIfPresent(key);
        if (old == null) {
            cache.put(key, value);
            return true;
        }
        return false;
    }

    @Override
    public void remove(K key) {
        cache.invalidate(key);
    }

    public void clear() {
        LOG.info("nop.cache.clear:cacheName={}",getName());
        cache.invalidateAll();
    }

    @Override
    public Map<K, V> getAll(Collection<? extends K> keys) {
        if (loadingCache != null)
            return loadingCache.getAll(keys);
        return cache.getAllPresent(keys);
    }

    @Override
    public Map<K, V> getAllPresent(Collection<? extends K> keys) {
        return cache.getAllPresent(keys);
    }

    @Override
    public void removeAll(Collection<? extends K> keys) {
        cache.invalidateAll(keys);
    }

    public void refresh(K key) {
        if (loadingCache != null) {
            loadingCache.refresh(key);
        } else {
            cache.invalidate(key);
        }
    }

    @Override
    public long estimatedSize() {
        return cache.estimatedSize();
    }

    @Override
    public @Nullable CompletionStage<V> getAsync(@NonNull K key) {
        if (asyncCache != null)
            return asyncCache.getIfPresent(key);
        return FutureHelper.futureCall(() -> get(key));
    }

    @Override
    public @NonNull CompletionStage<V> computeIfAbsentAsync(@NonNull K key,
                                                            @NonNull Function<? super K, ? extends V> mappingFunction) {
        if (asyncCache != null)
            return asyncCache.get(key, mappingFunction);
        return FutureHelper.futureCall(() -> computeIfAbsent(key, mappingFunction));
    }

    @Override
    public @NonNull CompletionStage<Map<K, V>> getAllAsync(Collection<? extends K> keys) {
        if (asyncCache != null)
            return asyncCache.getAll(keys, k -> null);
        return FutureHelper.futureCall(() -> getAll(keys));
    }

    @Override
    public void forEachEntry(BiConsumer<? super K, ? super V> consumer) {
        cache.asMap().entrySet().forEach(entry -> {
            consumer.accept(entry.getKey(), entry.getValue());
        });
    }

    @Override
    public CompletionStage<Boolean> containsKeyAsync(K key) {
        return FutureHelper.success(containsKey(key));
    }

    @Override
    public CompletionStage<Void> putAsync(K key, V value) {
        put(key, value);
        return FutureHelper.success(null);
    }

    @Override
    public CompletionStage<Void> putAllAsync(Map<? extends K, ? extends V> map) {
        putAll(map);
        return FutureHelper.success(null);
    }

    @Override
    public CompletionStage<Boolean> putIfAbsentAsync(K key, V value) {
        boolean result = putIfAbsent(key, value);
        return FutureHelper.success(result);
    }

    @Override
    public CompletionStage<V> getAndSetAsync(K key, V value) {
        V result = getAndSet(key, value);
        return FutureHelper.success(result);
    }

    @Override
    public CompletionStage<Void> removeAsync(K key) {
        remove(key);
        return FutureHelper.success(null);
    }

    @Override
    public CompletionStage<Boolean> removeIfMatchAsync(K key, V object) {
        return FutureHelper.success(remove(key, object));
    }

    @Override
    public boolean removeIfMatch(K key, V object) {
        return remove(key, object);
    }

    @Override
    public CompletionStage<Void> removeAllAsync(Collection<? extends K> keys) {
        removeAll(keys);
        return FutureHelper.success(null);
    }

    @Override
    public CompletionStage<Void> clearAsync() {
        clear();
        return FutureHelper.success(null);
    }

    @Override
    public CompletionStage<Void> forEachEntryAsync(BiConsumer<? super K, ? super V> consumer) {
        forEachEntry(consumer);
        return FutureHelper.success(null);
    }
}