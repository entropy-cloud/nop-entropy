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

public class GraphQLSelectionSet extends _GraphQLSelectionSet {

    private GraphQLObjectDefinition objectDefinition;

    public GraphQLObjectDefinition getObjectDefinition() {
        return objectDefinition;
    }

    public void setObjectDefinition(GraphQLObjectDefinition objectDefinition) {
        this.objectDefinition = objectDefinition;
    }

    public String getObjTypeName() {
        if (objectDefinition == null)
            return null;
        return objectDefinition.getName();
    }

    public GraphQLSelectionSet newInstance() {
        GraphQLSelectionSet ret = new GraphQLSelectionSet();
        ret.setObjectDefinition(objectDefinition);
        return ret;
    }

    public GraphQLFieldSelection getSelection(String name) {
        for (GraphQLSelection selection : getSelections()) {
            if (selection instanceof GraphQLFieldSelection) {
                GraphQLFieldSelection field = (GraphQLFieldSelection) selection;
                if (field.getAliasOrName().equals(name))
                    return field;
            }
        }
        return null;
    }

    public void removeSelection(String name) {
        GraphQLFieldSelection selection = getSelection(name);
        if (selection != null) {
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