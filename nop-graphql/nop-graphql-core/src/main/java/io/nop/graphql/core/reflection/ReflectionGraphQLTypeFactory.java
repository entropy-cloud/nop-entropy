/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.reflection;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.graphql.GraphQLBean;
import io.nop.api.core.annotations.graphql.GraphQLMap;
import io.nop.api.core.annotations.graphql.GraphQLScalar;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.graphql.GraphQLConnection;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.cache.ICache;
import io.nop.core.context.IServiceContext;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.type.IGenericType;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLEnumDefinition;
import io.nop.graphql.core.ast.GraphQLEnumValueDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLInputDefinition;
import io.nop.graphql.core.ast.GraphQLInputFieldDefinition;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.fetcher.BeanPropertyFetcher;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import io.nop.graphql.core.utils.GraphQLTypeHelper;
import io.nop.xlang.xmeta.reflect.ReflectObjMetaParser;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.graphql.core.GraphQLErrors.ARG_ARG_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_METHOD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_METHOD_ARG_TYPE_NOT_OBJ_TYPE;

public class ReflectionGraphQLTypeFactory {
    public static ReflectionGraphQLTypeFactory INSTANCE = new ReflectionGraphQLTypeFactory();

    public void getArgDefinitions(GraphQLFieldDefinition field, IFunctionModel func, TypeRegistry registry) {
        getArgDefinitions(field, func, registry, new HashMap<>());
    }

    protected void getArgDefinitions(GraphQLFieldDefinition field, IFunctionModel func, TypeRegistry registry,
                                     Map<String, GraphQLTypeDefinition> creatingTypes) {
        List<GraphQLArgumentDefinition> argDefs = new ArrayList<>();

        for (IFunctionArgument arg : func.getArgs()) {
            if (arg.getType().isAssignableTo(FieldSelectionBean.class))
                continue;
            if (arg.getType().isAssignableTo(IServiceContext.class))
                continue;
            if (arg.getType().isAssignableTo(ICache.class))
                continue;
            if (arg.getType().isAssignableTo(IUserContext.class))
                continue;

            if (arg.getType().getRawClass() == ApiRequest.class) {
                List<GraphQLArgumentDefinition> args = getArgTypes(func.getName(), arg, arg.getType().getTypeParameters().get(0), registry,
                        creatingTypes);
                field.setArguments(args);
                return;
            } else if (arg.isAnnotationPresent(RequestBean.class)) {
                List<GraphQLArgumentDefinition> args = getArgTypes(func.getName(), arg, arg.getType(), registry, creatingTypes);
                field.setArguments(args);
                return;
            } else if (arg.isAnnotationPresent(Name.class)) {
                GraphQLType type = buildGraphQLType(arg.getType(), null, registry, creatingTypes, true);
                boolean optional = arg.isAnnotationPresent(Optional.class);
                boolean nullable = arg.isAnnotationPresent(Nullable.class);

                boolean mandatory = false;
                if (!optional && !nullable) {
                    type = GraphQLTypeHelper.nonNullType(type);
                    mandatory = true;
                }

                GraphQLArgumentDefinition argDef = new GraphQLArgumentDefinition();
                argDef.setName(arg.getName());
                argDef.setType(type);
                argDef.setMandatory(mandatory);
                Description description = arg.getAnnotation(Description.class);
                if (description != null)
                    argDef.setDescription(description.value());

                argDefs.add(argDef);
            }
        }
        field.setArguments(argDefs);
    }

    private List<GraphQLArgumentDefinition> getArgTypes(String funcName, IFunctionArgument arg, IGenericType type,
                                                        TypeRegistry registry, Map<String, GraphQLTypeDefinition> creatingTypes) {
        GraphQLScalarType scalarType = GraphQLScalarType.fromJavaClass(type.getRawClass());
        if (scalarType != null) {
            throw new NopException(ERR_GRAPHQL_METHOD_ARG_TYPE_NOT_OBJ_TYPE).param(ARG_METHOD_NAME, funcName)
                    .param(ARG_ARG_NAME, arg.getName()).param(ARG_TYPE, arg.getType());
        }

        List<GraphQLArgumentDefinition> argDefs = getArgsFromInputType(type, registry, creatingTypes);
        return argDefs;
    }

