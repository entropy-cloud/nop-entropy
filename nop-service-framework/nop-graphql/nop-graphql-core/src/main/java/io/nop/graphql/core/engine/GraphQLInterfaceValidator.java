/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLInterfaceDefinition;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.schema.GraphQLSchema;

import java.util.List;
import java.util.Map;

import static io.nop.graphql.core.GraphQLErrors.ARG_EXPECTED_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_INTERFACE_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_OBJ_FIELD_TYPE_MISMATCH_INTERFACE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_OBJ_MISSING_REQUIRED_INTERFACE_FIELD;

public class GraphQLInterfaceValidator {
    public static final GraphQLInterfaceValidator INSTANCE = new GraphQLInterfaceValidator();

    public void validate(GraphQLSchema schema) {
        Map<String, GraphQLTypeDefinition> typeMap = schema.getTypes();

        for (Map.Entry<String, GraphQLTypeDefinition> entry : typeMap.entrySet()) {
            GraphQLTypeDefinition type = entry.getValue();
            if (type instanceof GraphQLObjectDefinition) {
                GraphQLObjectDefinition objType = (GraphQLObjectDefinition) type;
                validateObjectImplementation(schema, objType);
            }
        }
    }

    private void validateObjectImplementation(GraphQLSchema schema, GraphQLObjectDefinition objType) {
        List<GraphQLNamedType> interfaces = objType.getInterfaces();
        if (interfaces == null || interfaces.isEmpty()) {
            return;
        }

        for (GraphQLNamedType interfaceType : interfaces) {
            GraphQLDefinition resolvedType = interfaceType.getResolvedType();
            if (!(resolvedType instanceof GraphQLInterfaceDefinition)) {
                continue;
            }

            GraphQLInterfaceDefinition interfaceDef = (GraphQLInterfaceDefinition) resolvedType;
            validateInterfaceImplementation(schema, objType, interfaceDef);
        }
    }

    private void validateInterfaceImplementation(GraphQLSchema schema, GraphQLObjectDefinition objType,
                                                 GraphQLInterfaceDefinition interfaceDef) {
        String objName = objType.getName();
        String interfaceName = interfaceDef.getName();

        List<GraphQLFieldDefinition> interfaceFields = interfaceDef.getFields();
        if (interfaceFields == null || interfaceFields.isEmpty()) {
            return;
        }

        for (GraphQLFieldDefinition interfaceField : interfaceFields) {
            String fieldName = interfaceField.getName();
            GraphQLFieldDefinition objField = objType.getField(fieldName);

            if (objField == null) {
                throw new NopException(ERR_GRAPHQL_OBJ_MISSING_REQUIRED_INTERFACE_FIELD)
                        .param(ARG_OBJ_NAME, objName)
                        .param(ARG_INTERFACE_NAME, interfaceName)
                        .param(ARG_FIELD_NAME, fieldName)
                        .source(objType);
            }

            validateFieldType(schema, objType, interfaceDef, interfaceField, objField);
        }
    }

    private void validateFieldType(GraphQLSchema schema, GraphQLObjectDefinition objType,
                                   GraphQLInterfaceDefinition interfaceDef,
                                   GraphQLFieldDefinition interfaceField,
                                   GraphQLFieldDefinition objField) {
        GraphQLType expectedType = interfaceField.getType();
        GraphQLType actualType = objField.getType();

        if (expectedType == null || actualType == null) {
            return;
        }

        String expectedTypeName = getTypeName(expectedType);
        String actualTypeName = getTypeName(actualType);

        if (!isTypeCompatible(schema, actualType, expectedType)) {
            throw new NopException(ERR_GRAPHQL_OBJ_FIELD_TYPE_MISMATCH_INTERFACE)
                    .param(ARG_OBJ_NAME, objType.getName())
                    .param(ARG_FIELD_NAME, objField.getName())
                    .param(ARG_INTERFACE_NAME, interfaceDef.getName())
                    .param(ARG_TYPE, actualTypeName)
                    .param(ARG_EXPECTED_TYPE, expectedTypeName)
                    .source(objField);
        }
    }

