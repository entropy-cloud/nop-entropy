/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.stream.benchmark.BenchPayload;
import io.nop.stream.core.execution.transport.StreamElementCodec;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.transport.KafkaStringWireCodec;
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
 * StreamElementCodec + KafkaStringWireCodec 编解码往返基准（Plan 360 Phase 1）。
 *
 * <p>被测路径：{@code StreamElementCodec.encode/decode}（core，JSON payload 编解码 +
 * envelope 装配/解析）与 {@code KafkaStringWireCodec.toWire/fromWire}（runtime，envelope
 * 与 wire JSON 串互转）。
 *
 * <ul>
 *   <li><b>fullRoundTrip</b>：encode → toWire → fromWire → decode 全链路，
 *       每次调用都从同一个预构造 {@code StreamRecord<BenchPayload>} 出发。</li>
 *   <li><b>decodeOnly</b>：Trial setup 时预编码好的 wire JSON 串上反复
 *       fromWire → decode，隔离反序列化成本。</li>
 * </ul>
 *
 * <p>payload 为 5 字段 + 1 嵌套对象的 {@link BenchPayload}（JSON 序列化后约 200B）。
 * 其类名必须落在 {@code io.nop.stream.} 前缀下（decode 侧 ClassNameValidator 白名单约束，
 * 见 BenchPayload 的 javadoc）。所有产物均经 {@code Blackhole} 消费，防止 JIT 死代码消除。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class StreamElementCodecRoundTripBench {

    private static final long EPOCH_ID = 7L;

    private BenchPayload payload;
    private StreamRecord<BenchPayload> record;
    private KafkaStringWireCodec codec;

    /** decodeOnly 输入：setup 时预编码的 wire JSON 串（String，Kafka String schema 形态）。 */
    private String wireJson;

    @Setup(Level.Trial)
    public void setup() {
        payload = new BenchPayload("device-bench-0001", 20260926L, 23.5d, true,
                new BenchPayload.InnerMetric("cpu", 87L));
        record = new StreamRecord<>(payload, 1_000L);
        codec = KafkaStringWireCodec.INSTANCE;

        wireJson = (String) codec.toWire(
                StreamElementCodec.encode(record, BenchPayload.class.getName(), EPOCH_ID));

        // Sanity check: the full decode path must rebuild the payload bean.
        StreamElement decoded = StreamElementCodec.decode(codec.fromWire(wireJson));
        Object value = decoded.asRecord().getValue();
        if (!(value instanceof BenchPayload)
                || ((BenchPayload) value).getSequence() != 20260926L) {
            throw new IllegalStateException("codec round-trip sanity check failed: " + value);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // No external resources held; explicit no-op teardown for symmetry.
    }

    /** encode → toWire → fromWire → decode 全链路往返。 */
    @Benchmark
    public void fullRoundTrip(Blackhole bh) {
        StreamMessageEnvelope envelope =
                StreamElementCodec.encode(record, BenchPayload.class.getName(), EPOCH_ID);
        Object wire = codec.toWire(envelope);
        StreamMessageEnvelope received = codec.fromWire(wire);
        StreamElement element = StreamElementCodec.decode(received);
        bh.consume(element.asRecord().getValue());
    }

    /** 预编码 wire 串上的 fromWire → decode（仅反序列化侧）。 */
    @Benchmark
    public void decodeOnly(Blackhole bh) {
        StreamMessageEnvelope received = codec.fromWire(wireJson);
        StreamElement element = StreamElementCodec.decode(received);
        bh.consume(element.asRecord().getValue());
    }
}
