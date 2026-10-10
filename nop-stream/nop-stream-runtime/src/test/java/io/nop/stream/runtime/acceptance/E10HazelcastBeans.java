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
import java.util.List;
import java.util.Map;

/**
 * E10 Hazelcast-degradation beans: session window (gap 3s) + maxBy(price) composite
 * aggregation. Clicks [0,2000,2500,9000] (millis): 0+2000+2500 merge into session
 * [0,5500) (gaps 2s/0.5s &lt; 3s), 9000 is the standalone session [9000,12000).
 */
public final class E10HazelcastBeans {

    private E10HazelcastBeans() {
    }

    public static class StockSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
            int[][] points = {{0, 42}, {2000, 41}, {2500, 43}, {9000, 99}};
            for (int[] p : points) {
                if (!running)
                    return;
                ctx.collectWithTimestamp(
                        Map.of("sym", "hzx", "price", p[1]), (long) p[0]);
                ctx.emitWatermark((long) p[0] + 100);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** maxBy(price): keeps the record with the highest price in the pane. */
    public static class MaxPriceFn
            implements AggregateFunction<Map<String, Object>, Map<String, Object>, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public Map<String, Object> createAccumulator() {
            return null;
        }

        @Override
        public Map<String, Object> add(Map<String, Object> value, Map<String, Object> acc) {
            if (acc == null) {
                return new java.util.HashMap<>(value);
            }
            Number accPrice = (Number) acc.get("price");
            Number newPrice = (Number) value.get("price");
            return newPrice.doubleValue() > accPrice.doubleValue()
                    ? new java.util.HashMap<>(value) : acc;
        }

        @Override
        public String getResult(Map<String, Object> acc) {
            return "maxPrice=" + acc.get("price");
        }

        @Override
        public Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
            if (a == null)
                return b;
            if (b == null)
                return a;
            Number aPrice = (Number) a.get("price");
            Number bPrice = (Number) b.get("price");
            return bPrice.doubleValue() > aPrice.doubleValue() ? b : a;
        }
    }

    public static class SessionSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(String value) {
            E10Stores.EMISSIONS.add(value);
        }
    }

    static final class E10Stores {
        static final List<String> EMISSIONS =
                java.util.Collections.synchronizedList(new ArrayList<>());

        private E10Stores() {
        }
    }

    /** accessor */
    public static List<String> emissions() {
        return E10Stores.EMISSIONS;
    }

    interface Marker extends Serializable {
    }
}
