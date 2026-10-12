package io.nop.hazelcast.cache;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.CacheStats;
import io.nop.commons.cache.ISyncCache;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 基于 Hazelcast 原生 IMap 的 ISyncCache 实现（WI14 plan 2448：脱离 javax.cache JCache 面，
 * IMap 为 Hazelcast 官方标准数据结构面）。
 *
 * <p>语义面（plan 2448 Ruling 1b，hazelcast 5.5.0 IMap 方法面实测）：computeIfAbsent 路由到
 * IMap 服务端原子实现；getAndSet/getAndSetAsync 用 IMap.put/putAsync（返回旧值，原子）；
 * 异步面真异步覆写 5 个，putIfAbsentAsync/removeIfMatchAsync/clearAsync 无 IMap 异步对应，
 * 按裁定走 ISyncCache 的 sync 包装 default。
 */
public class HazelcastCache<K, V> implements ISyncCache<K, V>, AutoCloseable {
    private final String name;
    private final IMap<K, V> map;

    public HazelcastCache(HazelcastInstance hazelcastInstance, String cacheName) {
        this(cacheName, hazelcastInstance.getMap(cacheName));
    }

    public HazelcastCache(String cacheName, IMap<K, V> map) {
        this.name = cacheName;
        this.map = map;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * IMap 无独立 close；生命周期归 HazelcastInstance 持有者管理。
     */
    @Override
    public void close() {

    }

    @Override
    public long estimatedSize() {
        return map.size();
    }

    @Override
    public CacheConfig getConfig() {
        return null;
    }

    /**
     * Hazelcast 5.5.0 LocalMapStats 无 misses 统计（plan 2448 Ruling 5）：CacheStats.missCount
     * 不填充；NearCache 统计合并不在本实现范围。
     */
    @Override
    public CacheStats stats() {
        CacheStats stats = new CacheStats();
        var localStats = map.getLocalMapStats();
        stats.setEvictionCount(localStats.getEvictionCount());
        stats.setHitCount(localStats.getHits());
        stats.setCacheGets(localStats.getGetOperationCount());
        stats.setCachePuts(localStats.getPutOperationCount());
        return stats;
    }

    @Override
    public V getIfPresent(K key) {
        return map.get(key);
    }

    @Override
    public Map<K, V> getAllPresent(Collection<? extends K> keys) {
        return map.getAll(new HashSet<>(keys));
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
        // IMap 无 removeAll(Collection)，逐 key remove
        keys.forEach(map::remove);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public void refresh(K key) {
        map.remove(key);
    }

    @Override
    public void forEachEntry(BiConsumer<? super K, ? super V> consumer) {
        map.forEach(consumer);
    }

    @Override
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        this.map.putAll(map);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        return map.putIfAbsent(key, value) == null;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        // IMap.computeIfAbsent 为服务端原子实现，覆写 ICache 锁化 default
        return map.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public V getAndSet(K key, V value) {
        // IMap.put 返回旧值（原子）
        return map.put(key, value);
    }

    @Override
    public boolean remove(K key, V object) {
        return map.remove(key, object);
    }

    @Override
    public void refreshConfig() {

    }

    @Override
    public boolean removeIfMatch(K key, V object) {
        return map.remove(key, object);
    }

    @Nullable
    @Override
    public CompletionStage<V> getAsync(@Nonnull K key) {
        return map.getAsync(key);
    }

    @Override
    public CompletionStage<Void> putAsync(K key, V value) {
        return map.putAsync(key, value).thenApply(v -> null);
    }

    @Override
    public CompletionStage<Void> putAllAsync(Map<? extends K, ? extends V> map) {
        return this.map.putAllAsync(map);
    }

    @Override
    public CompletionStage<V> getAndSetAsync(K key, V value) {
        // IMap.putAsync 返回旧值（原子），与 getAndSet 语义一致
        return map.putAsync(key, value);
    }

    @Override
    public CompletionStage<Void> removeAsync(K key) {
        return map.removeAsync(key).thenApply(v -> null);
    }
}
