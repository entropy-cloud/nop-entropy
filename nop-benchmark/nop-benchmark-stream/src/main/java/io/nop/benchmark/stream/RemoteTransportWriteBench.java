/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.transport.RemoteResultPartition;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * RemoteResultPartition.write 基准（Plan 2279 Phase 1）——远程边数据面的
 * 编码 + 同步发送路径。生产实现中 {@code write} 在 sendLock 外编码、锁内做
 * {@code IMessageService.send}（默认实现阻塞等待 sendAsync 完成，plan 2279 Q2
 * 锁收窄后的形态）；心跳 {@code sendHeartbeatIfIdle} 与写共用同一 sendLock。
 *
 * <p>后端桩：{@code DelayedMessageService.sendAsync} 以 {@code LockSupport.parkNanos}
 * 模拟可参数化的后端发送延迟——{@code sendDelay=0} 为缓冲 append 型后端（Kafka 形态），
 * {@code sendDelay=5ms} 为同步 JDBC 写型后端（SysDao 形态，锁内阻塞的代表性场景）。
 * 真实生产路径：encode（StreamElementCodec + JsonTool）与 send 全部经过被测代码。
 *
 * <p>{@code @Param sendDelayNanos}：0 / 100_000 (100µs) / 5_000_000 (5ms)。
 * {@code @Param fanout}：1 / 4 / 16 —— writeBroadcast 复现 RecordWriter broadcast
 * 分支对 T 个分区逐个 write 的形状（同一条记录重复编码 T 次）。
 *
 * <p>{@code writerHeartbeatSamePartition} 组（各 1 线程）复现生产互卡形状：writer
 * 线程持续 write、heartbeat 线程持续调用 {@code sendHeartbeatIfIdle()}，两者竞争
 * 同一 partition 的 monitor（心跳 interval=1ms 使空闲判定大多数时候通过）。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class RemoteTransportWriteBench {

    @Param({"0", "100000", "5000000"})
    long sendDelayNanos;

    @Param({"1", "4", "16"})
    int fanout;

    RemoteResultPartition[] dataPartitions;
    /** 心跳互卡组专用：心跳 interval=1ms，使 sendHeartbeatIfIdle 的空闲判定常态通过。 */
    RemoteResultPartition heartbeatPartition;
    TypeRegistry typeRegistry;

    long cursor;

    @Setup(Level.Trial)
    public void setup() {
        typeRegistry = new TypeRegistry();
        typeRegistry.register("edge-1", Long.class.getName());

        DelayedMessageService service = new DelayedMessageService(sendDelayNanos);
        dataPartitions = new RemoteResultPartition[fanout];
        for (int i = 0; i < fanout; i++) {
            dataPartitions[i] = new RemoteResultPartition(
                    service, "bench-data-" + i, typeRegistry, "edge-1", 1L, 0L);
        }
        heartbeatPartition = new RemoteResultPartition(
                service, "bench-hb", typeRegistry, "edge-1", 1L, 1L);

        // Sanity: one record must flow through the real encode+send path.
        try {
            dataPartitions[0].write(new StreamRecord<>(-1L, 0L));
            heartbeatPartition.write(new StreamRecord<>(-1L, 0L));
        } catch (InterruptedException e) {
            throw new IllegalStateException("sanity write interrupted", e);
        }
        cursor = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Partitions are never finished in steady-state benchmarks; closing them
        // would send EOS through the delayed backend (5ms x fanout) — skip close,
        // the objects are garbage anyway.
    }

    /** 单分区写：encode + 锁内同步 send 的每记录成本。 */
    @Benchmark
    public void writeSingle(Blackhole bh) throws InterruptedException {
        long v = ++cursor;
        dataPartitions[0].write(new StreamRecord<>(v, v));
        bh.consume(v);
    }

    /** broadcast 写：同一条记录对 fanout 个分区逐个 write（RecordWriter broadcast 形状）。 */
    @Benchmark
    public void writeBroadcast(Blackhole bh) throws InterruptedException {
        long v = ++cursor;
        StreamRecord<Long> record = new StreamRecord<>(v, v);
        for (int i = 0; i < fanout; i++) {
            dataPartitions[i].write(record);
        }
        bh.consume(v);
    }

    /**
     * 写与心跳同分区的 monitor 竞争组：writer 与 heartbeat 各 1 线程。
     * 组整体 ops/s 反映互卡下的有效数据面吞吐。
     */
    @Group("writerHeartbeatSamePartition")
    @GroupThreads(1)
    @Benchmark
    public void writerThread(Blackhole bh) throws InterruptedException {
        long v = ++cursor;
        heartbeatPartition.write(new StreamRecord<>(v, v));
        bh.consume(v);
    }

    @Group("writerHeartbeatSamePartition")
    @GroupThreads(1)
    @Benchmark
    public void heartbeatThread(Blackhole bh) {
        bh.consume(heartbeatPartition.sendHeartbeatIfIdle());
    }

    /**
     * 可参数化延迟的消息服务桩：sendAsync 内 park 模拟后端延迟后立即完成。
     * 真实路径核查：write() 的 encode + messageService.send(topic, envelope)
     * 全部走被测生产代码，桩只替换传输末端的耗时。
     */
    static final class DelayedMessageService implements IMessageService {
        private final long delayNanos;

        DelayedMessageService(long delayNanos) {
            this.delayNanos = delayNanos;
        }

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                                              MessageSubscribeOptions options) {
            return new IMessageSubscription() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isSuspended() {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public void suspend() {
                }

                @Override
                public void resume() {
                }
            };
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message,
                                               MessageSendOptions options) {
            if (delayNanos > 0) {
                LockSupport.parkNanos(delayNanos);
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
