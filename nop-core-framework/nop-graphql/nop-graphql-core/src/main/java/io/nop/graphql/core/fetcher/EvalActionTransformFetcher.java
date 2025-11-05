/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.concurrent.CompletionStage;

public class EvalActionTransformFetcher implements IDataFetcher {
    private final IDataFetcher fetcher;
    private final IEvalAction transformAction;

    public EvalActionTransformFetcher(IDataFetcher fetcher, IEvalAction transformAction) {
        this.fetcher = fetcher;
        this.transformAction = transformAction;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        Object value = fetcher.get(env);
        if (value instanceof CompletionStage) {
            Object entity = env.getSource();
            return ((CompletionStage<?>) value).thenCompose(v -> {
                return FutureHelper.toCompletionStage(transform(entity, v, env));
            });
        } else {
            return transform(env.getSource(), value, env);
        }
    }

    Object transform(Object entity, Object value, IDataFetchingEnvironment env) {
        IEvalScope scope = env.getEvalScope().newChildScope();
        scope.setLocalValue(null, GraphQLConstants.VAR_ENTITY, entity);
        scope.setLocalValue(null, GraphQLConstants.VAR_VALUE, value);
        return transformAction.invoke(scope);
    }
}
