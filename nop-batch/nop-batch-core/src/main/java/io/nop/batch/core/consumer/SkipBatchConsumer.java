/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.batch.core.BatchSkipPolicy;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;

import java.util.Collection;

/**
 * 消费失败之后允许忽略skipCount条记录。
 */
public class SkipBatchConsumer<R> implements IBatchConsumer<R> {

    private final IBatchConsumer<R> consumer;
    private final BatchSkipPolicy skipPolicy;

    public SkipBatchConsumer(IBatchConsumer<R> consumer, BatchSkipPolicy skipPolicy) {
        this.consumer = consumer;
        this.skipPolicy = skipPolicy;
    }

    @Override
    public void consume(Collection<R> items, IBatchChunkContext context) {
        SkipConsumeHelper.consumeWithSkipPolicy(skipPolicy, consumer, items, context);
    }
}
