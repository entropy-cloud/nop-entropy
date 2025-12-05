package io.nop.graphql.core.schema.meta;

import io.nop.api.core.json.JsonSchema;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLEnumDefinition;
import io.nop.graphql.core.ast.GraphQLListType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.schema.GraphQLScalarType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class GraphQLToJsonSchema {
    public static final GraphQLToJsonSchema INSTANCE = new GraphQLToJsonSchema();

    public JsonSchema argsToJsonSchema(List<GraphQLArgumentDefinition> args) {
        if (args == null || args.isEmpty())
            return null;

        JsonSchema schema = new JsonSchema();
        schema.setType("object");
        Map<String, JsonSchema> properties = new LinkedHashMap<>();
        for (GraphQLArgumentDefinition arg : args) {
            JsonSchema argSchema = typeToJsonSchema(arg.getType());
            argSchema.setDescription(arg.getDescription());
            properties.put(arg.getName(), argSchema);
        }

        schema.setProperties(properties);
        return schema;
    }

    public JsonSchema typeToJsonSchema(GraphQLType type) {
        type = type.getNullableType();

        if (type.isListType()) {
            return fromListType((GraphQLListType) type);
        } else if (type.isScalarType()) {
            return fromScalarType(type.getScalarType());
        } else if (type.isEnumType()) {
            return fromEnumType((GraphQLEnumDefinition) type.getResolvedType());
        } else if (type.isObjectType()) {
            return fromObjType((GraphQLObjectDefinition) type.getResolvedType());
        } else {
            throw new IllegalArgumentException("Unsupported GraphQL type: " + type.getClass().getName());
        }
    }

    JsonSchema fromListType(GraphQLListType type) {
        JsonSchema schema = new JsonSchema();
        schema.setType("array");
        schema.setItems(typeToJsonSchema(type.getType()));
        return schema;
    }

    JsonSchema fromObjType(GraphQLObjectDefinition type) {
        JsonSchema schema = new JsonSchema();
        schema.setType("object");

        Map<String, JsonSchema> properties = new LinkedHashMap<>();
        LinkedHashSet<String> required = new LinkedHashSet<>();

        type.getFields().forEach(field -> {
            JsonSchema fieldSchema = typeToJsonSchema(field.getType());
            properties.put(field.getName(), fieldSchema);

            if (field.getType().isNonNullType()) {
                required.add(field.getName());
            }
        });

        schema.setProperties(properties);
        if (!required.isEmpty()) {
            schema.setRequired(required);
        }

        return schema;
    }

    JsonSchema fromScalarType(GraphQLScalarType type) {
        JsonSchema schema = new JsonSchema();

        switch (type) {
            case Int:
            case Float:
            case Long:
            case Double:
            case BigDecimal:
                schema.setType("number");
                break;
            case String:
                schema.setType("string");
                break;
            case Boolean:
                schema.setType("boolean");
                break;
            case ID:
                schema.setType("string");
                break;
            case Map:
                schema.setType("object");
                break;
            default:
                schema.setType("string");
                break;
        }

        return schema;
    }

    JsonSchema fromEnumType(GraphQLEnumDefinition type) {
        JsonSchema schema = new JsonSchema();
        schema.setType("string");
        schema.setEnum((List) type.getValueList());
        return schema;
    }
}
