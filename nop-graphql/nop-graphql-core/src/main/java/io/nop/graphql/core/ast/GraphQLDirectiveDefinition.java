/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLDirectiveDefinition;

import java.util.List;

public class GraphQLDirectiveDefinition extends _GraphQLDirectiveDefinition {

    public GraphQLArgumentDefinition getArg(String name) {
        List<GraphQLArgumentDefinition> args = this.getArguments();
        if (args == null || args.isEmpty())
            return null;

        for (GraphQLArgumentDefinition arg : args) {
            if (arg.getName().equals(name))
                return arg;
        }
        return null;
    }
}
