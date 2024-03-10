/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.chooser;

import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.lb.ILoadBalance;

import java.util.List;

/**
 * 先利用服务发现机制获取服务提供者的列表，然后再通过负载均衡策略选择得到唯一的一个实例。
 *
 * @param <R>
 */
public class LoadBalanceServerChooser<R> extends ServiceServerChooser<R> implements IServerChooser<R> {
    private ILoadBalance<ServiceInstance, R> loadBalance;

    public LoadBalanceServerChooser(IDiscoveryClient discoveryClient, List<IRequestServiceInstanceFilter<R>> filters,
                                    ILoadBalance<ServiceInstance, R> loadBalance) {
        super(discoveryClient, filters);
        this.loadBalance = loadBalance;
    }

    public LoadBalanceServerChooser() {
    }

    public void setLoadBalance(ILoadBalance<ServiceInstance, R> loadBalance) {
        this.loadBalance = loadBalance;
    }

    public ServiceInstance chooseFromCandidates(List<ServiceInstance> instances, R request) {
        if (instances.isEmpty())
            return null;

        if (instances.size() == 1)
            return instances.get(0);

        return loadBalance.choose(instances, request);
    }
}