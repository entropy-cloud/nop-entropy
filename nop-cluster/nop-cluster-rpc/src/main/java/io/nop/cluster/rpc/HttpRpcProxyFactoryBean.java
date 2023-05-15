package io.nop.cluster.rpc;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.rpc.IRpcService;
import io.nop.cluster.chooser.IServerChooser;
import io.nop.rpc.reflect.RpcServiceProxyFactoryBean;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

public class HttpRpcProxyFactoryBean extends RpcServiceProxyFactoryBean {
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
        IRpcService rpcService = new ClusterRpcClient(getServiceName(), serverChooser, clientProvider);
        setRpcService(rpcService);
        super.init();
    }
}
