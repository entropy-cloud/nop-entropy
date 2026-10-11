/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.windowing.assigners.SlidingEventTimeWindows;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.core.operators.windowing.WindowOperator;
import io.nop.stream.core.operators.windowing.WindowOperatorBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * WI12 (plan 2344) G2: the O(N)-windows-per-element cost of the ACCUMULATING sliding
 * model — every element is assigned to ALL overlapping windows and added to each one's
 * accumulator. window length is fixed at 60s; {@code slideCount} varies the slide so an
 * element overlaps exactly N windows (1/6/12/36). The datum: is per-element cost linear
 * in N (then the current model is asymptotically fine and frame/deduct is not worth its
 * fire-time retroactive cost), or super-linear/absolutely dominant (then a pane-based
 * frame/deduct design deserves a successor plan)?
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class SlidingWindowFanoutBench {

    /** overlapping windows per element (slide = 60s / slideCount). */
    @Param({"1", "6", "12", "36"})
    int slideCount;

    @Param({"100"})
    int keys;

    WindowOperator<String, Long, Long, Long, TimeWindow> operator;
    WindowOperatorProcessElementBench.BenchWindowOutput output;
    long tsCursor;
    long valueCursor;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        long slide = 60_000L / slideCount;
        WindowOperatorBuilder<Long, String, TimeWindow> builder =
                new WindowOperatorBuilder<Long, String, TimeWindow>()
                        .trigger(EventTimeTrigger.create())
                        .keySelector((KeySelector<Long, String>) v -> "k" + (v % keys))
                        .keyClass(String.class)
                        .keySerializer(new WindowOperatorProcessElementBench.BenchStringSerializer())
                        .windowSerializer(new WindowOperatorProcessElementBench.BenchTimeWindowSerializer())
                        .windowAssigner(SlidingEventTimeWindows.of(60_000L, slide));
        operator = builder.aggregate(new WindowOperatorProcessElementBench.LongSumAggregate(),
                Long.class, Long.class);
        output = new WindowOperatorProcessElementBench.BenchWindowOutput();
        operator.setOutput(output);
        operator.open();
        // sanity: one element flows into the fan-out windows
        operator.processElement(new StreamRecord<>(1L, 1L));
        if (output.count < 0) {
            throw new IllegalStateException("sliding fanout bench sanity failed");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    /** pure fan-out add: element into N overlapping window accumulators, watermark pinned. */
    @Benchmark
    public void slidingFanoutAdd(Blackhole bh) throws Exception {
        long ts = 1_000_000L + (++tsCursor % 1000);
        operator.processElement(new StreamRecord<>(++valueCursor, ts));
        bh.consume(output.count);
    }
}
