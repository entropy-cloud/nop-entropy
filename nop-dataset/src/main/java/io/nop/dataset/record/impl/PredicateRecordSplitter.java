/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.record.impl;

import io.nop.api.core.ApiConstants;
import io.nop.dataset.record.IRecordSplitter;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class PredicateRecordSplitter<T, C> implements IRecordSplitter<T, T, C> {
    private final Predicate<T> predicate;

    public PredicateRecordSplitter(Predicate<T> predicate) {
        this.predicate = predicate;
    }

    @Override
    public void split(T record, BiConsumer<String, T> collector, C context) {
        boolean b = predicate.test(record);
        if (b) {
            collector.accept(ApiConstants.YES_VALUE, record);
        } else {
            collector.accept(ApiConstants.NO_VALUE, record);
        }
    }
}