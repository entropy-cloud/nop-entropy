/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.utils.GraphQLArgsHelper;
import io.nop.graphql.core.reflection.IGraphQLArgsNormalizer;

import java.util.Map;

public class QueryBeanArgsNormalizer implements IGraphQLArgsNormalizer {
    @Override
    public Map<String, Object> normalize(Map<String, Object> args, IGraphQLExecutionContext context) {
        return GraphQLArgsHelper.normalizeQueryArgs(args);
    }
}