    public List<GraphQLArgumentDefinition> getArgsFromInputType(IGenericType type, TypeRegistry registry,
                                                                Map<String, GraphQLTypeDefinition> creatingTypes) {
        List<GraphQLArgumentDefinition> argDefs = new ArrayList<>();
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(type.getRawClass());
        beanModel.forEachSerializableProp(propModel -> {
            String propName = propModel.getName();
            GraphQLType graphqlType = buildGraphQLType(propModel.getType(), null, registry, creatingTypes, true);
            boolean mandatory = isMandatory(propModel);
            if (mandatory || Boolean.FALSE.equals(propModel.getNullable()))
                graphqlType = GraphQLTypeHelper.nonNullType(graphqlType);
            GraphQLArgumentDefinition argDef = buildArgDef(propName, graphqlType);
            argDef.setMandatory(mandatory);
            argDef.setSchema(ReflectObjMetaParser.INSTANCE.parsePropSchema(propModel));

            Description description = propModel.getAnnotation(Description.class);
            if (description != null)
                argDef.setDescription(description.value());
            argDefs.add(argDef);

        });
        return argDefs;
    }

    private boolean isMandatory(IBeanPropertyModel propModel) {
        PropMeta propMeta = propModel.getAnnotation(PropMeta.class);
        if (propMeta != null) {
            return propMeta.mandatory();
        }
        return propModel.getAnnotation(Nonnull.class) != null;
    }

    private GraphQLArgumentDefinition buildArgDef(String name, GraphQLType type) {
        GraphQLArgumentDefinition argDef = new GraphQLArgumentDefinition();
        argDef.setName(name);
        argDef.setType(type);
        return argDef;
    }

    public GraphQLType buildGraphQLType(IGenericType type, String thisObjName, String bizObjName,
                                        TypeRegistry registry, boolean input) {
        if (GraphQLConstants.BIZ_OBJ_NAME_THIS_OBJ.equals(bizObjName))
            bizObjName = thisObjName;
        return buildGraphQLType(type, bizObjName, registry, new HashMap<>(), input);
    }

    public GraphQLType buildGraphQLType(Class<?> type, TypeRegistry registry, boolean input) {
        String bizObjName = getBizObjName(type);
        return buildGraphQLType(ReflectionManager.instance().buildRawType(type), null, bizObjName, registry, input);
    }

    String getBizObjName(Class<?> type) {
        BizObjName bizObjName = type.getAnnotation(BizObjName.class);
        return bizObjName == null ? null : bizObjName.value();
    }

    protected GraphQLType buildGraphQLType(IGenericType type, String bizObjName, TypeRegistry registry,
                                           Map<String, GraphQLTypeDefinition> creatingTypes, boolean input) {
        if (type.isAssignableTo(CompletionStage.class)) {
            type = type.getGenericType(CompletionStage.class).getTypeParameters().get(0);
        }

        if (type.isCollectionLike() || type.isArray()) {
            GraphQLType componentType = buildGraphQLType(type.getComponentType(), bizObjName, registry, creatingTypes, input);
            return GraphQLTypeHelper.listType(componentType);
        }

        if (type.getRawClass() == PageBean.class) {
            IGenericType componentType = type.getTypeParameters().get(0);
            GraphQLType beanType = buildGraphQLType(componentType, bizObjName, registry, creatingTypes, input);
            return buildPageBeanType(beanType, registry, creatingTypes, input);
        }

        if (type.getRawClass() == GraphQLConnection.class) {
            IGenericType componentType = type.getTypeParameters().get(0);
            GraphQLType beanType = buildGraphQLType(componentType, bizObjName, registry, creatingTypes, input);
            return buildConnectionType(beanType, registry, creatingTypes, input);
        }

        if (type.getRawClass() == Object.class) {
            return GraphQLTypeHelper.scalarType(GraphQLScalarType.Any);
        }

        GraphQLScalarType scalarType = GraphQLScalarType.fromJavaClass(type.getRawClass());
        if (scalarType != null)
            return GraphQLTypeHelper.scalarType(scalarType);

        Class<?> clazz = type.getRawClass();

        if (bizObjName == null) {
            bizObjName = getBizObjName(clazz);
        }

        if (clazz.isEnum()) {
            String name = GraphQLNameHelper.getGraphQLTypeName(clazz, input);
            if (GraphQLNameHelper.isBizObject(clazz)) {
                registry.addBizObjClass(bizObjName, clazz);
                return GraphQLTypeHelper.namedType(bizObjName);
            }
            GraphQLDefinition typeDef = buildDef(name, clazz, registry, creatingTypes, input);
            GraphQLNamedType namedType = GraphQLTypeHelper.namedType(name);
            namedType.setResolvedType(typeDef);
            return namedType;
        }

        if (bizObjName != null) {
            registry.addBizObjClass(bizObjName, clazz);
            return GraphQLTypeHelper.namedType(bizObjName);
        }

        if (clazz.isAnnotationPresent(GraphQLMap.class))
            return GraphQLTypeHelper.scalarType(GraphQLScalarType.Map);

        if (clazz.isAnnotationPresent(GraphQLScalar.class))
            return GraphQLTypeHelper.scalarType(GraphQLScalarType.String);

        String name = GraphQLNameHelper.getGraphQLTypeName(clazz, input);
        GraphQLDefinition typeDef = buildDef(name, clazz, registry, creatingTypes, input);

        GraphQLNamedType gqlType = GraphQLTypeHelper.namedType(name);
        gqlType.setResolvedType(typeDef);
        return gqlType;
    }

