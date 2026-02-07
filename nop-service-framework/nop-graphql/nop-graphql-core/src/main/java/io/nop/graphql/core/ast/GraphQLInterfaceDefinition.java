/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.util.INeedInit;
import io.nop.graphql.core.ast._gen._GraphQLInterfaceDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphQLInterfaceDefinition extends _GraphQLInterfaceDefinition implements INeedInit {

    private Map<String, GraphQLFieldDefinition> fieldsMap;

    @Override
    public void init() {
        fieldsMap = new HashMap<>();
        if (fields == null) {
            fields = new java.util.ArrayList<>();
        }

        for (GraphQLFieldDefinition field : fields) {
            fieldsMap.put(field.getName(), field);
        }
    }

    public GraphQLFieldDefinition getField(String name) {
        if (fieldsMap == null) {
            init();
        }
        return fieldsMap.get(name);
    }

    public Map<String, GraphQLFieldDefinition> getFieldsMap() {
        if (fieldsMap == null) {
            init();
        }
        return fieldsMap;
    }
}
