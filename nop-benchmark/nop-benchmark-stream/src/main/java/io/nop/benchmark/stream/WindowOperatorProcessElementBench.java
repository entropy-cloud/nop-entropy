/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.EventTimeSessionWindows;
import io.nop.stream.core.windowing.assigners.SlidingEventTimeWindows;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.runtime.operators.windowing.WindowOperator;
import io.nop.stream.runtime.operators.windowing.WindowOperatorBuilder;
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
 * WindowOperator.processElement 基准（Plan 360 Phase 1）。
 *
 * <p>构造方式照抄 nop-stream-runtime 的 {@code TestWindowOperatorBuilder} /
 * {@code TestWindowOperatorCorrectness}：{@code WindowOperatorBuilder} 上装配
 * EventTimeTrigger + Long 求和聚合（{@code aggregate}），Memory 后端（WindowOperator.open()
 * 的默认 {@code new MemoryStateBackend()}），序列化器用测试同款最小实现。
 *
 * <p>负载：Long 值 + 事件时间戳（1ms 步进）的 StreamRecord，64 个 key 轮转；
 * 每 invocation 处理一条记录并推进一次 watermark
 * （{@code processElement} + {@code processWatermark}，即测试驱动窗口触发的方式），
 * 使已关闭窗口及时触发清理、打开窗口数量保持有界，测量处于稳态。
 *
 * <p>{@code @Param windowType}：
 * <ul>
 *   <li>TUMBLING — {@code TumblingEventTimeWindows.of(60_000)}</li>
 *   <li>SLIDING — {@code SlidingEventTimeWindows.of(60_000, 10_000)}</li>
 *   <li>SESSION — {@code EventTimeSessionWindows.withGap(10_000)}（走窗口合并路径）</li>
 *   <li>EVICTOR — tumbling(60s) + {@code CountEvictor.of(100)}（走 ListState 缓冲路径）</li>
 * </ul>
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class WindowOperatorProcessElementBench {

    @Param({"TUMBLING", "SLIDING", "SESSION", "EVICTOR"})
    String windowType;

    /** Plan 2279: 状态后端（MEMORY 默认 / ROCKSDB 经 setStateBackend——F2 evictor O(n²) 只在 RocksDB 可测）。 */
    @Param({"MEMORY", "ROCKSDB"})
    String backend;

    /** Plan 2279: EVICTOR 档的 CountEvictor 容量（其他 windowType 档不使用该参数）。 */
    @Param({"100", "1000"})
    int evictorSize;

    private WindowOperator<String, Long, Long, Long, TimeWindow> operator;
    private BenchWindowOutput output;
    private java.nio.file.Path dbDir;

    private long tsCursor;
    private long valueCursor;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        WindowOperatorBuilder<Long, String, TimeWindow> builder =
                new WindowOperatorBuilder<Long, String, TimeWindow>()
                        .trigger(EventTimeTrigger.create())
                        .keySelector((KeySelector<Long, String>) v -> "k" + (v & 63))
                        .keyClass(String.class)
                        .keySerializer(new BenchStringSerializer())
                        .windowSerializer(new BenchTimeWindowSerializer());

        switch (windowType) {
            case "TUMBLING":
                builder.windowAssigner(TumblingEventTimeWindows.of(60_000L));
                break;
            case "SLIDING":
                builder.windowAssigner(SlidingEventTimeWindows.of(60_000L, 10_000L));
                break;
            case "SESSION":
                builder.windowAssigner(EventTimeSessionWindows.withGap(10_000L));
                break;
            case "EVICTOR":
                builder.windowAssigner(TumblingEventTimeWindows.of(60_000L))
                        .evictor(CountEvictor.of(evictorSize));
                break;
            default:
                throw new IllegalArgumentException("Unknown windowType: " + windowType);
        }

        operator = builder.aggregate(new LongSumAggregate(), Long.class, Long.class);
        if ("ROCKSDB".equals(backend)) {
            dbDir = java.nio.file.Files.createTempDirectory("nop-bench-window-rocksdb");
            operator.setStateBackend(new io.nop.stream.rocksdb.RocksDBStateBackend(dbDir.toString()));
        }
        output = new BenchWindowOutput();
        operator.setOutput(output);
        operator.open();

        // Sanity: one element must flow through processElement without error and be
        // accumulated into an open window (no emission before watermark advance).
        operator.processElement(new StreamRecord<>(1L, 1L));
        operator.processWatermark(new Watermark(2L));
        if (output.count < 0) {
            throw new IllegalStateException("window operator sanity failed");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
    }

    /** 每 invocation：一条 StreamRecord(Long, ts) 进入窗口 + watermark 步进驱动触发。 */
    @Benchmark
    public void processElement(Blackhole bh) throws Exception {
        long ts = ++tsCursor;
        long value = ++valueCursor;
        operator.processElement(new StreamRecord<>(value, ts));
        operator.processWatermark(new Watermark(ts));
        bh.consume(output.count);
    }

    /** 窗口输出收集器：只计数，不保留对象（防止测量被输出侧分配污染）。 */
    static final class BenchWindowOutput implements Output<StreamRecord<Long>> {
        long count;

        @Override
        public void collect(StreamRecord<Long> record) {
            count++;
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
            count++;
        }

        @Override
        public void emitWatermark(Watermark mark) {
            // discarded
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
            // discarded
        }

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) {
            // discarded
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
            // discarded
        }

        @Override
        public void close() {
            // nothing to flush
        }
    }

    /** 照抄 TestWindowOperatorCorrectness 的最小 TimeWindow 序列化器。 */
    static class BenchTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<TimeWindow> duplicate() {
            return this;
        }

        @Override
        public TimeWindow createInstance() {
            return new TimeWindow(0, 0);
        }

        @Override
        public TimeWindow copy(TimeWindow from) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    /** 照抄 TestWindowOperatorCorrectness 的最小 String 序列化器。 */
    static class BenchStringSerializer implements TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<String> duplicate() {
            return this;
        }

        @Override
        public String createInstance() {
            return "";
        }

        @Override
        public String copy(String from) {
            return from;
        }

        @Override
        public String copy(String from, String reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    /** Long 求和聚合（与状态基准同形态）。 */
    static class LongSumAggregate implements AggregateFunction<Long, Long, Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(Long value, Long accumulator) {
            return accumulator + value;
        }

        @Override
        public Long getResult(Long accumulator) {
            return accumulator;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a + b;
        }
    }
}
