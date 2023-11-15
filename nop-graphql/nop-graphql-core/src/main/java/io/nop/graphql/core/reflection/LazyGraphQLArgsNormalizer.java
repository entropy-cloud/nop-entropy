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
