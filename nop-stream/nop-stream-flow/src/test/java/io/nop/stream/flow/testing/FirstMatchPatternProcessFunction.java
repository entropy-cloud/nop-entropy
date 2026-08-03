/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.testing;

import java.util.List;
import java.util.Map;

import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.core.util.Collector;

/**
 * Test-only {@link PatternProcessFunction} that emits the first matched event of each
 * pattern match. Used by the CEP focused test in {@code StreamModelDslBuilder}.
 */
public final class FirstMatchPatternProcessFunction<IN> extends PatternProcessFunction<IN, IN> {

    private static final long serialVersionUID = 1L;

    @Override
    public void processMatch(Map<String, List<IN>> match, Context ctx, Collector<IN> out) {
        for (List<IN> events : match.values()) {
            if (events != null && !events.isEmpty()) {
                out.collect(events.get(0));
                return;
            }
        }
    }
}
