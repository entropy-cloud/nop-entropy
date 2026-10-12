package io.nop.commons.cache;

import io.nop.commons.util.CollectionHelper;
import org.ehcache.Cache;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 基于 Ehcache 3（jakarta classifier 构件）的 ISyncCache 适配器，替代已死的 JSR-107
 * JavaxCache 适配器（WI14 plan 2448）。
 *
 * <p>依赖声明为 optional：本适配器当前零生产调用方，使用者引入时需自行声明
 * org.ehcache:ehcache:jakarta 依赖（版本由 SB BOM 管理）。
 *
 * <p>语义裁定（plan 2448 Ruling 1，org.ehcache.Cache 3.12 方法面实测）：
 * putIfAbsent 映射 cache.putIfAbsent（返回前值，==null 即成功）；getAndSet 与
 * computeIfAbsent 继承 ICache 锁化 default（Ehcache 无原子 getAndPut/computeIfAbsent）；
 * refresh 按先例映射 remove。
 */
public class EhcacheCache<K, V> implements ISyncCache<K, V>, AutoCloseable {
    private final String name;
    private final Cache<K, V> cache;

    public EhcacheCache(String name, Cache<K, V> cache) {
        this.name = name;
        this.cache = cache;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * org.ehcache.Cache 自身无 close；缓存生命周期归持有 CacheManager 的一方管理。
     */
    @Override
    public void close() {

    }

    @Override
    public long estimatedSize() {
        return -1L;
    }

    @Override
    public CacheConfig getConfig() {
        return null;
    }

    @Override
    public V getIfPresent(K key) {
        return cache.get(key);
    }

    @Override
    public Map<K, V> getAllPresent(Collection<? extends K> keys) {
        return cache.getAll(CollectionHelper.toSet(keys));
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void remove(K key) {
        cache.remove(key);
    }

    @Override
    public void removeAll(Collection<? extends K> keys) {
        cache.removeAll(CollectionHelper.toSet(keys));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void refresh(K key) {
        cache.remove(key);
    }

    @Override
    public void forEachEntry(BiConsumer<? super K, ? super V> consumer) {
        cache.forEach(entry -> consumer.accept(entry.getKey(), entry.getValue()));
    }

    @Override
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        cache.putAll(map);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        // org.ehcache.Cache.putIfAbsent 返回前值（无前值即成功）
        return cache.putIfAbsent(key, value) == null;
    }

    @Override
    public boolean remove(K key, V object) {
        return cache.remove(key, object);
    }

    /**
     * 本实现的缓存配置由 Ehcache CacheManager 配置自管，nop 侧 CacheConfig 不适用
     * （getConfig() 恒返回 null）：调用方应以 getConfig()==null 判断该实现是否支持
     * nop 侧配置（与 cache-contract 契约一致）。
     */
    @Override
    public void refreshConfig() {

    }

    @Override
    public boolean removeIfMatch(K key, V object) {
        return cache.remove(key, object);
    }
}
