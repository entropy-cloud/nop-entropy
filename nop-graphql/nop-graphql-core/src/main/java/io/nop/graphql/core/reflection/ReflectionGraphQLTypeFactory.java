/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.reflection;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLMap;
import io.nop.api.core.annotations.graphql.GraphQLScalar;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.type.IGenericType;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLEnumDefinition;
import io.nop.graphql.core.ast.GraphQLEnumValueDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.fetcher.BeanPropertyFetcher;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import io.nop.graphql.core.utils.GraphQLTypeHelper;

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

    public List<GraphQLArgumentDefinition> getArgDefinitions(IFunctionModel func, TypeRegistry registry) {
        return getArgDefinitions(func, registry, new HashMap<>());
    }

    private List<GraphQLArgumentDefinition> getArgDefinitions(IFunctionModel func, TypeRegistry registry,
                                                              Map<String, GraphQLTypeDefinition> creatingTypes) {
        List<GraphQLArgumentDefinition> argDefs = new ArrayList<>();

        for (IFunctionArgument arg : func.getArgs()) {
            if (arg.isAnnotationPresent(RequestBean.class)) {
                return getArgTypes(func.getName(), arg, arg.getType(), registry, creatingTypes);
            } else if (arg.isAnnotationPresent(Name.class)) {
                GraphQLType type = buildGraphQLType(arg.getType(), null, registry, creatingTypes);
                GraphQLArgumentDefinition argDef = new GraphQLArgumentDefinition();
                argDef.setName(arg.getName());
                argDef.setType(type);
                Description description = arg.getAnnotation(Description.class);
                if (description != null)
                    argDef.setDescription(description.value());

                argDefs.add(argDef);
            } else if (arg.getType().getRawClass() == ApiRequest.class) {
                return getArgTypes(func.getName(), arg, arg.getType().getTypeParameters().get(0), registry,
                        creatingTypes);
            }
        }
        return argDefs;
    }

    private List<GraphQLArgumentDefinition> getArgTypes(String funcName, IFunctionArgument arg, IGenericType type,
                                                        TypeRegistry registry, Map<String, GraphQLTypeDefinition> creatingTypes) {
        GraphQLScalarType scalarType = GraphQLScalarType.fromJavaClass(type.getRawClass());
        if (scalarType != null) {
            throw new NopException(ERR_GRAPHQL_METHOD_ARG_TYPE_NOT_OBJ_TYPE).param(ARG_METHOD_NAME, funcName)
                    .param(ARG_ARG_NAME, arg.getName()).param(ARG_TYPE, arg.getType());
        }

        List<GraphQLArgumentDefinition> argDefs = new ArrayList<>();
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(type.getRawClass());
        beanModel.forEachSerializableProp(propModel -> {
            String propName = propModel.getName();
            GraphQLType graphqlType = buildGraphQLType(propModel.getType(), null, registry, creatingTypes);
            argDefs.add(buildArgDef(propName, graphqlType));

        });
        return argDefs;
    }

    private GraphQLArgumentDefinition buildArgDef(String name, GraphQLType type) {
        GraphQLArgumentDefinition argDef = new GraphQLArgumentDefinition();
        argDef.setName(name);
        argDef.setType(type);
        return argDef;
    }

    public GraphQLType buildGraphQLType(IGenericType type, String thisObjName, String bizObjName,
                                        TypeRegistry registry) {
        if (GraphQLConstants.BIZ_OBJ_NAME_THIS_OBJ.equals(bizObjName))
            bizObjName = thisObjName;
        return buildGraphQLType(type, bizObjName, registry, new HashMap<>());
    }

    public GraphQLType buildGraphQLType(Class<?> type, TypeRegistry registry) {
        String bizObjName = getBizObjName(type);
        return buildGraphQLType(ReflectionManager.instance().buildRawType(type), null, bizObjName, registry);
    }

    String getBizObjName(Class<?> type) {
        BizObjName bizObjName = type.getAnnotation(BizObjName.class);
        return bizObjName == null ? null : bizObjName.value();
    }

    private GraphQLType buildGraphQLType(IGenericType type, String bizObjName, TypeRegistry registry,
                                         Map<String, GraphQLTypeDefinition> creatingTypes) {
        if (type.isAssignableTo(CompletionStage.class)) {
            type = type.getGenericType(CompletionStage.class).getTypeParameters().get(0);
        }

        if (type.isCollectionLike() || type.isArray()) {
            GraphQLType componentType = buildGraphQLType(type.getComponentType(), bizObjName, registry, creatingTypes);
            return GraphQLTypeHelper.listType(componentType);
        }

        if (type.getRawClass() == PageBean.class) {
            IGenericType componentType = type.getTypeParameters().get(0);
            GraphQLType beanType = buildGraphQLType(componentType, bizObjName, registry, creatingTypes);
            return buildPageBeanType(beanType, registry, creatingTypes);
        }

        if (type.getRawClass() == Object.class) {
            return GraphQLTypeHelper.scalarType(GraphQLScalarType.String);
        }

        GraphQLScalarType scalarType = GraphQLScalarType.fromJavaClass(type.getRawClass());
        if (scalarType != null)
            return GraphQLTypeHelper.scalarType(scalarType);

        Class<?> clazz = type.getRawClass();

        if (bizObjName == null) {
            bizObjName = getBizObjName(clazz);
        }

        if (clazz.isEnum()) {
            return GraphQLTypeHelper.namedType(clazz.getSimpleName());
        }

        if (bizObjName != null) {
            registry.addBizObjClass(bizObjName, clazz);
            return GraphQLTypeHelper.namedType(bizObjName);
        }

        if (clazz.isAnnotationPresent(GraphQLMap.class))
            return GraphQLTypeHelper.scalarType(GraphQLScalarType.Map);

        if (clazz.isAnnotationPresent(GraphQLScalar.class))
            return GraphQLTypeHelper.scalarType(GraphQLScalarType.String);

        String name = GraphQLNameHelper.getResultTypeName(clazz);
        GraphQLDefinition typeDef = buildDef(name, clazz, registry, creatingTypes);

        GraphQLNamedType gqlType = GraphQLTypeHelper.namedType(name);
        gqlType.setResolvedType(typeDef);
        return gqlType;
    }

    public GraphQLObjectDefinition buildDef(Class<?> clazz, TypeRegistry registry) {
        String name = GraphQLNameHelper.getResultTypeName(clazz);
        GraphQLObjectDefinition typeDef = (GraphQLObjectDefinition) buildDef(name, clazz, registry, new HashMap<>());
        return typeDef;
    }

    GraphQLType buildPageBeanType(GraphQLType type, TypeRegistry registry,
                                  Map<String, GraphQLTypeDefinition> creatingTypes) {
        String pageBeanTypeName = GraphQLConstants.PAGE_BEAN_PREFIX + type;
        GraphQLTypeDefinition objDef = creatingTypes.get(pageBeanTypeName);
        if (objDef == null) {
            objDef = registry.getType(pageBeanTypeName);
            if (objDef == null) {
                objDef = buildPageBeanType(pageBeanTypeName, type, registry, creatingTypes);
                registry.registerType(objDef);
            }
        }
        GraphQLNamedType namedType = GraphQLTypeHelper.namedType(pageBeanTypeName);
        namedType.setResolvedType(objDef);
        return namedType;
    }

    GraphQLObjectDefinition buildPageBeanType(String typeName, GraphQLType type, TypeRegistry registry,
                                              Map<String, GraphQLTypeDefinition> creatingTypes) {
        GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) buildDef(GraphQLConstants.PAGE_BEAN, PageBean.class,
                registry, creatingTypes);
        objDef = objDef.deepClone();
        creatingTypes.put(typeName, objDef);
        objDef.setName(typeName);
        GraphQLFieldDefinition field = objDef.getField(GraphQLConstants.FIELD_ITEMS);
        field.setType(GraphQLTypeHelper.listType(type));
        field.setFetcher(BeanPropertyFetcher.INSTANCE);
        return objDef;
    }

    public GraphQLObjectDefinition buildObjectDefinition(String name, Class<?> clazz, TypeRegistry registry) {
        return (GraphQLObjectDefinition) buildDef(name, clazz, registry, new HashMap<>());
    }

    GraphQLTypeDefinition buildDef(String name, Class<?> clazz, TypeRegistry registry,
                                   Map<String, GraphQLTypeDefinition> creatingTypes) {
        GraphQLTypeDefinition objDef = creatingTypes.get(name);
        if (objDef != null)
            return objDef;
        objDef = registry.getType(name);
        if (objDef != null)
            return objDef;

        if (clazz.isEnum())
            return buildEnumDef(name, clazz, creatingTypes);

        GraphQLObjectDefinition def = new GraphQLObjectDefinition();
        def.setName(name);
        creatingTypes.put(name, def);

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        List<GraphQLFieldDefinition> fields = new ArrayList<>();

        beanModel.forEachSerializableProp(propModel -> {
            GraphQLFieldDefinition field = new GraphQLFieldDefinition();
            field.setName(propModel.getName());
            field.setLazy(propModel.isLazyLoad());

            IGenericType type = propModel.getType();
            GraphQLType gqlType = buildGraphQLType(type, propModel.getBizObjName(), registry, creatingTypes);
            field.setType(gqlType);
            field.setFetcher(BeanPropertyFetcher.INSTANCE);
            fields.add(field);
        });
        def.setFields(fields);

        registry.registerType(def);
        return def;
    }

    GraphQLEnumDefinition buildEnumDef(String name, Class<?> clazz, Map<String, GraphQLTypeDefinition> creatingTypes) {
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
        return def;
    }
}
