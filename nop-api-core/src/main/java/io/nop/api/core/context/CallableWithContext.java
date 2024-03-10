/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.context;

import java.util.concurrent.Callable;

public class CallableWithContext<T> implements Callable<T> {
    private final IContext context;
    private final Callable<T> task;

    public CallableWithContext(IContext context, Callable<T> task) {
        this.context = context;
        this.task = task;
    }

    public static <T> CallableWithContext<T> wrapWithContext(IContext context, Callable<T> task) {
        if (task instanceof CallableWithContext)
            return ((CallableWithContext<T>) task).withContext(context);
        return new CallableWithContext<>(context, task);
    }

    public Callable<T> getTask() {
        return task;
    }

    public CallableWithContext<T> withContext(IContext context) {
        if (this.context == context)
            return this;
        return new CallableWithContext<>(context, task);
    }

    @Override
    public T call() throws Exception {
        return context.executeWithContext(task);
    }
}
