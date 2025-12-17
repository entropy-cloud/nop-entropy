/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class EmptyBatchConsumer<R> implements IBatchConsumerProvider.IBatchConsumer<R>, IBatchConsumerProvider<R> {
    static final Logger LOG = LoggerFactory.getLogger(EmptyBatchConsumer.class);

    private static final EmptyBatchConsumer<Object> INSTANCE = new EmptyBatchConsumer<>();

    public static <R> EmptyBatchConsumer<R> instance() {
        return (EmptyBatchConsumer) INSTANCE;
    }

    @Override
    public IBatchConsumer<R> setup(IBatchTaskContext context) {
        return this;
    }

    @Override
    public void consume(Collection<R> items, IBatchChunkContext context) {
        LOG.debug("batch.consumer.ignore:taskName={},taskId={},taskKey={},items={}",
                context.getTaskName(), context.getTaskId(), context.getTaskKey(), items);
    }
}