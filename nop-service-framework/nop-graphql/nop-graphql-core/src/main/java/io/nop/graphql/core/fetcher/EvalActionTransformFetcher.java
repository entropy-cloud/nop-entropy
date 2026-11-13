/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.context.IContext;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.concurrent.CompletableFuture;
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
            // C3-015：fetcher 层异步出口统一挂接（裁定③，与 TransformFetcher/出口泵同口径）——
            // XPL transform 表达式（EvalScope 线程亲和）在请求 context 的执行窗口内运行。
            // 经 context.execute 承载（窗口安全性论证见 GraphQLExecutor.thenFetchNext 同注释）
            IContext context = env.getContext();
            CompletableFuture<Object> promise = new CompletableFuture<>();
            ((CompletionStage<?>) value).whenComplete((v, err) -> {
                try {
                    context.execute(() -> {
                        try {
                            if (err != null) {
                                promise.completeExceptionally(err);
                                return;
                            }
                            FutureHelper.bindResult(
                                    FutureHelper.toCompletionStage(transform(entity, v, env)), promise);
                        } catch (Throwable e) {
                            promise.completeExceptionally(e);
                        }
                    });
                } catch (Throwable e) {
                    // context 已关闭等窗口不可进入场景：异常终态而非静默挂起（rule 24）
                    promise.completeExceptionally(e);
                }
            });
            return promise;
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

