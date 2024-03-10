/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;

public class ReducingStateDescriptor<T> extends StateDescriptor<T> {
    private final Class<? extends SimpleAccumulator<T>> accumulatorType;

    public ReducingStateDescriptor(String name, Class<T> valueType, Class<? extends SimpleAccumulator<T>> accumulatorType) {
        super(name, valueType);
        this.accumulatorType = accumulatorType;
    }

    public Class<? extends SimpleAccumulator<T>> getAccumulatorType() {
        return accumulatorType;
    }
}
