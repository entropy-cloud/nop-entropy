/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators.windowing;

import java.util.Arrays;
import java.util.List;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.functions.ReduceFunction;
import io.nop.stream.core.common.functions.WindowFunction;
import io.nop.stream.core.datastream.DataStreamImpl;
import io.nop.stream.core.datastream.KeyedStreamImpl;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.operators.IWindowOperatorFactory;
import io.nop.stream.core.operators.OneInputStreamOperator;
import io.nop.stream.core.operators.SimpleStreamOperatorFactory;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamOperatorFactory;
import io.nop.stream.core.operators.WindowSpec;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.evictors.Evictor;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.windowing.windows.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate ① — Window 粘合层构造参数完备性（invariant #1）.
 *
 * <p>参数化验证 4 个 call-site（apply/aggregate/reduce/process）经真实
 * {@link WindowOperatorFactoryImpl}（经 {@code IWindowOperatorFactory} 接口，WI8 起
 * 以 {@code WindowSpec} 单一契约携带配置）到 {@link WindowOperatorBuilder} 最终
 * round-trip 到 {@link WindowOperator} 构造器的字段。断言映射以 call-site 配置为准，
 * 映射表明示于 {@link WindowRoundTripAssertions}：windowAssigner→构造器 windowAssigner、
 * trigger→trigger、evictor→evictor、allowedLateness→allowedLateness、function→wrapped
 * userFunction、elementType→accClass/ListStateDescriptor 类型、keySelector→keySelector、
 * keyClass→keyClass。
 *
 * <p>正反例：正例 = 真实 {@code WindowedStreamImpl} call-site → {@code WindowSpec}
 * → 真实 {@code WindowOperatorFactoryImpl} 的接线链；反例 = 复制 spec 后改写
 * allowedLateness 的桩 factory 产生的算子，经判定辅助类 {@link WindowRoundTripAssertions}
 * 必须被抓住。
 */
public class TestWindowRoundTripInvariant {

    enum CallSite {APPLY, AGGREGATE, REDUCE, PROCESS}

    static final WindowFunction<Integer, String, String, TimeWindow> APPLY_FN =
            new WindowFunction<>() {
                private static final long serialVersionUID = 1L;

                @Override
                public void apply(String key, TimeWindow window, Iterable<Integer> input, Collector<String> out) {
                    out.collect("apply");
                }
            };

    static final ProcessWindowFunction<Integer, String, String, TimeWindow> PROCESS_FN =
            new ProcessWindowFunction<>() {
                private static final long serialVersionUID = 1L;

                @Override
                public void process(String key, TimeWindow window, Iterable<Integer> input,
                                    Context ctx, Collector<String> out) {
                    out.collect("process");
                }
            };

    static final AggregateFunction<Integer, int[], Integer> AGGREGATE_FN =
            new AggregateFunction<>() {
                private static final long serialVersionUID = 1L;

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

    static final ReduceFunction<Integer> REDUCE_FN = (a, b) -> a * 100 + b;

    static final TumblingEventTimeWindows WINDOW_ASSIGNER = TumblingEventTimeWindows.of(100);
    static final Trigger<? super Integer, ? super TimeWindow> TRIGGER = EventTimeTrigger.create();
    static final Evictor<? super Integer, TimeWindow> EVICTOR = CountEvictor.of(2);
    static final long ALLOWED_LATENESS = 42L;
    static final KeySelector<Integer, String> KEY_SELECTOR = v -> "key-" + v;
    // live call-site 实参表：WindowedStreamImpl 的 buildSpec（WI8 plan 2420）把
    // inferElementClass(getType()) 结果装入 spec.elementType，keyClass 为 erasure
    // 占位 Object.class（后续 keyClass/keySelector 经 WindowSpec 携带进工厂）。
    static final Class<Object> ELEMENT_TYPE = Object.class;
    static final Class<Object> KEY_CLASS = Object.class;

    static List<Object[]> expectations() {
        // 每行 = (call-site, function, withEvictor)；withEvictor 覆盖 WindowOperatorBuilder
        // 的两条构建路径（无 evictor：AggregatingStateDescriptor；有 evictor：
        // ListStateDescriptor + Buffering*ProcessWindowFunction）
        return List.of(
                new Object[]{CallSite.APPLY, APPLY_FN, false},
                new Object[]{CallSite.APPLY, APPLY_FN, true},
                new Object[]{CallSite.AGGREGATE, AGGREGATE_FN, false},
                new Object[]{CallSite.AGGREGATE, AGGREGATE_FN, true},
                new Object[]{CallSite.REDUCE, REDUCE_FN, false},
                new Object[]{CallSite.REDUCE, REDUCE_FN, true},
                new Object[]{CallSite.PROCESS, PROCESS_FN, false},
                new Object[]{CallSite.PROCESS, PROCESS_FN, true});
    }

    /**
     * 正例：4 个 call-site × 有/无 evictor，经真实 WindowOperatorFactoryImpl 的完整调用链
     * （fluent API → buildSpec 装配 WindowSpec → IWindowOperatorFactory →
     * WindowOperatorBuilder），断言 call-site 配置全部 round-trip 到 WindowOperator
     * 构造器字段（接线验证：非 mock，真实经 core 内工厂接口与实现）。
     */
    @ParameterizedTest
    @MethodSource("expectations")
    void testRoundTripViaRealFactory(CallSite callSite, Object function, boolean withEvictor) {
        WindowOperator<?, ?, ?, ?, ?> op = buildOperatorViaFactory(callSite, function, withEvictor, false);
        List<String> violations = WindowRoundTripAssertions.checkRoundTrip(op, callSite, function, withEvictor);
        assertTrue(violations.isEmpty(), callSite + " (evictor=" + withEvictor + ") round-trip violated:\n  "
                + String.join("\n  ", violations));
    }

    /**
     * 反例 fixture（作用于判定 helper）：桩 factory 漏传 allowedLateness（写 0 而非 42），
     * helper 必须抓住该字段缺失（证明 helper 有能力发现字段遗漏）。
     */
    @Test
    void testDroppedAllowedLatenessIsCaught() {
        WindowOperator<?, ?, ?, ?, ?> op =
                buildOperatorViaFactory(CallSite.AGGREGATE, AGGREGATE_FN, false, true);
        List<String> violations = WindowRoundTripAssertions.checkRoundTrip(op, CallSite.AGGREGATE, AGGREGATE_FN, false);
        assertTrue(violations.stream().anyMatch(v -> v.contains("allowedLateness")),
                "dropped allowedLateness must be caught, got: " + violations);
    }

    // ---- 接线：从 WindowedStreamImpl call-site 到 WindowOperator 构造器的调用链 ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static WindowOperator<?, ?, ?, ?, ?> buildOperatorViaFactory(
            CallSite callSite, Object function, boolean withEvictor, boolean dropAllowedLateness) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        DataStreamImpl<Integer> source = (DataStreamImpl<Integer>) env.fromCollection(Arrays.asList(1, 2, 3));
        KeyedStreamImpl<Integer, String> keyed = new KeyedStreamImpl<>(
                env,
                source.getTransformation(),
                KEY_SELECTOR);

