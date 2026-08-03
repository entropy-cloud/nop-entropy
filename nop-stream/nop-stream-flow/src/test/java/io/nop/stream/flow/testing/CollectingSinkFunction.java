/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.functions.SinkFunction;

/**
 * A test-only {@link SinkFunction} that collects every consumed element into a thread-safe
 * internal list. Used by {@code StreamModelDslBuilder} end-to-end tests so the builder's
 * output can be asserted on.
 *
 * <p>The collected list is exposed via {@link #getCollected()} as an unmodifiable view.
 */
public final class CollectingSinkFunction<T> implements SinkFunction<T> {

    private static final long serialVersionUID = 1L;

    private final List<T> collected = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void consume(T value) {
        collected.add(value);
    }

    public List<T> getCollected() {
        synchronized (collected) {
            return new ArrayList<>(collected);
        }
    }
}
