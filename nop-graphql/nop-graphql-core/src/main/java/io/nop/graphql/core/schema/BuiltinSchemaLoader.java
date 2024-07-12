/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.schema;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.ast.GraphQLASTVisitor;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLDirectiveDefinition;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLEnumDefinition;
import io.nop.graphql.core.ast.GraphQLEnumValueDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLInputDefinition;
import io.nop.graphql.core.ast.GraphQLInputFieldDefinition;
import io.nop.graphql.core.ast.GraphQLListType;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLScalarDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.ast.GraphQLUnionTypeDefinition;
import io.nop.graphql.core.parse.GraphQLDocumentParser;
import io.nop.graphql.core.schema.introspection.__Directive;
import io.nop.graphql.core.schema.introspection.__EnumValue;
import io.nop.graphql.core.schema.introspection.__Field;
import io.nop.graphql.core.schema.introspection.__InputValue;
import io.nop.graphql.core.schema.introspection.__Schema;
import io.nop.graphql.core.schema.introspection.__Type;
import io.nop.graphql.core.schema.introspection.__TypeKind;
import io.nop.graphql.core.schema.utils.GraphQLSourcePrinter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_BUILTIN_SCHEMA_PATHS;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNDEFINED_TYPE;

public class BuiltinSchemaLoader {
    private final IGraphQLSchemaLoader schemaLoader;
    private final boolean introspection;

    private GraphQLSchema schema;

    public BuiltinSchemaLoader(IGraphQLSchemaLoader schemaLoader, boolean introspection) {
        this.schemaLoader = schemaLoader;
        this.introspection = introspection;
    }

    public GraphQLSchema load() {
        this.schema = new GraphQLSchema();
        loadDefinitions("/nop/graphql/default.graphql", schema);

        if (introspection) {
            loadDefinitions("/nop/graphql/introspection.graphql", schema);
        }

        loadDefinitions("/nop/graphql/base.graphql", schema);

        Set<String> paths = ConvertHelper.toCsvSet(CFG_GRAPHQL_BUILTIN_SCHEMA_PATHS.get());
        if (paths != null) {
            for (String path : paths) {
                loadDefinitions(path, schema);
            }
        }

        if (introspection) {
            initIntrospection();
        }

        return schema;
    }