        WindowedStreamImpl<Integer, String, TimeWindow> windowed =
                new WindowedStreamImpl<>(keyed, WINDOW_ASSIGNER);
        windowed.trigger((Trigger) TRIGGER)
                .allowedLateness(ALLOWED_LATENESS);
        if (withEvictor) {
            windowed.evictor((Evictor) EVICTOR);
        }

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(dropAllowedLateness
                ? new DroppingAllowedLatenessFactory(0L)
                : new WindowOperatorFactoryImpl());

        SingleOutputStreamOperator<?> result;
        switch (callSite) {
            case APPLY:
                result = windowed.withComponents(components)
                        .apply((WindowFunction) APPLY_FN);
                break;
            case AGGREGATE:
                result = windowed.withComponents(components)
                        .aggregate((AggregateFunction) function);
                break;
            case REDUCE:
                result = windowed.withComponents(components)
                        .reduce((ReduceFunction) function);
                break;
            default:
                result = windowed.withComponents(components)
                        .<String>process((ProcessWindowFunction) PROCESS_FN);
                break;
        }
        assertNotNull(result, callSite + " must return an operator");

        Transformation<?> transformation = ((io.nop.stream.core.datastream.DataStreamImpl<?>) result).getTransformation();
        assertTrue(transformation instanceof OneInputTransformation,
                "expected OneInputTransformation, got " + transformation.getClass().getName());
        OneInputTransformation<?, ?> oneInput = (OneInputTransformation<?, ?>) transformation;
        StreamOperatorFactory<?> factory = oneInput.getOperatorFactory();
        assertTrue(factory instanceof SimpleStreamOperatorFactory,
                "expected SimpleStreamOperatorFactory, got " + factory.getClass().getName());
        StreamOperator<?> operator = ((SimpleStreamOperatorFactory<?>) factory).getRawOperator();
        assertTrue(operator instanceof WindowOperator,
                "factory must produce WindowOperator (real WindowOperatorFactoryImpl), got "
                        + operator.getClass().getName());
        return (WindowOperator<?, ?, ?, ?, ?>) operator;
    }

    /**
     * 桩 factory：委托真实实现但漏传 allowedLateness（写固定值）。用于反例 fixture。
     * WI8 后 allowedLateness 经 WindowSpec 携带，漏传实现为复制 spec 并改写该字段。
     */
    static class DroppingAllowedLatenessFactory implements IWindowOperatorFactory {
        private final long droppedValue;

        DroppingAllowedLatenessFactory(long droppedValue) {
            this.droppedValue = droppedValue;
        }

        @Override
        public <IN, ACC, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createAggregateOperator(
                WindowSpec<IN, K, W> spec,
                AggregateFunction<IN, ACC, OUT> aggregateFunction,
                Class<ACC> accumulatorType) {
            // 故意漏传：写 droppedValue（0）而非调用方传入的 allowedLateness
            return new WindowOperatorFactoryImpl().createAggregateOperator(
                    WindowSpec.builder(spec).allowedLateness(droppedValue).build(),
                    aggregateFunction, accumulatorType);
        }

        @Override
        public <IN, K, W extends Window>
        OneInputStreamOperator<IN, IN> createReduceOperator(
                WindowSpec<IN, K, W> spec,
                ReduceFunction<IN> reduceFunction) {
            return new WindowOperatorFactoryImpl().createReduceOperator(
                    WindowSpec.builder(spec).allowedLateness(droppedValue).build(), reduceFunction);
        }

        @Override
        public <IN, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createApplyOperator(
                WindowSpec<IN, K, W> spec,
                WindowFunction<IN, OUT, K, W> windowFunction) {
            return new WindowOperatorFactoryImpl().createApplyOperator(
                    WindowSpec.builder(spec).allowedLateness(droppedValue).build(), windowFunction);
        }

        @Override
        public <IN, OUT, K, W extends Window>
        OneInputStreamOperator<IN, OUT> createProcessOperator(
                WindowSpec<IN, K, W> spec,
                ProcessWindowFunction<IN, OUT, K, W> processWindowFunction) {
            return new WindowOperatorFactoryImpl().createProcessOperator(
                    WindowSpec.builder(spec).allowedLateness(droppedValue).build(), processWindowFunction);
        }
    }
}
