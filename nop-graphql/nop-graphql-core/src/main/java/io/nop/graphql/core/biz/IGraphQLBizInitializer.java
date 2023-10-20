/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.biz;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;

import java.util.function.BiConsumer;

public interface IGraphQLBizInitializer {
    void initialize(GraphQLObjectDefinition objDef, String entityName,
                    BiConsumer<QueryBean, IDataFetchingEnvironment> queryProcessor);
}
