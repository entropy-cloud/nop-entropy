package io.nop.api.core.rpc;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

/**
 * RPC 服务调用器接口。
 * <p>
 * 返回的 {@link ApiResponse} 保证不为 null，调用方可直接使用。
 * 如果调用失败，会通过 {@link CompletionStage#toCompletableFuture()} 抛出异常。
 */
public interface IRpcServiceInvoker {
    /**
     * 异步执行 RPC 调用
     *
     * @param serviceName   服务名
     * @param serviceMethod 服务方法名
     * @param request       请求对象，不为 null
     * @param cancelToken   取消令牌，可为 null
     * @return 返回的 ApiResponse 保证不为 null
     */
    CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                ApiRequest<?> request, ICancelToken cancelToken);

    default IRpcService toRpcService(String serviceName) {
        return (serviceMethod, request, cancelToken) -> invokeAsync(serviceName, serviceMethod, request, cancelToken);
    }

    default IRpcCall toRpcCall(String serviceName, String serviceMethod) {
        return (request, cancelToken) -> invokeAsync(serviceName, serviceMethod, request, cancelToken);
    }
}
