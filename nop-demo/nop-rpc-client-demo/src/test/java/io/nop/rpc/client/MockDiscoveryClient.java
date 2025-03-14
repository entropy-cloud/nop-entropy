package io.nop.rpc.client;

import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;

import java.util.List;

public class MockDiscoveryClient implements IDiscoveryClient {
    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        return List.of();
    }

    @Override
    public List<String> getServices() {
        return List.of();
    }
}
