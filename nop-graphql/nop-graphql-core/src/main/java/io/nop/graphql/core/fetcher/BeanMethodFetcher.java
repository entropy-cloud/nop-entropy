/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.core.reflect.IFunctionModel;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.List;
import java.util.function.Function;

public class BeanMethodFetcher implements IDataFetcher {
    private final Object bean;
    private final IFunctionModel function;
    private final List<Function<IDataFetchingEnvironment, Object>> argBuilders;

    public BeanMethodFetcher(Object bean, IFunctionModel function,
                             List<Function<IDataFetchingEnvironment, Object>> argBuilders) {
        this.bean = bean;
        this.function = function;
        this.argBuilders = argBuilders;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        Object[] args = new Object[argBuilders.size()];
        for (int i = 0, n = args.length; i < n; i++) {
            args[i] = argBuilders.get(i).apply(env);
        }
        return function.invoke(bean, args, env.getExecutionContext().getEvalScope());
    }
}
