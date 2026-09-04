package io.nop.tcc.dao.test;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class MockRpcServiceInvoker implements IRpcServiceInvoker {

    private ApiResponse<?> response;

    private Throwable exception;

    private ApiRequest<?> lastRequest;

    public MockRpcServiceInvoker() {
        this.response = ApiResponse.success(null);
    }

    public void setResponse(ApiResponse<?> response) {
        this.response = response;
    }

    /**
     * 设置后invokeAsync以异常完成，模拟网络故障/超时等RPC异常路径
     */
    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public ApiRequest<?> getLastRequest() {
        return lastRequest;
    }

    @Override
    public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                       ApiRequest<?> request, ICancelToken cancelToken) {
        this.lastRequest = request;
        if (exception != null)
            return CompletableFuture.failedFuture(exception);
        return CompletableFuture.completedFuture(response);
    }
}
