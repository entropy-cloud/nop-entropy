package io.nop.graphql.core.biz;

import io.nop.graphql.core.schema.TypeRegistry;

public interface IGraphQLSchemaInitializer {
    void initialize(TypeRegistry registry);
}
