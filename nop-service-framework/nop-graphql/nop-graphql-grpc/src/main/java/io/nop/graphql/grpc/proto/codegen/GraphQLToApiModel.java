/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.grpc.proto.codegen;

import io.nop.commons.type.BinaryScalarType;
import io.nop.commons.type.StdDataType;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.GenericRawTypeReferenceImpl;
import io.nop.core.type.utils.JavaGenericTypeBuilder;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLInputDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.ast.IGraphQLFieldDefinition;
import io.nop.graphql.core.ast.IGraphQLObjectDefinition;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.graphql.grpc.GrpcConstants;
import io.nop.graphql.grpc.proto.IFieldMarshaller;
import io.nop.graphql.grpc.proto.ProtobufMarshallerHelper;
import io.nop.graphql.grpc.proto.marshaller.EmptyMarshaller;
import io.nop.rpc.model.ApiMessageFieldModel;
import io.nop.rpc.model.ApiMessageModel;
import io.nop.rpc.model.ApiMethodModel;
import io.nop.rpc.model.ApiModel;
import io.nop.rpc.model.ApiServiceModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

import static io.nop.graphql.grpc.GrpcConfigs.CFG_GRAPHQL_API_PACKAGE;
import static io.nop.graphql.grpc.GrpcConfigs.CFG_GRPC_AUTO_INIT_PROP_ID;
import static io.nop.rpc.model.RpcModelConstants.PROTO_TYPE_EMPTY;

