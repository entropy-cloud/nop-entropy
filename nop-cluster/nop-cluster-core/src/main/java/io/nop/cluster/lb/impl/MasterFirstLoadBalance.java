/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.lb.impl;

import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.elector.ILeaderObserver;
import io.nop.cluster.lb.ILoadBalance;
import io.nop.commons.util.StringHelper;

import jakarta.inject.Inject;
import java.util.List;

public class MasterFirstLoadBalance<R> implements ILoadBalance<ServiceInstance, R> {
    private final ILoadBalance<ServiceInstance, R> loadBalance;
    private ILeaderObserver leaderObserver;

    public MasterFirstLoadBalance(ILoadBalance<ServiceInstance, R> loadBalance) {
        this.loadBalance = loadBalance;
    }

    @Inject
    public void setLeaderObserver(ILeaderObserver leaderObserver) {
        this.leaderObserver = leaderObserver;
    }

    @Override
    public ServiceInstance choose(List<ServiceInstance> candidates, R request) {
        ServiceInstance server = chooseMaster(candidates);
        if (server == null)
            return loadBalance.choose(candidates, request);
        return server;
    }


    ServiceInstance chooseMaster(List<ServiceInstance> instances) {
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
