/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.testing;

import java.util.Arrays;
import java.util.List;

import io.nop.stream.core.common.functions.source.SourceFunction;

/**
 * A test-only {@link SourceFunction} that emits the fixed sequence {@code ["a", "b", "c"]}.
 * Used by {@code StreamModelDslBuilder} end-to-end tests so the pipeline output is
 * deterministic and easy to assert on.
 */
public final class TestSourceFunction implements SourceFunction<String> {

    private static final long serialVersionUID = 1L;

    public static final List<String> FIXED_DATA = Arrays.asList("a", "b", "c");

    private volatile boolean running = true;

    @Override
    public void run(SourceContext<String> ctx) {
        for (String element : FIXED_DATA) {
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
