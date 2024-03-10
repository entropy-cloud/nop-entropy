/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.processor;

import io.nop.batch.core.IBatchProcessor;

import java.util.function.Consumer;

public final class CompositeBatchProcessor<S, R, T, C> implements IBatchProcessor<S, T, C> {
    private final IBatchProcessor<S, R, C> processor;
    private final IBatchProcessor<R, T, C> next;

    public CompositeBatchProcessor(IBatchProcessor<S, R, C> processor, IBatchProcessor<R, T, C> next) {
        this.processor = processor;
        this.next = next;
    }

    public IBatchProcessor<S, R, C> getProcessor() {
        return processor;
    }

    public IBatchProcessor<R, T, C> getNext() {
        return next;
    }

    @Override
    public void process(S item, Consumer<T> consumer, C context) {
        processor.process(item, r -> next.process(r, consumer, context), context);
    }
}