/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

public class FixedValueFetcher implements IDataFetcher {
    private final Object value;

    public FixedValueFetcher(Object value) {
        this.value = value;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        return value;
    }
}
