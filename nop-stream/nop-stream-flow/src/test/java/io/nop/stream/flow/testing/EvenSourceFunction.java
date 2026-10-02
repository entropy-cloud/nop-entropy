/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.testing;

import java.util.Arrays;
import java.util.List;

import io.nop.stream.core.common.functions.source.SourceFunction;

/**
 * Test-only {@link SourceFunction} that emits the fixed sequence {@code [2, 4]}.
 * Used by the union pipeline end-to-end test so the two merged sources carry
 * disjoint, easily assertable values.
 */
public final class EvenSourceFunction implements SourceFunction<Integer> {

    private static final long serialVersionUID = 1L;

    public static final List<Integer> FIXED_DATA = Arrays.asList(2, 4);

    private volatile boolean running = true;

    @Override
    public void run(SourceContext<Integer> ctx) {
        for (Integer element : FIXED_DATA) {
            if (!running) {
                break;
            }
            ctx.collect(element);
        }
    }

    @Override
    public void cancel() {
        running = false;
    }
}
