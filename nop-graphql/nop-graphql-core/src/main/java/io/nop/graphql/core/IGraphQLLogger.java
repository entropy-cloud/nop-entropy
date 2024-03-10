/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;

public interface IGraphQLLogger {
    void onRpcExecute(IGraphQLExecutionContext context, long beginTime,
                      ApiResponse<?> response, Throwable exception);

    void onGraphQLExecute(IGraphQLExecutionContext context, long beginTime,
                          GraphQLResponseBean response, Throwable exception);
}
