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
import io.nop.stream.core.operators.WindowSpec;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.AccumulationMode;
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
 * model's windowing strategy must reach the created {@link WindowOperator}
 * through the {@code WindowOperatorFactoryImpl}. Since WI8 (plan 2420) the mode
 * travels inside the immutable {@link WindowSpec}; all four create variants are
 * pinned (apply/reduce/process have no model-level host element, so this layer
 * is the only place the wiring can be proven for them). Null keeps the
 * operator's legacy ACCUMULATING default — the pure Java API path is unchanged.
 */
public class TestWindowOperatorFactoryAccumulationModeWiring {

    private static final WindowAssigner<Object, TimeWindow> ASSIGNER =
            TumblingEventTimeWindows.of(1000L);

    private static final KeySelector<Object, Object> KEY_SELECTOR = v -> v;

    private static WindowSpec<Object, Object, TimeWindow> spec(AccumulationMode mode) {
        return WindowSpec.<Object, Object, TimeWindow>builder(ASSIGNER, KEY_SELECTOR, Object.class)
                .trigger(ASSIGNER.getDefaultTrigger(null))
                .elementType(Object.class)
                .accumulationMode(mode)
                .build();
    }

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
                .createAggregateOperator(spec(AccumulationMode.DISCARDING), sumAggregate(), int[].class);
        assertOperatorMode(op, AccumulationMode.DISCARDING);
    }

    @Test
    void aggregateOperatorCarriesAccumulatingDeclaration() {
        OneInputStreamOperator<Object, Integer> op = new WindowOperatorFactoryImpl()
                .createAggregateOperator(spec(AccumulationMode.ACCUMULATING), sumAggregate(), int[].class);
        assertOperatorMode(op, AccumulationMode.ACCUMULATING);
    }

    @Test
    void aggregateOperatorNullKeepsLegacyAccumulatingDefault() {
        OneInputStreamOperator<Object, Integer> op = new WindowOperatorFactoryImpl()
                .createAggregateOperator(spec(null), sumAggregate(), int[].class);
        assertOperatorMode(op, AccumulationMode.ACCUMULATING);
    }

    @ParameterizedTest
    @EnumSource(value = AccumulationMode.class, names = {"DISCARDING", "ACCUMULATING"})
    @NullSource
    void reduceOperatorCarriesDeclaredMode(AccumulationMode mode) {
        OneInputStreamOperator<Object, Object> op = new WindowOperatorFactoryImpl()
                .createReduceOperator(spec(mode), (ReduceFunction<Object>) (a, b) -> a);
        AccumulationMode expected = mode != null ? mode : AccumulationMode.ACCUMULATING;
        assertOperatorMode(op, expected);
    }

    @ParameterizedTest
    @EnumSource(value = AccumulationMode.class, names = {"DISCARDING", "ACCUMULATING"})
    @NullSource
    void applyOperatorCarriesDeclaredMode(AccumulationMode mode) {
        OneInputStreamOperator<Object, Object> op = new WindowOperatorFactoryImpl()
                .createApplyOperator(spec(mode), (WindowFunction<Object, Object, Object, TimeWindow>)
                        (key, window, input, out) -> {
                        });
        AccumulationMode expected = mode != null ? mode : AccumulationMode.ACCUMULATING;
        assertOperatorMode(op, expected);
    }

    @ParameterizedTest
    @EnumSource(value = AccumulationMode.class, names = {"DISCARDING", "ACCUMULATING"})
    @NullSource
    void processOperatorCarriesDeclaredMode(AccumulationMode mode) {
        OneInputStreamOperator<Object, Object> op = new WindowOperatorFactoryImpl()
                .createProcessOperator(spec(mode), new ProcessWindowFunction<Object, Object, Object, TimeWindow>() {
                    @Override
                    public void process(Object key, TimeWindow window,
                                        Iterable<Object> input, Context context,
                                        Collector<Object> out) {
                    }
                });
        AccumulationMode expected = mode != null ? mode : AccumulationMode.ACCUMULATING;
        assertOperatorMode(op, expected);
    }
}
