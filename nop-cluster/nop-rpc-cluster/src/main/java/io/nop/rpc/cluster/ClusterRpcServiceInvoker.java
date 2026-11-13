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
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.lb.ILoadBalanceAdapter;
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
    private ILoadBalanceAdapter<ServiceInstance> loadBalanceAdapter;

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

    /**
     * 4.2-C8-001（plan 2559 裁定 G(c) 装配）：与 ClusterRpcProxyFactoryBean 同型注入活跃计数 adapter
     * （newBaseRpcService 构造 ClusterRpcClient 时透传 begin/end 挂接点）。
     */
    @Inject
    public void setLoadBalanceAdapter(ILoadBalanceAdapter<ServiceInstance> loadBalanceAdapter) {
        this.loadBalanceAdapter = loadBalanceAdapter;
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
            // 4.2-C4-003（plan 2498 Phase 6，设计 pool-facility-hardening §3.1.2 矩阵 D3 行）：
            // 裸 globalTimer 会使轮询任务体（全量请求体序列化等同步前缀）在 timer 线程执行——
            // 经 executeOn(globalWorker) 卸载（与 AbstractPollingLeaderElector/AutoRegistration C 类先例同口径），
            // timer 只承载触发，Bind 载体继承 R1-01 绝缘覆盖
            timer = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());

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
        rpcService.setLoadBalanceAdapter(loadBalanceAdapter);
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