/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLEnumDefinition;

import java.util.ArrayList;
import java.util.List;

public class GraphQLEnumDefinition extends _GraphQLEnumDefinition {

    public List<String> getValueList() {
        List<GraphQLEnumValueDefinition> values = this.getEnumValues();
        if (values == null)
            return List.of();
        List<String> ret = new ArrayList<>(values.size());
        for (GraphQLEnumValueDefinition value : values) {
            ret.add(value.getName());
        }
        return ret;
    }
}
