/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators.windowing;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.WindowSpec;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.WindowLifecycleEvent;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 (plan 2420) anti-parameter-creep pin, direct-call path. The former
 * 17-method factory surface failed by silently dropping newly-added arguments
 * in each generation's default shim. This test constructs a fully-loaded
 * {@link WindowSpec} (every axis at a non-default value) and asserts that the
 * real {@link WindowOperatorFactoryImpl} transfers all twelve spec fields plus
 * the operation function into the created {@link WindowOperator} — so a field
 * dropped in {@code configureBuilder} fails here. The fluent path
 * (WindowedStreamImpl.buildSpec) is pinned separately by
 * TestWindowedStreamAccumulationModeWiring#fullSpecReachesFactoryThroughFluentApi.
 *
 * <p>Builder contract is pinned too: the three required values fail fast and
 * the copy builder carries every field.
 */
public class TestWindowSpecFieldTransfer {

    private static final WindowAssigner<? super String, TimeWindow> ASSIGNER = TumblingEventTimeWindows.of(100);
    private static final Trigger<? super String, ? super TimeWindow> TRIGGER = EventTimeTrigger.create();
    private static final Evictor<? super String, TimeWindow> EVICTOR = CountEvictor.of(2);
    private static final KeySelector<String, String> KEY_SELECTOR = v -> v;
    private static final long ALLOWED_LATENESS = 4242L;
    private static final long MAX_DELAY = 8888L;
    private static final OutputTag<String> LATE_TAG = new OutputTag<>("spec-late", BasicTypeInfo.of(String.class));
    private static final OutputTag<WindowLifecycleEvent> EVENTS_TAG =
            new OutputTag<>("spec-events", BasicTypeInfo.of(WindowLifecycleEvent.class));

