package io.nop.graphql.core.engine;

import io.nop.api.core.util.ICancellable;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.core.context.IServiceContext;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 在途请求的取消令牌登记表。[G5-13-02]归属语义：
 * 带userId的重载用于绑定/校验请求归属；不带userId的
 * {@link #register(String, ICancellable)}登记为无归属（不开放公开取消通道），
 * {@link #cancel(String)}保留为框架内部系统级取消通道。
 */
public interface ICancelTokenManger {
    void register(String reqId, ICancellable cancelToken);

    /**
     * 绑定归属用户的注册。reqId已登记时的语义：
     * 同一token重复注册原样返回；同一用户携同reqId重试为幂等重绑定（保留既有登记，不取消先到请求，
     * 返回既有的token）；不同用户复用同一reqId抛出响亮冲突异常。
     *
     * @return reqId当前实际登记的token（可能是本token，也可能是先到请求的token）
     */
    ICancellable register(String reqId, ICancellable cancelToken, String userId);

    boolean cancel(String reqId);

    /**
     * 带归属校验的取消：仅允许取消userId本人注册的请求；未登录调用、或目标请求由未登录方注册时
     * 显式抛出异常，不做静默处理。
     */
    boolean cancel(String reqId, String userId);

    IAsyncFunctionInvoker wrap(IAsyncFunctionInvoker invoker, IServiceContext ctx);

    default IAsyncFunctionInvoker buildInvoker(IServiceContext ctx) {
        IAsyncFunctionInvoker invoker = new IAsyncFunctionInvoker() {
            @Override
            public <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request) {
                return task.apply(request);
            }
        };
        return wrap(invoker, ctx);
    }
}
