package io.nop.core.resource.tenant;

import io.nop.api.core.context.ContextProvider;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.StringHelper;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE;

public class TenantAwareModelReference<T> {
    private final AtomicReference<T> modelRef;
    private ICache<String, AtomicReference<T>> tenantCache;

    public TenantAwareModelReference(String name, BiFunction<String, String, T> creator) {
        this.modelRef = new AtomicReference<>(creator.apply(name, null));
        this.tenantCache = LocalCache.newCache(name,
                newConfig(CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE.get()),
                k -> new AtomicReference<>(creator.apply("tenant-" + name, k)));
    }

    public AtomicReference<T> getModelRef() {
        String tenantId = ContextProvider.currentTenantId();
        if (StringHelper.isEmpty(tenantId))
            return modelRef;
        return tenantCache.get(tenantId);
    }

    public T getModel() {
        return getModelRef().get();
    }

    public void update(T model) {
        getModelRef().set(model);
    }

    public T getStaticModel() {
        return modelRef.get();
    }

    public void updateStaticModel(T model) {
        modelRef.set(model);
    }

    public T getTenantModel(String tenantId) {
        return tenantCache.get(tenantId).get();
    }

    public void clear(){
        modelRef.set(null);
        tenantCache.clear();
    }
}
