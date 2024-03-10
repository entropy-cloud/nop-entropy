/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.accumulators;

import io.nop.api.core.time.CoreMetrics;

public class LastValue<T> implements SimpleAccumulator<T> {
    private T value;

    private long timestamp;

    public static <V> Class<? extends SimpleAccumulator<V>> type() {
        return (Class) LastValue.class;
    }

    @Override
    public void add(T value) {
        this.value = value;
        this.timestamp = CoreMetrics.currentTimeMillis();
    }

    @Override
    public T getLocalValue() {
        return value;
    }

    @Override
    public void resetLocal() {
        value = null;
    }

    @Override
    public void merge(Accumulator<T, T> other) {
        LastValue<T> v = (LastValue<T>) other;
        if (v.timestamp > timestamp) {
            this.value = v.value;
        }
    }

    @Override
    public Accumulator<T, T> clone() {
        LastValue<T> ret = new LastValue<>();
        ret.value = value;
        ret.timestamp = timestamp;
        return ret;
    }


    @Override
    public String toString() {
        return "LastValue " + this.value;
    }
}
