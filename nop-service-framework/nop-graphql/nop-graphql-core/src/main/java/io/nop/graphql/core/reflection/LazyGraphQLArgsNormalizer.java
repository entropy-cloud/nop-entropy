/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.reflection;

import io.nop.api.core.util.Guard;
import io.nop.graphql.core.IGraphQLExecutionContext;

import java.util.Map;

public class LazyGraphQLArgsNormalizer implements IGraphQLArgsNormalizer {
    private final String beanName;
    private IGraphQLArgsNormalizer normalizer;

    public LazyGraphQLArgsNormalizer(String beanName) {
        this.beanName = Guard.notEmpty(beanName, "beanName");
    }

    @Override
    public Map<String, Object> normalize(Map<String, Object> params, IGraphQLExecutionContext context) {
        if (normalizer == null)
            normalizer = (IGraphQLArgsNormalizer) context.getEvalScope().getBeanProvider().getBean(beanName);
        return normalizer.normalize(params, context);
    }
}
