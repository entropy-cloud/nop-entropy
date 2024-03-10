/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

public class PropGetterFetcher implements IDataFetcher {
    private final IEvalAction getter;

    public PropGetterFetcher(IEvalAction getter) {
        this.getter = getter;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IEvalScope scope = env.getEvalScope().newChildScope();
        scope.setLocalValue(null, GraphQLConstants.VAR_ENTITY, env.getSource());
        return getter.invoke(scope);
    }
}
