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
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.IWindowOperatorFactory;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.WindowSpec;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.assigners.GlobalWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.CountTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI38 (plan 03, FU-8 fix): the accumulation mode declared on the
 * {@link WindowedStreamImpl} fluent API must reach the factory. Since WI8
 * (plan 2420) the factory receives a single immutable {@link WindowSpec}, so
 * the capturing fake records the whole spec — the per-call-site mode cases
 * below pin the fluent → spec wiring, and {@code fullSpecReachesFactory} pins
 * every spec field at once (the WI8 anti-parameter-creep pin for the fluent
 * path: a field dropped by {@code WindowedStreamImpl.buildSpec} fails here).
 * The runtime module separately proves the real factory carries the spec into
 * the operator (TestWindowSpecFieldTransfer).
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
        assertEquals(AccumulationMode.DISCARDING, factory.appliedSpec.getAccumulationMode());
    }

    @Test
    void aggregateCallSitePassesDeclaredMode() {
        windowed(new CapturingFactory())
                .accumulationMode(AccumulationMode.DISCARDING)
                .aggregate(SUM);
        assertEquals(AccumulationMode.DISCARDING, factory.aggregatedSpec.getAccumulationMode());
    }

    @Test
    void reduceCallSitePassesDeclaredMode() {
        windowed(new CapturingFactory())
                .accumulationMode(AccumulationMode.ACCUMULATING)
                .reduce((ReduceFunction<Integer>) (a, b) -> a + b);
        assertEquals(AccumulationMode.ACCUMULATING, factory.reducedSpec.getAccumulationMode());
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
        assertEquals(AccumulationMode.ACCUMULATING, factory.processedSpec.getAccumulationMode());
    }

    @Test
    void undeclaredModeStaysNullAtCallSite() {
        // Pure Java API path without a declaration: null reaches the factory, and the
        // operator keeps its legacy ACCUMULATING default.
        WindowedStreamImpl<Integer, Integer, GlobalWindow> stream = windowed(new CapturingFactory());
        assertNull(stream.getAccumulationMode(), "no declaration means null on the fluent API");
        stream.aggregate(SUM);
        assertNull(factory.aggregatedSpec.getAccumulationMode(),
                "null must reach the factory unchanged (legacy default)");
    }

    /**
     * WI8 (plan 2420) anti-parameter-creep pin, fluent path: every axis set on the
     * fluent API arrives in the spec the factory receives — nothing is dropped or
     * replaced by a hardcoded default inside {@code WindowedStreamImpl.buildSpec}.
     * All fields are set to non-default values so a dropped field cannot pass by
     * coinciding with the spec's own defaults.
     */
    @Test
    void fullSpecReachesFactoryThroughFluentApi() {
        Trigger<? super Integer, ? super GlobalWindow> customTrigger = CountTrigger.of(3);
        Evictor<? super Integer, ? super GlobalWindow> evictor = CountEvictor.of(2);
        OutputTag<Integer> lateTag = new OutputTag<>("late-tag", BasicTypeInfo.of(Integer.class));

        windowed(new CapturingFactory())
                .trigger(customTrigger)
                .evictor(evictor)
                .allowedLateness(7777L)
                .sideOutputLateData(lateTag)
                .maxDelay(8888L)
                .emitWindowEvents()
                .accumulationMode(AccumulationMode.DISCARDING)
                .aggregate(SUM);

        WindowSpec<Integer, ?, ?> spec = factory.aggregatedSpec;
        assertNotNull(spec, "the factory must receive the assembled spec");
        assertSame(customTrigger, spec.getTrigger(), "trigger must round-trip");
        assertSame(evictor, spec.getEvictor(), "evictor must round-trip");
        assertEquals(7777L, spec.getAllowedLateness(), "allowedLateness must round-trip");
        assertEquals(lateTag, spec.getLateDataOutputTag(), "lateDataOutputTag must round-trip");
        assertEquals(8888L, spec.getMaxDelay(), "maxDelay must round-trip");
        assertTrue(spec.isEmitWindowEvents(), "emitWindowEvents must round-trip");
        assertNotNull(spec.getWindowEventsOutputTag(),
                "emitWindowEvents() derives a dedicated lifecycle-event tag");
        assertEquals(AccumulationMode.DISCARDING, spec.getAccumulationMode());
        assertEquals(Object.class, spec.getElementType(),
                "elementType comes from inferElementClass(getType()); this fromElements source "
                        + "stays uninferable (UnknownTypeInformation → Object.class, F-05 fallback)");
        assertEquals(Object.class, spec.getKeyClass(),
                "the fluent path passes the erasure placeholder keyClass (Object.class)");
    }

    /**
     * Records the whole spec per operation (WI8 contract); the created operator is
     * never executed in this test.
     */
    private static class CapturingFactory implements IWindowOperatorFactory {
        private static final long serialVersionUID = 1L;

        WindowSpec<Integer, ?, ?> appliedSpec;
        WindowSpec<Integer, ?, ?> aggregatedSpec;
        WindowSpec<Integer, ?, ?> reducedSpec;
        WindowSpec<Integer, ?, ?> processedSpec;

        @Override
        public <IN, ACC, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createAggregateOperator(
                WindowSpec<IN, K, W> spec, AggregateFunction<IN, ACC, OUT> aggregateFunction,
                Class<ACC> accumulatorType) {
            this.aggregatedSpec = asIntegerSpec(spec);
            return null;
        }

        @Override
        public <IN, K, W extends Window>
        OneInputStreamOperator<IN, IN> createReduceOperator(
                WindowSpec<IN, K, W> spec, ReduceFunction<IN> reduceFunction) {
            this.reducedSpec = asIntegerSpec(spec);
            return null;
        }

        @Override
        public <IN, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createApplyOperator(
                WindowSpec<IN, K, W> spec, WindowFunction<IN, OUT, K, W> windowFunction) {
            this.appliedSpec = asIntegerSpec(spec);
            return null;
        }

        @Override
        public <IN, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createProcessOperator(
                WindowSpec<IN, K, W> spec, ProcessWindowFunction<IN, OUT, K, W> processWindowFunction) {
            this.processedSpec = asIntegerSpec(spec);
            return null;
        }

        @SuppressWarnings("unchecked")
        private static <IN, K, W extends Window> WindowSpec<Integer, K, W> asIntegerSpec(WindowSpec<IN, K, W> spec) {
            return (WindowSpec<Integer, K, W>) spec;
        }
    }
}
