/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** E12 early-tail beans: out-of-order volumes, per-record watermark, sum reduce. */
public final class E12EarlyTailBeans {

    private E12EarlyTailBeans() {
    }

    /** Out-of-order volumes [0, 12000, 25000, 70000, 200000] with value = ts. */
    public static class VolumeSource implements SourceFunction<Long> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Long> ctx) throws Exception {
            for (long v : new long[]{0L, 12000L, 25000L, 70000L, 200000L}) {
                if (!running)
                    return;
                ctx.collect(v);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    public static class SumReduce implements ReduceFunction<Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Long reduce(Long a, Long b) {
            return a + b;
        }
    }

    public static class PartialSink implements SinkFunction<Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(Long value) {
            E12Stores.FLUSHED.add(value);
        }
    }

    static final class E12Stores {
        static final List<Long> FLUSHED =
                java.util.Collections.synchronizedList(new ArrayList<>());

        private E12Stores() {
        }
    }
}
