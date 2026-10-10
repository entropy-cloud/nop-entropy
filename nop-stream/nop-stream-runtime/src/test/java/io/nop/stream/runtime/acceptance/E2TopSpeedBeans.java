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
import io.nop.stream.core.windowing.triggers.Trigger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WI21 E2: the TopSpeedWindowing degradation beans — DeltaTrigger (speed delta
 * &gt; 50 fires) over GlobalWindows with a TimeEvictor and a maxBy aggregation.
 */
public final class E2TopSpeedBeans {

    private E2TopSpeedBeans() {
    }

    /** The speed-delta contract: |newSpeed - oldSpeed| as the delta. */
    public static class SpeedDelta implements io.nop.stream.core.windowing.delta.DeltaFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;

        @Override
        public double getDelta(Map<String, Object> oldDataPoint, Map<String, Object> newDataPoint) {
            return Math.abs(((Number) newDataPoint.get("speed")).doubleValue()
                    - ((Number) oldDataPoint.get("speed")).doubleValue());
        }
    }

    /** Builds the DeltaTrigger over the speed-delta function (threshold 50). */
    public static class DeltaTriggerFactory implements Serializable {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static Trigger<Map<String, Object>, ?> create() {
            return io.nop.stream.core.windowing.triggers.DeltaTrigger.of(
                    50d, (io.nop.stream.core.windowing.delta.DeltaFunction) new SpeedDelta(),
                    (Class) Map.class);
        }
    }

    /** Emits the ride sequence for one car: speeds 40, 100 (delta 60 → fires), 120, 130. */
    public static class RideSource implements SourceFunction<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private boolean running = true;

        @Override
        public void run(SourceContext<Map<String, Object>> ctx) throws Exception {
            int[] speeds = {40, 100, 120, 130};
            long ts = 1000L;
            for (int speed : speeds) {
                if (!running)
                    return;
                // collectWithTimestamp: the TimeEvictor needs element timestamps to
                // apply its horizon (untimestamped elements are evicted as a whole)
                ctx.collectWithTimestamp(Map.of("car", "cab-1", "speed", speed), ts);
                ts += 1000;
                Thread.sleep(10);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }

    /** maxBy aggregation over the pane: keeps the record with the highest speed. */
    public static class MaxBySpeedFn
            implements io.nop.stream.core.common.functions.AggregateFunction<Map<String, Object>, Map<String, Object>, Map<String, Object>> {
        private static final long serialVersionUID = 1L;

        @Override
        public Map<String, Object> createAccumulator() {
            return null;
        }

        @Override
        public Map<String, Object> add(Map<String, Object> value, Map<String, Object> accumulator) {
            if (accumulator == null) {
                return new java.util.HashMap<>(value);
            }
            Number accSpeed = (Number) accumulator.get("speed");
            Number newSpeed = (Number) value.get("speed");
            if (newSpeed.doubleValue() > accSpeed.doubleValue()) {
                return new java.util.HashMap<>(value);
            }
            return accumulator;
        }

        @Override
        public Map<String, Object> getResult(Map<String, Object> accumulator) {
            return accumulator;
        }

        @Override
        public Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
            if (a == null) {
                return b;
            }
            if (b == null) {
                return a;
            }
            Number aSpeed = (Number) a.get("speed");
            Number bSpeed = (Number) b.get("speed");
            return bSpeed.doubleValue() > aSpeed.doubleValue() ? b : a;
        }
    }

    public static class TopSpeedSink implements SinkFunction<Object> {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(Object value) {
            E2Stores.EMISSIONS.add(value);
        }
    }
}
