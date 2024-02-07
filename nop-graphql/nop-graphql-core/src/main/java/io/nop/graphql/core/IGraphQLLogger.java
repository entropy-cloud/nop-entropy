package io.nop.graphql.core;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;

public interface IGraphQLLogger {
    void onRpcExecute(IGraphQLExecutionContext context, long beginTime,
                      ApiResponse<?> response, Throwable exception);

    void onGraphQLExecute(IGraphQLExecutionContext context, long beginTime,
                          GraphQLResponseBean response, Throwable exception);
}
