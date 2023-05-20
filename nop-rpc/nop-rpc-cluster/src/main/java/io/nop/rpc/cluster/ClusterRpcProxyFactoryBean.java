package io.nop.rpc.cluster;

import io.nop.api.core.beans.ApiRequest;
import io.nop.cluster.chooser.IServerChooser;
import io.nop.rpc.api.IRpcService;
import io.nop.rpc.core.reflect.RpcServiceProxyFactoryBean;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

public class ClusterRpcProxyFactoryBean extends RpcServiceProxyFactoryBean {
    private IServerChooser<ApiRequest<?>> serverChooser;
    private IRpcClientInstanceProvider clientProvider;

    @Inject
    public void setServerChooser(IServerChooser<ApiRequest<?>> serverChooser) {
        this.serverChooser = serverChooser;
    }

    @Inject
    public void setClientProvider(IRpcClientInstanceProvider clientProvider) {
        this.clientProvider = clientProvider;
    }

    @Override
    @PostConstruct
    public void init() {
        ClusterRpcClient rpcService = new ClusterRpcClient(getServiceName(), serverChooser, clientProvider);
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
