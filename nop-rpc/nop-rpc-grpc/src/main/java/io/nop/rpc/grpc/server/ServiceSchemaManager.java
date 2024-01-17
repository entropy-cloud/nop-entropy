package io.nop.rpc.grpc.server;

import com.google.protobuf.DescriptorProtos;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rpc.grpc.proto.GenericFieldSchema;
import io.nop.rpc.grpc.proto.GenericObjSchema;
import io.nop.rpc.grpc.proto.IFieldMarshaller;
import io.nop.rpc.grpc.proto.ProtobufMarshallerHelper;
import io.nop.rpc.grpc.proto.marshaller.EmptyMarshaller;
import io.nop.rpc.grpc.proto.marshaller.GenericMessageMarshaller;
import io.nop.rpc.grpc.status.GrpcStatusMapping;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ServiceSchemaManager {
    static final Logger LOG = LoggerFactory.getLogger(ServiceSchemaManager.class);
    private IGraphQLEngine graphQLEngine;

    private GrpcStatusMapping statusMapping;

    private final ResourceLoadingCache<ServerServiceDefinition> serviceCache =
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
        return serviceCache.get(bizObjName);
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
        MethodDescriptor.Builder<S, R> builder = MethodDescriptor.<S, R>newBuilder().setFullMethodName(methodName);
        builder.setType(MethodDescriptor.MethodType.UNARY);
        builder.setSafe(fieldDef.getOperationType() == GraphQLOperationType.query);

        String requestName = getRequestName(methodName, fieldDef);
        String responseName = getResponseName(methodName, fieldDef);

        GenericObjSchema requestSchema = buildRequestSchema(requestName, fieldDef.getArguments());
        GenericObjSchema responseSchema = buildResponseSchema(responseName, fieldDef.getType());

        builder.setRequestMarshaller((MethodDescriptor.Marshaller<S>) buildSchemaMarshaller(requestSchema));
        builder.setResponseMarshaller((MethodDescriptor.Marshaller<R>) buildSchemaMarshaller(responseSchema));
        return builder.build();
    }

    private String getRequestName(String methodName, GraphQLFieldDefinition fieldDef) {
        return methodName + "_request";
    }

    private String getResponseName(String methodName, GraphQLFieldDefinition fieldDef) {
        GraphQLType type = fieldDef.getType();
        if (type.isScalarType() || type.isListType())
            return methodName + "_response";

        String name = type.getNamedTypeName();
        if (name.startsWith("g_")) {
            return StringHelper.lastPart(name, '_');
        }
        return name;
    }

    private MethodDescriptor.Marshaller<?> buildSchemaMarshaller(GenericObjSchema schema) {
        if (schema == null)
            return EmptyMarshaller.INSTANCE;
        return new GenericMessageMarshaller(schema);
    }

    private GenericObjSchema buildRequestSchema(String requestName, List<GraphQLArgumentDefinition> args) {
        if (args == null || args.isEmpty())
            return null;

        GenericObjSchema objSchema = new GenericObjSchema();
        objSchema.setName(requestName);
        List<GenericFieldSchema> fields = new ArrayList<>(args.size());
        int index = 0;
        for (GraphQLArgumentDefinition arg : args) {
            DescriptorProtos.FieldDescriptorProto.Label label = buildLabel(arg.getType(), true);
            IFieldMarshaller fieldMarshaller = buildTypeMarshaller(arg.getType(), true);
            GenericFieldSchema field = new GenericFieldSchema(++index, arg.getName(), label, fieldMarshaller);
            fields.add(field);
        }
        objSchema.setFieldList(fields);
        return objSchema;
    }

    private GenericObjSchema buildResponseSchema(String responseName, GraphQLType type) {
        if (type == null)
            return null;
        IFieldMarshaller marshaller = buildTypeMarshaller(type, false);
        if (type.isScalarType()) {
            GenericObjSchema objSchema = new GenericObjSchema();
            objSchema.setName(responseName);
            GenericFieldSchema fieldSchema = new GenericFieldSchema(1, "value",
                    DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL, marshaller);
            objSchema.setFieldList(Collections.singletonList(fieldSchema));
            return objSchema;
        } else if (type.isListType()) {
            GenericObjSchema objSchema = new GenericObjSchema();
            objSchema.setName(responseName);
            GenericFieldSchema fieldSchema = new GenericFieldSchema(1, "value",
                    DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED, marshaller);
            objSchema.setFieldList(Collections.singletonList(fieldSchema));
            return objSchema;
        } else {
            return (GenericObjSchema) marshaller;
        }
    }

    private IFieldMarshaller buildTypeMarshaller(GraphQLType type, boolean allowRequired) {
        if (type.isListType()) {
            GraphQLType itemType = type.getItemType().getNullableType();
            return buildItemTypeMarshaller(itemType, allowRequired);
        } else {
            return buildItemTypeMarshaller(type, allowRequired);
        }
    }

    private IFieldMarshaller buildItemTypeMarshaller(GraphQLType type, boolean allowRequired) {
        if (type.isScalarType()) {
            StdDataType dataType = type.getStdDataType();
            return ProtobufMarshallerHelper.getMarshallerForType(dataType);
        } else {
            // handle non-scalar types
            GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) graphQLEngine.getSchemaLoader().resolveTypeDefinition(type);
            GenericObjSchema objSchema = (GenericObjSchema) objDef.getGrpcSchema();
            if (objSchema == null) {
                objSchema = buildObjSchema(objDef, allowRequired);
                objDef.setGrpcSchema(objSchema);
            }
            return objSchema;
        }
    }

    private GenericObjSchema buildObjSchema(GraphQLObjectDefinition objDef, boolean allowRequired) {
        GenericObjSchema objSchema = new GenericObjSchema();
        List<GenericFieldSchema> fieldList = new ArrayList<>(objDef.getFields().size());

        for (GraphQLFieldDefinition fieldDef : objDef.getFields()) {
            int propId = fieldDef.getPropId();
            if (propId <= 0) {
                LOG.debug("nop.ignore-field-no-propId:objType={},prop={}", objDef.getName(), fieldDef.getName());
                continue;
            }

            GenericFieldSchema fieldSchema = new GenericFieldSchema(propId, fieldDef.getName(),
                    buildLabel(fieldDef.getType(), allowRequired),
                    buildTypeMarshaller(fieldDef.getType(), allowRequired));
            fieldList.add(fieldSchema);
        }
        objSchema.setFieldList(fieldList);
        return objSchema;
    }

    private DescriptorProtos.FieldDescriptorProto.Label buildLabel(GraphQLType type, boolean allowRequired) {
        if (type.isListType()) {
            return DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL;
        } else if (allowRequired && type.isNonNullType()) {
            return DescriptorProtos.FieldDescriptorProto.Label.LABEL_REQUIRED;
        } else {
            return DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL;
        }
    }

    private <S, R> ServerCallHandler<S, R> buildServerCall(GraphQLFieldDefinition fieldDef) {
        return new GraphQLServerCallHandler<>(graphQLEngine, statusMapping, fieldDef);
    }
}
