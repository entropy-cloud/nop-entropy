package io.nop.graphql.core.audit;

import io.nop.graphql.core.IDataFetchingEnvironment;

public interface IGraphQLAuditer {
    void beforeOperation(IDataFetchingEnvironment env);

    void afterOperation(IDataFetchingEnvironment env, Object result, Throwable exception);
}
