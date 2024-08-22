package io.nop.orm.factory;

import io.nop.core.resource.cache.IResourceCacheEntry;
import io.nop.core.resource.cache.ResourceCacheEntry;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.OrmConfigs;
import io.nop.orm.interceptor.XplOrmInterceptor;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.orm.persister.IPersistEnv;

public class DefaultOrmModelHolder implements IOrmModelHolder {
    private final IPersistEnv env;
    private LoadedOrmModel ormModel;

    private IResourceCacheEntry<XplOrmInterceptor> interceptorCache = new ResourceCacheEntry<>("orm-interceptor-cache");

    public DefaultOrmModelHolder(IPersistEnv env) {
        this.env = env;
        clearCache();
    }

    @Override
    public void close() {
        ormModel.close();
    }

    @Override
    public ILoadedOrmModel getOrmModel(IPersistEnv env) {
        IOrmInterceptor interceptor = interceptorCache.getObject(OrmConfigs.CFG_ORM_INTERCEPTOR_CACHE_CHECK_CHANGE.get(),
                cacheName -> new XplOrmInterceptorLoader().loadInterceptor(cacheName, false));
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
