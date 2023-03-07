/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.graphql.core.ast._gen._GraphQLLiteral;

import java.util.Map;

public class GraphQLLiteral extends _GraphQLLiteral {
    public static GraphQLLiteral valueOf(SourceLocation loc, Object value) {
        GraphQLLiteral node = new GraphQLLiteral();
        node.setLocation(loc);
        node.setValue(value);
        return node;
    }

    @Override
    public Object buildValue(Map<String, Object> vars) {
        return getValue();
    }

    @Override
    public boolean containsVariable() {
        return false;
    }
}