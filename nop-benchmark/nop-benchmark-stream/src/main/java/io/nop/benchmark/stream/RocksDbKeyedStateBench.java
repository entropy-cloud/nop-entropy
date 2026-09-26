/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.windowing.windows.TimeWindow;
import io.nop.stream.rocksdb.RocksDBKeyedStateBackend;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * RocksDB keyed-state 后端基准（Plan 360 Phase 1）。
 *
 * <p>构造方式照抄 nop-stream-rocksdb 的
 * {@code TestStateFamilyBackendMatrix}：临时目录上直接
 * {@code new RocksDBKeyedStateBackend<>(dir, Long.class, 128, null)}，Trial 级
 * setup/teardown（teardown 关闭全部 native 句柄并删除临时目录）。
 *
 * <ul>
 *   <li><b>valueGetUpdate</b>：{@code ValueState<Long>} 读旧值 → 写回。</li>
 *   <li><b>aggregatingAdd</b>：{@code getInternalAppendingState(AggregatingStateDescriptor)}
 *       （WindowOperator 同款入口），namespace 固定 {@code TimeWindow(0,60000)}，每
 *       invocation add 一个 Long。</li>
 *   <li><b>listAdd</b>：{@code InternalListState} 追加一个元素；
 *       {@code @Param listPreload} 控制 setup 时每个 key 预加载的列表长度（100/1000），
 *       用于观察 RocksDBListState.add 整表读改写的列表长度敏感性。</li>
 * </ul>
 *
 * <p>key 以 0..1023 轮转（{@code setCurrentKey}）。listAdd 为保持测量平稳：每个 key
 * 每累计追加 256 个元素后用 {@code update} 恢复到 preload 基线（摊还开销约为一次
 * 基线列表重写 / 256 次追加），使列表长度始终停留在 [preload, preload+256] 区间。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class RocksDbKeyedStateBench {

    private static final int KEY_COUNT = 1024;
    private static final TimeWindow NAMESPACE = new TimeWindow(0L, 60_000L);
    /** listAdd 每 key 重置基线前允许追加的元素数。 */
    private static final int LIST_GROW_LIMIT = 256;

    @Param({"100", "1000"})
    int listPreload;

    private RocksDBKeyedStateBackend<Long> backend;
    private ValueState<Long> valueState;
    private InternalAppendingState<Long, TimeWindow, Long, Long, Long> aggregatingState;
    private InternalListState<Long, TimeWindow, Long> listState;

    private List<Long> baselineList;
    private final int[] listGrowCounters = new int[KEY_COUNT];

    private long keyCursor;
    private Path dbDir;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        dbDir = Files.createTempDirectory("nop-bench-rocksdb");
        backend = new RocksDBKeyedStateBackend<>(dbDir.toString(), Long.class, 128, null);

        valueState = backend.getState(new ValueStateDescriptor<>("bench-value", Long.class));

        aggregatingState = backend.getInternalAppendingState(
                new AggregatingStateDescriptor<>("bench-agg", new LongSumAggregate(), Long.class));
        aggregatingState.setCurrentNamespace(NAMESPACE);

        listState = backend.getInternalListState(new ListStateDescriptor<>("bench-list", Long.class));
        listState.setCurrentNamespace(NAMESPACE);

        baselineList = new ArrayList<>(listPreload);
        for (int i = 0; i < listPreload; i++) {
            baselineList.add((long) i);
        }
        for (long key = 0; key < KEY_COUNT; key++) {
            backend.setCurrentKey(key);
            listState.addAll(baselineList);
        }

        // Sanity check the preloaded list and the value state write path.
        backend.setCurrentKey(0L);
        int seen = 0;
        for (Long ignored : listState.get()) {
            seen++;
        }
        if (seen != listPreload) {
            throw new IllegalStateException("list preload sanity failed: " + seen);
        }
        valueState.update(1L);
        if (!Long.valueOf(1L).equals(valueState.value())) {
            throw new IllegalStateException("value state sanity failed");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (backend != null) {
            backend.close();
        }
        if (dbDir != null) {
            deleteRecursively(dbDir);
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

    /** InternalListState 追加一个元素，listPreload 控制基线列表长度。 */
    @Benchmark
    public void listAdd(Blackhole bh) throws IOException {
        long key = nextKey();
        backend.setCurrentKey(key);
        listState.add(1L);
        int idx = (int) key;
        if (++listGrowCounters[idx] >= LIST_GROW_LIMIT) {
            // 摊还式基线恢复：列表长度有界，测量保持平稳。
            listState.update(baselineList);
            listGrowCounters[idx] = 0;
        }
        bh.consume(key);
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

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    // best effort on teardown
                }
            });
        }
        Paths.get(dir.toString()).toFile().deleteOnExit();
    }

    /** Long 求和聚合（照抄 TestStateFamilyBackendMatrix.SumAggregate 的形态）。 */
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
