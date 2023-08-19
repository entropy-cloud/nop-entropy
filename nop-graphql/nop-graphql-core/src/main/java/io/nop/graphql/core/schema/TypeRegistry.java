/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.schema;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.ast.GraphQLASTVisitor;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLInputDefinition;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.ast.GraphQLUnionTypeDefinition;
import io.nop.graphql.core.reflection.ReflectionGraphQLTypeFactory;
import io.nop.graphql.core.utils.GraphQLTypeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_CLASS;
import static io.nop.graphql.core.GraphQLErrors.ARG_OTHER_CLASS;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_MULTI_CLASS_HAS_SAME_BIZ_OBJ_NAME;

public class TypeRegistry {
    static final Logger LOG = LoggerFactory.getLogger(TypeRegistry.class);

    private final Map<String, GraphQLTypeDefinition> types = new ConcurrentHashMap<>();
    private final AtomicInteger genIndex = new AtomicInteger();

    /**
     * 对于自动生成的数据类型，这里从对象类型的定义内容映射到类型对象本身，这样可以对于同样的内容重复生成数据类型对象。
     */
    private final Map<String, GraphQLTypeDefinition> sourceToTypes = new ConcurrentHashMap<>();

    private final Map<String, Class<?>> bizObjClasses = new ConcurrentHashMap<>();

    private final Map<String, Class<?>> enumClasses = new ConcurrentHashMap<>();

    public void clear() {
        types.clear();
        genIndex.set(0);
        sourceToTypes.clear();
    }

    public Map<String, Class<?>> getEnumClasses() {
        return enumClasses;
    }

    public Class<?> getEnumClass(String name) {
        return enumClasses.get(name);
    }

    public void addEnumClass(String name, Class<?> enumClass) {
        enumClasses.put(name, enumClass);
    }

    public Class<?> getBizObjClass(String bizObjName) {
        return bizObjClasses.get(bizObjName);
    }

    public void addBizObjClass(String bizObjName, Class<?> clazz) {
        Class<?> oldClass = bizObjClasses.put(bizObjName, clazz);
        if (oldClass != null && oldClass != clazz) {
            throw new NopException(ERR_GRAPHQL_MULTI_CLASS_HAS_SAME_BIZ_OBJ_NAME).param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_CLASS, clazz).param(ARG_OTHER_CLASS, oldClass);
        }
    }

    public Map<String, GraphQLTypeDefinition> getTypes() {
        return types;
    }

    public GraphQLTypeDefinition getType(String name) {
        return types.get(name);
    }

    public void registerType(GraphQLTypeDefinition type) {
        GraphQLDefinition oldDef = types.put(type.getName(), type);
        if (oldDef != null) {
            LOG.debug("nop.graphql.replace-type-definition:old={},def={}", oldDef.toSource(), type.toSource());
        } else {
            LOG.debug("nop.graphql.register-type:def=\n{}", type.toSource());
        }
    }

    /**
     * 根据类型定义的内容分配一个唯一的类型名称
     *
     * @param def 类型定义的内容
     * @return 生成的类型名称
     */
    public GraphQLTypeDefinition normalizeType(GraphQLTypeDefinition def) {
        if (def instanceof GraphQLObjectDefinition) {
            GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) def;
            return sourceToTypes.computeIfAbsent(objDef.getFieldsSource(), k -> {
                def.setName("_GenObj_" + genIndex.incrementAndGet());
                return def;
            });
        } else if (def instanceof GraphQLUnionTypeDefinition) {
            GraphQLUnionTypeDefinition unionDef = (GraphQLUnionTypeDefinition) def;
            return sourceToTypes.computeIfAbsent(unionDef.getTypesSource(), k -> {
                def.setName("_GenUnion_" + genIndex.incrementAndGet());
                return def;
            });
        } else if (def instanceof GraphQLInputDefinition) {
            GraphQLInputDefinition inputDef = (GraphQLInputDefinition) def;
            return sourceToTypes.computeIfAbsent(inputDef.getFieldsSource(), k -> {
                def.setName("_GenInput_" + genIndex.incrementAndGet());
                return def;
            });
        } else {
            throw new IllegalArgumentException("unsupported type:" + def);
        }
    }

    public GraphQLType processSpecialType(GraphQLType type) {
        new GraphQLASTVisitor() {
            @Override
            public void visitGraphQLNamedType(GraphQLNamedType node) {
                String name = node.getNamedTypeName();
                if (name.startsWith(GraphQLConstants.GRAPHQL_CONNECTION_PREFIX)) {
                    String typeName = name.substring(GraphQLConstants.GRAPHQL_CONNECTION_PREFIX.length());
                    Guard.notEmpty(typeName, "typeName");
                    ReflectionGraphQLTypeFactory.INSTANCE.buildConnectionType(GraphQLTypeHelper.namedType(typeName),
                            TypeRegistry.this, new HashMap<>(), false);
                } else if (name.startsWith(GraphQLConstants.PAGE_BEAN_PREFIX)) {
                    String typeName = name.substring(GraphQLConstants.PAGE_BEAN_PREFIX.length());
                    Guard.notEmpty(typeName, "typeName");
                    ReflectionGraphQLTypeFactory.INSTANCE.buildPageBeanType(GraphQLTypeHelper.namedType(typeName),
                            TypeRegistry.this, new HashMap<>(), false);
                }
            }
        }.visit(type);
        return type;
    }
}