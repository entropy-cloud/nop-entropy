package io.nop.core.resource.tenant;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.lang.ICreationListener;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.cache.IResourceCacheEntry;
import io.nop.core.resource.cache.ResourceCacheEntry;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE;

public class TenantAwareResourceCacheEntry<V> implements IResourceCacheEntry<V> {
    private final ICache<String, ResourceCacheEntry<V>> tenantCaches;
    private final ResourceCacheEntry<V> shareCache;

    public TenantAwareResourceCacheEntry(String name, ICreationListener<V> listener) {
        this.tenantCaches = LocalCache.newCache(name,
                newConfig(CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE.get())
                        .useMetrics().destroyOnRemove(),
                k -> new ResourceCacheEntry<>("tenant-" + name, listener));
        this.shareCache = new ResourceCacheEntry<>(name, listener);
    }

    public void clear() {
        shareCache.clear();
        tenantCaches.clear();
    }

    public void clearForTenant(String tenantId) {
        tenantCaches.remove(tenantId);
    }

    protected String getTenantId() {
        return ContextProvider.currentTenantId();
    }

    protected ResourceCacheEntry<V> getCacheEntry() {
        String tenantId = getTenantId();
        if (StringHelper.isEmpty(tenantId)) {
            return shareCache;
        }
        ResourceTenantManager.instance().useTenant(tenantId);
        return tenantCaches.get(tenantId);
    }

    @Override
    public boolean isRefreshEnabled(int refreshMinInterval) {
        return getCacheEntry().isRefreshEnabled(refreshMinInterval);
    }

    @Override
    public V getObject(boolean checkChanged, IResourceObjectLoader<V> loader) {
        return getCacheEntry().getObject(checkChanged, loader);
    }
}
