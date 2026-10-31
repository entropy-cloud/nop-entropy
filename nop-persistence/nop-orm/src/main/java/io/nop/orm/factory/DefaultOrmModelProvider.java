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
    // plan 2517 R2-14 裁定②（C1-003）：volatile 发布边——reloadModel（公开运维 API）后旧模型不再无限期可见
    private volatile LoadedOrmModel ormModel;

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
        // plan 2517 R2-14 裁定②：每请求热写降偶发写——引用变化时才写
        if (interceptor != null && ormModel.getOrmInterceptor() != interceptor)
            ormModel.setOrmInterceptor(interceptor);
        return ormModel;
    }

    @Override
    public void clearCache() {
        // plan 2517 R2-14 裁定②：interceptorCache.clear() 先于新模型赋值发布——
        // volatile 写作发布边，混合态单向化（唯一残留窗 = 旧模型 + 新 interceptor，横切回调非结构映射）
        this.interceptorCache.clear();
        OrmModel ormModel = new OrmModelLoader().loadOrmModel(false);
        this.ormModel = new LoadedOrmModel(env, ormModel);
    }

    @Override
    public void clearCacheForTenant(String tenantId) {
        clearCache();
    }
}
