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

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * 总是返回一个新的List
     */
    private List<ServiceInstance> filterInstances(List<ServiceInstance> instances, R request) {
        if (instances.isEmpty())
            return Collections.emptyList();

        List<ServiceInstance> filtered = filterInstances(instances, request, true);
        if (filtered.isEmpty())
            filtered = filterInstances(instances, request, false);
        return filtered;
    }

    private List<ServiceInstance> filterInstances(List<ServiceInstance> instances, R request, boolean onlyPreferred) {
        if (filters == null || filters.isEmpty())
            return new ArrayList<>(instances);

        List<ServiceInstance> ret = new ArrayList<>(instances);
        for (IRequestServiceInstanceFilter filter : filters) {
            if (ret.isEmpty())
                break;

            if (filter.isEnabled())
                filter.filter(ret, request, onlyPreferred);
        }
        return ret;
    }
}
