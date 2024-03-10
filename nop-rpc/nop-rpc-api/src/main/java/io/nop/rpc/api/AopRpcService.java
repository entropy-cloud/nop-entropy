/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class AopRpcService implements IRpcService {
    private final String serviceName;
    private final IRpcService rpcService;
    private final List<IRpcServiceInterceptor> interceptors;
    private final boolean inbound;

    public AopRpcService(String serviceName, IRpcService rpcService,
                         List<IRpcServiceInterceptor> interceptors,
                         boolean inbound) {
        this.serviceName = serviceName;
        this.rpcService = Guard.notNull(rpcService, "rpcService");
        this.interceptors = Guard.notEmpty(interceptors, "interceptors");
        this.inbound = inbound;
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken) {
        return new AopRpcServiceInvocation(newInvocation(serviceMethod, request, cancelToken),
                interceptors).proceedAsync();
    }

    private IRpcServiceInvocation newInvocation(String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken) {
        String service = ApiHeaders.getSvcName(request);
        if (service == null)
            service = this.serviceName;

        DefaultRpcServiceInvocation inv = new DefaultRpcServiceInvocation(
                service, serviceMethod, request, cancelToken, inbound, rpcService);
        return inv;
    }
}
