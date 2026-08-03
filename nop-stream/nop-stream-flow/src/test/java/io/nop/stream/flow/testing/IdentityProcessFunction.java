/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.testing;

import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.util.Collector;

/**
 * Test-only {@link ProcessFunction} that forwards every input element unchanged. Used by
 * the {@code StreamModelDslBuilder} process-transform focused test.
 */
public final class IdentityProcessFunction<IN> extends ProcessFunction<IN, IN> {

    private static final long serialVersionUID = 1L;

    @Override
    public void processElement(IN value, Context ctx, Collector<IN> out) {
        out.collect(value);
    }
}
