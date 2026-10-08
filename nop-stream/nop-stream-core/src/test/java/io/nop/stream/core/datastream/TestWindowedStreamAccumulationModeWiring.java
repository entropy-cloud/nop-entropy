/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.IWindowOperatorFactory;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.WindowLifecycleEvent;
import io.nop.stream.core.windowing.assigners.GlobalWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI38 (plan 03, FU-8 fix): the accumulation mode declared on the
 * {@link WindowedStreamImpl} fluent API must be passed through every factory call
 * site (apply/aggregate/reduce/process). A capturing fake factory records the
 * fourth-generation argument so a dropped parameter at any call site fails here —
 * the runtime module separately proves the real factory carries it into the
 * operator.
 */
public class TestWindowedStreamAccumulationModeWiring {

    private CapturingFactory factory;

    @AfterEach
    void resetDefaultFactory() {
        WindowedStreamImpl.setDefaultFactory(null);
        factory = null;
    }

    private WindowedStreamImpl<Integer, Integer, GlobalWindow> windowed(CapturingFactory factory) {
        this.factory = factory;
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        WindowedStreamImpl.setDefaultFactory(factory);
        KeyedStream<Integer, Integer> keyed =
                env.fromElements(1, 2, 3).keyBy((KeySelector<Integer, Integer>) i -> 0);
        return new WindowedStreamImpl<>(keyed, GlobalWindows.create());
    }

    private static final AggregateFunction<Integer, int[], Integer> SUM =
            new AggregateFunction<Integer, int[], Integer>() {
                @Override
                public int[] createAccumulator() {
                    return new int[]{0};
                }

                @Override
                public int[] add(Integer value, int[] accumulator) {
                    accumulator[0] += value;
                    return accumulator;
                }

                @Override
                public Integer getResult(int[] accumulator) {
                    return accumulator[0];
                }

                @Override
                public int[] merge(int[] a, int[] b) {
                    a[0] += b[0];
                    return a;
                }
            };

    @Test
    void applyCallSitePassesDeclaredMode() {
        windowed(new CapturingFactory())
                .accumulationMode(AccumulationMode.DISCARDING)
                .apply((WindowFunction<Integer, String, Integer, GlobalWindow>)
                        (key, window, input, out) -> {
                        });
        assertEquals(AccumulationMode.DISCARDING, factory.appliedMode);
    }

    @Test
    void aggregateCallSitePassesDeclaredMode() {
        windowed(new CapturingFactory())
                .accumulationMode(AccumulationMode.DISCARDING)
                .aggregate(SUM);
        assertEquals(AccumulationMode.DISCARDING, factory.aggregatedMode);
    }

    @Test
    void reduceCallSitePassesDeclaredMode() {
        windowed(new CapturingFactory())
                .accumulationMode(AccumulationMode.ACCUMULATING)
                .reduce((ReduceFunction<Integer>) (a, b) -> a + b);
        assertEquals(AccumulationMode.ACCUMULATING, factory.reducedMode);
    }

    @Test
    void processCallSitePassesDeclaredMode() {
        windowed(new CapturingFactory())
                .accumulationMode(AccumulationMode.ACCUMULATING)
                .process(new ProcessWindowFunction<Integer, String, Integer, GlobalWindow>() {
                    @Override
                    public void process(Integer key, GlobalWindow window, Iterable<Integer> input,
                                        Context context, Collector<String> out) {
                    }
                });
        assertEquals(AccumulationMode.ACCUMULATING, factory.processedMode);
    }

    @Test
    void undeclaredModeStaysNullAtCallSite() {
        // Pure Java API path without a declaration: null reaches the factory, and the
        // operator keeps its legacy ACCUMULATING default.
        WindowedStreamImpl<Integer, Integer, GlobalWindow> stream = windowed(new CapturingFactory());
        assertNull(stream.getAccumulationMode(), "no declaration means null on the fluent API");
        stream.aggregate(SUM);
        assertNull(factory.aggregatedMode, "null must reach the factory unchanged (legacy default)");
    }

    /**
     * Records only the fourth-generation mode argument; the abstract base generation
     * methods return null (the created operator is never executed in this test).
     */
    private static class CapturingFactory implements IWindowOperatorFactory {
        private static final long serialVersionUID = 1L;

        AccumulationMode appliedMode;
        AccumulationMode aggregatedMode;
        AccumulationMode reducedMode;
        AccumulationMode processedMode;

