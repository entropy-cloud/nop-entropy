/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.schema;

import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.ast.GraphQLASTKind;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLDirectiveDefinition;
import io.nop.graphql.core.ast.GraphQLFragment;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLInterfaceDefinition;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static io.nop.graphql.core.GraphQLErrors.ARG_DIRECTIVE_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_FRAGMENT_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OLD_LOC;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_DUPLICATE_DIRECTIVE_DEF;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_DUPLICATE_FRAGMENT_DEF;

public class GraphQLSchema {
    private final Map<String, GraphQLTypeDefinition> types = new HashMap<>();
    private final Map<String, GraphQLDirectiveDefinition> directives = new HashMap<>();
    private final Map<String, GraphQLFragment> fragments = new HashMap<>();

    public Map<String, GraphQLTypeDefinition> getTypes() {
        return types;
    }

    public GraphQLTypeDefinition getType(String name) {
        return types.get(name);
    }

    public GraphQLObjectDefinition getObjectType(String name) {
        return (GraphQLObjectDefinition) getType(name);
    }

    public GraphQLInterfaceDefinition getInterfaceType(String name) {
        return (GraphQLInterfaceDefinition) getType(name);
    }

    public Map<String, GraphQLDirectiveDefinition> getDirectives() {
        return directives;
    }

    public GraphQLDirectiveDefinition getDirective(String name) {
        return directives.get(name);
    }

    public Map<String, GraphQLFragment> getFragments() {
        return fragments;
    }

    public GraphQLFragment getFragment(String name) {
        return fragments.get(name);
    }

    public void addDirective(GraphQLDirectiveDefinition directive) {
        GraphQLDirectiveDefinition old = directives.putIfAbsent(directive.getName(), directive);
        if (old != null)
            throw new NopException(ERR_GRAPHQL_DUPLICATE_DIRECTIVE_DEF).source(directive)
                    .param(ARG_DIRECTIVE_NAME, directive.getName()).param(ARG_OLD_LOC, old.getLocation());
    }

    public void addType(GraphQLTypeDefinition type) {
        GraphQLDefinition old = types.putIfAbsent(type.getName(), type);
        if (old != null)
            throw new NopException(ERR_GRAPHQL_DUPLICATE_DIRECTIVE_DEF).source(type)
                    .param(ARG_DIRECTIVE_NAME, type.getName()).param(ARG_OLD_LOC, old.getLocation());
    }

    public void addFragment(GraphQLFragment fragment) {
        GraphQLDefinition old = fragments.putIfAbsent(fragment.getName(), fragment);
        if (old != null)
            throw new NopException(ERR_GRAPHQL_DUPLICATE_FRAGMENT_DEF).source(fragment)
                    .param(ARG_FRAGMENT_NAME, fragment.getName()).param(ARG_OLD_LOC, old.getLocation());
    }

    public void addDefinitions(Collection<? extends GraphQLDefinition> definitions) {
        for (GraphQLDefinition def : definitions) {
            GraphQLASTKind kind = def.getASTKind();
            if (kind == GraphQLASTKind.GraphQLDirectiveDefinition) {
                addDirective((GraphQLDirectiveDefinition) def);
            } else if (def instanceof GraphQLTypeDefinition) {
                addType((GraphQLTypeDefinition) def);
            } else if (kind == GraphQLASTKind.GraphQLFragment) {
                addFragment((GraphQLFragment) def);
            }
        }
    }
}