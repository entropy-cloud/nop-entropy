/**
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
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.assigners.GlobalWindows;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the call-site fail-fast behavior of {@link WindowedStreamImpl} when no
 * {@code IWindowOperatorFactory} is available (i.e. nop-stream-runtime not on classpath).
 *
 * <p>G64: the reflective {@code getFactory()} no longer has an empty catch block (it logs the
 * missing-runtime condition), but {@code defaultFactory} stays null so that every window
 * operation (apply/aggregate/reduce/process) fails fast with a {@link StreamException} rather
 * than silently producing a broken plan. This test forces the null-factory path by supplying an
 * empty {@link StreamComponents} (whose {@code getWindowOperatorFactory()} returns null), which
 * makes {@code getFactory()} return null without relying on classpath exclusion.
 */
public class TestWindowedStreamFactoryFailFast {

    private KeyedStream<Integer, Integer> keyed(StreamExecutionEnvironment env) {
        return env.fromElements(1, 2, 3).keyBy((KeySelector<Integer, Integer>) i -> 0);
    }

    private WindowedStreamImpl<Integer, Integer, GlobalWindow> windowedWithNoFactory(StreamExecutionEnvironment env) {
        // Empty StreamComponents → getWindowOperatorFactory() returns null → getFactory() returns null.
        return new WindowedStreamImpl<>(keyed(env), GlobalWindows.create())
                .withComponents(new StreamComponents());
    }

    private static void assertFailFast(StreamException ex, String op) {
        assertTrue(ex.getMessage().contains("nop-stream-runtime on classpath"),
                () -> op + ": expected fail-fast StreamException, got: " + ex.getMessage());
    }

    @Test
    void applyFailsFastWhenNoFactoryAvailable() {
        WindowedStreamImpl<Integer, Integer, GlobalWindow> windowed =
                windowedWithNoFactory(StreamExecutionEnvironment.createTestEnvironment());

        StreamException ex = assertThrows(StreamException.class, () ->
                windowed.apply((WindowFunction<Integer, String, Integer, GlobalWindow>)
                        (key, window, input, out) -> {
                        })
        );
        assertFailFast(ex, "apply");
    }

    @Test
    void reduceFailsFastWhenNoFactoryAvailable() {
        WindowedStreamImpl<Integer, Integer, GlobalWindow> windowed =
                windowedWithNoFactory(StreamExecutionEnvironment.createTestEnvironment());

        StreamException ex = assertThrows(StreamException.class, () ->
                windowed.reduce((ReduceFunction<Integer>) (a, b) -> a + b)
        );
        assertFailFast(ex, "reduce");
    }

    @Test
    void aggregateFailsFastWhenNoFactoryAvailable() {
        WindowedStreamImpl<Integer, Integer, GlobalWindow> windowed =
                windowedWithNoFactory(StreamExecutionEnvironment.createTestEnvironment());

        StreamException ex = assertThrows(StreamException.class, () ->
                windowed.aggregate(new AggregateFunction<Integer, int[], Integer>() {
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
                })
        );
        assertFailFast(ex, "aggregate");
    }

    @Test
    void processFailsFastWhenNoFactoryAvailable() {
        WindowedStreamImpl<Integer, Integer, GlobalWindow> windowed =
                windowedWithNoFactory(StreamExecutionEnvironment.createTestEnvironment());

        StreamException ex = assertThrows(StreamException.class, () ->
                windowed.process(new ProcessWindowFunction<Integer, String, Integer, GlobalWindow>() {
                    @Override
                    public void process(Integer key, GlobalWindow window, Iterable<Integer> input,
                                        Context context, Collector<String> out) {
                    }
                })
        );
        assertFailFast(ex, "process");
    }
}
