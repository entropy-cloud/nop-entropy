/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.impl;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.Map;
import java.util.function.Function;

public class EvalActionDataFetcher implements IDataFetcher {
    private final IEvalAction action;
    private final Map<String, Function<IDataFetchingEnvironment, Object>> argBuilders;

    public EvalActionDataFetcher(IEvalAction action,
                                 Map<String, Function<IDataFetchingEnvironment, Object>> argBuilders) {
        this.action = action;
        this.argBuilders = argBuilders;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IEvalScope scope = env.getEvalScope().newChildScope();
        for (Map.Entry<String, Function<IDataFetchingEnvironment, Object>> entry : argBuilders.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue().apply(env);
            scope.setLocalValue(null, name, value);
        }
        return action.invoke(scope);
    }
}