    private String getTypeName(GraphQLType type) {
        if (type == null) {
            return null;
        }

        if (type.isNamedType()) {
            return ((GraphQLNamedType) type).getName();
        }

        GraphQLType innerType = type.getItemType();
        if (innerType != null) {
            String innerTypeName = getTypeName(innerType);
            if (type.isNonNullType()) {
                return innerTypeName + "!";
            } else if (type.isListType()) {
                return "[" + innerTypeName + "]";
            }
        }

        return type.toString();
    }

    /**
     * Check if the actual type is compatible with the expected type.
     * For output types (return types), object types must be covariant:
     * An object type implementing an interface is compatible with the interface type.
     */
    private boolean isTypeCompatible(GraphQLSchema schema, GraphQLType actualType, GraphQLType expectedType) {
        if (actualType == null || expectedType == null) {
            return false;
        }

        // Non-null wrappers
        boolean actualNonNull = actualType.isNonNullType();
        boolean expectedNonNull = expectedType.isNonNullType();

        if (actualNonNull != expectedNonNull) {
            // Covariance for non-null: a non-null type is compatible with a nullable type
            if (actualNonNull && !expectedNonNull) {
                // Check the inner type
                return isTypeCompatible(schema, actualType.getNullableType(), expectedType);
            } else {
                // Nullable is not compatible with non-null
                return false;
            }
        }

        // Unwrap non-null
        if (actualNonNull) {
            actualType = actualType.getNullableType();
            expectedType = expectedType.getNullableType();
        }

        // List types
        if (actualType.isListType() || expectedType.isListType()) {
            if (!actualType.isListType() || !expectedType.isListType()) {
                return false;
            }
            return isTypeCompatible(schema, actualType.getItemType(), expectedType.getItemType());
        }

        // Named types
        if (actualType.isNamedType() && expectedType.isNamedType()) {
            String actualTypeName = ((GraphQLNamedType) actualType).getName();
            String expectedTypeName = ((GraphQLNamedType) expectedType).getName();

            // Exact type match
            if (actualTypeName.equals(expectedTypeName)) {
                return true;
            }

            // Check if actual type implements expected interface
            GraphQLDefinition resolvedActual = actualType.getResolvedType();
            if (resolvedActual instanceof GraphQLObjectDefinition) {
                GraphQLObjectDefinition actualObjType = (GraphQLObjectDefinition) resolvedActual;
                return implementsInterface(schema, actualObjType, expectedTypeName);
            }
        }

        return false;
    }

    /**
     * Check if an object type implements a given interface (directly or transitively).
     */
    private boolean implementsInterface(GraphQLSchema schema, GraphQLObjectDefinition objType, String interfaceName) {
        List<GraphQLNamedType> interfaces = objType.getInterfaces();
        if (interfaces == null || interfaces.isEmpty()) {
            return false;
        }

        for (GraphQLNamedType interfaceType : interfaces) {
            GraphQLDefinition resolvedInterface = interfaceType.getResolvedType();
            if (!(resolvedInterface instanceof GraphQLInterfaceDefinition)) {
                continue;
            }

            GraphQLInterfaceDefinition interfaceDef = (GraphQLInterfaceDefinition) resolvedInterface;
            if (interfaceDef.getName().equals(interfaceName)) {
                return true;
            }

            // Check transitive interfaces
            if (implementsInterface(schema, interfaceDef, interfaceName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if an interface extends another interface (for transitive interface support).
     */
    private boolean implementsInterface(GraphQLSchema schema, GraphQLInterfaceDefinition interfaceType, String interfaceName) {
        // Future: if interfaces can extend other interfaces, check here
        return false;
    }
}
