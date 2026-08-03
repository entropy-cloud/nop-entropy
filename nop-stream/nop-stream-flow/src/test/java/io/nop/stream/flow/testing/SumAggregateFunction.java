/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.testing;

import io.nop.stream.core.common.functions.AggregateFunction;

/**
 * Test-only {@link AggregateFunction} that accumulates the sum of {@link Integer} values.
 * The accumulator is a single-element {@code int[]} so it is mutable and serializable.
 *
 * <p>Used by {@code StreamModelDslBuilder} window/aggregate focused tests.
 */
public final class SumAggregateFunction implements AggregateFunction<Integer, int[], Integer> {

    private static final long serialVersionUID = 1L;

    @Override
    public int[] createAccumulator() {
        return new int[]{0};
    }

    @Override
    public int[] add(Integer value, int[] accumulator) {
        accumulator[0] += value;
        return accumulator;
    }

    @Override
    public Integer getResult(int[] accumulator) {
        return accumulator[0];
    }

    @Override
    public int[] merge(int[] a, int[] b) {
        a[0] += b[0];
        return a;
    }
}
