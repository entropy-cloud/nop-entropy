package io.nop.graphql.core.biz;

import io.nop.core.context.action.IServiceAction;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;

import java.util.Map;

public interface IGraphQLBizObject {
    String getBizObjName();

    String getEntityName();

    Object getExtAttribute(String name);

    boolean isAllowInheritAction(String action);

    Map<String, IServiceAction> getActions();

    Map<String, GraphQLFieldDefinition> getOperations();

    default void addAction(String name, IServiceAction action) {
        getActions().put(name, action);
    }

    default void addOperation(String name, GraphQLFieldDefinition operation) {
        getOperations().put(name, operation);
    }


    GraphQLObjectDefinition getObjectDefinition();
}
