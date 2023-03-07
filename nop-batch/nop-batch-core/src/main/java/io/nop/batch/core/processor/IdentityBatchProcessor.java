/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.processor;

import io.nop.batch.core.IBatchProcessor;

import java.util.function.Consumer;

public class IdentityBatchProcessor<T, C> implements IBatchProcessor<T, T, C> {
    private static final IdentityBatchProcessor INSTANCE = new IdentityBatchProcessor();

    public static <T, C> IdentityBatchProcessor<T, C> instance() {
        return (IdentityBatchProcessor<T, C>) INSTANCE;
    }

    @Override
    public void process(T item, Consumer<T> consumer, C context) {
        consumer.accept(item);
    }
}