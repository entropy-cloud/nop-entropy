package io.nop.graphql.core.ast;

import java.util.List;

public interface IGraphQLObjectDefinition {
    String getName();

    List<? extends IGraphQLFieldDefinition> getFields();

    String getDescription();

    String getDisplayString();

    void initPropId();

    Object getGrpcSchema();

    void setGrpcSchema(Object grpcSchema);
}
