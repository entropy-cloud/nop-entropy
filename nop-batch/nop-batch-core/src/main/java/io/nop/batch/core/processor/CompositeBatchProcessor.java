/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.processor;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchProcessorProvider.IBatchProcessor;

import java.util.function.Consumer;

public final class CompositeBatchProcessor<S, R, T> implements IBatchProcessor<S, T> {
    private final IBatchProcessor<S, R> processor;
    private final IBatchProcessor<R, T> next;

    public CompositeBatchProcessor(IBatchProcessor<S, R> processor, IBatchProcessor<R, T> next) {
        this.processor = processor;
        this.next = next;
    }

    public IBatchProcessor<S, R> getProcessor() {
        return processor;
    }

    public IBatchProcessor<R, T> getNext() {
        return next;
    }

    @Override
    public void process(S item, Consumer<T> consumer, IBatchChunkContext context) {
        processor.process(item, r -> next.process(r, consumer, context), context);
    }
}