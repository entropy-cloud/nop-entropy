package io.nop.graphql.core.audit;

import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class GraphQLAuditOperationInvoker implements IAsyncFunctionInvoker {
    private final IGraphQLAuditer auditer;

    public GraphQLAuditOperationInvoker(IGraphQLAuditer auditer) {
        this.auditer = auditer;
    }

    @Override
    public <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request) {
        IDataFetchingEnvironment env = (IDataFetchingEnvironment) request;
        auditer.beforeOperation(env);
        return task.apply(request).whenComplete((r, e) -> auditer.afterOperation(env, r, e));
    }
}