package io.nop.tcc.service.test;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 测试用 RPC invoker 桩：nop-tcc-service 测试容器启动需要 IRpcServiceInvoker bean
 * （nopTccEngine 装配必需），本模块无真实 RPC 依赖，沿 nop-tcc-dao 测试桩同款实现。
 */
public class MockRpcServiceInvoker implements IRpcServiceInvoker {

    private ApiResponse<?> response;

    private Throwable exception;

    private ApiRequest<?> lastRequest;

    private boolean hang;

    public MockRpcServiceInvoker() {
        this.response = ApiResponse.success(null);
    }

    public void setResponse(ApiResponse<?> response) {
        this.response = response;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public void setHang(boolean hang) {
        this.hang = hang;
    }

    public ApiRequest<?> getLastRequest() {
        return lastRequest;
    }

    @Override
    public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                       ApiRequest<?> request, ICancelToken cancelToken) {
        this.lastRequest = request;
        if (hang)
            return new CompletableFuture<>();
        if (exception != null)
            return CompletableFuture.failedFuture(exception);
        return CompletableFuture.completedFuture(response);
    }
}
