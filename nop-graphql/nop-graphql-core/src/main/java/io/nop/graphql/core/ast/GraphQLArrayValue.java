/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.graphql.core.ast._gen._GraphQLArrayValue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.nop.commons.util.CollectionHelper.toNotNull;

public class GraphQLArrayValue extends _GraphQLArrayValue {

    public int size() {
        return this.getItems() == null ? 0 : this.getItems().size();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return size() <= 0;
    }

    @Override
    public Object buildValue(Map<String, Object> vars) {
        List<GraphQLValue> items = getItems();
        if (items == null)
            return Collections.emptyList();
        return this.getItems().stream().map(v -> v.buildValue(vars)).collect(Collectors.toList());
    }

    @Override
    public boolean containsVariable() {
        for (GraphQLValue value : toNotNull(getItems())) {
            if (value.containsVariable())
                return true;
        }
        return false;
    }
}