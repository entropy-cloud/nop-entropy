package io.nop.graphql.core.engine;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.util.ApiHeaders;
import io.nop.commons.functional.IAsyncFunctionInvoker;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.rpc.api.flowcontrol.FlowControlEntry;
import io.nop.rpc.api.flowcontrol.IFlowControlRunner;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class GraphQLFlowControlInvoker implements IAsyncFunctionInvoker {
    private final IFlowControlRunner runner;
    private final IAsyncFunctionInvoker invoker;

    public GraphQLFlowControlInvoker(IFlowControlRunner runner, IAsyncFunctionInvoker invoker) {
        this.runner = runner;
        this.invoker = invoker;
    }

    @Override
    public <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request) {
        FlowControlEntry entry = newFlowEntry(request);
        return runner.runAsync(entry, () -> {
            if (invoker == null)
                return task.apply(request);
            return invoker.invokeAsync(task, request);
        });
    }

    private FlowControlEntry newFlowEntry(Object request) {
        return newFlowEntryForExecution((IGraphQLExecutionContext) request);
    }

    private FlowControlEntry newFlowEntryForExecution(IGraphQLExecutionContext ctx) {
        FlowControlEntry entry = new FlowControlEntry();
        entry.setInBound(true);
        boolean user = IUserContext.get() != null;
        entry.setResourceType(user ? FlowControlEntry.RESOURCE_TYPE_WEB : FlowControlEntry.RESOURCE_TYPE_RPC);
        entry.setResource("/graphql");
        entry.setOrigin(ApiHeaders.getStringHeader(ctx.getRequestHeaders(), ApiConstants.HEADER_APP_ID));
        entry.setBizKey(ApiHeaders.getStringHeader(ctx.getRequestHeaders(), ApiConstants.HEADER_BIZ_KEY));
        return entry;
    }
}
