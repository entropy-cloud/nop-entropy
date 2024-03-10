/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLTypeDefinition;

public abstract class GraphQLTypeDefinition extends _GraphQLTypeDefinition {
    private Object grpcSchema;

    public Object getGrpcSchema() {
        return grpcSchema;
    }

    public void setGrpcSchema(Object grpcSchema) {
        this.grpcSchema = grpcSchema;
    }
}
