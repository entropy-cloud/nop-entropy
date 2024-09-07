package io.nop.core.resource.tenant;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.component.IResourceDependencyManager;
import io.nop.core.resource.deps.IResourceChangeChecker;
import io.nop.core.resource.deps.ResourceDependencySet;
import io.nop.core.resource.deps.ResourceDependsManager;

import java.util.Collection;
import java.util.function.Supplier;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE;

public class TenantAwareDependencyManager implements IResourceDependencyManager {
    private final ICache<String, IResourceDependencyManager> tenantCaches;
    private final IResourceDependencyManager shareCache;

    public TenantAwareDependencyManager(IResourceChangeChecker checker) {
        this.tenantCaches = LocalCache.newCache("resource-dependency-manager",
                newConfig(CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE.get())
                        .useMetrics().destroyOnRemove(),
                k -> new ResourceDependsManager(checker));
        this.shareCache = new ResourceDependsManager(checker);
    }


    @Override
    public void clearDependencies() {
        tenantCaches.clear();
        shareCache.clearDependencies();
    }

    protected String getTenantId() {
        return ContextProvider.currentTenantId();
    }

    protected IResourceDependencyManager getImpl() {
        String tenantId = getTenantId();
        if (StringHelper.isEmpty(tenantId)) {
            return shareCache;
        }
        ResourceTenantManager.instance().useTenant(tenantId);
        return tenantCaches.get(tenantId);
    }


    @Override
    public boolean isDependencyChanged(String resourcePath) {
        return getImpl().isDependencyChanged(resourcePath);
    }

    @Override
    public <T> T collectDepends(String resourcePath, Supplier<T> task) {
        return getImpl().collectDepends(resourcePath, task);
    }

    @Override
    public <T> T ignoreDepends(Supplier<T> task) {
        return getImpl().ignoreDepends(task);
    }

    @Override
    public void traceDepends(String depResourcePath) {
        getImpl().traceDepends(depResourcePath);
    }

    @Override
    public <T> T collectDependsTo(ResourceDependencySet dep, Supplier<T> task) {
        return getImpl().collectDependsTo(dep, task);
    }

    @Override
    public boolean isAnyDependsChange(Collection<String> depends) {
        return getImpl().isAnyDependsChange(depends);
    }

    @Override
    public ResourceDependencySet getResourceDepends(String resourcePath) {
        return getImpl().getResourceDepends(resourcePath);
    }

    @Override
    public <T> T runWhenDependsChanged(String resourcePath, Supplier<T> task) {
        return getImpl().runWhenDependsChanged(resourcePath, task);
    }
}
