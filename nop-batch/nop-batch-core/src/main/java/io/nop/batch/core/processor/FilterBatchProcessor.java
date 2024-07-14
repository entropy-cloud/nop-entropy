/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.processor;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchProcessor;
import io.nop.batch.core.IBatchRecordFilter;

import java.util.function.Consumer;

public class FilterBatchProcessor<T> implements IBatchProcessor<T, T, IBatchChunkContext> {
    private final IBatchRecordFilter<T> predicate;

    public FilterBatchProcessor(IBatchRecordFilter<T> predicate) {
        this.predicate = predicate;
    }

    @Override
    public void process(T item, Consumer<T> consumer, IBatchChunkContext context) {
        if (predicate.accept(item, context.getTaskContext())) {
            consumer.accept(item);
        }
    }
}