package io.nop.orm.factory;

import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.cache.CacheEntryManagement;
import io.nop.core.resource.tenant.ResourceTenantManager;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.OrmConfigs;
import io.nop.orm.interceptor.XplOrmInterceptor;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.orm.persister.IPersistEnv;

public class DefaultOrmModelProvider implements IOrmModelProvider {
    private final IPersistEnv env;
    private LoadedOrmModel ormModel;

    private final CacheEntryManagement<XplOrmInterceptor> interceptorCache = ResourceTenantManager.instance()
            .makeCacheEntry("orm-interceptor-cache", false, null);

    public DefaultOrmModelProvider(IPersistEnv env) {
        this.env = env;
        clearCache();

        GlobalCacheRegistry.instance().register(interceptorCache);
    }

    @Override
    public void close() {
        GlobalCacheRegistry.instance().unregister(interceptorCache);
        ormModel.close();
    }

    @Override
    public ILoadedOrmModel getOrmModel(IPersistEnv env) {
        IOrmInterceptor interceptor = interceptorCache.getObject(OrmConfigs.CFG_ORM_INTERCEPTOR_CACHE_CHECK_CHANGE.get(),
                cacheName -> new XplOrmInterceptorLoader().loadInterceptor(ModuleManager.instance().getEnabledModules(false)));
        if (interceptor != null)
            ormModel.setOrmInterceptor(interceptor);
        return ormModel;
    }

    @Override
    public void clearCache() {
        OrmModel ormModel = new OrmModelLoader().loadOrmModel(false);
        this.ormModel = new LoadedOrmModel(env, ormModel);
        this.interceptorCache.clear();
    }

    @Override
    public void clearCacheForTenant(String tenantId) {
        clearCache();
    }
}
