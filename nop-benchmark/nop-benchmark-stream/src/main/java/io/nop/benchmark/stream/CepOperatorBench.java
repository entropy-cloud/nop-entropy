/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.stream.bench.BenchCepEvent;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.operator.CepOperator;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.rocksdb.RocksDBStateBackend;
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

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CepOperator 算子层基准（Plan 2279 Phase 1）——补 360 缺失的 operator 层覆盖：
 * {@code processElement} 的事件缓冲（elementQueueState 每事件 RMW）+
 * {@code processWatermark} 的 timer 批 drain（{@code getSortedTimestamps} 对
 * elementQueueState.keys() 的全扫 + 台账深拷贝）。NFA 本体成本已由
 * {@link NfaProcessBench} 单独覆盖，本基准用深度 1 的平凡模式使其占比最小。
 *
 * <p>构造方式照抄 nop-stream-cep 的 {@code TestCepOperatorMultiKeyWatermark}：
 * 事件时间模式（{@code isProcessingTime=false}）+ Memory/RocksDB 后端 +
 * 反射注入 ProcessingTimeService（生产接线形态）。
 *
 * <p>负载：{@code @Param keys}（1/64）个 key 轮转；每事件时间戳 = base + seq，
 * watermark 恒滞后 {@code @Param bucketsPerKey}（8/64）个时间片 —— 每 key 常态持有
 * bucketsPerKey 个未 drain 桶，每次 watermark 恰好 drain 每 key 最老的一个桶
 * （keys=64 时每个 watermark 将 64 个事件送入 NFA）。每 invocation =
 * 1 processElement + 1 processWatermark。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class CepOperatorBench {

    @Param({"MEMORY", "ROCKSDB"})
    String backend;

    @Param({"1", "64"})
    int keys;

    @Param({"8", "64"})
    int bucketsPerKey;

    private static final String PATTERN_ID = "start";

    private CepOperator<BenchCepEvent, Long, Long> operator;
    private CountingMatchFunction matchFunction;
    private CountingOutput output;
    private Path dbDir;

    private long seqCursor;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // 深度 1 平凡模式：隔离算子层（缓冲 RMW + watermark drain）成本。
        Pattern<BenchCepEvent, ?> pattern = Pattern.<BenchCepEvent>begin(PATTERN_ID)
                .where(SimpleCondition.of(BenchCepEvent::isMatching));

        matchFunction = new CountingMatchFunction();
        operator = new CepOperator<>(
                new BenchCepEventSerializer(),
                false,
                NFACompiler.compileFactory(pattern, false),
                null,
                null,
                matchFunction,
                null);

        if ("ROCKSDB".equals(backend)) {
            dbDir = Files.createTempDirectory("nop-bench-cep-rocksdb");
            operator.setStateBackend(new RocksDBStateBackend(dbDir.toString()));
        } else {
            operator.setStateBackend(new MemoryStateBackend());
        }

        output = new CountingOutput();
        operator.setOutput(output);
        injectProcessingTimeService(operator, new SteppingProcessingTimeService());
        operator.open();

        // Sanity: one element + watermark must flow through the real buffering
        // and drain path without error.
        operator.setCurrentKey("k0");
        operator.processElement(new StreamRecord<>(new BenchCepEvent(1, true), 1));
        operator.processWatermark(new Watermark(1));
        seqCursor = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (operator != null) {
            operator.close();
        }
        // RocksDB temp dir left for OS cleanup (same policy as RocksDbKeyedStateBench).
    }

    /** 每 invocation：一个事件入缓冲 + 一次 watermark 推进触发 drain。 */
    @Benchmark
    public void eventTimeBufferDrain(Blackhole bh) throws Exception {
        long seq = ++seqCursor;
        long ts = seq;
        int keyIndex = (int) (seq % keys);
        operator.setCurrentKey("k" + keyIndex);
        // 50% 事件满足条件（与 NfaProcessBench 同形态），条件判断零分配。
        operator.processElement(new StreamRecord<>(new BenchCepEvent(seq, (seq & 1) == 0), ts));
        // watermark 恒滞后 bucketsPerKey 个时间片：每 key 常态持有 bucketsPerKey
        // 个未 drain 桶，本次 watermark drain 每 key 最老的一个桶。
        operator.processWatermark(new Watermark(ts - bucketsPerKey));
        bh.consume(matchFunction.matchCount.get());
    }

    /** 匹配收集函数：只计数，不保留对象。 */
    static final class CountingMatchFunction extends PatternProcessFunction<BenchCepEvent, Long> {
        final AtomicLong matchCount = new AtomicLong();

        @Override
        public void processMatch(Map<String, java.util.List<BenchCepEvent>> match,
                                 Context ctx, Collector<Long> out) {
            matchCount.incrementAndGet();
            out.collect(1L);
        }
    }

    /** 匹配输出收集器：只计数，不保留对象（照抄 BenchWindowOutput 形态）。 */
    static final class CountingOutput implements Output<StreamRecord<Long>> {
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

    /** 步进处理时间服务（事件时间模式下仅 cache-stats 统计使用）。 */
    static final class SteppingProcessingTimeService implements ProcessingTimeService {
        private long time = 1000;

        @Override
        public long getCurrentProcessingTime() {
            return time++;
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> registerTimer(long timestamp,
                                                                     ProcessingTimeCallback target) {
            return null;
        }
    }

    /** 与生产接线一致的反射注入（照抄 cep 测试 CepTestUtils，主会话作用域无该测试类）。 */
    private static void injectProcessingTimeService(io.nop.stream.core.operators.AbstractStreamOperator<?> op,
                                                    ProcessingTimeService svc) {
        try {
            Field f = io.nop.stream.core.operators.AbstractStreamOperator.class
                    .getDeclaredField("processingTimeService");
            f.setAccessible(true);
            f.set(op, svc);
        } catch (Exception e) {
            throw new IllegalStateException("cannot inject processing time service", e);
        }
    }

    /** BenchCepEvent 最小序列化器（照抄 cep 测试 EventTypeSerializer 形态）。 */
    static final class BenchCepEventSerializer implements TypeSerializer<BenchCepEvent> {
        @Override
        public boolean isImmutableType() {
            return false;
        }

        @Override
        public TypeSerializer<BenchCepEvent> duplicate() {
            return this;
        }

        @Override
        public BenchCepEvent createInstance() {
            return new BenchCepEvent();
        }

        @Override
        public BenchCepEvent copy(BenchCepEvent from) {
            return new BenchCepEvent(from.getId(), from.isMatching());
        }

        @Override
        public BenchCepEvent copy(BenchCepEvent from, BenchCepEvent reuse) {
            return new BenchCepEvent(from.getId(), from.isMatching());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }
}
