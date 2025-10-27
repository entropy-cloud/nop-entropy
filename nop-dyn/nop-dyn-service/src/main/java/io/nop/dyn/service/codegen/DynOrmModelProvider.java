package io.nop.dyn.service.codegen;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.module.ModuleManager;
import io.nop.core.module.ModuleModel;
import io.nop.core.resource.cache.CacheEntryManagement;
import io.nop.core.resource.tenant.ResourceTenantManager;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.factory.IOrmModelProvider;
import io.nop.orm.factory.LoadedOrmModel;
import io.nop.orm.factory.XplOrmInterceptorLoader;
import io.nop.orm.interceptor.XplOrmInterceptor;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.lazy.IDynamicEntityModelProvider;
import io.nop.orm.model.lazy.LazyLoadOrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.support.MultiOrmInterceptor;

import java.util.Collection;

import static io.nop.orm.OrmConfigs.CFG_ORM_MODEL_CACHE_CHECK_CHANGE;

public class DynOrmModelProvider implements IOrmModelProvider {
    private final CacheEntryManagement<ILoadedOrmModel> cache;

    private IDynamicEntityModelProvider entityModelLoader;

    public DynOrmModelProvider() {
        this.cache = ResourceTenantManager.instance().makeCacheEntry("dyn-loaded-orm-model-cache", isUseTenant(), null);
        GlobalCacheRegistry.instance().register(cache);
    }

    /**
     * 直接注入存在循环依赖的问题，ioc初始化失败。
     */
    public IDynamicEntityModelProvider getEntityModelLoader(){
        if(entityModelLoader == null)
            entityModelLoader = BeanContainer.getBeanByType(IDynamicEntityModelProvider.class);
        return entityModelLoader;
    }

    @Override
    public void close() {
        cache.clear();
        GlobalCacheRegistry.instance().unregister(cache);
    }

    protected boolean isUseTenant() {
        if (ResourceTenantManager.instance().isEnableTenantResource())
            return true;
        return false;
    }

    @Override
    public ILoadedOrmModel getOrmModel(IPersistEnv env) {
        String tenantId = ContextProvider.currentTenantId();
        // 避免租户模型初始化的时候重入
        if (StringHelper.isEmpty(tenantId) || ResourceTenantManager.isInitializingTenant())
            return ContextProvider.runWithoutTenantId(() -> doGetSharedOrmModel(env));

        return doGetOrmModel(env);
    }

    private ILoadedOrmModel doGetOrmModel(IPersistEnv env) {
        ILoadedOrmModel baseModel = ContextProvider.runWithoutTenantId(() -> doGetSharedOrmModel(env));

        return cache.getObject(CFG_ORM_MODEL_CACHE_CHECK_CHANGE.get(), k -> {
            return loadDynamicOrmModel(baseModel);
        });
    }

    private ILoadedOrmModel loadDynamicOrmModel(ILoadedOrmModel baseModel) {
        Collection<ModuleModel> modules = ModuleManager.instance().getEnabledTenantModules().values();
        XplOrmInterceptor interceptor = new XplOrmInterceptorLoader().loadInterceptor(modules);
        IOrmInterceptor mergedInterceptor = baseModel.getOrmInterceptor();
        if (!interceptor.isEmpty()) {
            if (mergedInterceptor == null) {
                mergedInterceptor = interceptor;
            } else {
                mergedInterceptor = MultiOrmInterceptor.of(mergedInterceptor, interceptor);
            }
        }

        LazyLoadOrmModel lazyModel = new LazyLoadOrmModel(baseModel.getOrmModel(), getEntityModelLoader());
        LoadedOrmModel loadedModel = new LoadedOrmModel(baseModel.getEnv(), lazyModel);
        loadedModel.setOrmInterceptor(mergedInterceptor);
        return loadedModel;
    }

    private ILoadedOrmModel doGetSharedOrmModel(IPersistEnv env) {
        return cache.getObject(CFG_ORM_MODEL_CACHE_CHECK_CHANGE.get(), k -> loadSharedOrmModel(env));
    }

    private LoadedOrmModel loadSharedOrmModel(IPersistEnv env) {
        Collection<ModuleModel> modules = ModuleManager.instance().getEnabledModules(false);
        OrmModel ormModel = new OrmModelLoader().loadOrmModel(modules);
        IOrmInterceptor interceptor = new XplOrmInterceptorLoader().loadInterceptor(modules);
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
