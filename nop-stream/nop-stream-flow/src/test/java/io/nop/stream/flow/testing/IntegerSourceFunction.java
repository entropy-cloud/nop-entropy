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
 * Test-only {@link SourceFunction} that emits a fixed sequence of {@link Integer} values
 * with repeated keys so {@code reduce} can combine them. Emits {@code [1, 1, 2, 2, 2]}.
 *
 * <p>Used by the advanced end-to-end pipeline test (keyBy → reduce → sink).
 */
public final class IntegerSourceFunction implements SourceFunction<Integer> {

    private static final long serialVersionUID = 1L;

    public static final List<Integer> FIXED_DATA = Arrays.asList(1, 1, 2, 2, 2);

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
