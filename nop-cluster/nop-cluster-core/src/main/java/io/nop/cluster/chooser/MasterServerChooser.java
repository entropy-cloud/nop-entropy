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
import io.nop.cluster.elector.ILeaderObserver;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class MasterServerChooser<R> implements IServerChooser<R> {
    private final IDiscoveryClient discoveryClient;
    private final ILeaderObserver leaderObserver;

    public MasterServerChooser(IDiscoveryClient discoveryClient, ILeaderObserver leaderObserver) {
        this.discoveryClient = discoveryClient;
        this.leaderObserver = leaderObserver;
    }

    @Override
    public CompletionStage<List<ServiceInstance>> getServersAsync(String serviceName, R request) {
        return discoveryClient.getInstancesAsync(serviceName).thenApply(ArrayList::new);
    }

    @Override
    public List<ServiceInstance> getServers(String serviceName, R request) {
        return new ArrayList<>(discoveryClient.getInstances(serviceName));
    }

    @Override
    public ServiceInstance chooseFromCandidates(List<ServiceInstance> instances, R request) {
        String leaderId = leaderObserver.getLeaderId();
        if (StringHelper.isEmpty(leaderId))
            return null;

        for (ServiceInstance instance : instances) {
            if (instance.getInstanceId().equals(leaderId))
                return instance;
        }
        return null;
    }
}
