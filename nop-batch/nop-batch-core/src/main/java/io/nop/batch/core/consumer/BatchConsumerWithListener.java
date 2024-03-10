/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.consumer;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchConsumeListener;
import io.nop.batch.core.IBatchConsumer;

import java.util.List;

public class BatchConsumerWithListener<R, C> implements IBatchConsumer<R, C> {
    private final IBatchConsumer<R, C> consumer;
    private final IBatchConsumeListener<R, C> listener;

    public BatchConsumerWithListener(IBatchConsumer<R, C> consumer, IBatchConsumeListener<R, C> listener) {
        this.consumer = consumer;
        this.listener = listener;
    }

    @Override
    public void consume(List<R> items, C context) {
        listener.onConsumeBegin(items, context);
        Throwable exception = null;
        try {
            consumer.consume(items, context);
        } catch (Exception e) {
            exception = e;
            throw NopException.adapt(e);
        } finally {
            listener.onConsumeEnd(exception, items, context);
        }
    }
}