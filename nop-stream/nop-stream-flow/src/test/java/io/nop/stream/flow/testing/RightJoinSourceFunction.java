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
 * WI13 test-only right join input: emits {@code [k2:x, k1:y]} (reversed order — the
 * join must not depend on emission order).
 */
public final class RightJoinSourceFunction implements SourceFunction<JoinTestRecord> {

    private static final long serialVersionUID = 1L;

    public static final List<JoinTestRecord> FIXED_DATA =
            Arrays.asList(new JoinTestRecord("k2", "x"), new JoinTestRecord("k1", "y"));

    private volatile boolean running = true;

    @Override
    public void run(SourceContext<JoinTestRecord> ctx) {
        for (JoinTestRecord element : FIXED_DATA) {
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
