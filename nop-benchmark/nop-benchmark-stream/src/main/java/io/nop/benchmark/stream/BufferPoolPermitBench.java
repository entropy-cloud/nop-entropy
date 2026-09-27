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
import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.execution.buffer.IBufferPool;
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

/**
 * BufferPool 许可申请基准（Plan 2279 Phase 1）——生产侧
 * {@code ResultPartition.enqueueWithBackpressure} 每元素 acquire、消费侧
 * {@code InputChannel.read} 每元素 release 的真实路径成本。池为公平信号量
 * （跨分区 FIFO 契约），生产-消费 ping-pong 下每次 release 唤醒一个 park 的
 * acquirer（AQS 握手）。
 *
 * <p>{@code @Param poolCapacity}：
 * <ul>
 *   <li>1 —— 强制乒乓：每条 write 必须等消费侧 read 释放唯一许可（背压挤压
 *       极端形态，批量许可候选的目标场景）</li>
 *   <li>64 —— 近畅通：许可充裕，acquire 基本不阻塞（诚实预期此档收益 &lt;2%）</li>
 * </ul>
 *
 * <p>两个测量：单线程无竞争 acquire/release 对（公平信号量无竞争基线），与
 * {@code pingPong} 组（生产/消费各 1 线程，经真实 ResultPartition 队列传递）。
 * 池边界候选（批量 release/acquire 记账）实施后以同口径对比。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class BufferPoolPermitBench {

    @Param({"1", "64"})
    int poolCapacity;

    IBufferPool pool;
    /** pingPong 组共享：生产 partition（池边界）与消费 channel。 */
    ResultPartition partition;
    InputChannel channel;
    InputGate gate;

    long cursor;

    @Setup(Level.Trial)
    public void setup() {
        pool = new BufferPool(poolCapacity);
        partition = new ResultPartition(128, pool);
        channel = new InputChannel(partition);
        gate = new InputGate(channel, null);
        cursor = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (gate != null) {
            gate.close();
        }
    }

    /** 无竞争基线：acquire + release 对（许可充裕，永不阻塞）。 */
    @Benchmark
    public void acquireReleaseUncontended(Blackhole bh) throws InterruptedException {
        pool.acquire();
        bh.consume(cursor);
        pool.release();
    }

    /**
     * 生产-消费乒乓组：producer 经真实 enqueueWithBackpressure 路径 acquire+put，
     * consumer 经真实 read 路径 poll+release。capacity=1 时构成强制乒乓。
     */
    @Group("pingPong")
    @GroupThreads(1)
    @Benchmark
    public void producer(Blackhole bh) throws InterruptedException {
        long v = ++cursor;
        partition.write(new StreamRecord<>(v, v));
        bh.consume(v);
    }

    @Group("pingPong")
    @GroupThreads(1)
    @Benchmark
    public void consumer(Blackhole bh) throws InterruptedException {
        bh.consume(channel.read());
    }
}
