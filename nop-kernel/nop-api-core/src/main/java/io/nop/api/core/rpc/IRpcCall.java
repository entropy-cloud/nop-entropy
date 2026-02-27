package io.nop.api.core.rpc;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

/**
 * RPC 调用接口。
 * <p>
 * 返回的 {@link ApiResponse} 保证不为 null，调用方可直接使用。
 * 如果调用失败，会通过 {@link CompletionStage#toCompletableFuture()} 抛出异常。
 */
public interface IRpcCall {
    /**
     * 异步执行 RPC 调用
     *
     * @param request     请求对象，不为 null
     * @param cancelToken 取消令牌，可为 null
     * @return 返回的 ApiResponse 保证不为 null
     */
    CompletionStage<ApiResponse<?>> callAsync(ApiRequest<?> request, ICancelToken cancelToken);

    /**
     * 同步执行 RPC 调用
     *
     * @param request     请求对象，不为 null
     * @param cancelToken 取消令牌，可为 null
     * @return 返回的 ApiResponse 保证不为 null
     */
    default ApiResponse<?> call(ApiRequest<?> request, ICancelToken cancelToken) {
        return FutureHelper.syncGet(callAsync(request, cancelToken));
    }
}
