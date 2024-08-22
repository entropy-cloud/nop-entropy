package io.nop.dyn.service.codegen;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.tenant.TenantAwareResourceCacheEntry;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.factory.IOrmModelHolder;
import io.nop.orm.factory.LoadedOrmModel;
import io.nop.orm.factory.XplOrmInterceptorLoader;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.orm.persister.IPersistEnv;

import java.util.function.Supplier;

import static io.nop.orm.OrmConfigs.CFG_ORM_MODEL_CACHE_CHECK_CHANGE;

public class DynOrmModelHolder implements IOrmModelHolder {
    private TenantAwareResourceCacheEntry<LoadedOrmModel> cache =
            new TenantAwareResourceCacheEntry<>("tenant-loaded-orm-model-cache", null);

    static final ThreadLocal<Boolean> s_initializing = new ThreadLocal<>();

    @Override
    public void close() {
        cache.clear();
    }

    public static boolean isInitializing() {
        return Boolean.TRUE.equals(s_initializing.get());
    }

    public static <T> T runInitializeTask(Supplier<T> task) {
        Boolean b = s_initializing.get();
        if (Boolean.TRUE.equals(b)) {
            return task.get();
        } else {
            s_initializing.set(true);
            try {
                return task.get();
            } finally {
                s_initializing.set(false);
            }
        }
    }

    @Override
    public ILoadedOrmModel getOrmModel(IPersistEnv env) {
        // 避免租户模型初始化的时候重入
        if (isInitializing())
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
