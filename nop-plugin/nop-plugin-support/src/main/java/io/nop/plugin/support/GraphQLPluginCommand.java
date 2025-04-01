package io.nop.plugin.support;

import io.nop.api.core.beans.ApiRequest;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.plugin.api.IPluginCancelToken;
import io.nop.plugin.api.IPluginCommand;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public class GraphQLPluginCommand implements IPluginCommand {

    private IGraphQLEngine graphQLEngine;

    public void setGraphQLEngine(IGraphQLEngine graphQLEngine) {
        this.graphQLEngine = graphQLEngine;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                                   String fieldSelection, IPluginCancelToken cancelToken) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(args);
        if (fieldSelection != null)
            request.setSelection(new FieldSelectionBeanParser().parseFromText(null, fieldSelection));

        IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null, command, request);
        if (cancelToken != null)
            cancelToken.appendOnCancel(gqlCtx::cancel);

        return graphQLEngine.executeRpcAsync(gqlCtx).thenApply(res -> (Map<String, Object>) res.get());
    }
}