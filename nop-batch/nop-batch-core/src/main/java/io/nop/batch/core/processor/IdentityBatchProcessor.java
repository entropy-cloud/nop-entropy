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

public class IdentityBatchProcessor<T> implements IBatchProcessor<T, T> {
    private static final IdentityBatchProcessor INSTANCE = new IdentityBatchProcessor();

    public static <T> IdentityBatchProcessor<T> instance() {
        return (IdentityBatchProcessor<T>) INSTANCE;
    }

    @Override
    public void process(T item, Consumer<T> consumer, IBatchChunkContext context) {
        consumer.accept(item);
    }
}