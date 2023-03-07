/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.util.INeedInit;
import io.nop.graphql.core.ast._gen._GraphQLOperation;

import java.util.HashMap;
import java.util.Map;

public class GraphQLOperation extends _GraphQLOperation implements INeedInit {
    private Map<String, GraphQLVariableDefinition> vars;

    public Map<String, GraphQLVariableDefinition> getVars() {
        return vars;
    }

    public boolean isExceedDepth(int maxDepth) {
        GraphQLSelectionSet selectionSet = this.getSelectionSet();
        if (selectionSet != null) {
            return selectionSet.isExceedDepth(maxDepth);
        }
        return false;
    }

    @Override
    public void init() {
        vars = new HashMap<>();
        if (this.getVariableDefinitions() != null) {
            for (GraphQLVariableDefinition var : this.getVariableDefinitions()) {
                vars.put(var.getName(), var);
            }
        }
    }

    public GraphQLFieldSelection getFieldSelection() {
        return (GraphQLFieldSelection) getSelectionSet().getSelections().get(0);
    }

    public GraphQLFieldSelection addBizAction(String actionName, Object request) {
        GraphQLFieldSelection field = new GraphQLFieldSelection();
        field.setName(actionName);
        field.setOpRequest(request);
        GraphQLSelectionSet selectionSet = new GraphQLSelectionSet();
        selectionSet.addFieldSelection(field);
        setSelectionSet(selectionSet);
        return field;
    }
}