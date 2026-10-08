/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.stream.core.common.functions.source.SourceFunction;

import java.util.List;

/**
 * WI8 Phase 2 (plan 2332) bounded test source for the side-output pipeline E2E:
 * emits the fixed sequence [1, 2, 3] then finishes.
 */
public final class SideOutputIntSourceFunction implements SourceFunction<Integer> {

    private static final long serialVersionUID = 1L;

    public static final List<Integer> FIXED_DATA = List.of(1, 2, 3);

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
