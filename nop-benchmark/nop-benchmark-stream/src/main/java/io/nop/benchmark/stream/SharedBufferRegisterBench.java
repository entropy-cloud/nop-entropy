/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.DeweyNumber;
import io.nop.stream.cep.nfa.sharedbuffer.EventId;
import io.nop.stream.cep.nfa.sharedbuffer.NodeId;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBuffer;
import io.nop.stream.cep.nfa.sharedbuffer.SharedBufferAccessor;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * SharedBuffer 事件注册 + 节点挂载基准（Plan 360 Phase 1）。
 *
 * <p>构造方式照抄 nop-stream-cep 的 {@code TestSharedBuffer}：
 * {@code new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig())}
 * （Memory 后端），accessor 以 try-with-resources 打开/关闭（close 会 flush 缓存，
 * 与 NFA 每事件一个 accessor 的真实调用形态一致）。
 *
 * <p>负载：每 invocation {@code registerEvent} 一个新事件，并在其上挂出 3 级路径
 * {@code put("bench-s1", e, null, d1)} → {@code put("bench-s2", e, n1, d2)} →
 * {@code put("bench-s3", e, n2, d3)}（DeweyNumber 逐级 addStage，照抄
 * TestSharedBuffer.testRetrieveByCondition 的版本链形态）。
 *
 * <p>有界性：registerEvent/put 之后不做释放（规格即 register + put），为防止
 * SharedBuffer 随 invocation 无限增长，每 {@value #BUFFER_ROTATE_INTERVAL} 次
 * invocation 换一个全新 SharedBuffer（旧实例整体丢弃交由 GC）。替换操作摊还后
 * 每次调用的额外开销可忽略，且对任意吞吐/时长都保证堆有界。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class SharedBufferRegisterBench {
    /** key-scoped accessor 的键（与生产 CepOperator.getCurrentKey() 对应，plan 360 R3）。 */
    private static final Object BENCH_KEY = "bench-key-1";


    private static final int BUFFER_ROTATE_INTERVAL = 4096;

    private SharedBuffer<BenchCepEvent> buffer;

    private long seqCursor;
    private long tsCursor;
    private long opCursor;

    @Setup(Level.Trial)
    public void setup() {
        buffer = newBuffer();

        // Sanity: one register+put cycle must land in the buffer.
        try (SharedBufferAccessor<BenchCepEvent> accessor = buffer.getAccessor(BENCH_KEY)) { // key-scoped：与生产 CepOperator 一致（plan 360 R3）
            EventId eventId = accessor.registerEvent(new BenchCepEvent(0L, true), 1L);
            DeweyNumber d1 = new DeweyNumber(1);
            NodeId n1 = accessor.put("bench-s1", eventId, null, d1);
            if (n1 == null) {
                throw new IllegalStateException("shared buffer put sanity failed");
            }
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        buffer = null;
    }

    /** 每 invocation：registerEvent + 3 级 DeweyNumber 路径 put。 */
    @Benchmark
    public void registerEventAndPut(Blackhole bh) throws Exception {
        if ((++opCursor & (BUFFER_ROTATE_INTERVAL - 1)) == 0) {
            // 有界性保护：整体轮换缓冲，摊开销可忽略。
            buffer = newBuffer();
        }
        BenchCepEvent event = new BenchCepEvent(++seqCursor, true);
        long ts = ++tsCursor;
        try (SharedBufferAccessor<BenchCepEvent> accessor = buffer.getAccessor(BENCH_KEY)) { // key-scoped：与生产 CepOperator 一致（plan 360 R3）
            EventId eventId = accessor.registerEvent(event, ts);
            DeweyNumber d1 = new DeweyNumber(1);
            NodeId n1 = accessor.put("bench-s1", eventId, null, d1);
            DeweyNumber d2 = d1.addStage();
            NodeId n2 = accessor.put("bench-s2", eventId, n1, d2);
            DeweyNumber d3 = d2.addStage();
            NodeId n3 = accessor.put("bench-s3", eventId, n2, d3);
            bh.consume(n3);
            bh.consume(n2.getPageName());
        }
    }

    private static SharedBuffer<BenchCepEvent> newBuffer() {
        return new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
    }
}
