package io.nop.core.resource.tenant;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.lang.ICreationListener;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.cache.CacheEntryManagement;
import io.nop.core.resource.cache.IResourceCacheEntry;
import io.nop.core.resource.cache.IResourceLoadingCache;
import io.nop.core.resource.cache.ResourceCacheEntry;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.core.resource.store.ITenantResourceStoreSupplier;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.core.CoreConfigs.CFG_TENANT_RESOURCE_DISABLED_PATHS;
import static io.nop.core.CoreConfigs.CFG_TENANT_RESOURCE_ENABLED;
import static io.nop.core.CoreConfigs.CFG_TENANT_RESOURCE_ENABLED_PATHS;
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

    private Set<String> enabledTenantPaths;
    private Set<String> disabledTenantPaths;

    public boolean isEnableTenantResource() {
        return CFG_TENANT_RESOURCE_ENABLED.get();
    }

    /**
     * 检查一个资源路径对应的模型是否会被租户定制。注意，除了直接定制模型文件之外，模型所依赖的其他文件有可能被租户定制的时候，这里也要返回true
     */
    public static boolean supportTenant(String path) {
        return instance().isSupportTenant(path);
    }

    private final Map<String, TenantInitialization> tenantInitializations = new ConcurrentHashMap<>();

    private final List<IResourceTenantInitializer> tenantInitializers = new CopyOnWriteArrayList<>();

    private final Map<String, IResourceStore> tenantStores = new ConcurrentHashMap<>();

    private ITenantModuleDiscovery tenantModuleDiscovery;

    class TenantInitialization {
        private final String tenantId;

        ICancellable cleanup;
        final AtomicInteger state = new AtomicInteger();
        final CompletableFuture<Void> ready = new CompletableFuture<>();

        public TenantInitialization(String tenantId) {
            this.tenantId = tenantId;
        }

        public void cancel() {
            if (cleanup != null)
                cleanup.cancel();
        }

        public void init() {
            if (state.compareAndSet(0, 1)) {
                Cancellable cancellable = new Cancellable();
                try {
                    for (IResourceTenantInitializer initializer : tenantInitializers) {
                        cancellable.appendOnCancelTask(initializer.initializeTenant(tenantId));
                    }
                    this.cleanup = cancellable;
                    ready.complete(null);
                } catch (Exception e) {
                    ready.completeExceptionally(e);
                    cancellable.cancel();
                    throw NopException.adapt(e);
                }
            }
        }

        public void awaitReady() {
            init();
            FutureHelper.syncGet(ready);
        }
    }

    public void addTenantInitializer(IResourceTenantInitializer tenantInitializer) {
        this.tenantInitializers.add(tenantInitializer);
    }

    public void removeTenantInitializer(IResourceTenantInitializer tenantInitializer) {
        this.tenantInitializers.remove(tenantInitializer);
    }

    public ITenantModuleDiscovery getTenantModuleDiscovery() {
        return tenantModuleDiscovery;
    }

    public void setTenantModuleDiscovery(ITenantModuleDiscovery tenantModuleDiscovery) {
        this.tenantModuleDiscovery = tenantModuleDiscovery;
    }

    public boolean isSupportTenant(String resourcePath) {
        if (disabledTenantPaths == null) {
            disabledTenantPaths = CFG_TENANT_RESOURCE_DISABLED_PATHS.get();
            if (disabledTenantPaths == null)
                disabledTenantPaths = Collections.emptySet();
        }

        if (enabledTenantPaths == null) {
            enabledTenantPaths = CFG_TENANT_RESOURCE_ENABLED_PATHS.get();
            if (enabledTenantPaths == null || enabledTenantPaths.isEmpty())
                enabledTenantPaths = Collections.singleton(ResourceConstants.RESOLVE_PREFIX);
        }

        for (String enabledPath : enabledTenantPaths) {
            if (resourcePath.startsWith(enabledPath))
                return true;
        }

        for (String disabledPath : disabledTenantPaths) {
            if (resourcePath.startsWith(disabledPath))
                return false;
        }

        String moduleName = ResourceHelper.getModuleName(resourcePath);
        if (StringHelper.isEmpty(moduleName))
            return false;
        // nop资源不支持租户缓存
        return moduleName.startsWith("nop-");
    }

    public void reset() {
        tenantInitializations.clear();
    }

    public boolean isTenantUsed(String tenantId) {
        return tenantInitializations.containsKey(tenantId);
    }

    public Set<String> getUsedTenants() {
        return new TreeSet<>(tenantInitializations.keySet());
    }

    /**
     * 销毁租户所占用的资源
     */
    public void clearForTenant(String tenantId) {
        TenantInitialization cleanup = tenantInitializations.remove(tenantId);
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
        TenantInitialization tenant = tenantInitializations.computeIfAbsent(tenantId, TenantInitialization::new);
        tenant.init();
    }

    public void awaitTenantReady(String tenantId) {
        TenantInitialization tenant = tenantInitializations.computeIfAbsent(tenantId, TenantInitialization::new);
        tenant.awaitReady();
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
