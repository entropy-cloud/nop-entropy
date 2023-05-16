package io.nop.graphql.core.biz;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;

import java.util.function.BiConsumer;

public interface IGraphQLBizInitializer {
    void initialize(GraphQLObjectDefinition objDef, String entityName,
                    BiConsumer<QueryBean, IDataFetchingEnvironment> queryProcessor);
}
