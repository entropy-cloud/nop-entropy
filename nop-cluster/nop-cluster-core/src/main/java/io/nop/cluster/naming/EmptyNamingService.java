package io.nop.cluster.naming;

import io.nop.cluster.discovery.ServiceInstance;

import java.util.List;

public class EmptyNamingService implements INamingService {
    @Override
    public void registerInstance(ServiceInstance instance) {

    }

    @Override
    public void unregisterInstance(ServiceInstance instance) {

    }

    @Override
    public List<String> getServices() {
        return List.of();
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        return List.of();
    }
}
