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