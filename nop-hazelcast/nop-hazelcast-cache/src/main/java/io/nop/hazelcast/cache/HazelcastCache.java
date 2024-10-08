package io.nop.hazelcast.cache;

import com.hazelcast.cache.CacheStatistics;
import com.hazelcast.core.HazelcastInstance;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.CacheStats;
import io.nop.commons.cache.JavaxCache;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class HazelcastCache<K, V> extends JavaxCache<K, V> {
    private final com.hazelcast.cache.ICache<K, V> cache;

    public HazelcastCache(HazelcastInstance hazelcastInstance, String cacheName) {
        this(hazelcastInstance.getCacheManager().getCache(cacheName));
    }

    public HazelcastCache(com.hazelcast.cache.ICache<K, V> cache) {
        super(cache);
        this.cache = cache;
    }

    @Override
    public long estimatedSize() {
        return cache.size();
    }

    @Override
    public CacheConfig getConfig() {
        return null;
    }

    @Override
    public CacheStats stats() {
        CacheStats stats = new CacheStats();
        CacheStatistics cacheStats = cache.getLocalCacheStatistics();
        stats.setEvictionCount(cacheStats.getCacheEvictions());
        stats.setHitCount(cacheStats.getCacheHits());
        stats.setMissCount(cacheStats.getCacheMisses());
        stats.setCacheGets(cacheStats.getCacheGets());
        stats.setCachePuts(cacheStats.getCachePuts());
        return stats;
    }

    @Override
    public CompletionStage<Void> clearAsync() {
        return super.clearAsync();
    }

    @Nonnull
    @Override
    public CompletionStage<V> computeIfAbsentAsync(@Nonnull K key, @Nonnull Function<? super K, ? extends V> mappingFunction) {
        return super.computeIfAbsentAsync(key, mappingFunction);
    }

    @Override
    public CompletionStage<Boolean> containsKeyAsync(K key) {
        return super.containsKeyAsync(key);
    }

    @Override
    public CompletionStage<V> getAndSetAsync(K key, V value) {
        return cache.getAndPutAsync(key, value);
    }

    @Nullable
    @Override
    public CompletionStage<V> getAsync(@Nonnull K key) {
        return cache.getAsync(key);
    }

    @Override
    public CompletionStage<Void> putAllAsync(Map<? extends K, ? extends V> map) {
        return super.putAllAsync(map);
    }

    @Override
    public CompletionStage<Void> putAsync(K key, V value) {
        return cache.putAsync(key, value);
    }

    @Override
    public CompletionStage<Boolean> putIfAbsentAsync(K key, V value) {
        return cache.putIfAbsentAsync(key, value);
    }


    @Override
    public CompletionStage<Void> removeAsync(K key) {
        Function<Object, Void> f = object -> null;
        return cache.removeAsync(key).thenApply(f);
    }

    @Override
    public CompletionStage<Boolean> removeIfMatchAsync(K key, V object) {
        return cache.removeAsync(key, object);
    }
}
