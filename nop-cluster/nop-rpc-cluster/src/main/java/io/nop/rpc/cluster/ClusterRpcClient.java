/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.cluster;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopConnectException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.cluster.chooser.IServerChooser;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.api.core.rpc.IRpcService;
import io.nop.rpc.core.composite.CancellableRpcClient;
import io.nop.rpc.core.composite.PollingRpcClient;
import io.nop.rpc.core.utils.RpcHelper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.IntConsumer;

import static io.nop.cluster.ClusterErrors.ARG_SERVICE_NAME;
import static io.nop.cluster.ClusterErrors.ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE;

/**
 * 利用IServerChooser接口选择集群中的某个服务器，然后向其发送RPC请求
 */
public class ClusterRpcClient implements IRpcService {
    private final String serviceName;
    private final IServerChooser<ApiRequest<?>> serverChooser;
    private final IRpcClientInstanceProvider clientProvider;
    private final IScheduledExecutor timer;

    /**
     * 如果连接不上则自动尝试下一个连接
     */
    private int retryCount;

    public ClusterRpcClient(String serviceName, IServerChooser<ApiRequest<?>> serverChooser,
                            IRpcClientInstanceProvider clientProvider, IScheduledExecutor timer) {
        this.serviceName = serviceName;
        this.clientProvider = clientProvider;
        this.serverChooser = serverChooser;
        this.timer = timer;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        IContext ctx = ContextProvider.currentContext();

        return serverChooser.getServersAsync(serviceName, request).thenCompose(instances -> {
            if (instances.isEmpty()) {
                throw newNoAvailableServerError(serviceName);
            }

            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            IntConsumer fn = new IntConsumer() {
                @Override
                public void accept(int retryTimes) {
                    ServiceInstance instance = serverChooser.chooseFromCandidates(instances, request);
                    getRpcClient(instance, request).callAsync(serviceMethod, request, cancelToken)
                            .whenComplete((ret, err) -> {
                                if (err == null) {
                                    future.complete(ret);
                                } else {
                                    if (retryTimes >= retryCount || (ctx != null && ctx.isCallExpired()) || !isAllowRetry(err)) {
                                        future.completeExceptionally(err);
                                    } else {
                                        if (instances.size() > 1) {
                                            instances.remove(instance);
                                        }
                                        this.accept(retryTimes + 1);
                                    }
                                }
                            });
                }
            };
            fn.accept(0);
            return future;
        });
    }

    @Override
    public ApiResponse<?> call(String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken) {
        List<ServiceInstance> instances = serverChooser.getServers(serviceName, request);
        if (instances.isEmpty()) {
            throw newNoAvailableServerError(serviceName);
        }

        Exception error = null;
        for (int i = 0; i <= retryCount; i++) {
            ServiceInstance instance = serverChooser.chooseFromCandidates(instances, request);
            try {
                return getRpcClient(instance, request).call(serviceMethod, request, cancelToken);
            } catch (Exception e) {
                error = e;

                // 服务调用已经超时则不需要再重试
                if (ContextProvider.isCallExpired())
                    break;

                if (!isAllowRetry(e)) {
                    break;
                }

                if (instances.size() > 1) {
                    // 删除刚才出错的连接，然后重试
                    instances.remove(instance);
                }
            }
        }
        throw NopException.adapt(error);
    }

    protected NopException newNoAvailableServerError(String serviceName) {
        return new NopException(ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE).param(ARG_SERVICE_NAME,
                serviceName);
    }

    protected boolean isAllowRetry(Throwable e) {
        return e instanceof NopConnectException;
    }

    protected IRpcService getRpcClient(ServiceInstance instance, ApiRequest<?> request) {
        IRpcService service = clientProvider.getRpcClientInstance(instance);
        String cancelMethod = RpcHelper.getCancelMethod(request);
        if (cancelMethod != null)
            service = new CancellableRpcClient(service, cancelMethod);

        String pollingMethod = RpcHelper.getPollingMethod(request);
        if (pollingMethod != null) {
            int interval = RpcHelper.getPollInterval(request);
            if (interval > 0) {
                service = new PollingRpcClient(service, pollingMethod, timer,
                        interval, RpcHelper.getMaxPollErrorCount(request));
            }
        }
        return service;
    }
}