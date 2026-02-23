package io.nop.rpc.cluster;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.cluster.chooser.IServerChooser;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.rpc.api.AopRpcService;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static io.nop.rpc.api.RpcErrors.ARG_SERVICE_NAME;
import static io.nop.rpc.api.RpcErrors.ERR_RPC_NOT_ALLOWED_SERVICE_NAME;

public class ClusterRpcServiceInvoker implements IRpcServiceInvoker {
    private IServerChooser<ApiRequest<?>> serverChooser;
    private IRpcClientInstanceProvider clientProvider;
    private IScheduledExecutor timer;
    private int retryCount;
    private List<IRpcServiceInterceptor> interceptors;

    private Set<String> allowedServiceNames;

    public void setInterceptors(List<IRpcServiceInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    @Inject
    public void setServerChooser(IServerChooser<ApiRequest<?>> serverChooser) {
        this.serverChooser = serverChooser;
    }

    @Inject
    public void setClientProvider(IRpcClientInstanceProvider clientProvider) {
        this.clientProvider = clientProvider;
    }

    public void setAllowedServiceNames(Set<String> allowedServiceNames) {
        this.allowedServiceNames = allowedServiceNames;
    }

    public void setTimer(IScheduledExecutor timer) {
        this.timer = timer;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getRetryCount() {
        return retryCount;
    }

    @PostConstruct
    public void init() {
        if (timer == null)
            timer = GlobalExecutors.globalTimer();

        if (this.interceptors == null)
            this.interceptors = Collections.emptyList();
    }

    @Override
    public IRpcService getRpcService(String serviceName) {
        IRpcService rpcService = newBaseRpcService(serviceName);

        if (interceptors.isEmpty())
            return rpcService;

        return new AopRpcService(serviceName, rpcService, interceptors, false);
    }

    protected IRpcService newBaseRpcService(String serviceName) {
        ClusterRpcClient rpcService = new ClusterRpcClient(serviceName, serverChooser, clientProvider, timer);
        rpcService.setRetryCount(getRetryCount());
        return rpcService;
    }

    protected boolean isAllowedService(String serviceName) {
        if (allowedServiceNames == null)
            return false;

        return allowedServiceNames.contains(serviceName) || allowedServiceNames.contains("*");
    }

    @Override
    public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                       ApiRequest<?> request, ICancelToken cancelToken) {
        if (!isAllowedService(serviceName))
            return FutureHelper.reject(new NopException(ERR_RPC_NOT_ALLOWED_SERVICE_NAME)
                    .param(ARG_SERVICE_NAME, serviceName));

        checkRequest(request);
        return getRpcService(serviceName).callAsync(serviceMethod, request, cancelToken);
    }

    protected void checkRequest(ApiRequest<?> request) {

    }
}