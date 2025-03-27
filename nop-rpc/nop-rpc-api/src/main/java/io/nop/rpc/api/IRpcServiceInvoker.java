package io.nop.rpc.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

public interface IRpcServiceInvoker {
    CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                ApiRequest<?> request, ICancelToken cancelToken);
}
