/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.EpochState;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CheckpointSerDe 序列化/反序列化基准（Plan 360 Phase 1）。
 *
 * <p>被测入口（照实生产类型）：{@code CheckpointSerDe.serializeEpochManifest(EpochManifest)}
 * 与 {@code CheckpointSerDe.deserializeEpochManifest(byte[])}（runtime checkpoint persist
 * 路径，两个存储后端都经过这里）。
 *
 * <p>输入构造：一个 {@link EpochManifest}，其唯一 {@link TaskStateSnapshot} 携带
 * <ul>
 *   <li>10 000 条 keyed 状态条目（{@code "k0".."k9999"} → 单字段 bean/long 值）；</li>
 *   <li>1 个 1MB {@code byte[]} 载荷（operator state；JSON persist 路径将其按
 *       base64 内联，是当前生产的真实编码方式）。</li>
 * </ul>
 *
 * <p>两个基准方法分开测量：serialize 每次 从同一 manifest 重新序列化
 * （含 Stage 51 canonical checksum 的 JSON 往返 + SHA-256）；deserialize 消费
 * setup 时一次性序列化好的字节数组（含 checksum 校验与任务快照重建）。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class CheckpointSerDeBench {

    private static final int KEYED_ENTRY_COUNT = 10_000;
    private static final int PAYLOAD_BYTES = 1024 * 1024;

    private EpochManifest manifest;
    private byte[] serialized;

    @Setup(Level.Trial)
    public void setup() {
        TaskLocation location = new TaskLocation("bench-job", "bench-pipeline", "bench-vertex", 0);
        TaskStateSnapshot snapshot = new TaskStateSnapshot(location);

        Map<String, Object> keyedEntries = new LinkedHashMap<>(KEYED_ENTRY_COUNT * 2);
        for (int i = 0; i < KEYED_ENTRY_COUNT; i++) {
            keyedEntries.put("k" + i, (long) i);
        }
        snapshot.putKeyedState("bench-backend", keyedEntries);

        byte[] payload = new byte[PAYLOAD_BYTES];
        for (int i = 0; i < PAYLOAD_BYTES; i++) {
            payload[i] = (byte) (i & 0xFF);
        }
        snapshot.putOperatorState("bench-payload", payload);

        Map<TaskLocation, TaskStateSnapshot> taskSnapshots = new LinkedHashMap<>();
        taskSnapshots.put(location, snapshot);

        manifest = new EpochManifest(42L, "bench-job", "bench-pipeline", 1_735_000_000_000L,
                CheckpointType.CHECKPOINT, EpochState.COMMITTED,
                taskSnapshots, null, new ArrayList<>(), Collections.emptyMap());

        serialized = CheckpointSerDe.serializeEpochManifest(manifest);

        // Sanity: the serialized bytes must round-trip epochId and payload size hints.
        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(serialized);
        if (restored == null || restored.getEpochId() != 42L) {
            throw new IllegalStateException("checkpoint serde sanity failed");
        }
        Object restoredKeyed = restored.getTaskSnapshots().get(location)
                .getKeyedState("bench-backend");
        if (!(restoredKeyed instanceof Map) || ((Map<?, ?>) restoredKeyed).size() != KEYED_ENTRY_COUNT) {
            throw new IllegalStateException("checkpoint keyed-state sanity failed");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        manifest = null;
        serialized = null;
    }

    /** manifest → JSON 字节（含 canonical checksum 计算）。 */
    @Benchmark
    public void serialize(Blackhole bh) {
        byte[] bytes = CheckpointSerDe.serializeEpochManifest(manifest);
        bh.consume(bytes.length);
    }

    /** JSON 字节 → manifest（含 checksum 校验 + 任务快照重建）。 */
    @Benchmark
    public void deserialize(Blackhole bh) {
        EpochManifest restored = CheckpointSerDe.deserializeEpochManifest(serialized);
        bh.consume(restored.getEpochId());
    }
}
