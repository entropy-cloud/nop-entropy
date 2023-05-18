/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.cluster;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.cluster.chooser.IServerChooser;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.rpc.api.IRpcService;

import java.util.concurrent.CompletionStage;

import static io.nop.cluster.ClusterErrors.ARG_SERVICE_NAME;
import static io.nop.cluster.ClusterErrors.ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE;

/**
 * 利用IServerChooser接口选择集群中的某个服务器，然后向其发送RPC请求
 */
public class ClusterRpcClient implements IRpcService {
    private final String serviceName;
    private final IServerChooser<ApiRequest<?>> serverChooser;
    private final IRpcClientInstanceProvider clientProvider;

    public ClusterRpcClient(String serviceName, IServerChooser<ApiRequest<?>> serverChooser,
                            IRpcClientInstanceProvider clientProvider) {
        this.serviceName = serviceName;
        this.clientProvider = clientProvider;
        this.serverChooser = serverChooser;
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        return serverChooser.chooseServerAsync(serviceName, request).thenCompose(instance -> {
            if (instance == null) {
                NopException err = new NopException(ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE).param(ARG_SERVICE_NAME,
                        serviceName);
                throw err;
            }
            return clientProvider.getRpcClientInstance(instance).callAsync(serviceMethod, request, cancelToken);
        });
    }

    @Override
    public ApiResponse<?> call(String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken) {
        ServiceInstance instance = serverChooser.chooseServer(serviceName, request);
        if (instance == null) {
            NopException err = new NopException(ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE).param(ARG_SERVICE_NAME,
                    serviceName);
            throw err;
        }
        return clientProvider.getRpcClientInstance(instance).call(serviceMethod, request, cancelToken);
    }
}