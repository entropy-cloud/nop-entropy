/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.processor;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchProcessorProvider;
import io.nop.batch.core.IBatchProcessorProvider.IBatchProcessor;
import io.nop.batch.core.IBatchRecordFilter;
import io.nop.batch.core.IBatchTaskContext;

import java.util.function.Consumer;

public class FilterBatchProcessor<T> implements IBatchProcessor<T, T>, IBatchProcessorProvider<T,T> {
    private final IBatchRecordFilter<T,IBatchChunkContext> predicate;

    public FilterBatchProcessor(IBatchRecordFilter<T,IBatchChunkContext> predicate) {
        this.predicate = predicate;
    }

    @Override
    public IBatchProcessor<T, T> setup(IBatchTaskContext taskContext) {
        return this;
    }

    @Override
    public void process(T item, Consumer<T> consumer, IBatchChunkContext context) {
        if (predicate.accept(item, context)) {
            consumer.accept(item);
        }
    }
}