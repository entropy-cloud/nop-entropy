package io.nop.cluster.naming;

import io.nop.api.core.util.FutureHelper;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCache;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class CachingNamingService implements INamingService {
    private final INamingService service;
    private final LocalCache<String, List<ServiceInstance>> cache;

    public CachingNamingService(INamingService service, long cacheTimeout) {
        this.service = service;
        this.cache = LocalCache.newCache("naming-service-cache", CacheConfig.newConfig(1000, cacheTimeout));
    }

    @Override
    public void registerInstance(ServiceInstance instance) {
        service.registerInstance(instance);
    }

    @Override
    public void unregisterInstance(ServiceInstance instance) {
        service.unregisterInstance(instance);
    }

    @Override
    public List<String> getServices() {
        return service.getServices();
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        return cache.computeIfAbsent(serviceName, k -> service.getInstances(serviceName));
    }

    @Override
    public void updateInstance(ServiceInstance instance) {
        service.updateInstance(instance);
    }

    @Override
    public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceName) {
        List<ServiceInstance> instances = cache.get(serviceName);
        if (instances != null)
            return FutureHelper.success(instances);
        return service.getInstancesAsync(serviceName).thenApply(ret -> {
            cache.put(serviceName, ret);
            return ret;
        });
    }

    @Override
    public int order() {
        return service.order();
    }
}