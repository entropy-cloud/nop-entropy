package io.nop.core.resource.tenant;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.lang.ICreationListener;
import io.nop.commons.util.CollectionHelper;
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
import io.nop.core.resource.component.IResourceDependencyManager;
import io.nop.core.resource.deps.IResourceChangeChecker;
import io.nop.core.resource.deps.ResourceDependsManager;
import io.nop.core.resource.store.ITenantResourceStoreSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import static io.nop.core.CoreConfigs.CFG_RESOURCE_NOT_SUPPORT_TENANT_CACHE;
import static io.nop.core.CoreConfigs.CFG_TENANT_RESOURCE_DISABLED_PATHS;
import static io.nop.core.CoreConfigs.CFG_TENANT_RESOURCE_ENABLED;
import static io.nop.core.CoreConfigs.CFG_TENANT_RESOURCE_ENABLED_PATHS;
import static io.nop.core.CoreErrors.ERR_RESOURCE_STORE_NOT_SUPPORT_TENANT_DELTA;

/**
 * 统一管理租户的启用和初始化。Resource的管理功能需要在IoC容器初始化之前执行，所以无法使用IoC容器来创建ResourceTenantManager
 */
@GlobalInstance
public class ResourceTenantManager implements ITenantResourceStoreSupplier {
    static final Logger LOG = LoggerFactory.getLogger(ResourceTenantManager.class);

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

    static final ThreadLocal<Boolean> s_initializingTenant = new ThreadLocal<>();

    public static boolean isInitializingTenant() {
        return Boolean.TRUE.equals(s_initializingTenant.get());
    }

    public static <T> T runInitializeTenantTask(Supplier<T> task) {
        Boolean b = s_initializingTenant.get();
        if (Boolean.TRUE.equals(b)) {
            return task.get();
        } else {
            s_initializingTenant.set(true);
            try {
                return task.get();
            } finally {
                s_initializingTenant.set(false);
            }
        }
    }

    private ITenantResourceProvider tenantResourceProvider;

    public ITenantResourceProvider getTenantResourceProvider() {
        return tenantResourceProvider;
    }

    public void setTenantResourceProvider(ITenantResourceProvider tenantResourceProvider) {
        this.tenantResourceProvider = tenantResourceProvider;
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
        if (moduleName.startsWith("nop-")) {
            return false;
        }

        // 从配置中获取不支持租户缓存的资源路径
        Set<String> setPath = CFG_RESOURCE_NOT_SUPPORT_TENANT_CACHE.get();
        if (!CollectionHelper.isEmpty(setPath)) {
            for (String path : setPath) {
                if (moduleName.startsWith(path))
                    return false;
            }
        }
        return true;
    }

    public Set<String> getUsedTenants() {
        ITenantResourceProvider provider = this.tenantResourceProvider;
        if (provider == null)
            return Collections.emptySet();
        return new TreeSet<>(provider.getUsedTenantIds());
    }

    /**
     * 销毁租户所占用的资源
     */
    public void clearForTenant(String tenantId) {
        ITenantResourceProvider provider = this.tenantResourceProvider;
        if (provider != null)
            provider.clearForTenant(tenantId);
        GlobalCacheRegistry.instance().clearForTenant(tenantId);
    }

    public void clearAllTenants() {
        for (String tenantId : getUsedTenants()) {
            clearForTenant(tenantId);
        }
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

    public IResourceDependencyManager makeDependencyManager(IResourceChangeChecker checker) {
        if (isEnableTenantResource())
            return new TenantAwareDependencyManager(checker);
        return new ResourceDependsManager(checker);
    }

    @Override
    public IResourceStore getTenantResourceStore(String tenantId) {
        if (!isEnableTenantResource())
            throw new NopException(ERR_RESOURCE_STORE_NOT_SUPPORT_TENANT_DELTA);

        if (isInitializingTenant())
            return null;

        ITenantResourceProvider provider = getTenantResourceProvider();
        if (provider == null)
            return null;
        return provider.getTenantResourceStore(tenantId);
    }
}
