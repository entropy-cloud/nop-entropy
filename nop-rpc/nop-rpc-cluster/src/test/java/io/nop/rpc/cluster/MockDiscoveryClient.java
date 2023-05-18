package io.nop.rpc.cluster;

import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MockDiscoveryClient implements IDiscoveryClient {

    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        List<ServiceInstance> ret = new ArrayList<>();
        ServiceInstance server1 = new ServiceInstance();
        server1.setAddr("invalid-domain");
        server1.setServiceName("ServiceA");
        server1.setPort(18080);
        ret.add(server1);

        ServiceInstance server2 = new ServiceInstance();
        server2.setAddr("invalid-domain");
        server2.setServiceName("ServiceA");
        server2.setPort(18081);
        ret.add(server2);

        return ret;
    }

    @Override
    public List<String> getServices() {
        return Arrays.asList("ServiceA", "ServiceB");
    }
}
