/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.stream.core.common.functions.SinkFunction;

/**
 * WI8 Phase 2 (plan 2332) E2E sink for the tag channel (fed from the tag edge through
 * the tagMap transform) — collects under the "tag" key of {@link SideOutputSinkStore}.
 */
public final class SideOutputTagSink implements SinkFunction<Integer> {

    private static final long serialVersionUID = 1L;

    @Override
    public void consume(Integer value) {
        SideOutputSinkStore.add("tag", value);
    }
}
