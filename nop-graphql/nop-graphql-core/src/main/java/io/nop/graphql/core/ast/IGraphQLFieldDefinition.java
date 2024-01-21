package io.nop.graphql.core.ast;

public interface IGraphQLFieldDefinition {
    String getName();

    String getDescription();

    String getDisplayString();

    int getPropId();

    GraphQLType getType();
}
