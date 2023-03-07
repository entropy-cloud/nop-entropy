/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public interface IAsyncFunctionInvoker {
    <R, T> CompletionStage<T> invokeAsync(Function<R, CompletionStage<T>> task, R request);

    default <R, T> Function<R, CompletionStage<T>> wrapAsync(Function<R, CompletionStage<T>> task) {
        return r -> invokeAsync(task, r);
    }

    default <R, T> Callable<CompletionStage<T>> toAsyncCallable(Function<R, CompletionStage<T>> task, R request) {
        return () -> invokeAsync(task, request);
    }
}
