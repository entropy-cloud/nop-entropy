/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.functions.SinkFunction;

/**
 * WI17 test fixture: a {@link SinkFunction} collecting every element (flow-test
 * CollectingSinkFunction form, copied here because flow test classes are not on the
 * sql module's test classpath).
 */
public final class SqlTestSink implements SinkFunction<Object> {

    private static final long serialVersionUID = 1L;

    private final List<Object> collected = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void consume(Object value) {
        collected.add(value);
    }

    public List<Object> getCollected() {
        synchronized (collected) {
            return new ArrayList<>(collected);
        }
    }
}
