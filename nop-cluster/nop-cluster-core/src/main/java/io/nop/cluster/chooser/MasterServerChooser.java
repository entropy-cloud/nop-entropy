/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.chooser;

import io.nop.api.core.exceptions.NopException;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.elector.ILeaderObserver;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static io.nop.cluster.ClusterErrors.ARG_SERVICE_NAME;
import static io.nop.cluster.ClusterErrors.ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE;

public class MasterServerChooser<R> implements IServerChooser<R> {
    private final IDiscoveryClient discoveryClient;
    private final ILeaderObserver leaderObserver;

    /**
     * 可选：类型化错误的 ARG_SERVICE_NAME 参数来源（未设置时回退为 leaderId 留痕）。
     */
    private String serviceName;

    public MasterServerChooser(IDiscoveryClient discoveryClient, ILeaderObserver leaderObserver) {
        this.discoveryClient = discoveryClient;
        this.leaderObserver = leaderObserver;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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
        // 4.2-MA-09（plan 2559 裁定 D）：chooser 无实例即语义失败——两处 return null 改为类型化错误上抛，
        // 不返回 null 让下游猜（chooser 当前无生产装配，抛错不破坏任何调用方）。
        String leaderId = leaderObserver.getLeaderId();
        if (StringHelper.isEmpty(leaderId))
            throw noAvailableServer(leaderId);

        for (ServiceInstance instance : instances) {
            if (instance.getInstanceId().equals(leaderId))
                return instance;
        }
        throw noAvailableServer(leaderId);
    }

    private NopException noAvailableServer(String leaderId) {
        return new NopException(ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE)
                .param(ARG_SERVICE_NAME, serviceName != null ? serviceName
                        : (leaderId == null ? "" : leaderId));
    }
}
