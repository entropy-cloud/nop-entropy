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
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.cluster.chooser.IServerChooser;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.rpc.api.IRpcService;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static io.nop.cluster.ClusterErrors.ARG_SERVICE_NAME;
import static io.nop.cluster.ClusterErrors.ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE;

public class BroadcastRpcClient implements IRpcService {
    private final String serviceName;
    private IRpcClientInstanceProvider clientProvider;
    private boolean waitAll = true;
    private IServerChooser<ApiRequest<?>> serverChooser;

    public BroadcastRpcClient(String serviceName) {
        this.serviceName = serviceName;
    }

    @Inject
    public void setClientProvider(IRpcClientInstanceProvider clientProvider) {
        this.clientProvider = clientProvider;
    }

    @Inject
    public void setServerChooser(IServerChooser<ApiRequest<?>> serverChooser) {
        this.serverChooser = serverChooser;
    }

    public void setWaitAll(boolean waitAll) {
        this.waitAll = waitAll;
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        return serverChooser.getServersAsync(serviceName, request)
                .thenCompose(servers -> callServersAsync(servers, serviceMethod, request, cancelToken));
    }

    private CompletionStage<ApiResponse<?>> callServersAsync(List<ServiceInstance> servers, String serviceMethod,
                                                             ApiRequest<?> request, ICancelToken cancelToken) {
        if (servers.isEmpty())
            return FutureHelper.reject(
                    new NopException(ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE).param(ARG_SERVICE_NAME, serviceName));

        Cancellable cancellable = new Cancellable();
        if (cancelToken != null) {
            cancelToken.appendOnCancel(cancellable::cancel);
        }

        List<CompletionStage<ApiResponse<?>>> futures = new ArrayList<>(servers.size());
        for (ServiceInstance server : servers) {
            CompletionStage<ApiResponse<?>> future = clientProvider.getRpcClientInstance(server)
                    .callAsync(serviceMethod, request, cancellable);
            futures.add(future);
        }

        if (waitAll) {
            return FutureHelper.waitAll(futures).thenApply(r -> buildResponse(futures));
        } else {
            return FutureHelper.waitAnySuccess(futures).thenApply(r -> {
                // 任何一个成功都取消其他调用
                cancellable.cancel();
                return (ApiResponse<?>) r;
            });
        }
    }

    protected ApiResponse<?> buildResponse(List<CompletionStage<ApiResponse<?>>> futures) {
        return FutureHelper.syncGet(futures.get(0));
    }
}