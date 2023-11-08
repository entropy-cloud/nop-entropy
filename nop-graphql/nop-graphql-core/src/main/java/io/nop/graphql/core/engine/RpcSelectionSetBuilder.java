/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.ast.*;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.schema.GraphQLSchema;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.graphql.core.utils.GraphQLValueHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.nop.graphql.core.GraphQLErrors.*;

public class RpcSelectionSetBuilder {
    static final Logger LOG = LoggerFactory.getLogger(RpcSelectionSetBuilder.class);

    private final GraphQLSchema builtinSchema;
    private final IGraphQLSchemaLoader schemaLoader;
    private final int maxObjLevel;

    public RpcSelectionSetBuilder(GraphQLSchema builtinSchema, IGraphQLSchemaLoader schemaLoader, int maxObjLevel) {
        this.builtinSchema = builtinSchema;
        this.schemaLoader = schemaLoader;
        this.maxObjLevel = maxObjLevel;
    }

    public GraphQLSelectionSet buildForType(GraphQLType returnType, FieldSelectionBean selectionBean) {
        String typeName = returnType.getNamedTypeName();
        if (typeName == null)
            return null;

        if (GraphQLScalarType.fromText(typeName) != null)
            return null;

        GraphQLTypeDefinition typeDef = getTypeDefinition(typeName);
        if (typeDef == null)
            throw new NopException(ERR_GRAPHQL_UNKNOWN_OBJ_TYPE).param(ARG_OBJ_NAME, typeName);

        if (typeDef instanceof GraphQLObjectDefinition) {
            GraphQLSelectionSet selectionSet = new GraphQLSelectionSet();
            selectionSet.makeSelections();
            addNonLazyFields(selectionSet, (GraphQLObjectDefinition) typeDef, 0, selectionBean);
            return selectionSet;
        } else {
            return null;
        }
    }

    void addNonLazyFields(GraphQLSelectionSet selectionSet, GraphQLObjectDefinition objDef, int level,
                          FieldSelectionBean selectionBean) {
        if (selectionBean != null && !selectionBean.getFields().isEmpty()) {
            for (Map.Entry<String, FieldSelectionBean> entry : selectionBean.getFields().entrySet()) {
                String alias = entry.getKey();
                FieldSelectionBean subSelection = entry.getValue();
                String fieldName = subSelection.getName();
                if (fieldName == null) {
                    fieldName = alias;
                }
                GraphQLFieldDefinition fieldDef = objDef.getField(fieldName);
                if (fieldDef == null)
                    throw new NopException(ERR_GRAPHQL_UNDEFINED_FIELD).param(ARG_OBJ_NAME, objDef.getName())
                            .param(ARG_FIELD_NAME, fieldName);

                GraphQLFieldSelection field = buildField(objDef, fieldDef, subSelection, level);
                field.setAlias(alias);
                selectionSet.addFieldSelection(field);
            }
        } else {
            // 标记了TreeChildren则由GraphQL引擎负责展开
            if (selectionBean != null && selectionBean.getDirective(GraphQLConstants.DIRECTIVE_TREE_CHILDREN) != null)
                return;

            for (GraphQLFieldDefinition fieldDef : objDef.getFields()) {
                if (isLazy(fieldDef)) {
                    continue;
                }

                GraphQLFieldSelection field = buildField(objDef, fieldDef, null, level);
                selectionSet.addFieldSelection(field);
            }
        }
    }

    GraphQLFieldSelection buildField(GraphQLObjectDefinition objDef, GraphQLFieldDefinition fieldDef,
                                     FieldSelectionBean selectionBean, int level) {
        GraphQLFieldSelection field = new GraphQLFieldSelection();
        field.setName(fieldDef.getName());
        field.setFieldDefinition(fieldDef);

        buildDirectives(field, selectionBean);

        String typeName = fieldDef.getType().getNamedTypeName();
        if (typeName != null) {
            if (GraphQLScalarType.fromText(typeName) == null) {
                GraphQLTypeDefinition relDef = getTypeDefinition(typeName);
                if (relDef == null)
                    throw new NopException(ERR_GRAPHQL_NOT_OBJ_TYPE_FOR_FIELD).param(ARG_OBJ_TYPE, objDef.getName())
                            .param(ARG_FIELD_NAME, fieldDef.getName()).param(ARG_TYPE, typeName);

                if (relDef instanceof GraphQLObjectDefinition) {
                    // 最多只获取maxObjLevel层的对象数据
                    if (level >= maxObjLevel) {
                        LOG.debug("nop.graphql.ignore-obj-level-exceed-limit:objType={},field={},level={}",
                                objDef.getName(), typeName, level);
                        return field;
                    }

                    GraphQLSelectionSet subSelection = new GraphQLSelectionSet();
                    subSelection.makeSelections();
                    addNonLazyFields(subSelection, (GraphQLObjectDefinition) relDef, level + 1, selectionBean);
                    field.setSelectionSet(subSelection);
                }
            }
        }
        return field;
    }

    void buildDirectives(GraphQLFieldSelection field, FieldSelectionBean selection) {
        if (selection == null || selection.getDirectives() == null)
            return;

        for (Map.Entry<String, Map<String, Object>> entry : selection.getDirectives().entrySet()) {
            String name = entry.getKey();
            Map<String, Object> values = entry.getValue();

            GraphQLDirective directive = new GraphQLDirective();
            directive.setName(name);

            if (values != null) {
                values.forEach((prop, value) -> {
                    directive.addArgument(prop, GraphQLValueHelper.buildValue(value));
                });
            }
            field.addDirective(directive);
        }
    }

    GraphQLTypeDefinition getTypeDefinition(String typeName) {
        if (builtinSchema != null) {
            GraphQLTypeDefinition type = builtinSchema.getType(typeName);
            if (type != null)
                return type;
        }
        return schemaLoader.getTypeDefinition(typeName);
    }

    boolean isLazy(GraphQLFieldDefinition fieldDef) {
        return fieldDef.getLazy() != null && fieldDef.getLazy();
    }
}
