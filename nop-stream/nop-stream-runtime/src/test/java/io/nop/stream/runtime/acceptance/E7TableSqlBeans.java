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

/** E7 Table-API degradation beans: windowed sum aggregation over orders. */
public final class E7TableSqlBeans {

    private E7TableSqlBeans() {
    }

    public static class OrderSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
            Object[][] orders = {{"alice", 10L}, {"alice", 15L}, {"bob", 7L}};
            long ts = 1000L;
            for (Object[] o : orders) {
                if (!running)
                    return;
                ctx.collectWithTimestamp(
                        Map.of("user", o[0], "amount", o[1]), ts);
                ts += 1000;
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    public static class SumAmountFn
            implements AggregateFunction<Map<String, Object>, Map<String, Object>, Map<String, Object>> {
        private static final long serialVersionUID = 1L;

        @Override
        public Map<String, Object> createAccumulator() {
            Map<String, Object> acc = new java.util.HashMap<>();
            acc.put("sum", 0L);
            return acc;
        }

        @Override
        public Map<String, Object> add(Map<String, Object> value, Map<String, Object> acc) {
            acc.merge("sum", ((Number) value.get("amount")).longValue(), (a, b) -> (Long) a + (Long) b);
            acc.putIfAbsent("user", value.get("user"));
            return acc;
        }

        @Override
        public Map<String, Object> getResult(Map<String, Object> acc) {
            return acc;
        }

        @Override
        public Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
            b.merge("sum", a.get("sum"), (x, y) -> (Long) x + (Long) y);
            return b;
        }
    }

    public static class SumSink implements SinkFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(Map<String, Object> value) {
            E7Stores.SUMS.add(value);
        }
    }

    static final class E7Stores {
        static final List<Map<String, Object>> SUMS =
                java.util.Collections.synchronizedList(new ArrayList<>());

        private E7Stores() {
        }
    }

    /** accessor for assertions */
    public static List<Map<String, Object>> sums() {
        return E7Stores.SUMS;
    }

    interface Marker extends Serializable {
    }
}
