/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.ast.GraphQLArgument;
import io.nop.graphql.core.ast.GraphQLDirective;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLLiteral;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLSelectionSet;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.schema.GraphQLSchema;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.graphql.core.utils.GraphQLValueHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.graphql.core.GraphQLErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_FRAGMENT_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_NOT_OBJ_TYPE_FOR_FIELD;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNDEFINED_FIELD;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_FRAGMENT_SELECTION;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_OBJ_TYPE;

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

    void addNonLazyFields(GraphQLSelectionSet selectionSet,
                          GraphQLObjectDefinition objDef, int level,
                          FieldSelectionBean selectionBean) {
        if (selectionBean != null && !selectionBean.getFields().isEmpty()) {
            addFieldsForSelection(selectionSet, objDef, level, selectionBean);
        } else {
            // 标记了TreeChildren则由GraphQL引擎负责展开
            if (selectionBean != null && selectionBean.getDirective(GraphQLConstants.DIRECTIVE_TREE_CHILDREN) != null)
                return;

            FieldSelectionBean defaultSelection = this.schemaLoader.getFragmentDefinition(objDef.getName(),
                    GraphQLConstants.FRAGMENT_DEFAULTS);
            if (defaultSelection != null) {
                addFieldsForSelection(selectionSet, objDef, level, defaultSelection);
                return;
            }

            addDefaultFieldsForObjType(selectionSet, selectionBean, objDef, level);
        }
    }

    void addFieldsForSelection(GraphQLSelectionSet selectionSet, GraphQLObjectDefinition objDef, int level,
                               FieldSelectionBean selectionBean) {
        addFieldsForSelection(selectionSet, objDef, level, selectionBean, Collections.emptySet());
    }

    void addFieldsForSelection(GraphQLSelectionSet selectionSet, GraphQLObjectDefinition objDef, int level,
                               FieldSelectionBean selectionBean, Set<String> excludeFields) {
        for (Map.Entry<String, FieldSelectionBean> entry : selectionBean.getFields().entrySet()) {
            String alias = entry.getKey();
            if (alias.startsWith("...")) {
                String fragmentName = alias.substring("...".length());
                FieldSelectionBean fragmentSelectionBean = entry.getValue();
                addFragment(selectionSet, selectionBean, objDef, fragmentName, level, fragmentSelectionBean);
                continue;
            }
            FieldSelectionBean subSelection = entry.getValue();
            String fieldName = subSelection.getName();
            if (fieldName == null) {
                fieldName = alias;
            }

            if (excludeFields.contains(fieldName))
                continue;

            GraphQLFieldDefinition fieldDef = objDef.getField(fieldName);
            if (fieldDef == null)
                throw new NopException(ERR_GRAPHQL_UNDEFINED_FIELD).param(ARG_OBJ_NAME, objDef.getName())
                        .param(ARG_FIELD_NAME, fieldName);

            GraphQLFieldSelection field = buildField(objDef, fieldDef, subSelection, level);
            if (field != null) {
                field.setAlias(alias);
                selectionSet.addFieldSelection(field);
            }
        }
    }

    void addDefaultFieldsForObjType(GraphQLSelectionSet selectionSet,
                                    FieldSelectionBean assignedSelection,
                                    GraphQLObjectDefinition objType, int level) {
        addDefaultFieldsForObjType(selectionSet, assignedSelection, objType, level, Collections.emptySet());
    }

    void addDefaultFieldsForObjType(GraphQLSelectionSet selectionSet,
                                    FieldSelectionBean assignedSelection,
                                    GraphQLObjectDefinition objType, int level,
                                    Set<String> excludeFields) {
        for (GraphQLFieldDefinition fieldDef : objType.getFields()) {
            if (isLazy(fieldDef)) {
                continue;
            }

            if (excludeFields.contains(fieldDef.getName()))
                continue;

            if (assignedSelection != null && assignedSelection.hasField(fieldDef.getName()))
                continue;

            GraphQLFieldSelection field = buildField(objType, fieldDef, null, level);
            if (field != null)
                selectionSet.addFieldSelection(field);
        }
    }

    private void addFragment(GraphQLSelectionSet selectionSet, FieldSelectionBean assignedSelection,
                             GraphQLObjectDefinition objType, String fragmentName, int level,
                             FieldSelectionBean fragmentSelectionBean) {
        Set<String> excludeFields = getExcludeFields(fragmentSelectionBean);

        FieldSelectionBean fragment = this.schemaLoader.getFragmentDefinition(objType.getName(), fragmentName);
        if (fragment == null) {
            if (GraphQLConstants.FRAGMENT_DEFAULTS.equals(fragmentName)) {
                addDefaultFieldsForObjType(selectionSet, assignedSelection, objType, level, excludeFields);
                return;
            }

            throw new NopException(ERR_GRAPHQL_UNKNOWN_FRAGMENT_SELECTION)
                    .param(ARG_BIZ_OBJ_NAME, objType)
                    .param(ARG_FRAGMENT_NAME, fragmentName);
        }

        selectionSet.makeSelections();
        addFieldsForSelection(selectionSet, objType, level, fragment, excludeFields);
    }

    @SuppressWarnings("unchecked")
    private Set<String> getExcludeFields(FieldSelectionBean selectionBean) {
        if (selectionBean == null || selectionBean.getDirectives() == null)
            return Collections.emptySet();

        Map<String, Object> excludeDirective = selectionBean.getDirectives().get(GraphQLConstants.DIRECTIVE_EXCLUDE);
        if (excludeDirective == null)
            return Collections.emptySet();

        Object fieldsValue = excludeDirective.get(GraphQLConstants.DIRECTIVE_ARG_FIELDS);
        if (fieldsValue == null)
            return Collections.emptySet();

        if (fieldsValue instanceof List) {
            return new HashSet<>((List<String>) fieldsValue);
        }
        return Collections.emptySet();
    }

    GraphQLFieldSelection buildField(GraphQLObjectDefinition objDef, GraphQLFieldDefinition fieldDef,
                                     FieldSelectionBean selectionBean, int level) {
        GraphQLFieldSelection field = new GraphQLFieldSelection();
        field.setName(fieldDef.getName());
        field.setFieldDefinition(fieldDef);

        buildDirectives(field, selectionBean);
        buildArgs(field, selectionBean);

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

                        // 超过限制层次的对象不读取
                        return null;
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

    void buildArgs(GraphQLFieldSelection field, FieldSelectionBean selection) {
        if (selection == null || selection.getArgs() == null)
            return;

        for (Map.Entry<String, Object> entry : selection.getArgs().entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            GraphQLArgument arg = new GraphQLArgument();
            arg.setName(name);
            arg.setValue(GraphQLLiteral.valueOf(null, value));
            field.addArg(arg);
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
