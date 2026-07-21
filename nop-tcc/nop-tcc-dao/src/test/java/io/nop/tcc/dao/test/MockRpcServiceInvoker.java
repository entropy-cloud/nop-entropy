package io.nop.tcc.dao.test;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class MockRpcServiceInvoker implements IRpcServiceInvoker {

    private ApiResponse<?> response;

    public MockRpcServiceInvoker() {
        this.response = ApiResponse.success(null);
    }

    public void setResponse(ApiResponse<?> response) {
        this.response = response;
    }

    @Override
    public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                       ApiRequest<?> request, ICancelToken cancelToken) {
        return CompletableFuture.completedFuture(response);
    }
}
