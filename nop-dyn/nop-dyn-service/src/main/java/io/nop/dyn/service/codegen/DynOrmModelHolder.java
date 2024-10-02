package io.nop.dyn.service.codegen;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.tenant.ResourceTenantManager;
import io.nop.core.resource.tenant.TenantAwareResourceCacheEntry;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.factory.IOrmModelHolder;
import io.nop.orm.factory.LoadedOrmModel;
import io.nop.orm.factory.XplOrmInterceptorLoader;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.orm.persister.IPersistEnv;

import static io.nop.orm.OrmConfigs.CFG_ORM_MODEL_CACHE_CHECK_CHANGE;

public class DynOrmModelHolder implements IOrmModelHolder {
    private TenantAwareResourceCacheEntry<LoadedOrmModel> cache =
            new TenantAwareResourceCacheEntry<>("tenant-loaded-orm-model-cache", null);

    @Override
    public void close() {
        cache.clear();
    }

    @Override
    public ILoadedOrmModel getOrmModel(IPersistEnv env) {
        // 避免租户模型初始化的时候重入
        if (ResourceTenantManager.isInitializingTenant())
            return ContextProvider.runWithoutTenantId(() -> doGetOrmModel(env));
        return doGetOrmModel(env);
    }

    private ILoadedOrmModel doGetOrmModel(IPersistEnv env) {
        return cache.getObject(CFG_ORM_MODEL_CACHE_CHECK_CHANGE.get(), k -> loadOrmModel(k, env));
    }

    private LoadedOrmModel loadOrmModel(String cacheName, IPersistEnv env) {
        String tenantId = ContextProvider.currentTenantId();
        boolean includeTenant = !StringHelper.isEmpty(tenantId);
        OrmModel ormModel = new OrmModelLoader().loadOrmModel(includeTenant);
        IOrmInterceptor interceptor = new XplOrmInterceptorLoader().loadInterceptor(cacheName, includeTenant);
        LoadedOrmModel ret = new LoadedOrmModel(env, ormModel);
        ret.setOrmInterceptor(interceptor);
        return ret;
    }

    @Override
    public void clearCache() {
        cache.clear();
    }

    @Override
    public void clearCacheForTenant(String tenantId) {
        cache.clearForTenant(tenantId);
    }
}
