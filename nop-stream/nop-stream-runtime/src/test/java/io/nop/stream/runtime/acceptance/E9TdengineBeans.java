/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** E9 TDengine-degradation beans: sliding window avg with ACCUMULATING emissions. */
public final class E9TdengineBeans {

    private E9TdengineBeans() {
    }

    public static class CurrentSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
            int[][] points = {{1000, 10}, {2000, 20}, {4000, 30}, {6000, 40}};
            for (int[] p : points) {
                if (!running)
                    return;
                ctx.collectWithTimestamp(
                        Map.of("meter", "m1", "current", p[1]), (long) p[0]);
                ctx.emitWatermark((long) p[0] + 100);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** ACCUMULATING avg: emits the running average with the count for determinism. */
    public static class AvgFn
            implements AggregateFunction<Map<String, Object>, long[], String> {
        private static final long serialVersionUID = 1L;

        @Override
        public long[] createAccumulator() {
            return new long[2];
        }

        @Override
        public long[] add(Map<String, Object> value, long[] acc) {
            acc[0] += ((Number) value.get("current")).longValue();
            acc[1] += 1;
            return acc;
        }

        @Override
        public String getResult(long[] acc) {
            return "avg=" + acc[0] / acc[1] + ":n=" + acc[1];
        }

        @Override
        public long[] merge(long[] a, long[] b) {
            return new long[]{a[0] + b[0], a[1] + b[1]};
        }
    }

    public static class AvgSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(String value) {
            E9Stores.EMISSIONS.add(value);
        }
    }

    static final class E9Stores {
        static final List<String> EMISSIONS =
                java.util.Collections.synchronizedList(new ArrayList<>());
        static final Map<String, Integer> SEEN =
                new java.util.concurrent.ConcurrentHashMap<>();

        private E9Stores() {
        }
    }

    /** accessor */
    public static List<String> emissions() {
        return E9Stores.EMISSIONS;
    }

    interface Marker extends Serializable {
    }
}
