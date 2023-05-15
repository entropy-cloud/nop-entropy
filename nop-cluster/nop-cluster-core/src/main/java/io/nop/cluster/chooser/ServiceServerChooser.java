/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.chooser;

import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class ServiceServerChooser<R> {
    private IDiscoveryClient discoveryClient;
    private List<IRequestServiceInstanceFilter<R>> filters;

    public ServiceServerChooser(IDiscoveryClient discoveryClient, List<IRequestServiceInstanceFilter<R>> filters) {
        this.discoveryClient = discoveryClient;
        this.filters = filters;
    }

    public ServiceServerChooser() {
    }

    @Inject
    public void setDiscoveryClient(IDiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    public void setFilters(List<IRequestServiceInstanceFilter<R>> filters) {
        this.filters = filters;
    }

    public List<ServiceInstance> getServers(String serviceName, R request) {
        List<ServiceInstance> servers = discoveryClient.getInstances(serviceName);
        return filterInstances(servers, request);
    }

    public CompletionStage<List<ServiceInstance>> getServersAsync(String serviceName, R request) {
        return discoveryClient.getInstancesAsync(serviceName).thenApply(servers -> filterInstances(servers, request));
    }

    private List<ServiceInstance> filterInstances(List<ServiceInstance> instances, R request) {
        if (filters == null || filters.isEmpty())
            return instances;

        List<ServiceInstance> ret = new ArrayList<>();
        for (ServiceInstance instance : instances) {
            if (accept(instance, request))
                ret.add(instance);
        }
        return ret;
    }

    private boolean accept(ServiceInstance instance, R request) {
        for (IRequestServiceInstanceFilter<R> filter : filters) {
            if (!filter.isEnabled())
                continue;

            if (!filter.accept(instance, request))
                return false;
        }
        return true;
    }
}
