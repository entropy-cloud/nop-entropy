/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10/D9 end-to-end: a late record within allowedLateness triggers a re-fire
 * that carries the FULL aggregate (early + late elements), not a partial
 * aggregate of only the late element. The WindowOperator purge-on-fire must be
 * deferred to the cleanup point for windows with allowedLateness &gt; 0.
 */
class TestE2EWindowAllowedLatenessFullAggregate {

    static final class IntEvent implements Serializable {
        private static final long serialVersionUID = 1L;

        final String key;
        final long ts;
        final int value;

        IntEvent(String key, long ts, int value) {
            this.key = key;
            this.ts = ts;
            this.value = value;
        }
    }

    /**
     * Source timeline (event time): ts=1000 value=10, watermark 1999, ts=1500
     * value=7, watermark 6999. Watermark processing in the test env is async, so
     * both records may accumulate in the pane before the fire — this e2e proves
     * the allowedLateness-decorated chain builds and runs end-to-end; the D9
     * late-update semantics (full vs partial aggregate) is pinned by the
     * operator-level red test TestWindowOperatorAllowedLateness.
     */
    private static SourceFunction<IntEvent> lateSource() {
        return new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<IntEvent> ctx) throws Exception {
                ctx.collectWithTimestamp(new IntEvent("k1", 1000, 10), 1000);
                ctx.emitWatermark(1999);
                ctx.collectWithTimestamp(new IntEvent("k1", 1500, 7), 1500);
                ctx.emitWatermark(6999);
                Thread.sleep(200);
            }

            @Override
            public void cancel() {
            }
        };
    }

    @Test
    void lateUpdateCarriesFullAggregateEndToEnd() throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        KeyedStream<IntEvent, String> keyed = env
                .addSource(lateSource(), "src")
                .keyBy((KeySelector<IntEvent, String>) e -> e.key);

        // WI8b 先例：接口面缺 allowedLateness，直接持有 WindowedStreamImpl（M2 裁定零 core 变更）
        new WindowedStreamImpl<>(keyed, TumblingEventTimeWindows.of(2000L))
                .trigger(EventTimeTrigger.create())
                .allowedLateness(5000L)
                .aggregate(new io.nop.stream.core.common.functions.AggregateFunction<IntEvent, long[], Long>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public long[] createAccumulator() {
                        return new long[]{0};
                    }

                    @Override
                    public long[] add(IntEvent value, long[] accumulator) {
                        accumulator[0] += value.value;
                        return accumulator;
                    }

                    @Override
                    public Long getResult(long[] accumulator) {
                        return accumulator[0];
                    }

                    @Override
                    public long[] merge(long[] a, long[] b) {
                        a[0] += b[0];
                        return a;
                    }
                })
                .sink((SinkFunction<Long>) v -> results.add(String.valueOf(v)));

        env.execute("wi10-late-full-aggregate-e2e");

        // First fire (watermark 1999) emits 10; the late re-fire must emit 17
        // (10+7 full aggregate), not 7 (partial aggregate of the late element only).
        assertTrue(results.contains("17"),
                "late update must carry the full aggregate 10+7=17; results=" + results);
        assertTrue(results.contains("10"),
                "first fire must carry the early aggregate; results=" + results);
    }
}
