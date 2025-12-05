/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.cluster;

import io.nop.api.core.beans.ApiRequest;
import io.nop.cluster.chooser.IServerChooser;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.api.core.rpc.IRpcService;
import io.nop.rpc.core.reflect.RpcServiceProxyFactoryBean;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

public class ClusterRpcProxyFactoryBean extends RpcServiceProxyFactoryBean {
    private IServerChooser<ApiRequest<?>> serverChooser;
    private IRpcClientInstanceProvider clientProvider;
    private IScheduledExecutor timer;

    @Inject
    public void setServerChooser(IServerChooser<ApiRequest<?>> serverChooser) {
        this.serverChooser = serverChooser;
    }

    @Inject
    public void setClientProvider(IRpcClientInstanceProvider clientProvider) {
        this.clientProvider = clientProvider;
    }

    public void setTimer(IScheduledExecutor timer) {
        this.timer = timer;
    }

    @Override
    @PostConstruct
    public void init() {
        if (timer == null)
            timer = GlobalExecutors.globalTimer();

        ClusterRpcClient rpcService = new ClusterRpcClient(getServiceName(), serverChooser, clientProvider, timer);
        rpcService.setRetryCount(getRetryCount());
        setRpcService(rpcService);
        super.init();
    }

    @Override
    public void setRetryCount(int retryCount) {
        super.setRetryCount(retryCount);

        // 动态更新retryCount
        IRpcService service = getRpcService();
        if (service instanceof ClusterRpcClient) {
            ((ClusterRpcClient) service).setRetryCount(retryCount);
        }
    }
}
