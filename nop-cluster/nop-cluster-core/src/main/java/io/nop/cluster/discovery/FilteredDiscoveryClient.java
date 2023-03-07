/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.discovery;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 对服务发现机制返回的服务实例列表进行再次过滤。
 */
public class FilteredDiscoveryClient implements IDiscoveryClient {
    private final IDiscoveryClient baseClient;
    private final List<IServiceInstanceFilter> filters;

    public FilteredDiscoveryClient(IDiscoveryClient baseClient, List<IServiceInstanceFilter> filters) {
        this.baseClient = baseClient;
        this.filters = filters;
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        List<ServiceInstance> ret = baseClient.getInstances(serviceName);
        return filterInstances(ret);
    }

    @Override
    public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceName) {
        return baseClient.getInstancesAsync(serviceName).thenApply(this::filterInstances);
    }

    private List<ServiceInstance> filterInstances(List<ServiceInstance> instances) {
        if (instances.isEmpty())
            return instances;

        for (IServiceInstanceFilter filter : filters) {
            if (!filter.isEnabled())
                continue;
            instances = filter.filter(instances);
        }
        return instances;
    }

    @Override
    public List<String> getServices() {
        return baseClient.getServices();
    }
}
