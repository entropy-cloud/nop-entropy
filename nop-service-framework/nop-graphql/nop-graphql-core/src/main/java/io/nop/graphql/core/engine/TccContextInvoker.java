package io.nop.graphql.core.engine;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.TccContext;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static io.nop.api.core.context.ContextProvider.thenOnContext;
import static io.nop.graphql.core.GraphQLErrors.ARG_TXN_ID;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_TCC_NO_BOUND_CONTEXT;

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

        // C3-008 fail-fast：无线附着场景语义化错误，不再经 setCurrent 的 getOrCreateContext
        // 静默新建并永久附着（无线程附着 = 池线程发起，附着泄漏将使 txnId/branchId 跨任务残留）
        IContext boundContext = ContextProvider.currentContext();
        if (boundContext == null) {
            throw new NopException(ERR_GRAPHQL_TCC_NO_BOUND_CONTEXT)
                    .param(ARG_TXN_ID, tccContext.getTxnId());
        }

        TccContext oldContext = TccContext.getCurrent();
        // setCurrent 返回实际承载 TCC 属性的 IContext 对象（open 守卫后与本线程 currentContext 一致）
        IContext attached = TccContext.setCurrent(tccContext);
        // C3-008：显式捕获 context 对象的泵级挂接（无参 thenOnContext 依赖 getOrCreateContext 的
        // 隐式绑定，跨线程传递不保证绑定）
        return thenOnContext(doInvokeAsync(task, request), attached).whenComplete((ret, err) -> {
            // 恢复逻辑：对捕获的 IContext 对象属性写回，不依赖完成线程的 currentContext——
            // 完成线程可能无线附着或附着其他 context，读点恢复会写错对象
            restore(attached, oldContext, tccContext);
        });
    }

    private void restore(IContext attached, TccContext oldContext, TccContext tccContext) {
        if (attached.isClosed())
            return;
        if (oldContext != null) {
            attached.setAttribute(TccContext.class.getName(), oldContext);
        } else {
            attached.removeAttribute(TccContext.class.getName(), tccContext);
        }
    }

    <R, T> CompletionStage<T> doInvokeAsync(Function<R, CompletionStage<T>> task, R request) {
        if (invoker == null)
            return task.apply(request);
        return invoker.invokeAsync(task, request);
    }
}
