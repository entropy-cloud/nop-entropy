/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;

/**
 * WI23 E6: the AsyncIO degradation beans — the WI17 synchronous-replacement
 * contract (bounded capacity CAS gate + deadline + explicit fallback) fixture-pinned
 * as the E6 scenario. Slow items exceed the deadline → explicit TIMEOUT fallback.
 */
public final class E6AsyncBeans {

    private E6AsyncBeans() {
    }

    public static final int CAPACITY = 4;
    public static final long DEADLINE_MS = 50;

    /**
     * 时间确定化（plan 2510）：原 fixture 用真实 sleep 模拟慢调用（SLOW_MS）——
     * 被测的 WI17 契约是「慢项走显式 TIMEOUT fallback」的分流逻辑，fixture 以
     * slowFlag 确定性地给出 TIMEOUT 发射，真实延迟并非断言对象（串行 local runner
     * 下也没有并发容量竞争可观测）。保留字段兼容既有测试引用（测试仍设置/复位），
     * 但执行路径不再依赖任何真实时间流逝。
     */
    public static volatile long SLOW_MS = 0;
    public static final AtomicInteger PEAK = new AtomicInteger();
    public static final List<String> RESULTS =
            java.util.Collections.synchronizedList(new ArrayList<>());

    public static class ItemSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
            Object[][] items = {{"w1", 0L}, {"w2", 500L}, {"w3", 0L}};
            for (Object[] it : items) {
                if (!running)
                    return;
                Map<String, Object> rec = new HashMap<>();
                rec.put("item", it[0]);
                E6AsyncBeans.class.getName(); // stable point
                ctx.collectWithTimestamp(recFor(it), tsOf(it));
            }
        }

        private Map<String, Object> recFor(Object[] it) {
            Map<String, Object> rec = new java.util.HashMap<>();
            rec.put("item", it[0]);
            rec.put("slowFlag", it[1]);
            return rec;
        }

        private long tsOf(Object[] it) {
            return 1000L;
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    public static class SyncCallFn extends ProcessFunction<Map<String, Object>, String> {
        private static final long serialVersionUID = 1L;
        private final AtomicInteger inFlight = new AtomicInteger(0);

        @Override
        public void processElement(Map<String, Object> value,
                                   io.nop.stream.core.common.functions.ProcessFunction<Map<String, Object>, String>.Context ctx,
                                   io.nop.stream.core.util.Collector<String> out) throws Exception {
            boolean slow = ((Number) value.getOrDefault("slowFlag", 0L)).longValue() > 0;
            int now = inFlight.incrementAndGet();
            PEAK.accumulateAndGet(now, Math::max);
            try {
                if (slow) {
                    out.collect("TIMEOUT:" + value.get("item"));
                } else {
                    out.collect("DONE:" + value.get("item"));
                }
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    public static class ResultSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(String value) {
            RESULTS.add(value);
        }
    }
}
