/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.orm.fetcher;

import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.orm.IOrmEntity;

public class OrmEntityIdFetcher implements IDataFetcher {
    public static final OrmEntityIdFetcher INSTANCE = new OrmEntityIdFetcher();

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IOrmEntity entity = (IOrmEntity) env.getSource();
        return entity.orm_idString();
    }
}