package io.nop.rpc.grpc.proto.codegen;

import io.nop.commons.type.BinaryScalarType;
import io.nop.commons.type.StdDataType;
import io.nop.core.type.impl.GenericRawTypeReferenceImpl;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.rpc.grpc.GrpcConstants;
import io.nop.rpc.grpc.proto.IFieldMarshaller;
import io.nop.rpc.grpc.proto.ProtobufMarshallerHelper;
import io.nop.rpc.model.ApiMessageFieldModel;
import io.nop.rpc.model.ApiMessageModel;
import io.nop.rpc.model.ApiMethodModel;
import io.nop.rpc.model.ApiModel;
import io.nop.rpc.model.ApiServiceModel;

import java.util.Collection;
import java.util.stream.Collectors;

import static io.nop.rpc.model.RpcModelConstants.PROTO_TYPE_EMPTY;

public class GraphQLToApiModel {
    public ApiModel transformToApi(IGraphQLSchemaLoader schemaLoader) {
        ApiModel model = new ApiModel();
        model.setApiPackageName(GrpcConstants.GRPAHQL_API_PACKAGE_NAME);

        for (GraphQLTypeDefinition typeDef : schemaLoader.getTypeDefinitions()) {
            addType(model, typeDef);
        }

        for (String bizObjName : schemaLoader.getBizObjNames()) {
            addService(model, bizObjName, schemaLoader.getBizOperationDefinitions(bizObjName).values());
        }
        return model;
    }

    private void addType(ApiModel model, GraphQLTypeDefinition typeDef) {
        if (typeDef instanceof GraphQLObjectDefinition) {
            GraphQLObjectDefinition objDef =
                    (GraphQLObjectDefinition) typeDef;
            ApiMessageModel messageModel = toMessageModel(objDef);
            model.addMessage(messageModel);
        }
    }

    private ApiMessageModel toMessageModel(GraphQLObjectDefinition objDef) {
        objDef.initPropId();

        ApiMessageModel messageModel = new ApiMessageModel();
        messageModel.setName(objDef.getName());
        messageModel.setDescription(objDef.getDescription());
        messageModel.setDisplayName(objDef.getDisplayString());

        for (GraphQLFieldDefinition field : objDef.getFields()) {
            messageModel.addField(toFieldModel(field));
        }
        return messageModel;
    }

    private ApiMessageFieldModel toFieldModel(GraphQLFieldDefinition field) {
        ApiMessageFieldModel fieldModel = new ApiMessageFieldModel();
        fieldModel.setName(field.getName());
        fieldModel.setDisplayName(field.getDisplayString());
        fieldModel.setDescription(field.getDescription());
        fieldModel.setMandatory(field.getType().isNonNullType());
        fieldModel.setPropId(field.getPropId());

        StdDataType dataType = field.getType().getStdDataType();
        if (dataType != null) {
            IFieldMarshaller marshaller = ProtobufMarshallerHelper.getMarshallerForType(dataType);
            fieldModel.setBinaryScalarType(marshaller.getBinaryScalarType());
        } else {
            GraphQLType itemType;
            if (field.getType().isListType()) {
                itemType = field.getType().getItemType();
            } else {
                itemType = field.getType();
            }
            if (itemType.isScalarType()) {
                IFieldMarshaller marshaller = ProtobufMarshallerHelper.getMarshallerForType(itemType.getStdDataType());
                fieldModel.setBinaryScalarType(marshaller.getBinaryScalarType());
            } else if (itemType.isEnumType()) {
                fieldModel.setBinaryScalarType(BinaryScalarType.INT32);
            } else {
                String namedType = itemType.getNamedTypeName();
                fieldModel.setType(new GenericRawTypeReferenceImpl(namedType));
            }
        }
        return fieldModel;
    }

    private void addService(ApiModel model, String bizObjName, Collection<GraphQLFieldDefinition> operations) {
        ApiServiceModel serviceModel = new ApiServiceModel();
        serviceModel.setName(bizObjName);
        serviceModel.setMethods(operations.stream().map(this::toMethodModel).collect(Collectors.toList()));
        model.addService(serviceModel);
    }

    private ApiMethodModel toMethodModel(GraphQLFieldDefinition field) {
        ApiMethodModel method = new ApiMethodModel();
        method.setName(field.getName());
        method.setDescription(field.getDisplayString());
        method.setDescription(field.getDescription());
        if (field.getArguments().isEmpty()) {
            method.setRequestMessage(PROTO_TYPE_EMPTY);
        } else {
            method.setRequestMessage(field.getOperationName() + "_request");
        }

        if (field.getType() == null) {
            method.setRequestMessage(PROTO_TYPE_EMPTY);
        } else {
            GraphQLType responseType = field.getType();
            if (responseType.isScalarType() || responseType.isEnumType()) {
                method.setResponseMessage(new GenericRawTypeReferenceImpl(field.getOperationName() + "_response"));
            } else {
                method.setResponseMessage(new GenericRawTypeReferenceImpl(responseType.getNamedTypeName()));
            }
        }
        return method;
    }
}
