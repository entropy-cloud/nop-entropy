/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
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
import java.util.concurrent.locks.LockSupport;

/**
 * InputGate 读取循环基准（Plan 2279 Phase 1）——量化已登记候选"空通道固定
 * 50ms 轮询"的真实成本。关键事实（审计 03-r2 实证）：channel poll 是信号驱动的，
 * 饱和流下 50ms 只是超时上限、零吞吐成本；真实成本在空闲 CPU 与空闲→首记录延迟
 * （多通道整轮空扫后 10ms park 不被队列插入唤醒）。
 *
 * <p>{@code @Param gapNanos}（生产者两条记录间隔）：0=饱和流（吞吐口径，预期
 * 空闲参数零影响——若实测显示差异即为测量伪影）；100_000=100µs 间歇；
 * 10_000_000=10ms 间歇（空闲→首记录的 park 罚进入口径）。
 *
 * <p><b>JMH 阻塞环失真授权</b>：JMH @Group 迭代是栅栏同步的，gap 大档的 per-op
 * 时间会被等待支配。若判读失真（op 时间 ≈ gap 量级且不随代码改动变化），按计划
 * 退化为独立多线程 harness + 自采直方图，失真判定记录入 evidence——饱和吞吐档
 * （gap=0）不受此影响，始终有效。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Group)
public class InputGateReadLoopBench {

    @Param({"0", "100000", "10000000"})
    long gapNanos;

    ResultPartition partition;
    InputChannel channel;
    InputGate gate;

    long cursor;

    @Setup(Level.Trial)
    public void setup() {
        partition = new ResultPartition();
        channel = new InputChannel(partition);
        gate = new InputGate(channel, null);
        cursor = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        gate.close();
        try {
            partition.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Group("readLoop")
    @GroupThreads(1)
    @Benchmark
    public void producer(Blackhole bh) {
        long v = ++cursor;
        try {
            partition.write(new StreamRecord<>(v, v));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("producer interrupted", e);
        }
        if (gapNanos > 0) {
            LockSupport.parkNanos(gapNanos);
        }
        bh.consume(v);
    }

    @Group("readLoop")
    @GroupThreads(1)
    @Benchmark
    public void consumer(Blackhole bh) {
        bh.consume(gate.read());
    }
}
