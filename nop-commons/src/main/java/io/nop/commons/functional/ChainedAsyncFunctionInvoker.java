/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class ChainedAsyncFunctionInvoker implements IAsyncFunctionInvoker {
    private final IAsyncFunctionInvoker first;
    private final IAsyncFunctionInvoker next;

    public ChainedAsyncFunctionInvoker(IAsyncFunctionInvoker first, IAsyncFunctionInvoker next) {
        this.first = first;
        this.next = next;
    }

    public ChainedAsyncFunctionInvoker(List<IAsyncFunctionInvoker> invokers) {
        IAsyncFunctionInvoker invoker = chain(invokers);
        if (invoker instanceof ChainedAsyncFunctionInvoker) {
            first = ((ChainedAsyncFunctionInvoker) invoker).first;
            next = ((ChainedAsyncFunctionInvoker) invoker).next;
        } else {
            first = invoker;
            next = null;
        }
    }

    public static IAsyncFunctionInvoker chain(List<IAsyncFunctionInvoker> invokers) {
        if (invokers.isEmpty()) {
            return null;
        } else if (invokers.size() == 1) {
            return invokers.get(0);
        } else if (invokers.size() == 2) {
            return new ChainedAsyncFunctionInvoker(invokers.get(0), invokers.get(1));
        } else {
            return new ChainedAsyncFunctionInvoker(invokers);
        }
    }

    @Override
    public <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request) {
        if (first == null)
            return task.apply(request);

        if (next == null)
            return first.invokeAsync(task, request);

        return first.invokeAsync(r -> {
            return next.invokeAsync(task, r);
        }, request);
    }
}