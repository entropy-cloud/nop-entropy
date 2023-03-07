/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchConsumer;
import io.nop.commons.functional.IFunctionInvoker;

import java.util.List;

public class InvokerBatchConsumer<R, C> implements IBatchConsumer<R, C> {
    private final IFunctionInvoker invoker;
    private final IBatchConsumer<R, C> consumer;

    public InvokerBatchConsumer(IFunctionInvoker invoker, IBatchConsumer<R, C> consumer) {
        this.invoker = invoker;
        this.consumer = consumer;
    }

    @Override
    public void consume(List<R> items, C context) {
        invoker.invoke(c -> {
            consumer.consume(items, context);
            return null;
        }, context);
    }
}
