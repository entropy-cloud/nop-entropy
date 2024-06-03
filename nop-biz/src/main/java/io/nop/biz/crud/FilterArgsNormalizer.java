package io.nop.biz.crud;

import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.reflection.IGraphQLArgsNormalizer;

import java.util.Map;

import static io.nop.graphql.core.utils.GraphQLArgsHelper.normalizeFilterArgs;

public class FilterArgsNormalizer implements IGraphQLArgsNormalizer {
    @Override
    public Map<String, Object> normalize(Map<String, Object> args, IGraphQLExecutionContext context) {
        return normalizeFilterArgs(args);
    }
}
