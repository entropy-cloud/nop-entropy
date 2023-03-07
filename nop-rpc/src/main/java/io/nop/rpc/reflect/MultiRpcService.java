/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.reflect;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.rpc.RpcErrors.ARG_SERVICE_NAME;
import static io.nop.rpc.RpcErrors.ERR_RPC_MISSING_SERVICE_HEADER;
import static io.nop.rpc.RpcErrors.ERR_RPC_UNKNOWN_SERVICE;

/**
 * 多个服务聚合为一个整体的服务对象。
 */
public class MultiRpcService implements IRpcService {
    /**
     * 从服务名映射得到具体的服务对象
     */
    private final Map<String, IRpcService> services;

    public MultiRpcService(Map<String, IRpcService> services) {
        this.services = Guard.notEmpty(services, "services");
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        String serviceName = ApiHeaders.getSvcName(request);
        if (StringHelper.isEmpty(serviceName))
            return error(request, new NopException(ERR_RPC_MISSING_SERVICE_HEADER));

        IRpcService service = services.get(serviceName);
        if (service == null)
            return error(request, new NopException(ERR_RPC_UNKNOWN_SERVICE).param(ARG_SERVICE_NAME, serviceName));

        return service.callAsync(serviceMethod, request, cancelToken);
    }

    CompletionStage<ApiResponse<?>> error(ApiRequest<?> req, NopException e) {
        ApiResponse<?> res = ErrorMessageManager.instance().buildResponse(req, e);
        return FutureHelper.success(res);
    }
}
