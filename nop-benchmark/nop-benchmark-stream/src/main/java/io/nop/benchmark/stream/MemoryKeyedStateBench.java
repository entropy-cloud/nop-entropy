/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.windowing.windows.TimeWindow;
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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Memory keyed-state 后端基准（Plan 360 Phase 1）。
 *
 * <p>构造方式照抄 nop-stream-core 的 {@code TestStateFamilyBackendMatrix}：
 * {@code new MemoryStateBackend().createKeyedStateBackend(Long.class)}。与
 * {@link RocksDbKeyedStateBench} 保持同一负载形态（同 key 轮转 0..1023、同
 * Long 求和聚合、同 TimeWindow(0,60000) namespace），两者数据可直接对比。
 *
 * <ul>
 *   <li><b>valueGetUpdate</b>：{@code ValueState<Long>} 读旧值 → 写回。</li>
 *   <li><b>aggregatingAdd</b>：{@code getInternalAppendingState(AggregatingStateDescriptor)}
 *       （WindowOperator 同款入口），namespace 固定 {@code TimeWindow(0,60000)}，
 *       每 invocation add 一个 Long。</li>
 * </ul>
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MemoryKeyedStateBench {

    private static final int KEY_COUNT = 1024;
    private static final TimeWindow NAMESPACE = new TimeWindow(0L, 60_000L);

    private MemoryKeyedStateBackend<Long> backend;
    private ValueState<Long> valueState;
    private InternalAppendingState<Long, TimeWindow, Long, Long, Long> aggregatingState;

    private long keyCursor;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        backend = (MemoryKeyedStateBackend<Long>) new MemoryStateBackend().createKeyedStateBackend(Long.class);

        valueState = backend.getState(new ValueStateDescriptor<>("bench-value", Long.class));

        aggregatingState = backend.getInternalAppendingState(
                new AggregatingStateDescriptor<>("bench-agg", new LongSumAggregate(), Long.class));
        aggregatingState.setCurrentNamespace(NAMESPACE);

        // Sanity check both access paths.
        backend.setCurrentKey(0L);
        valueState.update(1L);
        if (!Long.valueOf(1L).equals(valueState.value())) {
            throw new IllegalStateException("memory value state sanity failed");
        }
        aggregatingState.add(2L);
        if (!Long.valueOf(2L).equals(aggregatingState.get())) {
            throw new IllegalStateException("memory aggregating state sanity failed");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (backend != null) {
            backend.close();
        }
    }

    /** ValueState 读旧值 + 写回（key 轮转）。 */
    @Benchmark
    public void valueGetUpdate(Blackhole bh) throws IOException {
        backend.setCurrentKey(nextKey());
        Long previous = valueState.value();
        valueState.update(previous == null ? 1L : previous + 1L);
        bh.consume(previous);
    }

    /** InternalAppendingState（aggregating）add 一个 Long，TimeWindow namespace。 */
    @Benchmark
    public void aggregatingAdd(Blackhole bh) throws IOException {
        backend.setCurrentKey(nextKey());
        aggregatingState.add(1L);
        bh.consume(NAMESPACE);
    }

    /**
     * 访问模式：rotate=key 轮转（最坏情形，缓存全冷）；local=同 key 连续访问
     * （keyBy 分区后同键记录连续到达的常态路径，缓存类优化的目标场景）。
     */
    @Param({"local", "rotate"})
    private String accessPattern;

    private long localKey = 0L;

    private long nextKey() {
        if ("local".equals(accessPattern)) {
            return localKey; // 同 key 连续访问：常态路径
        }
        long next = keyCursor++;
        return next < KEY_COUNT ? next : (next % KEY_COUNT);
    }

    /** Long 求和聚合（与 RocksDbKeyedStateBench.LongSumAggregate 同形态）。 */
    static class LongSumAggregate implements io.nop.stream.core.common.functions.AggregateFunction<Long, Long, Long> {
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
