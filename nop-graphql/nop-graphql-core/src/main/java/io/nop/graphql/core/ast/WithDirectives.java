/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.GraphQLConstants;
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

    public GraphQLDirective makeDirective(String name) {
        GraphQLDirective directive = getDirective(name);
        if (directive == null) {
            directive = new GraphQLDirective();
            directive.setName(name);
            makeDirectives().add(directive);
        }
        return directive;
    }

    public void setLabel(String label) {
        GraphQLDirective directive = makeDirective(GraphQLConstants.DIRECTIVE_LABEL);
        directive.setArgValue(GraphQLConstants.VAR_VALUE, label);
    }

    public String getLabel() {
        GraphQLDirective directive = getDirective(GraphQLConstants.DIRECTIVE_LABEL);
        if (directive == null)
            return null;
        return (String) directive.getArgValue(GraphQLConstants.VAR_VALUE);
    }

    public void addDirective(GraphQLDirective directive) {
        List<GraphQLDirective> directives = makeDirectives();
        directives.add(directive);
    }

    public String getDisplayString() {
        GraphQLDirective directive = getDirective(GraphQLConstants.DIRECTIVE_LABEL);
        if (directive != null)
            return (String) directive.getArgValue(GraphQLConstants.VAR_VALUE);
        return getClass().getSimpleName();
    }
}