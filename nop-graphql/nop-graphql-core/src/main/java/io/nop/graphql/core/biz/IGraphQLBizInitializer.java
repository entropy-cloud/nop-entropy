/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.biz;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.IOrdered;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.schema.TypeRegistry;

import java.util.function.BiConsumer;

public interface IGraphQLBizInitializer extends IOrdered {
    void initialize(IGraphQLBizObject bizObj,
                    BiConsumer<QueryBean, IDataFetchingEnvironment> queryProcessor,
                    TypeRegistry typeRegistry);
}
