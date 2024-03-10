/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.processor;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchProcessListener;
import io.nop.batch.core.IBatchProcessor;

import java.util.function.Consumer;

public class BatchProcessorWithListener<S, R, C> implements IBatchProcessor<S, R, C> {
    private final IBatchProcessor<S, R, C> processor;
    private final IBatchProcessListener<S, R, C> listener;

    public BatchProcessorWithListener(IBatchProcessor<S, R, C> processor, IBatchProcessListener<S, R, C> listener) {
        this.processor = processor;
        this.listener = listener;
    }

    @Override
    public void process(S item, Consumer<R> consumer, C context) {
        listener.onProcessBegin(item, consumer, context);
        Throwable exception = null;
        try {
            processor.process(item, consumer, context);
        } catch (Exception e) {
            exception = e;
            throw NopException.adapt(e);
        } finally {
            listener.onProcessEnd(exception, item, consumer, context);
        }
    }
}