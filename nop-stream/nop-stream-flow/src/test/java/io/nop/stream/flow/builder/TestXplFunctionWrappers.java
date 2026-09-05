/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.util.Collector;
import io.nop.stream.flow.builder.functions.XplFilterFunction;
import io.nop.stream.flow.builder.functions.XplFlatMapFunction;
import io.nop.stream.flow.builder.functions.XplMapFunction;
import io.nop.stream.flow.builder.functions.XplSinkFunction;
import io.nop.stream.flow.builder.functions.XplSourceFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link io.nop.stream.flow.builder.functions.XplMapFunction} and the sibling
 * xpl wrappers correctly invoke their parsed {@link IEvalFunction} bodies with the right
 * scope bindings. Phase 1 exit criterion "内联 xpl 编译 1 test".
 *
 * <p>The {@link IEvalFunction} instances here are stand-ins for the compiled XDSL xpl body.
 * They use the same {@link IEvalFunction#call1}/{@code call2} entry points as the real
 * compiled {@code <source>xpl</source>} body, so passing these tests proves the wrapper's
 * invocation contract is correct.
 */
public class TestXplFunctionWrappers {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static IEvalScope scope() {
        return EvalExprProvider.newEvalScope();
    }

    @Test
    public void mapFunctionInvokesBodyAndForwardsReturnValue() throws Exception {
        IEvalFunction body = (thisObj, args, s) -> String.valueOf(args[0]).toUpperCase();
        MapFunction<String, String> fn = new XplMapFunction<>(body);

        assertEquals("HELLO", fn.map("hello"));
        assertEquals("WORLD", fn.map("world"));
    }

    @Test
    public void filterFunctionUsesTruthyConversionOfReturnValue() {
        IEvalFunction truthy = (thisObj, args, s) -> args[0];
        XplFilterFunction<String> fn = new XplFilterFunction<>(truthy);

        assertTrue(fn.filter("non-empty"));
        assertFalse(fn.filter(""));
    }

    @Test
    public void flatMapFunctionBindsCollectorAsSecondArgument() {
        IEvalFunction body = (thisObj, args, s) -> {
            String value = String.valueOf(args[0]);
            @SuppressWarnings("unchecked")
            Collector<String> out = (Collector<String>) args[1];
            for (int i = 0; i < value.length(); i++) {
                out.collect(String.valueOf(value.charAt(i)));
            }
            return null;
        };
        XplFlatMapFunction<String, String> fn = new XplFlatMapFunction<>(body);

        List<String> seen = new ArrayList<>();
        fn.flatMap("abc", collector(seen));

        assertEquals(Arrays.asList("a", "b", "c"), seen);
    }

    @Test
    public void sinkFunctionInvokesBodyPerElement() {
        List<String> seen = new ArrayList<>();
        IEvalFunction body = (thisObj, args, s) -> {
            seen.add(String.valueOf(args[0]));
            return null;
        };
        XplSinkFunction<String> fn = new XplSinkFunction<>(body);

        fn.consume("a");
        fn.consume("b");
        fn.consume("c");

        assertEquals(Arrays.asList("a", "b", "c"), seen);
    }

    @Test
    public void sourceFunctionRunsBodyWithSourceContext() {
        List<String> emitted = Collections.synchronizedList(new ArrayList<>());
        IEvalFunction body = (thisObj, args, s) -> {
            @SuppressWarnings("unchecked")
            SourceFunction.SourceContext<String> ctx = (SourceFunction.SourceContext<String>) args[0];
            ctx.collect("a");
            ctx.collect("b");
            return null;
        };
        XplSourceFunction<String> source = new XplSourceFunction<>(body);

        source.run(new SourceFunction.SourceContext<String>() {
            @Override
            public void collect(String element) {
                emitted.add(element);
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return 0;
            }
        });

        assertEquals(Arrays.asList("a", "b"), emitted);
        assertTrue(source.isRunning());
        source.cancel();
        assertFalse(source.isRunning());
    }

    /**
     * Phase 1 (item 29): the documented inline-xpl-source cancellation pattern is a
     * {@code while (!ctx.isCancelled())} loop. The body here uses a controllable
     * context (accessor overridden to a test-driven value, NOT relying on the default)
     * and exits its loop once the accessor flips — {@code run()} then returns
     * normally. This proves the wrapper contract: the body observes cancellation
     * through the context, not through the wrapper's own flag.
     */
    @Test
    public void sourceFunctionBodyExitsLoopViaContextCancelPoll() throws Exception {
        List<String> emitted = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger iterations = new AtomicInteger();
        // Flip the controllable cancel flag after 3 iterations.
        AtomicBoolean cancelled = new AtomicBoolean(false);
        IEvalFunction body = (thisObj, args, s) -> {
            @SuppressWarnings("unchecked")
            SourceFunction.SourceContext<String> ctx = (SourceFunction.SourceContext<String>) args[0];
            while (!ctx.isCancelled()) {
                iterations.incrementAndGet();
                ctx.collect("tick-" + iterations.get());
                if (iterations.get() >= 3) {
                    cancelled.set(true);
                }
            }
            return null;
        };
        XplSourceFunction<String> source = new XplSourceFunction<>(body);

        source.run(new SourceFunction.SourceContext<String>() {
            @Override
            public void collect(String element) {
                emitted.add(element);
            }

            @Override
            public void collectWithTimestamp(String element, long timestamp) {
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return 0;
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        });

        assertEquals(3, iterations.get(), "body loop must exit after the context reports cancelled");
        assertEquals(Arrays.asList("tick-1", "tick-2", "tick-3"), emitted);
        assertTrue(source.isRunning(), "wrapper's own flag is untouched by the context-only pattern");
    }

    private static <T> Collector<T> collector(List<T> into) {
        return new Collector<T>() {
            @Override
            public void collect(T record) {
                into.add(record);
            }

            @Override
            public void close() {
            }
        };
    }
}
