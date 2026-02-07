/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.graphql.core.ast._gen._GraphQLSelectionSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GraphQLSelectionSet extends _GraphQLSelectionSet {

    private Map<String, GraphQLSelection> selectionMap;

    private GraphQLTypeDefinition typeDefinition;

    public GraphQLSelectionSet newInstance() {
        GraphQLSelectionSet ret = new GraphQLSelectionSet();
        ret.setTypeDefinition(null);
        return ret;
    }

    public GraphQLTypeDefinition getTypeDefinition() {
        return typeDefinition;
    }

    public void setTypeDefinition(GraphQLTypeDefinition typeDefinition) {
        this.typeDefinition = typeDefinition;
    }

    public GraphQLObjectDefinition getObjectDefinition() {
        if (typeDefinition instanceof GraphQLObjectDefinition) {
            return (GraphQLObjectDefinition) typeDefinition;
        }
        return null;
    }

    public void setObjectDefinition(GraphQLObjectDefinition objectDefinition) {
        this.typeDefinition = objectDefinition;
    }

    public String getObjTypeName() {
        if (typeDefinition != null) {
            return typeDefinition.getName();
        }
        return null;
    }

    private Map<String, GraphQLSelection> buildSelectionMap() {
        if (selectionMap == null) {
            selectionMap = new HashMap<>();
            for (GraphQLSelection selection : getSelections()) {
                selectionMap.put(selection.getAliasOrName(), selection);
            }
        }
        return selectionMap;
    }

    public GraphQLSelection getSelection(String name) {
        return buildSelectionMap().get(name);
    }

    public void removeSelection(String name) {
        GraphQLSelection selection = getSelection(name);
        if (selection != null) {
            selectionMap.remove(name);
            removeChild(selection);
        }
    }

    public boolean isExceedDepth(int maxDepth) {
        for (GraphQLSelection selection : getSelections()) {
            if (selection.isExceedDepth(maxDepth - 1))
                return true;
        }
        return false;
    }

    public void addFieldSelection(GraphQLSelection field) {
        if (this.selections == null)
            this.selections = new ArrayList<>();
        this.selections.add(field);
        if (selectionMap != null)
            selectionMap.put(field.getAliasOrName(), field);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return getSelections() == null || getSelections().isEmpty();
    }

    @JsonIgnore
    public int size() {
        return getSelections() == null ? 0 : getSelections().size();
    }
}
