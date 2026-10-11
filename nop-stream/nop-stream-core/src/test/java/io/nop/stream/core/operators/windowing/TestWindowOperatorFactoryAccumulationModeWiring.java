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
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.WindowLifecycleEvent;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * WI38 (plan 03, FU-8 fix / D10 release): the accumulation mode declared by the
 * model's windowing strategy must reach the created {@link WindowOperator} through
 * the {@code WindowOperatorFactoryImpl} fourth-generation factory methods.
 *
 * <p>All four {@code create*Operator} variants are pinned (apply/reduce/process have
 * no model-level host element, so the runtime layer is the only place the wiring can
 * be proven for them). Null keeps the operator's legacy ACCUMULATING default — the
 * pure Java API path is unchanged.
 */
public class TestWindowOperatorFactoryAccumulationModeWiring {

    private static final WindowAssigner<Object, TimeWindow> ASSIGNER =
            TumblingEventTimeWindows.of(1000L);

    private static final KeySelector<Object, Object> KEY_SELECTOR = v -> v;

    private static AggregateFunction<Object, int[], Integer> sumAggregate() {
        return new AggregateFunction<Object, int[], Integer>() {
            @Override
            public int[] createAccumulator() {
                return new int[]{0};
            }

            @Override
            public int[] add(Object value, int[] accumulator) {
                accumulator[0] += ((Number) value).intValue();
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
    }

    private void assertOperatorMode(OneInputStreamOperator<?, ?> operator, AccumulationMode expected) {
        WindowOperator<?, ?, ?, ?, ?> op = assertInstanceOf(WindowOperator.class, operator,
                "the factory must build a WindowOperator");
        assertEquals(expected, WindowRoundTripAssertions.readField(op, "accumulationMode"),
                "the declared accumulation mode must reach the operator field");
    }

    @Test
    void aggregateOperatorCarriesDiscardingDeclaration() {
        OneInputStreamOperator<Object, Integer> op = new WindowOperatorFactoryImpl()
                .createAggregateOperator(ASSIGNER, ASSIGNER.getDefaultTrigger(null), null,
                        0L, sumAggregate(), int[].class, Object.class, KEY_SELECTOR, Object.class,
                        null, 0L, false, null, AccumulationMode.DISCARDING);
        assertOperatorMode(op, AccumulationMode.DISCARDING);
    }

    @Test
    void aggregateOperatorCarriesAccumulatingDeclaration() {
        OneInputStreamOperator<Object, Integer> op = new WindowOperatorFactoryImpl()
                .createAggregateOperator(ASSIGNER, ASSIGNER.getDefaultTrigger(null), null,
                        0L, sumAggregate(), int[].class, Object.class, KEY_SELECTOR, Object.class,
                        null, 0L, false, null, AccumulationMode.ACCUMULATING);
        assertOperatorMode(op, AccumulationMode.ACCUMULATING);
    }

    @Test
    void aggregateOperatorNullKeepsLegacyAccumulatingDefault() {
        OneInputStreamOperator<Object, Integer> op = new WindowOperatorFactoryImpl()
                .createAggregateOperator(ASSIGNER, ASSIGNER.getDefaultTrigger(null), null,
                        0L, sumAggregate(), int[].class, Object.class, KEY_SELECTOR, Object.class,
                        null, 0L, false, null, null);
        assertOperatorMode(op, AccumulationMode.ACCUMULATING);
    }

    @ParameterizedTest
    @EnumSource(value = AccumulationMode.class, names = {"DISCARDING", "ACCUMULATING"})
    @NullSource
    void reduceOperatorCarriesDeclaredMode(AccumulationMode mode) {
        OneInputStreamOperator<Object, Object> op = new WindowOperatorFactoryImpl()
                .createReduceOperator(ASSIGNER, ASSIGNER.getDefaultTrigger(null), null,
                        0L, (ReduceFunction<Object>) (a, b) -> a, Object.class,
                        KEY_SELECTOR, Object.class, null, 0L, false, null, mode);
        AccumulationMode expected = mode != null ? mode : AccumulationMode.ACCUMULATING;
        assertOperatorMode(op, expected);
    }

    @ParameterizedTest
    @EnumSource(value = AccumulationMode.class, names = {"DISCARDING", "ACCUMULATING"})
    @NullSource
    void applyOperatorCarriesDeclaredMode(AccumulationMode mode) {
        OneInputStreamOperator<Object, Object> op = new WindowOperatorFactoryImpl()
                .createApplyOperator(ASSIGNER, ASSIGNER.getDefaultTrigger(null), null,
                        0L, (WindowFunction<Object, Object, Object, TimeWindow>)
                                (key, window, input, out) -> {
                                },
                        Object.class, KEY_SELECTOR, Object.class, null, 0L, false, null, mode);
        AccumulationMode expected = mode != null ? mode : AccumulationMode.ACCUMULATING;
        assertOperatorMode(op, expected);
    }

    @ParameterizedTest
    @EnumSource(value = AccumulationMode.class, names = {"DISCARDING", "ACCUMULATING"})
    @NullSource
    void processOperatorCarriesDeclaredMode(AccumulationMode mode) {
        OneInputStreamOperator<Object, Object> op = new WindowOperatorFactoryImpl()
                .createProcessOperator(ASSIGNER, ASSIGNER.getDefaultTrigger(null), null,
                        0L, new ProcessWindowFunction<Object, Object, Object, TimeWindow>() {
                            @Override
                            public void process(Object key, TimeWindow window,
                                                Iterable<Object> input, Context context,
                                                Collector<Object> out) {
                            }
                        },
                        Object.class, KEY_SELECTOR, Object.class, null, 0L, false, null, mode);
        AccumulationMode expected = mode != null ? mode : AccumulationMode.ACCUMULATING;
        assertOperatorMode(op, expected);
    }
}
