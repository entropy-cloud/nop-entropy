package io.nop.api.core.rpc;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

public interface IRpcServiceInvoker extends IRpcServiceLocator {
    CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                ApiRequest<?> request, ICancelToken cancelToken);

    default IRpcService getRpcService(String serviceName) {
        return (serviceMethod, request, cancelToken)
                -> invokeAsync(serviceName, serviceMethod, request, cancelToken);
    }
}
