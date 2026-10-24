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

    /**
     * 3-arg显式超时变体：语义钉（plan 2500裁定④）——「isRefreshEnabled(timeout) -&gt; clear()重建」的显式超时
     * 重建语义维持不变；timeout配置为null时分支恒死亦维持现状（不静默改绑全局TTL键nop.core.component.resource-cache.timeout，
     * 配置契约显式性）。直连路径的节流与maxAge自愈由底层entry内建门承载（IResourceCacheEntry.getObject协议）
     */
    public V getObject(boolean checkChanged, IResourceObjectLoader<V> loader, Duration timeout) {
        if (cache.isRefreshEnabled(timeout))
            cache.clear();
        return cache.getObject(checkChanged, loader);
    }
}
