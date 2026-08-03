/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.testing;

import io.nop.stream.core.common.functions.ReduceFunction;

/**
 * Test-only {@link ReduceFunction} that sums two {@link Integer} values. Used by
 * {@code StreamModelDslBuilder} focused tests and the advanced end-to-end pipeline
 * (keyBy → reduce → sink) so the result is deterministic and easy to assert on.
 */
public final class SumReduceFunction implements ReduceFunction<Integer> {

    private static final long serialVersionUID = 1L;

    @Override
    public Integer reduce(Integer value1, Integer value2) {
        return value1 + value2;
    }
}
