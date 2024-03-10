/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLArgument;

public class GraphQLArgument extends _GraphQLArgument {
    private GraphQLArgumentDefinition argDefinition;

    public GraphQLArgumentDefinition getArgDefinition() {
        return argDefinition;
    }

    public void setArgDefinition(GraphQLArgumentDefinition argDefinition) {
        this.argDefinition = argDefinition;
    }
}