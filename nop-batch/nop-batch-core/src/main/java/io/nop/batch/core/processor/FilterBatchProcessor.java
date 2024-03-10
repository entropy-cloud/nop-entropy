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
import java.util.function.Predicate;

public class FilterBatchProcessor<T, C> implements IBatchProcessor<T, T, C> {
    private final Predicate<T> predicate;

    public FilterBatchProcessor(Predicate<T> predicate) {
        this.predicate = predicate;
    }

    @Override
    public void process(T item, Consumer<T> consumer, C context) {
        if (predicate.test(item)) {
            consumer.accept(item);
        }
    }
}