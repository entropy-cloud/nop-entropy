package io.nop.graphql.core.engine;

import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.TccContext;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static io.nop.api.core.context.ContextProvider.thenOnContext;

public class TccContextInvoker implements IAsyncFunctionInvoker {

    private final IAsyncFunctionInvoker invoker;

    public TccContextInvoker(IAsyncFunctionInvoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request) {
        IServiceContext context = ((IDataFetchingEnvironment) request).getServiceContext();
        TccContext tccContext = TccContext.buildFromServiceContext(context);
        if (tccContext == null)
            return doInvokeAsync(task, request);

        TccContext oldContext = TccContext.getCurrent();
        TccContext.setCurrent(tccContext);
        return thenOnContext(doInvokeAsync(task, request)).whenComplete((ret, err) -> {
            if (oldContext != null) {
                TccContext.setCurrent(oldContext);
            } else {
                TccContext.removeCurrent(tccContext);
            }
        });
    }

    <R, T> CompletionStage<T> doInvokeAsync(Function<R, CompletionStage<T>> task, R request) {
        if (invoker == null)
            return task.apply(request);
        return invoker.invokeAsync(task, request);
    }
}