    void loadDefinitions(String path, GraphQLSchema schema) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        GraphQLDocument doc = new GraphQLDocumentParser().parseFromResource(resource);
        schema.addDefinitions(doc.getDefinitions());
        new GraphQLASTVisitor() {
            @Override
            public void visitGraphQLNamedType(GraphQLNamedType node) {
                String name = node.getName();
                GraphQLTypeDefinition def = schema.getType(name);
                if (def == null)
                    throw new NopException(ERR_GRAPHQL_UNDEFINED_TYPE).source(node).param(ARG_TYPE_NAME, name);
                node.setResolvedType(def);
            }
        }.visit(doc);
    }

    void initIntrospection() {
        GraphQLObjectDefinition query = schema.getObjectType("Query");
        initQueryFetcher(query);

        GraphQLObjectDefinition __schema = schema.getObjectType(__Schema.class.getSimpleName());

        initSchemaFetcher(__schema);

        GraphQLObjectDefinition __type = schema.getObjectType(__Type.class.getSimpleName());
        initTypeFetcher(__type);
    }

    void initQueryFetcher(GraphQLObjectDefinition query) {
        setFetcher(query, "__schema", env -> new __Schema());
        setFetcher(query, "__type", this::fetchType);
    }

    void initSchemaFetcher(GraphQLObjectDefinition schema) {
        setFetcher(schema, "types", this::fetchTypes);
        setFetcher(schema, "queryType", this::fetchQueryType);
        setFetcher(schema, "mutationType", this::fetchMutationType);
        setFetcher(schema, "subscriptionType", this::fetchSubscriptionType);
        setFetcher(schema, "directives", this::fetchDirectives);
    }

    void initTypeFetcher(GraphQLObjectDefinition type) {
        setFetcher(type, "fields", this::fetchFields);
        setFetcher(type, "inputFields", this::fetchInputFields);
        setFetcher(type, "enumValues", this::fetchEnumValues);
        setFetcher(type, "interfaces", this::fetchInterfaces);
        setFetcher(type, "possibleTypes", this::fetchPossibleTypes);
    }

    List<__Type> fetchInterfaces(IDataFetchingEnvironment env) {
        __Type type = (__Type) env.getSource();
        if (type.getKind() == __TypeKind.OBJECT) {
            // 不允许为null指针
            return Collections.emptyList();
        }
        return type.getInterfaces();
    }

    __Type fetchType(IDataFetchingEnvironment env) {
        String name = (String) env.getArg("name");
        GraphQLTypeDefinition type = getTypeDefinition(name);
        if (type == null)
            throw new NopException(ERR_GRAPHQL_UNDEFINED_TYPE).param(ARG_TYPE_NAME, name);
        return toType(type);
    }

    List<__Type> fetchTypes(IDataFetchingEnvironment env) {
        Map<String, GraphQLTypeDefinition> types = new TreeMap<>(schema.getTypes());

        Collection<GraphQLFieldDefinition> operations = schemaLoader
                .getOperationDefinitions(GraphQLOperationType.subscription);
        if (operations.isEmpty())
            types.remove("Subscription");

        for (GraphQLTypeDefinition type : schemaLoader.getTypeDefinitions()) {
            types.put(type.getName(), type);
        }

        List<__Type> ret = new ArrayList<>();
        for (GraphQLTypeDefinition objDef : types.values()) {
            if (objDef.getName().startsWith("__"))
                continue;
            ret.add(toType(objDef));
        }
        return ret;
    }

    __Type toType(GraphQLTypeDefinition def) {
        __Type ret = new __Type();
        ret.setName(def.getName());
        ret.setDescription(resolveDescription(def.getDescription()));

        if (def instanceof GraphQLScalarDefinition) {
            ret.setKind(__TypeKind.SCALAR);
        } else if (def instanceof GraphQLUnionTypeDefinition) {
            ret.setKind(__TypeKind.UNION);
        } else if (def instanceof GraphQLEnumDefinition) {
            ret.setKind(__TypeKind.ENUM);
        } else if (def instanceof GraphQLInputDefinition) {
            ret.setKind(__TypeKind.INPUT_OBJECT);
        } else {
            ret.setKind(__TypeKind.OBJECT);
        }
        return ret;
    }

    __Type fetchQueryType(IDataFetchingEnvironment env) {
        return fetchType(GraphQLOperationType.query);
    }

    __Type fetchMutationType(IDataFetchingEnvironment env) {
        return fetchType(GraphQLOperationType.mutation);
    }

    __Type fetchSubscriptionType(IDataFetchingEnvironment env) {
        Collection<GraphQLFieldDefinition> operations = schemaLoader
                .getOperationDefinitions(GraphQLOperationType.subscription);
        if (operations.isEmpty())
            return null;
        return fetchType(GraphQLOperationType.subscription);
    }

    __Type fetchType(GraphQLOperationType opType) {
        __Type ret = new __Type();
        ret.setKind(__TypeKind.OBJECT);
        ret.setName(opType.getTypeName());
        return ret;
    }

    List<__Field> toOperations(Collection<GraphQLFieldDefinition> fieldDefs) {
        List<__Field> ret = new ArrayList<>(fieldDefs.size());
        for (GraphQLFieldDefinition fieldDef : fieldDefs) {
            __Field field = toField(toGraphQLType(fieldDef.getType(), false), fieldDef);
            field.setName(fieldDef.getOperationName());
            ret.add(field);
        }
        return ret;
    }

    List<__Field> toFields(Collection<GraphQLFieldDefinition> fieldDefs) {
        List<__Field> ret = new ArrayList<>(fieldDefs.size());
        for (GraphQLFieldDefinition fieldDef : fieldDefs) {
            ret.add(toField(toGraphQLType(fieldDef.getType(), false), fieldDef));
        }
        return ret;
    }

    List<__InputValue> toInputFields(Collection<GraphQLInputFieldDefinition> fieldDefs) {
        List<__InputValue> ret = new ArrayList<>(fieldDefs.size());
        for (GraphQLInputFieldDefinition fieldDef : fieldDefs) {
            ret.add(toInputField(toGraphQLType(fieldDef.getType(), true), fieldDef));
        }
        return ret;
    }

    __Field toField(__Type type, GraphQLFieldDefinition fieldDef) {
        __Field field = new __Field();
        field.setName(fieldDef.getName());
        field.setDescription(resolveDescription(fieldDef.getDescription()));
        field.setType(type);
        field.setArgs(toArgs(fieldDef));
        return field;
    }

    __InputValue toInputField(__Type type, GraphQLInputFieldDefinition fieldDef) {
        __InputValue field = new __InputValue();
        field.setName(fieldDef.getName());
        field.setDescription(resolveDescription(fieldDef.getDescription()));
        field.setType(type);
        return field;
    }


    List<__InputValue> toArgs(GraphQLFieldDefinition fieldDef) {
        if (fieldDef.getArguments() == null || fieldDef.getArguments().isEmpty())
            return Collections.emptyList();

        List<__InputValue> ret = new ArrayList<>(fieldDef.getArguments().size());
        for (GraphQLArgumentDefinition argDef : fieldDef.getArguments()) {
            ret.add(toArg(argDef));
        }
        return ret;
    }

    __InputValue toArg(GraphQLArgumentDefinition argDef) {
        __InputValue ret = new __InputValue();
        __Type type = toGraphQLType(argDef.getType(), true);
        // 暂时不支持复杂input对象格式，全部转换为Map
//        if (type.getKind() == __TypeKind.OBJECT) {
//            type.setKind(__TypeKind.SCALAR);
//            type.setName(GraphQLScalarType.Map.name());
//        }
        ret.setType(type);
        ret.setName(argDef.getName());
        ret.setDescription(resolveDescription(argDef.getDescription()));
        if (argDef.getDefaultValue() != null) {
            ret.setDefaultValue(new GraphQLSourcePrinter().print(argDef.getDefaultValue()));
        }
        return ret;
    }

    List<__Directive> fetchDirectives(IDataFetchingEnvironment env) {
        List<__Directive> ret = new ArrayList<>();
        Map<String, GraphQLDirectiveDefinition> directives = new TreeMap<>(schema.getDirectives());
        for (GraphQLDirectiveDefinition def : directives.values()) {
            ret.add(toDirective(def));
        }
        return ret;
    }

    __Directive toDirective(GraphQLDirectiveDefinition def) {
        __Directive ret = new __Directive();
        ret.setName(def.getName());
        ret.setDescription(def.getDescription());
        ret.setIsRepeatable(def.getRepeatable());
        ret.setArgs(toArgDefinitions(def.getArguments()));

        if (def.getLocations() != null) {
            ret.setLocations(def.getLocations());
        }
        return ret;
    }

    List<__InputValue> toArgDefinitions(List<GraphQLArgumentDefinition> args) {
        if (args == null)
            return Collections.emptyList();

        List<__InputValue> ret = new ArrayList<>(args.size());
        for (GraphQLArgumentDefinition def : args) {
            __InputValue arg = new __InputValue();
            arg.setName(def.getName());
            arg.setType(toGraphQLType(def.getType(), true));
            if (def.getDefaultValue() != null)
                arg.setDefaultValue(new GraphQLSourcePrinter().print(def.getDefaultValue()));
            ret.add(arg);
        }
        return ret;
    }

    __Type toGraphQLType(GraphQLType type, boolean forInput) {
        __Type ret = new __Type();
        if (type.isNonNullType()) {
            ret.setKind(__TypeKind.NON_NULL);
            ret.setOfType(toGraphQLType(type.getNullableType(), forInput));
        } else if (type.isListType()) {
            ret.setKind(__TypeKind.LIST);
            GraphQLListType listType = (GraphQLListType) type;
            ret.setOfType(toGraphQLType(listType.getType(), forInput));
        } else if (type.isScalarType()) {
            ret.setName(((GraphQLNamedType) type).getName());
            ret.setKind(__TypeKind.SCALAR);
        } else if (type.isNamedType()) {
            GraphQLNamedType namedType = (GraphQLNamedType) type;
            GraphQLDefinition def = getTypeDefinition(namedType.getName());
            ret.setName(namedType.getName());
            if (def instanceof GraphQLUnionTypeDefinition) {
                ret.setKind(__TypeKind.UNION);
            } else if (def instanceof GraphQLEnumDefinition) {
                ret.setKind(__TypeKind.ENUM);
            } else if (def instanceof GraphQLInputDefinition) {
                ret.setKind(__TypeKind.INPUT_OBJECT);
            } else {
                ret.setKind(__TypeKind.OBJECT);
//
//                if (forInput) {
//                    ret.setKind(__TypeKind.SCALAR);
//                    ret.setName(GraphQLScalarType.Map.name());
//                }
            }
        } else {
            // not supported
            ret.setKind(__TypeKind.SCALAR);
            ret.setName(GraphQLScalarType.String.name());
        }
        return ret;
    }

    List<__Field> fetchFields(IDataFetchingEnvironment env) {
        __Type type = (__Type) env.getSource();
        if (type.getKind() == __TypeKind.OBJECT) {
            GraphQLTypeDefinition typeDef = getTypeDefinition(type.getName());
            if (typeDef instanceof GraphQLObjectDefinition) {
                GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) typeDef;

                if (type.getName().equals("Query")) {
                    return fetchOperations(objDef, GraphQLOperationType.query);
                } else if (type.getName().equals("Mutation")) {
                    return fetchOperations(objDef, GraphQLOperationType.mutation);
                } else if (type.getName().equals("Subscription")) {
                    return fetchOperations(objDef, GraphQLOperationType.subscription);
                } else {
                    return toFields(objDef.getFields());
                }
            }
        }
        return type.getFields();
    }

    List<__InputValue> fetchInputFields(IDataFetchingEnvironment env) {
        __Type type = (__Type) env.getSource();
        if (type.getKind() == __TypeKind.INPUT_OBJECT) {
            GraphQLInputDefinition objDef = (GraphQLInputDefinition) getTypeDefinition(type.getName());
            return toInputFields(objDef.getFields());
        }
        return type.getInputFields();
    }


    List<__Field> fetchOperations(GraphQLObjectDefinition def, GraphQLOperationType opType) {
        Collection<GraphQLFieldDefinition> operations = schemaLoader.getOperationDefinitions(opType);
        List<__Field> fields = toOperations(operations);
        // List<__Field> builtinFields = toFields(def.getFields());
        // if (!builtinFields.isEmpty()) {
        // fields.addAll(builtinFields);
        // }
        return fields;
    }

    GraphQLTypeDefinition getTypeDefinition(String typeName) {
        GraphQLTypeDefinition type = schema.getType(typeName);
        if (type != null) {
            return type;
        }
        return schemaLoader.getTypeDefinition(typeName);
    }

    List<__Type> fetchPossibleTypes(IDataFetchingEnvironment env) {
        __Type type = (__Type) env.getSource();
        if (type.getKind() == __TypeKind.UNION) {
            List<__Type> ret = new ArrayList<>();
            GraphQLUnionTypeDefinition unionType = (GraphQLUnionTypeDefinition) getTypeDefinition(type.getName());
            for (GraphQLType subType : unionType.getTypes()) {
                ret.add(toGraphQLType(subType, false));
            }
            return ret;
        }
        return type.getPossibleTypes();
    }

    List<__EnumValue> fetchEnumValues(IDataFetchingEnvironment env) {
        __Type type = (__Type) env.getSource();
        if (type.getKind() == __TypeKind.ENUM) {
            List<__EnumValue> ret = new ArrayList<>();
            GraphQLEnumDefinition def = (GraphQLEnumDefinition) getTypeDefinition(type.getName());
            for (GraphQLEnumValueDefinition enumValue : def.getEnumValues()) {
                ret.add(toEnumValue(enumValue));
            }
            return ret;
        }
        return type.getEnumValues();
    }

    __EnumValue toEnumValue(GraphQLEnumValueDefinition valueDef) {
        __EnumValue ret = new __EnumValue();
        ret.setName(valueDef.getName());
        ret.setDescription(resolveDescription(valueDef.getDescription()));
        return ret;
    }

    String resolveDescription(String desc) {
        if (StringHelper.isEmpty(desc))
            return desc;

        return I18nMessageManager.instance().resolveI18nVar(ContextProvider.currentLocale(), desc);
    }

    void setFetcher(GraphQLObjectDefinition objDef, String fieldName, IDataFetcher fetcher) {
        GraphQLFieldDefinition fieldDef = objDef.getField(fieldName);
        fieldDef.setFetcher(fetcher);
    }
}
