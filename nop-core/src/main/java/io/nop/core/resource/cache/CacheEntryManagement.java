package io.nop.core.resource.cache;

import io.nop.commons.cache.CacheStats;
import io.nop.commons.cache.ICacheManagement;
import io.nop.core.resource.IResourceObjectLoader;
import jakarta.annotation.Nonnull;

import java.time.Duration;

public class CacheEntryManagement<V> implements ICacheManagement<V>, IResourceCacheEntry<V> {
    private final String name;
    private final IResourceCacheEntry<V> cache;

    public CacheEntryManagement(String name, IResourceCacheEntry<V> cache) {
        this.name = name;
        this.cache = cache;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void remove(@Nonnull V key) {
        clear();
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public CacheStats stats() {
        return null;
    }

    @Override
    public void clearForTenant(String tenantId) {
        cache.clearForTenant(tenantId);
    }

    @Override
    public boolean isRefreshEnabled(int refreshMinInterval) {
        return cache.isRefreshEnabled(refreshMinInterval);
    }

    @Override
    public V getObject(boolean checkChanged, IResourceObjectLoader<V> loader) {
        return cache.getObject(checkChanged, loader);
    }

    public V getObject(boolean checkChanged, IResourceObjectLoader<V> loader, Duration timeout) {
        if (cache.isRefreshEnabled(timeout))
            cache.clear();
        return cache.getObject(checkChanged, loader);
    }
}
