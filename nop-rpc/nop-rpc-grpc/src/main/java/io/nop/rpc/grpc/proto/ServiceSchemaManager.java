package io.nop.rpc.grpc.proto;

import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rpc.grpc.status.GrpcStatusMapping;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Set;

public class ServiceSchemaManager {
    private IGraphQLEngine graphQLEngine;

    private GrpcStatusMapping statusMapping;

    private final ResourceLoadingCache<ServerServiceDefinition> cache =
            new ResourceLoadingCache<>("grpc-schema-cache", this::loadServiceDefinition, null);

    @Inject
    public void setGraphQLEngine(IGraphQLEngine graphQLEngine) {
        this.graphQLEngine = graphQLEngine;
    }

    @Inject
    public void setStatusMapping(GrpcStatusMapping statusMapping) {
        this.statusMapping = statusMapping;
    }

    public Set<String> getGraphQLObjectTypes() {
        return graphQLEngine.getSchemaLoader().getBizObjNames();
    }

    public ServerServiceDefinition getServiceDefinition(String bizObjName) {
        return cache.get(bizObjName);
    }

    private ServerServiceDefinition loadServiceDefinition(String bizObjName) {
        Map<String, GraphQLFieldDefinition> operations = graphQLEngine.getSchemaLoader()
                .getBizOperationDefinitions(bizObjName);

        ServerServiceDefinition.Builder builder = ServerServiceDefinition.builder(bizObjName);
        for (Map.Entry<String, GraphQLFieldDefinition> entry : operations.entrySet()) {
            String fieldName = entry.getKey();
            GraphQLFieldDefinition fieldDef = entry.getValue();
            builder.addMethod(buildMethodDescriptor(fieldName, fieldDef), buildServerCall(fieldDef));
        }
        return builder.build();
    }

    private <S, R> MethodDescriptor<S, R> buildMethodDescriptor(String methodName, GraphQLFieldDefinition fieldDef) {
        var builder = MethodDescriptor.newBuilder().setFullMethodName(methodName);

        //builder.setRequestMarshaller();
        //return builder.build();
        return null;
    }

    private <S, R> ServerCallHandler<S, R> buildServerCall(GraphQLFieldDefinition fieldDef) {
        return new GraphQLServerCallHandler<>(graphQLEngine, statusMapping, fieldDef);
    }
}
