/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.CollectionHelper;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.GraphQLErrors;
import io.nop.graphql.core.ast.*;

import java.util.List;
import java.util.Map;

import static io.nop.commons.util.CollectionHelper.toNotNull;
import static io.nop.graphql.core.GraphQLErrors.*;

public class SelectionBeanBuilder {
    private final Map<String, GraphQLDirectiveDefinition> directiveTypes;

    public SelectionBeanBuilder(Map<String, GraphQLDirectiveDefinition> directiveTypes) {
        this.directiveTypes = Guard.notNull(directiveTypes, "directiveTypes");
    }

    public FieldSelectionBean buildSelectionBean(String name, GraphQLSelectionSet selectionSet,
                                                 Map<String, Object> vars) {
        FieldSelectionBean bean = new FieldSelectionBean();
        bean.setName(name);
        if (selectionSet != null) {
            resolveSelections(bean, selectionSet.getSelections(), vars);
        }
        return bean;
    }

    private void resolveSelections(FieldSelectionBean bean, List<GraphQLSelection> selections,
                                   Map<String, Object> vars) {
        for (GraphQLSelection selection : toNotNull(selections)) {
            if (selection instanceof GraphQLFieldSelection) {
                GraphQLFieldSelection fieldSelection = (GraphQLFieldSelection) selection;

                String alias = fieldSelection.getAliasOrName();
                if (bean.hasField(alias))
                    throw new NopException(ERR_GRAPHQL_FIELD_NAME_DUPLICATED).source(selection)
                            .param(ARG_PARENT_NAME, bean.getName()).param(ARG_FIELD_NAME, alias);

                FieldSelectionBean fieldBean = resolveFieldSelection(fieldSelection, vars);
                // 根据条件跳过此字段
                if (fieldBean == null)
                    continue;

                bean.addField(alias, fieldBean);
            } else if (selection instanceof GraphQLFragmentSelection) {
                GraphQLFragmentSelection fragmentSelection = (GraphQLFragmentSelection) selection;
                GraphQLFragment fragment = fragmentSelection.getResolvedFragment();
                if (fragment == null)
                    throw new IllegalArgumentException(
                            "nop.graphql.fragment-not-resolved:" + fragmentSelection.getFragmentName());

                resolveSelections(bean, fragment.getSelectionSet().getSelections(), vars);
            }
        }
    }

    private FieldSelectionBean resolveFieldSelection(GraphQLFieldSelection selection, Map<String, Object> vars) {
        Map<String, Map<String, Object>> directives = resolveDirectives(selection.getDirectives(), vars);
        if (shouldSkip(directives) || !shouldInclude(directives))
            return null;

        FieldSelectionBean bean = new FieldSelectionBean();
        bean.setName(selection.getName());
        bean.setDirectives(directives);

        bean.setArgs(selection.buildArgs(vars));
        if (selection.getSelectionSet() != null) {
            resolveSelections(bean, selection.getSelectionSet().getSelections(), vars);
        }
        return bean;
    }

    boolean shouldInclude(Map<String, Map<String, Object>> directives) {
        if (directives == null)
            return true;

        Map<String, Object> include = directives.get(GraphQLConstants.DIRECTIVE_INCLUDE);
        if (include == null)
            return true;

        Object ifValue = include.get(GraphQLConstants.DIRECTIVE_ARG_IF);
        boolean b = ConvertHelper.toPrimitiveBoolean(ifValue, true, NopException::new);
        return b;
    }

    boolean shouldSkip(Map<String, Map<String, Object>> directives) {
        if (directives == null)
            return false;

        Map<String, Object> skip = directives.get(GraphQLConstants.DIRECTIVE_SKIP);
        if (skip == null)
            return false;

        Object skipValue = skip.get(GraphQLConstants.DIRECTIVE_ARG_IF);
        boolean b = ConvertHelper.toPrimitiveBoolean(skipValue, true, NopException::new);
        return b;
    }

    private Map<String, Map<String, Object>> resolveDirectives(List<GraphQLDirective> directives,
                                                               Map<String, Object> vars) {
        if (directives == null || directives.isEmpty())
            return null;

        Map<String, Map<String, Object>> map = CollectionHelper.newLinkedHashMap(directives.size());
        for (GraphQLDirective directive : directives) {
            map.put(directive.getName(), resolveDirective(directive, vars));
        }
        return map;
    }

    private Map<String, Object> resolveDirective(GraphQLDirective directive, Map<String, Object> vars) {
        String name = directive.getName();
        GraphQLDirectiveDefinition directiveType = directiveTypes.get(name);
        if (directiveType == null)
            throw new NopException(GraphQLErrors.ERR_GRAPHQL_UNKNOWN_DIRECTIVE).param(GraphQLErrors.ARG_DIRECTIVE, name)
                    .source(directive);

        return directive.buildArgs(vars);
    }
}