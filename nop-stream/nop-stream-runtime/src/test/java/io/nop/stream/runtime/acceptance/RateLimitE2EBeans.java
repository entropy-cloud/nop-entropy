/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** U3 (deferred-unlock, plan 2376): beans for the rate-limit declaration E2E. */
public final class RateLimitE2EBeans {

    private RateLimitE2EBeans() {
    }

    public static class TenRecordSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<String> ctx) {
            for (int i = 0; i < 10; i++) {
                ctx.collect("v" + i);
            }
        }

        @Override
        public void cancel() {
        }
    }

    public static class ThreeRecordSource implements SourceFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void run(SourceContext<String> ctx) {
            for (int i = 0; i < 3; i++) {
                ctx.collect("w" + i);
            }
        }

        @Override
        public void cancel() {
        }
    }

    public static class CollectingSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;

        static final List<String> ROWS = new CopyOnWriteArrayList<>();

        @Override
        public void consume(String value) {
            ROWS.add(value);
        }
    }

    interface Marker extends Serializable {
    }
}
