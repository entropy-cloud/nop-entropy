/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.reflection;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.graphql.core.IGraphQLExecutionContext;

import java.util.Map;

public class EvalGraphQLArgsNormalizer implements IGraphQLArgsNormalizer {
    private final IEvalFunction function;

    public EvalGraphQLArgsNormalizer(IEvalFunction function) {
        this.function = function;
    }

    @Override
    public Map<String, Object> normalize(Map<String, Object> params, IGraphQLExecutionContext context) {
        return (Map<String, Object>) function.call2(null, params, context, context.getEvalScope());
    }
}