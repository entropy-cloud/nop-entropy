/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.InternalTimer;
import io.nop.stream.core.operators.Triggerable;
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

import java.util.concurrent.TimeUnit;

/**
 * HeapInternalTimerService 事件时间定时器基准（Plan 360 Phase 1）。
 *
 * <p>直接构造 {@code new HeapInternalTimerService<>(triggerable, currentKeySupplier)}
 * （WindowOperator.open() 同款组件），key 类型 Long、namespace 固定
 * {@code TimeWindow(0,60000)}，key 经 supplier 在 0..keyCount-1 间轮转。
 *
 * <p>两个基准方法分开测量，均为"注册/触发 1:1 流水线"的有界稳态负载：
 * <ul>
 *   <li><b>registerTimer</b>：每 invocation 注册一个 (key, window) 的 event-time
 *       timer（时间戳步进）；每 {@value #DRAIN_INTERVAL} 次调用推进一次 watermark
 *       把已到期定时器批量清空（摊还后每次调用约 1 次触发），堆上待触发定时器数量
 *       保持有界。</li>
 *   <li><b>advanceWatermark</b>：每 invocation 先在 {@code wm + HORIZON} 处补注册一个
 *       替换定时器，再步进 watermark 1 触发到期批——setup 时预灌 {@value #HORIZON}
 *       个定时器，之后注入/触发速率 1:1，堆上常驻约 {@value #HORIZON} 个 pending
 *       timer，测得的是"推进 watermark + 触发到期批"在真实堆负载下的单步成本。</li>
 * </ul>
 *
 * <p>触发回调只做 {@code fired++} 计数，不引入额外工作。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class TimerServiceBench {

    private static final TimeWindow NAMESPACE = new TimeWindow(0L, 60_000L);
    private static final int DRAIN_INTERVAL = 1024;
    private static final int HORIZON = 4096;

    @Param({"1000"})
    int keyCount;

    private HeapInternalTimerService<Long, TimeWindow> service;
    private CountingTriggerable triggerable;
    private Long[] keys;

    private long keyCursor;
    private long tsCursor;
    private long wmCursor;

    @Setup(Level.Trial)
    public void setup() {
        triggerable = new CountingTriggerable();
        keys = new Long[keyCount];
        for (int i = 0; i < keyCount; i++) {
            keys[i] = (long) i;
        }
        service = new HeapInternalTimerService<>(triggerable, () -> nextKey());

        // 预灌一个 horizon 的定时器，advanceWatermark 从第一步起就有到期批可触发。
        for (long ts = 1; ts <= HORIZON; ts++) {
            service.registerEventTimeTimer(NAMESPACE, ts);
        }
        if (service.numEventTimeTimers() != HORIZON) {
            throw new IllegalStateException(
                    "timer prefill sanity failed: " + service.numEventTimeTimers());
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Heap-only state; explicit no-op teardown for symmetry.
    }

    /** 每 invocation 注册一个 (key, window) 的 event-time timer，key 轮转。 */
    @Benchmark
    public void registerTimer(Blackhole bh) throws Exception {
        long ts = ++tsCursor;
        service.registerEventTimeTimer(NAMESPACE, ts);
        if ((ts & (DRAIN_INTERVAL - 1)) == 0) {
            // 摊还式排空：注入/触发 1:1，堆保持有界。
            service.advanceWatermark(ts - DRAIN_INTERVAL);
        }
        bh.consume(ts);
    }

    /** 每 invocation 步进 watermark 1，触发到期批（替换注册保持流水线非空）。 */
    @Benchmark
    public void advanceWatermark(Blackhole bh) throws Exception {
        long nextWm = ++wmCursor;
        service.registerEventTimeTimer(NAMESPACE, nextWm + HORIZON);
        service.advanceWatermark(nextWm);
        bh.consume(service.currentWatermark());
    }

    private long nextKey() {
        long next = keyCursor++;
        return keys[(int) (next < keyCount ? next : (next % keyCount))];
    }

    /** 只计数的触发回调。 */
    static final class CountingTriggerable implements Triggerable<Long, TimeWindow> {
        long fired;

        @Override
        public void onEventTime(InternalTimer<Long, TimeWindow> timer) {
            fired++;
        }

        @Override
        public void onProcessingTime(InternalTimer<Long, TimeWindow> timer) {
            fired++;
        }
    }
}
