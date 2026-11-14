/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.util.FutureHelper;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * 在一个已有fetcher的基础上执行转换操作得到最终的值
 */
public class TransformFetcher implements IDataFetcher {
    private final IDataFetcher fetcher;
    private final BiFunction<Object, IDataFetchingEnvironment, Object> transformer;

    public TransformFetcher(IDataFetcher fetcher, BiFunction<Object, IDataFetchingEnvironment, Object> transformer) {
        this.fetcher = fetcher;
        this.transformer = transformer;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        Object value = fetcher.get(env);
        if (value instanceof CompletionStage) {
            // C3-015：fetcher 层异步出口统一挂接（裁定③，与出口泵/回合出口泵同口径）——
            // transformer 用户表达式（可携带 env/EvalScope）在请求 context 的执行窗口内运行。
            // 经 context.execute 承载（窗口安全性论证见 GraphQLExecutor.thenFetchNext 同注释），
            // 上游已完成时同样不逃逸到注册线程的空/残留 context
            IContext context = env.getContext();
            CompletableFuture<Object> promise = new CompletableFuture<>();
            // plan 2572 Phase 5：包络收敛到 ContextProvider.whenCompleteInContextWindow（窗口载体不变）
            ContextProvider.whenCompleteInContextWindow((CompletionStage<?>) value, context,
                    (v, err) -> {
                        if (err != null) {
                            promise.completeExceptionally(err);
                            return;
                        }
                        FutureHelper.bindResult(FutureHelper.success(transformer.apply(v, env)),
                                promise);
                    }, promise::completeExceptionally);
            return promise;
        } else {
            return transformer.apply(value, env);
        }
    }
}
