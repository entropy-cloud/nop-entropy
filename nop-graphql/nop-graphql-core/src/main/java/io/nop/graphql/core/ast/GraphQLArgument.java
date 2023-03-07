/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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