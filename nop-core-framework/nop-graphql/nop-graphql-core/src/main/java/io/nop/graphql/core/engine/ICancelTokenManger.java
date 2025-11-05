package io.nop.graphql.core.engine;

import io.nop.api.core.util.ICancellable;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.core.context.IServiceContext;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public interface ICancelTokenManger {
    void register(String reqId, ICancellable cancelToken);

    boolean cancel(String reqId);

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