    private static final AggregateFunction<String, int[], Integer> SUM =
            new AggregateFunction<String, int[], Integer>() {
                private static final long serialVersionUID = 1L;

                @Override
                public int[] createAccumulator() {
                    return new int[]{0};
                }

                @Override
                public int[] add(String value, int[] accumulator) {
                    accumulator[0] += value.length();
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

    private static final ReduceFunction<String> REDUCE_FN = (a, b) -> a + b;

    private static final WindowFunction<String, String, String, TimeWindow> APPLY_FN =
            (key, window, input, out) -> out.collect("apply");

    private static final ProcessWindowFunction<String, String, String, TimeWindow> PROCESS_FN =
            new ProcessWindowFunction<String, String, String, TimeWindow>() {
                @Override
                public void process(String key, TimeWindow window, Iterable<String> input,
                                    Context context, Collector<String> out) {
                    out.collect("process");
                }
            };

    /**
     * Fully-loaded spec: every configuration axis at a distinct non-default value,
     * so a dropped field can never coincide with the spec's own defaults.
     */
    private static WindowSpec<String, String, TimeWindow> fullSpec() {
        return WindowSpec.<String, String, TimeWindow>builder(ASSIGNER, KEY_SELECTOR, String.class)
                .trigger(TRIGGER)
                .evictor(EVICTOR)
                .allowedLateness(ALLOWED_LATENESS)
                .elementType(String.class)
                .lateDataOutputTag(LATE_TAG)
                .maxDelay(MAX_DELAY)
                .emitWindowEvents(true)
                .windowEventsOutputTag(EVENTS_TAG)
                .accumulationMode(AccumulationMode.DISCARDING)
                .build();
    }

    private static void assertSpecFieldsArrived(WindowOperator<?, ?, ?, ?, ?> op, Class<?> expectedAccClass) {
        assertSame(ASSIGNER, WindowRoundTripAssertions.readField(op, "windowAssigner"), "windowAssigner");
        assertSame(TRIGGER, WindowRoundTripAssertions.readField(op, "trigger"), "trigger");
        assertSame(EVICTOR, WindowRoundTripAssertions.readField(op, "evictor"), "evictor");
        assertEquals(ALLOWED_LATENESS, WindowRoundTripAssertions.readField(op, "allowedLateness"), "allowedLateness");
        assertSame(KEY_SELECTOR, WindowRoundTripAssertions.readField(op, "keySelector"), "keySelector");
        assertSame(String.class, WindowRoundTripAssertions.readField(op, "keyClass"), "keyClass");
        assertSame(LATE_TAG, WindowRoundTripAssertions.readField(op, "lateDataOutputTag"), "lateDataOutputTag");
        assertEquals(MAX_DELAY, WindowRoundTripAssertions.readField(op, "maxDelay"), "maxDelay");
        assertEquals(Boolean.TRUE, WindowRoundTripAssertions.readField(op, "emitWindowEvents"), "emitWindowEvents");
        assertSame(EVENTS_TAG, WindowRoundTripAssertions.readField(op, "windowEventsOutputTag"),
                "windowEventsOutputTag");
        assertSame(AccumulationMode.DISCARDING, WindowRoundTripAssertions.readField(op, "accumulationMode"),
                "accumulationMode");
        assertSame(expectedAccClass, WindowRoundTripAssertions.readField(op, "accClass"), "accClass");
    }

    private static void assertListStateElementType(WindowOperator<?, ?, ?, ?, ?> op, Class<?> expected) {
        Object stateDesc = WindowRoundTripAssertions.readField(op, "windowStateDescriptor");
        assertTrue(stateDesc instanceof ListStateDescriptor,
                "evictor path must buffer via ListStateDescriptor, got " + stateDesc);
        assertSame(expected, ((ListStateDescriptor<?>) stateDesc).getValueType(),
                "spec.elementType must reach the state descriptor");
    }

    @Test
    void aggregateTransfersFullSpec() {
        // P1-01 口径：accumulatorType 传 Object.class（WindowedStreamImpl 调用点的 erasure
        // 强转形态），accClass 期望从 createAccumulator() 推断出的真实累加器类型；
        // spec.elementType 的到达断言走 ListStateDescriptor valueType。
        Class<int[]> recordedAcc = (Class<int[]>) (Class<?>) Object.class;
        OneInputStreamOperator<String, Integer> op =
                new WindowOperatorFactoryImpl().createAggregateOperator(fullSpec(), SUM, recordedAcc);
        WindowOperator<?, ?, ?, ?, ?> windowOp = assertWindowOperator(op);
        assertSpecFieldsArrived(windowOp, int[].class);
        assertListStateElementType(windowOp, String.class);
    }

    @Test
    void reduceTransfersFullSpec() {
        // reduce 无推断：spec.elementType 直落 accClass 与 ListStateDescriptor valueType。
        OneInputStreamOperator<String, String> op =
                new WindowOperatorFactoryImpl().createReduceOperator(fullSpec(), REDUCE_FN);
        WindowOperator<?, ?, ?, ?, ?> windowOp = assertWindowOperator(op);
        assertSpecFieldsArrived(windowOp, String.class);
        assertListStateElementType(windowOp, String.class);
    }

    @Test
    void applyTransfersFullSpec() {
        OneInputStreamOperator<String, String> op =
                new WindowOperatorFactoryImpl().createApplyOperator(fullSpec(), APPLY_FN);
        WindowOperator<?, ?, ?, ?, ?> windowOp = assertWindowOperator(op);
        assertSpecFieldsArrived(windowOp, String.class);
        assertListStateElementType(windowOp, String.class);
        assertSame(APPLY_FN, WindowRoundTripAssertions.readField(
                        WindowRoundTripAssertions.readField(op, "userFunction"), "wrappedFunction"),
                "apply must wrap the call-site WindowFunction unchanged");
    }

    @Test
    void processTransfersFullSpec() {
        OneInputStreamOperator<String, String> op =
                new WindowOperatorFactoryImpl().createProcessOperator(fullSpec(), PROCESS_FN);
        WindowOperator<?, ?, ?, ?, ?> windowOp = assertWindowOperator(op);
        assertSpecFieldsArrived(windowOp, String.class);
        assertListStateElementType(windowOp, String.class);
        assertSame(PROCESS_FN, WindowRoundTripAssertions.readField(
                        WindowRoundTripAssertions.readField(op, "userFunction"), "wrappedFunction"),
                "process must wrap the call-site ProcessWindowFunction unchanged");
    }

    @Test
    void builderRejectsMissingRequiredValues() {
        assertThrows(StreamException.class, () -> WindowSpec.<String, String, TimeWindow>builder(
                null, KEY_SELECTOR, String.class).build(), "missing windowAssigner must fail fast");
        assertThrows(StreamException.class, () -> WindowSpec.<String, String, TimeWindow>builder(
                ASSIGNER, null, String.class).build(), "missing keySelector must fail fast");
        assertThrows(StreamException.class, () -> WindowSpec.<String, String, TimeWindow>builder(
                ASSIGNER, KEY_SELECTOR, null).build(), "missing keyClass must fail fast");
    }

    @Test
    void copyBuilderCarriesEveryField() {
        WindowSpec<String, String, TimeWindow> source = fullSpec();
        WindowSpec<String, String, TimeWindow> copy = WindowSpec.builder(source)
                .allowedLateness(1L)
                .build();

        assertSame(source.getWindowAssigner(), copy.getWindowAssigner(), "copy: windowAssigner");
        assertSame(source.getTrigger(), copy.getTrigger(), "copy: trigger");
        assertSame(source.getEvictor(), copy.getEvictor(), "copy: evictor");
        assertEquals(1L, copy.getAllowedLateness(), "copy: only the overridden field changes");
        assertSame(source.getKeySelector(), copy.getKeySelector(), "copy: keySelector");
        assertSame(source.getKeyClass(), copy.getKeyClass(), "copy: keyClass");
        assertSame(source.getElementType(), copy.getElementType(), "copy: elementType");
        assertSame(source.getLateDataOutputTag(), copy.getLateDataOutputTag(), "copy: lateDataOutputTag");
        assertEquals(source.getMaxDelay(), copy.getMaxDelay(), "copy: maxDelay");
        assertEquals(source.isEmitWindowEvents(), copy.isEmitWindowEvents(), "copy: emitWindowEvents");
        assertSame(source.getWindowEventsOutputTag(), copy.getWindowEventsOutputTag(),
                "copy: windowEventsOutputTag");
        assertSame(source.getAccumulationMode(), copy.getAccumulationMode(), "copy: accumulationMode");

        // 源 spec 永不被变异
        assertEquals(ALLOWED_LATENESS, source.getAllowedLateness(), "source spec stays immutable");
        assertFalse(copy == source, "copy builder produces a new instance");
    }

    private static WindowOperator<?, ?, ?, ?, ?> assertWindowOperator(OneInputStreamOperator<?, ?> op) {
        assertNotNull(op, "the factory must produce an operator");
        assertTrue(op instanceof WindowOperator, "expected WindowOperator, got " + op.getClass().getName());
        return (WindowOperator<?, ?, ?, ?, ?>) op;
    }
}
