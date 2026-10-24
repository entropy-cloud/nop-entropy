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
            // 4.2-C4-003（plan 2498 Phase 6，设计 pool-facility-hardening §3.1.2 矩阵 D4 行）：
            // 裸 globalTimer 会使轮询任务体（全量请求体序列化等同步前缀）在 timer 线程执行——
            // 经 executeOn(globalWorker) 卸载（与 AbstractPollingLeaderElector/AutoRegistration C 类先例同口径），
            // timer 只承载触发，Bind 载体继承 R1-01 绝缘覆盖
            timer = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());

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
