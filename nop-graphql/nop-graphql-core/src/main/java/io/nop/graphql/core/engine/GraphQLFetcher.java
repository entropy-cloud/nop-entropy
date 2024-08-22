package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.util.Guard;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.graphql.core.IGraphQLExecutionContext;

import java.util.Map;

public class GraphQLFetcher implements IGraphQLFetcher {
    private final IGraphQLEngine graphQLEngine;

    public GraphQLFetcher(IGraphQLEngine graphQLEngine) {
        this.graphQLEngine = Guard.notNull(graphQLEngine, "graphqlEngine");
    }

    @EvalMethod
    @Override
    public IGraphQLSourceFetcher forSource(IEvalScope scope, Object source) {
        String sourceType = getSourceType(source);
        return forSource(scope, source, sourceType);
    }

    private String getSourceType(Object source) {
        return null;
    }

    @EvalMethod
    @Override
    public IGraphQLSourceFetcher forSource(IEvalScope scope,
                                           Object source, String sourceType) {
        return null;
    }

    protected static class GraphQLSourceFetcher implements IGraphQLSourceFetcher {
        private final IGraphQLEngine graphQLEngine;
        private final IGraphQLExecutionContext context;

        public GraphQLSourceFetcher(IGraphQLEngine graphQLEngine,
                                    IGraphQLExecutionContext context) {
            this.graphQLEngine = graphQLEngine;
            this.context = context;
        }

        @Override
        public Object fetch(String fieldName) {
            return null;
        }

        @Override
        public Map<String, Object> fetchAll(FieldSelectionBean selectionBean) {
            return Map.of();
        }
    }
}
