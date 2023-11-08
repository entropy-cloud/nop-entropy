/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._WithDirectives;

import java.util.List;

public abstract class WithDirectives extends _WithDirectives {
    public GraphQLDirective getDirective(String name) {
        List<GraphQLDirective> directives = getDirectives();
        if (directives == null || directives.isEmpty())
            return null;

        for (GraphQLDirective directive : directives) {
            if (directive.getName().equals(name))
                return directive;
        }
        return null;
    }

    public void addDirective(GraphQLDirective directive) {
        List<GraphQLDirective> directives = makeDirectives();
        directives.add(directive);
    }
}