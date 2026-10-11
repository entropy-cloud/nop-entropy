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
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.windowing.assigners.SlidingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
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
 * WI12 (plan 2344) G1: the watermark punctuation path cost. Splits the fused
 * {@code WindowOperatorProcessElementBench.processElement} (element + watermark per
 * invocation) into its two halves so the watermark share of the per-record cost is
 * visible — the go/no-go datum for a "emit punctuation every R records" throttle
 * (adjudication: AR-06 itself stays with r5-successors; this only quantifies the cost).
 *
 * <p>Benchmark shapes:
 * <ul>
 *   <li>{@code watermarkOnly} — one {@code processWatermark} per invocation on a
 *       NON-FIRING position (window far from fire): the pure control-plane cost;</li>
 *   <li>{@code elementOnly} — one {@code processElement} per invocation, watermark
 *       pinned far in the future: the element share for comparison;</li>
 *   <li>{@code elementPlusWatermark} — the fused shape (cross-check against the
 *       existing bench).</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class WatermarkEmitBench {

    @Param({"TUMBLING", "SLIDING"})
    String windowType;

    @Param({"100", "10000"})
    int keys;

    WindowOperator<String, Long, Long, Long, TimeWindow> operator;
    WindowOperatorProcessElementBench.BenchWindowOutput output;
    WindowOperatorProcessElementBench.BenchWindowOutput sinkOnly;

    long tsCursor;
    long valueCursor;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        operator = build(windowType);
        output = new WindowOperatorProcessElementBench.BenchWindowOutput();
        operator.setOutput(output);
        operator.open();
        operator.processElement(new StreamRecord<>(1L, 1L));

        // a second instance driven WITHOUT watermark advance: elementOnly never fires,
        // isolating the pure element share
        sinkOnly = new WindowOperatorProcessElementBench.BenchWindowOutput();
    }

    private WindowOperator<String, Long, Long, Long, TimeWindow> build(String type) {
        WindowOperatorBuilder<Long, String, TimeWindow> builder =
                new WindowOperatorBuilder<Long, String, TimeWindow>()
                        .trigger(EventTimeTrigger.create())
                        .keySelector((KeySelector<Long, String>) v -> "k" + (v % keys))
                        .keyClass(String.class)
                        .keySerializer(new WindowOperatorProcessElementBench.BenchStringSerializer())
                        .windowSerializer(new WindowOperatorProcessElementBench.BenchTimeWindowSerializer());
        if ("SLIDING".equals(type)) {
            builder.windowAssigner(SlidingEventTimeWindows.of(60_000L, 10_000L));
        } else {
            builder.windowAssigner(TumblingEventTimeWindows.of(60_000L));
        }
        WindowOperator<String, Long, Long, Long, TimeWindow> op =
                builder.aggregate(new WindowOperatorProcessElementBench.LongSumAggregate(), Long.class, Long.class);
        return op;
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    /** pure control-plane share: watermark advance WITHOUT firing (window far ahead). */
    @Benchmark
    public void watermarkOnly(Blackhole bh) throws Exception {
        long ts = 1_000_000L + (++tsCursor % 1000);
        operator.processWatermark(new Watermark(ts));
        bh.consume(ts);
    }

    /** pure element share: watermark pinned far in the future — never fires. */
    @Benchmark
    public void elementOnly(Blackhole bh) throws Exception {
        long ts = 1_000_000L + (++valueCursor % 1000);
        operator.processElement(new StreamRecord<>(++valueCursor, ts));
        bh.consume(ts);
    }

    /** the fused shape (element + firing watermark), cross-check vs the existing bench. */
    @Benchmark
    public void elementPlusFiringWatermark(Blackhole bh) throws Exception {
        long ts = ++tsCursor;
        operator.processElement(new StreamRecord<>(++valueCursor, ts));
        operator.processWatermark(new Watermark(ts));
        bh.consume(output.count);
    }
}
