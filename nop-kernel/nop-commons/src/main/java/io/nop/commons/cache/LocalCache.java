/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Policy;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.lang.IDestroyable;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.DestroyHelper;
import io.nop.commons.util.StringHelper;
import jakarta.annotation.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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
            this.metricsHandle = registerMetrics();
    }

    /**
     * C6-004：本缓存绑定的 meter 句柄，destroy 时 close 注销。
     */
    private volatile AutoCloseable metricsHandle;

    public LocalCache(String name, CacheConfig cacheConfig) {
        this(name, cacheConfig, null);
    }

    public void destroy() {
        // C6-004：注销本缓存的 meter（绑定/注销对称）
        AutoCloseable handle = this.metricsHandle;
        this.metricsHandle = null;
        if (handle != null) {
            try {
                handle.close();
            } catch (Exception e) {
                LOG.warn("nop.warn.commons.cache.metrics-close-fail", e);
            }
        }
        // C6-005（plan 2548 裁定 D）：invalidateAll 先触发 destroyOnRemove 回调（entry.destroy()），
        // 再 cleanUp 等待异步 removal 完成；原仅 cleanUp() 会跳过销毁回调
        cache.invalidateAll();
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

    /**
     * C6-004（plan 2548 裁定 C）：绑定后返回注销句柄，由实例持有，{@link #destroy()} 时 close。
     * 同时做同名 meter 去重（tenant 场景多个 "tenant-"+name 实例共享同名 tag）：绑定前先移除
     * 同名旧 meter（旧实例的句柄随后 close 亦为幂等 no-op），避免重复注册异常/指标混杂。
     */
    private AutoCloseable registerMetrics() {
        if (StringHelper.isEmpty(name)) {
            return () -> {
            };
        }
        MeterRegistry registry = GlobalMeterRegistry.instance();
        if (registry == null) {
            return () -> {
            };
        }
        // 同名去重：CaffeineCacheMetrics 的 meter 名以 "cache." 前缀、tag "cache"=name
        List<Meter> stale = new ArrayList<>();
        for (Meter meter : registry.getMeters()) {
            String tagName = meter.getId().getTag("cache");
            if (meter.getId().getName().startsWith("cache.") && name.equals(tagName))
                stale.add(meter);
        }
        for (Meter meter : stale) {
            registry.remove(meter);
        }

        new CaffeineCacheMetrics(cache, name, Collections.emptyList()).bindTo(registry);
        List<Meter> added = new ArrayList<>();
        for (Meter meter : registry.getMeters()) {
            String tagName = meter.getId().getTag("cache");
            if (meter.getId().getName().startsWith("cache.") && name.equals(tagName)
                    && !stale.contains(meter))
                added.add(meter);
        }
        return () -> {
            for (Meter meter : added) {
                registry.remove(meter);
            }
        };
    }

    private void buildCache(CacheConfig config, ICacheLoader<K, V> loader) {
        Caffeine builder = Caffeine.newBuilder();
        // 1.7-C3-010（plan 2547 裁定 F）：显式 executor 出口——缺省仍走 Caffeine 默认 commonPool
        if (config.getExecutor() != null)
            builder.executor(config.getExecutor());
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
            policy.eviction().ifPresent(evt -> {
                evt.setMaximum(config.getMaximumSize());
            });
        }

        if (config.getMaximumWeight() > 0) {
            policy.eviction().ifPresent(evt -> {
                evt.setMaximum(config.getMaximumWeight());
            });
        }

        if (config.getExpireAfterAccess() != null) {
            policy.expireAfterAccess().ifPresent(exp -> {
                exp.setExpiresAfter(config.getExpireAfterAccess());
            });
        }

        if (config.getExpireAfterWrite() != null) {
            policy.expireAfterWrite().ifPresent(exp -> {
                exp.setExpiresAfter(config.getExpireAfterWrite());
            });
        }

        if (config.getRefreshAfterWrite() != null) {
            policy.refreshAfterWrite().ifPresent(exp -> {
                exp.setRefreshesAfter(config.getRefreshAfterWrite());
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
        // 底层Caffeine的asMap()提供原子putIfAbsent，避免check-then-act竞态
        return cache.asMap().putIfAbsent(key, value) == null;
    }

    @Override
    public void remove(K key) {
        cache.invalidate(key);
    }

    public void clear() {
        LOG.info("nop.cache.clear:cacheName={}", getName());
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