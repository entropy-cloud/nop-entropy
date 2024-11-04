/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.commons.functional.IFunctionInvoker;

import java.util.List;

public class InvokerBatchConsumer<R> implements IBatchConsumer<R> {
    private final IFunctionInvoker invoker;
    private final IBatchConsumer<R> consumer;

    public InvokerBatchConsumer(IFunctionInvoker invoker, IBatchConsumer<R> consumer) {
        this.invoker = invoker;
        this.consumer = consumer;
    }

    @Override
    public void consume(List<R> items, IBatchChunkContext context) {
        invoker.invoke(c -> {
            consumer.consume(items, context);
            return null;
        }, context);
    }
}