        @Override
        public <IN, ACC, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createAggregateOperator(
                WindowAssigner<? super IN, W> windowAssigner, Trigger<? super IN, ? super W> trigger,
                Evictor<? super IN, W> evictor, long allowedLateness,
                AggregateFunction<IN, ACC, OUT> aggregateFunction, Class<ACC> accumulatorType,
                KeySelector<IN, K> keySelector, Class<K> keyClass) {
            return null;
        }

        @Override
        public <IN, K, W extends Window>
        OneInputStreamOperator<IN, IN> createReduceOperator(
                WindowAssigner<? super IN, W> windowAssigner, Trigger<? super IN, ? super W> trigger,
                Evictor<? super IN, W> evictor, long allowedLateness,
                ReduceFunction<IN> reduceFunction, Class<IN> valueType,
                KeySelector<IN, K> keySelector, Class<K> keyClass) {
            return null;
        }

        @Override
        public <IN, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createApplyOperator(
                WindowAssigner<? super IN, W> windowAssigner, Trigger<? super IN, ? super W> trigger,
                Evictor<? super IN, W> evictor, long allowedLateness,
                WindowFunction<IN, OUT, K, W> windowFunction, Class<IN> elementType,
                KeySelector<IN, K> keySelector, Class<K> keyClass) {
            return null;
        }

        @Override
        public <IN, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createProcessOperator(
                WindowAssigner<? super IN, W> windowAssigner, Trigger<? super IN, ? super W> trigger,
                Evictor<? super IN, W> evictor, long allowedLateness,
                ProcessWindowFunction<IN, OUT, K, W> processWindowFunction, Class<IN> elementType,
                KeySelector<IN, K> keySelector, Class<K> keyClass) {
            return null;
        }

        @Override
        public <IN, ACC, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createAggregateOperator(
                WindowAssigner<? super IN, W> windowAssigner, Trigger<? super IN, ? super W> trigger,
                Evictor<? super IN, W> evictor, long allowedLateness,
                AggregateFunction<IN, ACC, OUT> aggregateFunction, Class<ACC> accumulatorType,
                Class<IN> elementType, KeySelector<IN, K> keySelector, Class<K> keyClass,
                OutputTag<IN> lateDataOutputTag, long maxDelay, boolean emitWindowEvents,
                OutputTag<WindowLifecycleEvent> windowEventsOutputTag,
                AccumulationMode accumulationMode) {
            this.aggregatedMode = accumulationMode;
            return null;
        }

        @Override
        public <IN, K, W extends Window>
        OneInputStreamOperator<IN, IN> createReduceOperator(
                WindowAssigner<? super IN, W> windowAssigner, Trigger<? super IN, ? super W> trigger,
                Evictor<? super IN, W> evictor, long allowedLateness,
                ReduceFunction<IN> reduceFunction, Class<IN> valueType,
                KeySelector<IN, K> keySelector, Class<K> keyClass,
                OutputTag<IN> lateDataOutputTag, long maxDelay, boolean emitWindowEvents,
                OutputTag<WindowLifecycleEvent> windowEventsOutputTag,
                AccumulationMode accumulationMode) {
            this.reducedMode = accumulationMode;
            return null;
        }

        @Override
        public <IN, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createApplyOperator(
                WindowAssigner<? super IN, W> windowAssigner, Trigger<? super IN, ? super W> trigger,
                Evictor<? super IN, W> evictor, long allowedLateness,
                WindowFunction<IN, OUT, K, W> windowFunction, Class<IN> elementType,
                KeySelector<IN, K> keySelector, Class<K> keyClass,
                OutputTag<IN> lateDataOutputTag, long maxDelay, boolean emitWindowEvents,
                OutputTag<WindowLifecycleEvent> windowEventsOutputTag,
                AccumulationMode accumulationMode) {
            this.appliedMode = accumulationMode;
            return null;
        }

        @Override
        public <IN, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createProcessOperator(
                WindowAssigner<? super IN, W> windowAssigner, Trigger<? super IN, ? super W> trigger,
                Evictor<? super IN, W> evictor, long allowedLateness,
                ProcessWindowFunction<IN, OUT, K, W> processWindowFunction, Class<IN> elementType,
                KeySelector<IN, K> keySelector, Class<K> keyClass,
                OutputTag<IN> lateDataOutputTag, long maxDelay, boolean emitWindowEvents,
                OutputTag<WindowLifecycleEvent> windowEventsOutputTag,
                AccumulationMode accumulationMode) {
            this.processedMode = accumulationMode;
            return null;
        }
    }
}
