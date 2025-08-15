/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.xlang.xmeta.IObjPropMeta;

public class PropGetterFetcher implements IDataFetcher {
    private final IEvalFunction getter;
    private final IObjPropMeta propMeta;

    public PropGetterFetcher(IEvalFunction getter, IObjPropMeta propMeta) {
        this.getter = getter;
        this.propMeta = propMeta;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IEvalScope scope = env.getEvalScope();
        return getter.call3(null, env.getSource(), env.getArgs(), propMeta, scope);
    }
}
