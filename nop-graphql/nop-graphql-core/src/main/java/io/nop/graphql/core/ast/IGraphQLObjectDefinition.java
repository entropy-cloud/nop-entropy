/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import java.util.List;

public interface IGraphQLObjectDefinition {
    String getName();

    List<? extends IGraphQLFieldDefinition> getFields();

    String getDescription();

    String getDisplayString();

    void initPropId();

    Object getGrpcSchema();

    void setGrpcSchema(Object grpcSchema);
}
