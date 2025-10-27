package io.nop.orm.factory;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.StringHelper;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.persister.IPersistEnv;

import java.util.function.Function;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.orm.OrmConfigs.CFG_ORM_MODEL_CACHE_TENANT_CACHE_CONTAINER_SIZE;

public class TenantAwareOrmModelProvider implements IOrmModelProvider {
    private ILoadedOrmModel ormModel;

    private final ICache<String, ILoadedOrmModel> tenantCaches;

    private final Function<String, ILoadedOrmModel> loader;

    public TenantAwareOrmModelProvider(String name, Function<String, ILoadedOrmModel> loader) {
        this.tenantCaches = LocalCache.newCache(name,
                newConfig(CFG_ORM_MODEL_CACHE_TENANT_CACHE_CONTAINER_SIZE.get())
                        .useMetrics().destroyOnRemove(),
                loader::apply);
        this.ormModel = loader.apply(null);
        this.loader = loader;
    }

    @Override
    public ILoadedOrmModel getOrmModel(IPersistEnv env) {
        String tenantId = ContextProvider.currentTenantId();
        if (StringHelper.isEmpty(tenantId))
            return ormModel;
        return tenantCaches.get(tenantId);
    }

    @Override
    public void clearCache() {
        this.ormModel = loader.apply(null);
        this.tenantCaches.clear();
    }

    @Override
    public void clearCacheForTenant(String tenantId) {
        if (tenantId != null)
            tenantCaches.remove(tenantId);
    }

    @Override
    public void close() {
        this.ormModel.close();
        this.tenantCaches.clear();
    }
}
