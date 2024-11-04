/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchConsumerProvider.IBatchConsumer;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.commons.concurrent.IBlockingSink;

import java.util.List;

public class BlockingSinkBatchConsumer<R> implements IBatchConsumer<R>, IBatchConsumerProvider<R> {
    private IBlockingSink<R> sink;

    public IBlockingSink<R> getSink() {
        return sink;
    }

    public void setSink(IBlockingSink<R> sink) {
        this.sink = sink;
    }

    @Override
    public IBatchConsumer<R> setup(IBatchTaskContext context) {
        return this;
    }

    @Override
    public void consume(List<R> items, IBatchChunkContext context) {
        try {
            sink.sendMulti(items);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        }
    }
}