/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.functional;

import java.util.concurrent.Callable;
import java.util.function.Function;

public interface IFunctionInvoker {
    <R, T> T invoke(Function<R, T> fn, R request);

    default <R, T> Function<R, T> wrap(Function<R, T> task) {
        return r -> invoke(task, r);
    }

    default <R, T> Callable<T> toCallable(Function<R, T> task, R request) {
        return () -> invoke(task, request);
    }
}