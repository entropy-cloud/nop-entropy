/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.util.FutureHelper;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

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
            return ((CompletionStage<?>) value).thenCompose(v -> {
                Object ret = transformer.apply(v, env);
                return FutureHelper.success(ret);
            });
        } else {
            return transformer.apply(value, env);
        }
    }
}
