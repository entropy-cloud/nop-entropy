/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.graphql.core.ast._gen._GraphQLFieldSelection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.graphql.core.GraphQLErrors.ARG_ARG_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_FIELD_NULL_ARG;

public class GraphQLFieldSelection extends _GraphQLFieldSelection {
    private GraphQLFieldDefinition fieldDefinition;

    private Object opRequest;

    public Object getOpRequest() {
        return opRequest;
    }

    public GraphQLFieldSelection newInstance() {
        GraphQLFieldSelection ret = new GraphQLFieldSelection();
        ret.setFieldDefinition(fieldDefinition);
        ret.setOpRequest(opRequest);
        return ret;
    }

    public void setOpRequest(Object opRequest) {
        this.opRequest = opRequest;
    }

    @Override
    public boolean isExceedDepth(int maxDepth) {
        GraphQLSelectionSet selection = getSelectionSet();
        if (selection != null) {
            return selection.isExceedDepth(maxDepth);
        }
        return false;
    }

    /**
     * alias为结果名称，name为源名称
     */
    @JsonIgnore
    public String getAliasOrName() {
        String alias = getAlias();
        if (StringHelper.isEmpty(alias))
            return getName();
        return alias;
    }

    public boolean isSimpleField() {
        return !hasArg() && !hasDirective() && !hasSelection();
    }

    public boolean hasDirective() {
        return getDirectives() != null && !getDirectives().isEmpty();
    }

    public boolean hasArg() {
        return getArguments() != null && !getArguments().isEmpty();
    }

    public boolean hasSelection() {
        return getSelectionSet() != null && !getSelectionSet().isEmpty();
    }

    @JsonIgnore
    public List<GraphQLSelection> getSelections() {
        GraphQLSelectionSet selectionSet = this.getSelectionSet();
        return selectionSet == null ? null : selectionSet.getSelections();
    }

    public Map<String, Object> buildArgs(Map<String, Object> vars) {
        List<GraphQLArgument> args = this.getArguments();
        if (args == null || args.isEmpty())
            return Collections.emptyMap();

        Map<String, Object> map = new HashMap<>();
        for (GraphQLArgument arg : args) {
            Object value = arg.getValue().buildValue(vars);
            if (value == null) {
                if (arg.getArgDefinition().getType().isNonNullType())
                    throw new NopException(ERR_GRAPHQL_FIELD_NULL_ARG)
                            .source(arg)
                            .param(ARG_FIELD_NAME, getName())
                            .param(ARG_ARG_NAME, arg.getName());
            }
            map.put(arg.getName(), value);
        }
        return map;
    }

    public GraphQLArgument getArg(String name) {
        List<GraphQLArgument> args = this.getArguments();
        if (args == null || args.isEmpty())
            return null;

        for (GraphQLArgument arg : args) {
            if (arg.getName().equals(name))
                return arg;
        }
        return null;
    }

    public void addArg(GraphQLArgument arg) {
        List<GraphQLArgument> args = this.getArguments();
        if (args == null) {
            args = new ArrayList<>();
            this.setArguments(args);
        }
        args.add(arg);
    }

    public GraphQLFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public void setFieldDefinition(GraphQLFieldDefinition fieldDefinition) {
        checkAllowChange();
        this.fieldDefinition = fieldDefinition;
    }
}