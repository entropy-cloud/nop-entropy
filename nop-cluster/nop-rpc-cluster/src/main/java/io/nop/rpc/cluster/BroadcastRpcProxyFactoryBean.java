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
import io.nop.rpc.core.reflect.RpcServiceProxyFactoryBean;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

public class BroadcastRpcProxyFactoryBean extends RpcServiceProxyFactoryBean {
    private IServerChooser<ApiRequest<?>> serverChooser;
    private IRpcClientInstanceProvider clientProvider;
    private boolean waitAll = true;

    @Inject
    public void setServerChooser(IServerChooser<ApiRequest<?>> serverChooser) {
        this.serverChooser = serverChooser;
    }

    @Inject
    public void setClientProvider(IRpcClientInstanceProvider clientProvider) {
        this.clientProvider = clientProvider;
    }

    public void setWaitAll(boolean waitAll) {
        this.waitAll = waitAll;
    }

    @Override
    @PostConstruct
    public void init() {
        BroadcastRpcClient rpcService = new BroadcastRpcClient(getServiceName());
        rpcService.setClientProvider(clientProvider);
        rpcService.setServerChooser(serverChooser);
        rpcService.setWaitAll(waitAll);
        setRpcService(rpcService);
        super.init();
    }
}
