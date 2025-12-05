/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.core.reflect.bean.BeanTool;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

public class SpecificPropertyFetcher implements IDataFetcher {
    private final String name;

    public SpecificPropertyFetcher(String name) {
        this.name = name;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        return BeanTool.instance().getProperty(env.getSource(), name);
    }
}
