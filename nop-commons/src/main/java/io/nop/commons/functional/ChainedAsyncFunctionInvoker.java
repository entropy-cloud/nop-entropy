/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class ChainedAsyncFunctionInvoker implements IAsyncFunctionInvoker {
    private final IAsyncFunctionInvoker first;
    private final IAsyncFunctionInvoker next;

    public ChainedAsyncFunctionInvoker(IAsyncFunctionInvoker first, IAsyncFunctionInvoker next) {
        this.first = first;
        this.next = next;
    }

    @Override
    public <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request) {
        return first.invokeAsync(r -> {
            return next.invokeAsync(task, r);
        }, request);
    }
}