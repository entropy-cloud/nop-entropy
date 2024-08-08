package io.nop.core.resource.tenant;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.lang.ICreationListener;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.cache.CacheEntryManagement;
import io.nop.core.resource.cache.IResourceCacheEntry;
import io.nop.core.resource.cache.IResourceLoadingCache;
import io.nop.core.resource.cache.ResourceCacheEntry;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.core.resource.store.ITenantResourceStoreSupplier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.nop.core.CoreConfigs.CFG_RESOURCE_STORE_ENABLE_TENANT_DELTA;
import static io.nop.core.CoreErrors.ERR_RESOURCE_STORE_NOT_SUPPORT_TENANT_DELTA;

/**
 * 统一管理租户的启用和初始化。Resource的管理功能需要在IoC容器初始化之前执行，所以无法使用IoC容器来创建ResourceTenantManager
 */
@GlobalInstance
public class ResourceTenantManager implements ITenantResourceStoreSupplier {
    private static ResourceTenantManager _instance = new ResourceTenantManager();

    public static ResourceTenantManager instance() {
        return _instance;
    }

    public static void registerInstance(ResourceTenantManager instance) {
        _instance = Guard.notNull(instance, "instance");
    }

    public boolean isEnableTenantResource() {
        return getTenantChecker().isEnableTenant();
    }

    /**
     * 检查一个资源路径对应的模型是否会被租户定制。注意，除了直接定制模型文件之外，模型所依赖的其他文件有可能被租户定制的时候，这里也要返回true
     */
    public static boolean supportTenant(String path) {
        return instance().getTenantChecker().isSupportTenant(path);
    }

    private final Map<String, ICancellable> tenantCleanups = new ConcurrentHashMap<>();

    private IResourceTenantChecker tenantChecker;
    private final List<IResourceTenantInitializer> tenantInitializers = new CopyOnWriteArrayList<>();

    private final Map<String, IResourceStore> tenantStores = new ConcurrentHashMap<>();

    public void addTenantInitializer(IResourceTenantInitializer tenantInitializer) {
        this.tenantInitializers.add(tenantInitializer);
    }

    public void removeTenantInitializer(IResourceTenantInitializer tenantInitializer) {
        this.tenantInitializers.remove(tenantInitializer);
    }

    public IResourceTenantChecker getTenantChecker() {
        if (this.tenantChecker == null) {
            if (CFG_RESOURCE_STORE_ENABLE_TENANT_DELTA.get()) {
                IResourceTenantChecker checker = ConfigurableResourceTenantChecker.createFromConfig();
                if (checker == null)
                    checker = DefaultResourceTenantChecker.INSTANCE;
                this.tenantChecker = checker;
            } else {
                this.tenantChecker = DefaultResourceTenantChecker.INSTANCE;
            }
        }
        return this.tenantChecker;
    }

    public void setTenantChecker(IResourceTenantChecker feature) {
        this.tenantChecker = feature;
    }

    public void reset() {
        tenantChecker = null;
        tenantCleanups.clear();
    }

    public boolean isTenantUsed(String tenantId) {
        return tenantCleanups.containsKey(tenantId);
    }

    public Set<String> getUsedTenants() {
        return new TreeSet<>(tenantCleanups.keySet());
    }

    /**
     * 销毁租户所占用的资源
     */
    public void clearForTenant(String tenantId) {
        ICancellable cleanup = tenantCleanups.remove(tenantId);
        if (cleanup != null)
            cleanup.cancel();
        GlobalCacheRegistry.instance().clearForTenant(tenantId);
    }

    public void clearAllTenants() {
        for (String tenantId : getUsedTenants()) {
            clearForTenant(tenantId);
        }
    }

    /**
     * 第一次执行时会自动触发租户的初始化函数
     */
    public void useTenant(String tenantId) {
        tenantCleanups.computeIfAbsent(tenantId, k -> {
            Cancellable cancellable = new Cancellable();
            try {
                for (IResourceTenantInitializer initializer : tenantInitializers) {
                    cancellable.appendOnCancelTask(initializer.initializeTenant(tenantId));
                }
                return cancellable;
            } catch (Exception e) {
                cancellable.cancel();
                throw NopException.adapt(e);
            }
        });
    }

    public <V> IResourceLoadingCache<V> makeLoadingCache(String name,
                                                         IResourceObjectLoader<V> loader,
                                                         ICreationListener<V> listener) {
        return makeLoadingCache(name, isEnableTenantResource(), loader, listener, null, null);
    }

    public <V> IResourceLoadingCache<V> makeLoadingCache(String name, boolean enableTenant,
                                                         IResourceObjectLoader<V> loader,
                                                         ICreationListener<V> listener,
                                                         IConfigReference<Integer> cacheMaxSize,
                                                         IConfigReference<Duration> cacheTimeout) {
        IResourceLoadingCache<V> cache;
        if (enableTenant) {
            cache = new TenantAwareResourceLoadingCache<>(name, loader, listener, cacheMaxSize, cacheTimeout);
        } else {
            cache = new ResourceLoadingCache<>(name, loader, listener, cacheMaxSize, cacheTimeout);
        }
        return cache;
    }

    public <V> CacheEntryManagement<V> makeCacheEntry(String name, boolean enableTenant,
                                                      ICreationListener<V> listener) {
        IResourceCacheEntry<V> cache;
        if (enableTenant) {
            cache = new TenantAwareResourceCacheEntry<>(name, listener);
        } else {
            cache = new ResourceCacheEntry<>(name, listener);
        }
        return new CacheEntryManagement<>(name, cache);
    }

    @Override
    public IResourceStore getTenantResourceStore(String tenantId) {
        if (!isEnableTenantResource())
            throw new NopException(ERR_RESOURCE_STORE_NOT_SUPPORT_TENANT_DELTA);

        useTenant(tenantId);
        return tenantStores.get(tenantId);
    }

    public void updateTenantResourceStore(String tenantId, IResourceStore store) {
        if (!isEnableTenantResource())
            throw new NopException(ERR_RESOURCE_STORE_NOT_SUPPORT_TENANT_DELTA);
        tenantStores.put(tenantId, store);
    }
}