    public GraphQLObjectDefinition buildDef(Class<?> clazz, TypeRegistry registry) {
        String name = GraphQLNameHelper.getGraphQLTypeName(clazz, false);
        GraphQLObjectDefinition typeDef = (GraphQLObjectDefinition) buildDef(name, clazz, registry, new HashMap<>(), false);
        return typeDef;
    }

    public GraphQLType buildPageBeanType(GraphQLType type, TypeRegistry registry,
                                         Map<String, GraphQLTypeDefinition> creatingTypes, boolean input) {
        String pageBeanTypeName = GraphQLConstants.PAGE_BEAN_PREFIX + type;
        GraphQLTypeDefinition objDef = creatingTypes.get(pageBeanTypeName);
        if (objDef == null) {
            objDef = registry.getType(pageBeanTypeName);
            if (objDef == null) {
                objDef = buildPageBeanType(pageBeanTypeName, type, registry, creatingTypes, input);
                registry.registerType(objDef);
            }
        }
        GraphQLNamedType namedType = GraphQLTypeHelper.namedType(pageBeanTypeName);
        namedType.setResolvedType(objDef);
        return namedType;
    }

    GraphQLObjectDefinition buildPageBeanType(String typeName, GraphQLType type, TypeRegistry registry,
                                              Map<String, GraphQLTypeDefinition> creatingTypes, boolean input) {
        GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) buildDef(GraphQLConstants.PAGE_BEAN, PageBean.class,
                registry, creatingTypes, input);
        objDef = objDef.deepClone();
        creatingTypes.put(typeName, objDef);
        objDef.setName(typeName);
        GraphQLFieldDefinition field = objDef.getField(GraphQLConstants.FIELD_ITEMS);
        field.setType(GraphQLTypeHelper.listType(type));
        field.setFetcher(BeanPropertyFetcher.INSTANCE);
        return objDef;
    }

    public GraphQLType buildConnectionType(GraphQLType type, TypeRegistry registry,
                                           Map<String, GraphQLTypeDefinition> creatingTypes, boolean input) {
        String retTypeName = GraphQLConstants.GRAPHQL_CONNECTION_PREFIX + type;
        GraphQLTypeDefinition objDef = creatingTypes.get(retTypeName);
        if (objDef == null) {
            objDef = registry.getType(retTypeName);
            if (objDef == null) {
                objDef = buildConnectionType(retTypeName, type, registry, creatingTypes, input);
                registry.registerType(objDef);
            }
        }
        GraphQLNamedType namedType = GraphQLTypeHelper.namedType(retTypeName);
        namedType.setResolvedType(objDef);
        return namedType;
    }

    GraphQLObjectDefinition buildConnectionType(String typeName, GraphQLType type, TypeRegistry registry,
                                                Map<String, GraphQLTypeDefinition> creatingTypes, boolean input) {
        GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) buildDef(GraphQLConstants.GRAPHQL_CONNECTION, GraphQLConnection.class,
                registry, creatingTypes, input);
        objDef = objDef.deepClone();
        creatingTypes.put(typeName, objDef);
        objDef.setName(typeName);
        GraphQLFieldDefinition field = objDef.getField(GraphQLConstants.FIELD_ITEMS);
        field.setType(GraphQLTypeHelper.listType(type));
        field.setFetcher(BeanPropertyFetcher.INSTANCE);
        return objDef;
    }

    public GraphQLObjectDefinition buildObjectDefinition(String name, Class<?> clazz, TypeRegistry registry) {
        return (GraphQLObjectDefinition) buildDef(name, clazz, registry, new HashMap<>(), false);
    }

    protected GraphQLTypeDefinition buildDef(String name, Class<?> clazz, TypeRegistry registry,
                                             Map<String, GraphQLTypeDefinition> creatingTypes, boolean input) {
        GraphQLTypeDefinition objDef = creatingTypes.get(name);
        if (objDef != null)
            return objDef;
        objDef = registry.getType(name);
        if (objDef != null)
            return objDef;

        if (clazz.isEnum())
            return buildEnumDef(name, clazz, registry, creatingTypes);

        if (input) {
            return newInputDefinition(name, clazz, registry, creatingTypes);
        }

        GraphQLTypeDefinition def = newObjectDefinition(name, clazz, registry, creatingTypes);
        return def;
    }

    GraphQLInputDefinition newInputDefinition(String name, Class<?> clazz, TypeRegistry registry,
                                              Map<String, GraphQLTypeDefinition> creatingTypes) {
        GraphQLInputDefinition def = new GraphQLInputDefinition();
        def.setName(name);
        creatingTypes.put(name, def);

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        List<GraphQLInputFieldDefinition> fields = new ArrayList<>();

        beanModel.forEachSerializableProp(propModel -> {
            GraphQLInputFieldDefinition field = new GraphQLInputFieldDefinition();
            field.setName(propModel.getName());
            PropMeta propMeta = propModel.getAnnotation(PropMeta.class);
            field.setBeanPropMeta(propMeta);
            field.setJavaType(propModel.getType());

            if (propMeta != null && !propMeta.displayName().isEmpty()) {
                field.setLabel(propMeta.displayName());
            }

            IGenericType type = propModel.getType();
            GraphQLType gqlType = buildGraphQLType(type, propModel.getBizObjName(), registry, creatingTypes, true);
            field.setType(gqlType);
            fields.add(field);
        });
        def.setFields(fields);

        registry.registerType(def);

        return def;
    }

    GraphQLObjectDefinition newObjectDefinition(String name, Class<?> clazz, TypeRegistry registry,
                                                Map<String, GraphQLTypeDefinition> creatingTypes) {
        GraphQLObjectDefinition def = new GraphQLObjectDefinition();
        def.setGraphqlBean(clazz.isAnnotationPresent(GraphQLBean.class));
        def.setName(name);
        creatingTypes.put(name, def);

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        List<GraphQLFieldDefinition> fields = new ArrayList<>();

        beanModel.forEachSerializableProp(propModel -> {
            GraphQLFieldDefinition field = new GraphQLFieldDefinition();
            field.setName(propModel.getName());
            if (propModel.isLazyLoad())
                field.setLazy(propModel.isLazyLoad());

            IGenericType type = propModel.getType();
            GraphQLType gqlType = buildGraphQLType(type, propModel.getBizObjName(), registry, creatingTypes, false);
            field.setType(gqlType);
            field.setFetcher(BeanPropertyFetcher.INSTANCE);
            PropMeta propMeta = propModel.getAnnotation(PropMeta.class);
            field.setBeanPropMeta(propMeta);
            field.setJavaType(propModel.getType());

            if (propMeta != null && !propMeta.displayName().isEmpty()) {
                field.setLabel(propMeta.displayName());
            }
            fields.add(field);
        });
        def.setFields(fields);

        registry.registerType(def);
        return def;
    }

    GraphQLEnumDefinition buildEnumDef(String name, Class<?> clazz, TypeRegistry registry, Map<String, GraphQLTypeDefinition> creatingTypes) {
        GraphQLEnumDefinition def = new GraphQLEnumDefinition();
        def.setName(name);
        creatingTypes.put(name, def);

        Object[] values = clazz.getEnumConstants();
        List<GraphQLEnumValueDefinition> list = new ArrayList<>(values.length);
        for (Object value : values) {
            Enum e = (Enum) value;

            GraphQLEnumValueDefinition valueDef = new GraphQLEnumValueDefinition();
            valueDef.setName(e.name());
            list.add(valueDef);
        }
        def.setEnumValues(list);

        registry.registerType(def);
        return def;
    }
}
