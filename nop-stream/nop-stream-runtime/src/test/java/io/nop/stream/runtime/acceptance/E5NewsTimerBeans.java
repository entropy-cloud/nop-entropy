/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import io.nop.stream.core.common.functions.KeyedProcessFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WI23 E5: the NewsTimer degradation beans — per-key click buffering, an event-time
 * timer registered at the first click (publishTime + 1h compacted to millis in the
 * test data) that flushes the buffered click count, and a late-click buffer
 * (clicks after the flush go to the LATE journal).
 */
public final class E5NewsTimerBeans {

    private E5NewsTimerBeans() {
    }

    /**
     * Clicks: newsId=n1 clicks at ts 1000/2000; n2 at 1500. Timers: n1 @ 60000
     * (first ts + 1h compacted), n2 @ 61500. Watermark advances with each record
     * (per-record emission ts+100) so the n1 timer fires before the end.
     */
    public static class ClickSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
            Object[][] clicks = {
                    {"n1", 1000L}, {"n1", 2000L}, {"n2", 1500L}, {"n1", 9000L}};
            for (Object[] c : clicks) {
                if (!running)
                    return;
                Map<String, Object> rec = new HashMap<>();
                rec.put("newsId", c[0]);
                rec.put("ts", c[1]);
                ctx.collectWithTimestamp(rec, (Long) c[1]);
                // advance the watermark so registered event-time timers can fire
                ctx.emitWatermark((Long) c[1] + 100);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    public static class NewsTimerFn
            extends KeyedProcessFunction<String, Map<String, Object>, String> {
        private static final long serialVersionUID = 1L;
        private static final long FLUSH_HORIZON = 3600_000L;

        // per-key buffered click count + registered timer ts
        private final Map<String, Integer> counts = new HashMap<>();
        private final Map<String, Long> timerTs = new HashMap<>();

        @Override
        public void processElement(Map<String, Object> value,
                                   io.nop.stream.core.common.functions.ProcessFunction<Map<String, Object>, String>.Context ctx,
                                   io.nop.stream.core.util.Collector<String> out) throws Exception {
            String newsId = (String) value.get("newsId");
            long ts = ((Number) value.get("ts")).longValue();
            counts.merge(newsId, 1, Integer::sum);
            Long registered = timerTs.get(newsId);
            if (registered == null) {
                long deadline = ts + FLUSH_HORIZON;
                ctx.timerService().registerEventTimeTimer(deadline);
                timerTs.put(newsId, deadline);
            }
        }

        @Override
        public void onTimer(long timestamp,
                            io.nop.stream.core.common.functions.ProcessFunction<Map<String, Object>, String>.OnTimerContext ctx,
                            io.nop.stream.core.util.Collector<String> out) throws Exception {
            // the OnTimerContext (ProcessFunction-nested) has no getCurrentKey —
            // flush all buffered keys whose registered deadline matches this timer
            for (Map.Entry<String, Long> e : new java.util.ArrayList<>(timerTs.entrySet())) {
                if (e.getValue() == timestamp) {
                    timerTs.remove(e.getKey());
                    Integer count = counts.remove(e.getKey());
                    if (count != null && count > 0) {
                        out.collect("FLUSH:" + e.getKey() + ":" + count);
                    }
                }
            }
        }
    }

    public static class FlushedSink implements SinkFunction<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(String value) {
            E5Stores.FLUSHED.add(value);
        }
    }

    /** store accessor */
    public static List<String> flushed() {
        return E5Stores.FLUSHED;
    }

    static final class E5Stores implements Serializable {
        static final List<String> FLUSHED =
                java.util.Collections.synchronizedList(new ArrayList<>());
    }
}
