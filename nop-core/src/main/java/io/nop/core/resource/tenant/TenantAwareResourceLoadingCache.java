package io.nop.core.resource.tenant;

import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.context.ContextProvider;
import io.nop.commons.cache.CacheStats;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.lang.ICreationListener;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.cache.IResourceLoadingCache;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.core.resource.deps.ResourceDependencySet;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.time.Duration;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE;

public class TenantAwareResourceLoadingCache<V> implements IResourceLoadingCache<V> {

    private final ICache<String, ResourceLoadingCache<V>> tenantCaches;
    private final ResourceLoadingCache<V> shareCache;

    public TenantAwareResourceLoadingCache(String name, IResourceObjectLoader<V> loader, ICreationListener<V> listener,
                                           IConfigReference<Integer> cacheMaxSize, IConfigReference<Duration> cacheTimeout) {
        this.tenantCaches = LocalCache.newCache(name,
                newConfig(CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE.get()),
                k -> new ResourceLoadingCache<>("tenant-" + name, loader, listener, cacheMaxSize, cacheTimeout));
        this.shareCache = new ResourceLoadingCache<>(name, loader, listener, cacheMaxSize, cacheTimeout);
    }

    public TenantAwareResourceLoadingCache(String name, IResourceObjectLoader<V> loader, ICreationListener<V> listener) {
        this(name, loader, listener, null, null);
    }

    @Override
    public void clearForTenant(String tenantId) {
        tenantCaches.remove(tenantId);
    }

    @Override
    public String getName() {
        return shareCache.getName();
    }

    protected String getTenantId() {
        return ContextProvider.currentTenantId();
    }

    protected ResourceLoadingCache<V> getCache(String path) {
        String tenantId = getTenantId();
        if (StringHelper.isEmpty(tenantId)) {
            return shareCache;
        }

        if (!ResourceTenantManager.supportTenant(path))
            return shareCache;

        return tenantCaches.get(tenantId);
    }

    @Override
    public void remove(@Nonnull String path) {
        getCache(path).remove(path);
    }

    @Override
    public void clear() {
        tenantCaches.forEachEntry((key, cache) -> {
            cache.clear();
        });

        shareCache.clear();
    }

    @Override
    public CacheStats stats() {
        return getCache("/").stats();
    }

    @Override
    public void refreshConfig() {
        tenantCaches.forEachEntry((key, cache) -> {
            cache.refreshConfig();
        });

        shareCache.refreshConfig();
    }

    @Override
    public V require(String resourcePath) {
        return getCache(resourcePath).require(resourcePath);
    }

    @Override
    public void state_saveTo(ObjectOutput out) throws IOException {
        shareCache.state_saveTo(out);
    }

    @Override
    public void state_loadFrom(ObjectInput in) throws ClassNotFoundException, IOException {
        shareCache.state_loadFrom(in);
    }

    @Override
    public V get(String resourcePath) {
        return getCache(resourcePath).get(resourcePath);
    }

    @Override
    public V get(String resourcePath, IResourceObjectLoader<V> loader) {
        return getCache(resourcePath).get(resourcePath, loader);
    }

    @Override
    public ResourceDependencySet getResourceDependsSet(String resourcePath) {
        return getCache(resourcePath).getResourceDependsSet(resourcePath);
    }

    @Override
    public boolean shouldCheckChanged() {
        return getCache("/").shouldCheckChanged();
    }
}