public class GraphQLToApiModel {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLToApiModel.class);

    public ApiModel transformToApi(IGraphQLSchemaLoader schemaLoader) {
        ApiModel model = new ApiModel();
        model.setApiPackageName(CFG_GRAPHQL_API_PACKAGE.get());

        model.addImportPath(GrpcConstants.IMPORT_EMPTY_PROTO);
        model.addImportPath(GrpcConstants.IMPORT_ANY_PROTO);

        for (GraphQLTypeDefinition typeDef : schemaLoader.getTypeDefinitions()) {
            addType(model, typeDef);
        }

        for (String bizObjName : schemaLoader.getBizObjNames()) {
            addService(model, bizObjName, schemaLoader.getBizOperationDefinitions(bizObjName).values());
        }
        return model;
    }

    private void addType(ApiModel model, GraphQLTypeDefinition typeDef) {
        if (typeDef instanceof IGraphQLObjectDefinition) {
            IGraphQLObjectDefinition objDef =
                    (IGraphQLObjectDefinition) typeDef;
            ApiMessageModel messageModel = toMessageModel(objDef);
            model.addMessage(messageModel);
        }
    }

    private ApiMessageModel toMessageModel(IGraphQLObjectDefinition objDef) {
        if (CFG_GRPC_AUTO_INIT_PROP_ID.get())
            objDef.initPropId();

        ApiMessageModel messageModel = new ApiMessageModel();
        messageModel.setName(objDef.getName());
        messageModel.setDescription(objDef.getDescription());
        messageModel.setDisplayName(objDef.getDisplayString());

        for (IGraphQLFieldDefinition field : objDef.getFields()) {
            if (field.getPropId() <= 0) {
                LOG.debug("nop.ignore-field-no-propId:objType={},prop={}", objDef.getName(), field.getName());
                continue;
            }
            messageModel.addField(toFieldModel(field, objDef instanceof GraphQLInputDefinition));
        }

        messageModel.getFields().sort(Comparator.comparing(ApiMessageFieldModel::getPropId));
        return messageModel;
    }

    private ApiMessageFieldModel toFieldModel(IGraphQLFieldDefinition field, boolean input) {
        ApiMessageFieldModel fieldModel = new ApiMessageFieldModel();
        fieldModel.setName(field.getName());
        fieldModel.setDisplayName(field.getDisplayString());
        fieldModel.setDescription(field.getDescription());
        // 考虑到selection机制，所有字段都是可选字段
        fieldModel.setMandatory(input ? field.getType().isNonNullType() : false);
        fieldModel.setPropId(field.getPropId());

        setFieldType(fieldModel, field.getType());
        return fieldModel;
    }

    private void setFieldType(ApiMessageFieldModel fieldModel, GraphQLType type) {
        StdDataType dataType = type.getStdDataType();
        if (dataType != null && !type.isListType()) {
            IFieldMarshaller marshaller = ProtobufMarshallerHelper.getMarshallerForType(dataType);
            if (marshaller != null && marshaller != EmptyMarshaller.INSTANCE)
                fieldModel.setBinaryScalarType(marshaller.getBinaryScalarType());
        } else {
            GraphQLType itemType;
            boolean repeated;
            if (type.isListType()) {
                itemType = type.getItemType();
                repeated = true;
            } else {
                itemType = type;
                repeated = false;
            }
            if (itemType.isScalarType()) {
                IFieldMarshaller marshaller = ProtobufMarshallerHelper.getMarshallerForType(itemType.getStdDataType());
                fieldModel.setBinaryScalarType(marshaller.getBinaryScalarType());
                if (repeated) {
                    IGenericType javaType = ReflectionManager.instance().buildRawType(marshaller.getBinaryScalarType().toStdDataType().getJavaClass());
                    javaType = JavaGenericTypeBuilder.buildListType(javaType);
                    fieldModel.setType(javaType);
                }
            } else if (itemType.isEnumType()) {
                fieldModel.setBinaryScalarType(BinaryScalarType.INT32);
                if (repeated) {
                    fieldModel.setType(JavaGenericTypeBuilder.buildListType(PredefinedGenericTypes.INT_TYPE));
                }
            } else {
                String namedType = itemType.getNamedTypeName();
                IGenericType javaType = new GenericRawTypeReferenceImpl(namedType);
                if (repeated)
                    javaType = JavaGenericTypeBuilder.buildListType(javaType);
                fieldModel.setType(javaType);
            }
        }
    }

    private void addService(ApiModel model, String bizObjName, Collection<GraphQLFieldDefinition> operations) {
        ApiServiceModel serviceModel = new ApiServiceModel();
        serviceModel.setName(bizObjName);
        serviceModel.setMethods(operations.stream().map(m -> toMethodModel(m, model)).collect(Collectors.toList()));
        model.addService(serviceModel);
    }

    private ApiMethodModel toMethodModel(GraphQLFieldDefinition field, ApiModel model) {
        ApiMethodModel method = new ApiMethodModel();
        method.setName(field.getName());
        method.setDisplayName(field.getDisplayString());
        method.setDescription(field.getDescription());
        if (field.getArguments().isEmpty()) {
            method.setRequestMessage(PROTO_TYPE_EMPTY);
        } else {
            method.setRequestMessage(field.getOperationName() + "_request");

            if (model.getMessage(method.getRequestMessage()) == null)
                addRequestMessage(model, field);
        }

        if (field.getType() == null) {
            method.setRequestMessage(PROTO_TYPE_EMPTY);
        } else {
            GraphQLType responseType = field.getType();
            if (responseType != null) {
                if (responseType.isVoidType()) {
                    method.setResponseMessage(PredefinedGenericTypes.VOID_TYPE);
                } else if (responseType.isScalarType() || responseType.isEnumType() || responseType.isListType()) {
                    method.setResponseMessage(new GenericRawTypeReferenceImpl(field.getOperationName() + "_response"));
                    addResponseMessage(model, field);
                } else {
                    method.setResponseMessage(new GenericRawTypeReferenceImpl(responseType.getNamedTypeName()));
                }
            }
        }
        return method;
    }

    void addRequestMessage(ApiModel model, GraphQLFieldDefinition field) {
        ApiMessageModel messageModel = new ApiMessageModel();
        messageModel.setName(field.getOperationName() + "_request");

        field.initArgPropId();

        for (GraphQLArgumentDefinition arg : field.getArguments()) {
            ApiMessageFieldModel propertyModel = new ApiMessageFieldModel();
            propertyModel.setName(arg.getName());
            propertyModel.setDescription(arg.getDescription());
            propertyModel.setMandatory(arg.getType().isNonNullType());
            propertyModel.setPropId(arg.getPropId());

            setFieldType(propertyModel, arg.getType());
            messageModel.addField(propertyModel);
        }
        model.addMessage(messageModel);
    }

    void addResponseMessage(ApiModel model, GraphQLFieldDefinition field) {
        ApiMessageModel messageModel = new ApiMessageModel();
        messageModel.setName(field.getOperationName() + "_response");

        ApiMessageFieldModel propertyModel = new ApiMessageFieldModel();
        propertyModel.setName("value");
        propertyModel.setMandatory(false);
        propertyModel.setPropId(1);

        setFieldType(propertyModel, field.getType());
        messageModel.addField(propertyModel);
        model.addMessage(messageModel);
    }
}
