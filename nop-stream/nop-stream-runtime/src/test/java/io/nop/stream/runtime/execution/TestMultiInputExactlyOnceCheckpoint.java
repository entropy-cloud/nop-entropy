/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.StorageJobIds;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7 regression 3/4 — exactly-once over the multi-input path, end to end: two
 * offset-checkpointed sources → union → sink, with enableCheckpointing +
 * LocalFileCheckpointStorage and an explicit jobId/pipelineId (both storage planes
 * key on them). Assertions: no loss, no duplication at the sink; a durable epoch
 * manifest exists whose task snapshots BIND both sources' durable offsets (a=6, b=6);
 * and a second run on the same jobId RESTORES from that lineage — each source's
 * initializeState receives its durable offset (6) and the consumed range is not
 * re-emitted (§八 6: recovery from the latest durable state).
 */
public class TestMultiInputExactlyOnceCheckpoint {

    @TempDir
    Path tempDir;

    /** Serializable EOS-gate supplier (sources are serialized into subtask copies). */
    @FunctionalInterface
    interface EosGate extends java.io.Serializable {
        boolean getAsBoolean();
    }

    /** Heartbeat sentinel value dropped by the filter before the sink. */
    static final int HEARTBEAT = -1;

    /** Offset-checkpointed source: replay-safe via the durable offset. */
    static class CountingSource implements CheckpointedSourceFunction<Integer> {
        private static final long serialVersionUID = 1L;
        private final String tag;
        private final int count;
        /**
         * 负载稳定化（plan 2510）：非 null 时，发射完成后以心跳 collect（每次
         * collect 排水控制邮箱，barrier 注入得以推进）等待该 EOS 证据成立才结束
         * ——bounded run 不再与 checkpoint 周期赛跑，manifest 必然绑定发射完成
         * 后的偏移。30s 上限仅挂死保护，触及即失败。
         */
        private final EosGate eosGate;
        private static final String OFFSET_KEY = "wi7-offset";
        private volatile boolean running = true;
        private int emitted;
        /** tag → the offset initializeState received (restored-run evidence). */
        private static final java.util.concurrent.ConcurrentHashMap<String, Integer> RESTORE_PROBE =
                new java.util.concurrent.ConcurrentHashMap<>();

        static void setRestoreProbe(java.util.concurrent.ConcurrentHashMap<String, Integer> probe) {
            RESTORE_PROBE.clear();
        }

        static int restoreProbe(String tag) {
            Integer v = RESTORE_PROBE.get(tag);
            return v == null ? -1 : v;
        }

        CountingSource(String tag, int count) {
            this(tag, count, null);
        }

        CountingSource(String tag, int count, EosGate eosGate) {
            this.tag = tag;
            this.count = count;
            this.eosGate = eosGate;
        }

        @Override
        public void run(SourceFunction.SourceContext<Integer> ctx) throws Exception {
            for (int i = emitted + 1; i <= count && running; i++) {
                ctx.collect(i);
                emitted = i;
            }
            if (eosGate != null) {
                long deadline = System.currentTimeMillis() + 30_000L;
                long beats = 0;
                while (running && !eosGate.getAsBoolean()) {
                    if (System.currentTimeMillis() >= deadline) {
                        throw new IllegalStateException(
                                "EOS gate evidence did not converge within 30s (tag=" + tag + ")");
                    }
                    ctx.collect(HEARTBEAT);
                    beats++;
                    if (beats % 64 == 0) {
                        java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
                    }
                }
            }
        }

        @Override
        public void cancel() {
            running = false;
        }

        @Override
        public io.nop.stream.core.checkpoint.OperatorSnapshotResult snapshotState(long checkpointId) {
            io.nop.stream.core.checkpoint.OperatorSnapshotResult result =
                    io.nop.stream.core.checkpoint.OperatorSnapshotResult.empty();
            result.putOperatorState(OFFSET_KEY + ":" + tag, emitted);
            return result;
        }

        @Override
        public void initializeState(io.nop.stream.core.checkpoint.TaskStateSnapshot state) {
            if (state != null && state.getOperatorStates() != null) {
                Object restored = state.getOperatorStates().get(OFFSET_KEY + ":" + tag);
                if (restored instanceof Integer) {
                    emitted = (Integer) restored;
                    RESTORE_PROBE.put(tag, emitted);
                }
            }
        }
    }

    @Test
    public void twoSourceUnionIsExactlyOnceAndRestoresFromDurableState() throws Exception {
        String storagePath = tempDir.resolve("wi7-storage").toString();
        List<Integer> sink = new CopyOnWriteArrayList<>();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(50);
        env.getCheckpointConfig().setStorageProperty("path", storagePath);
        env.getCheckpointConfig().setJobId("wi7-eos-job");
        env.getCheckpointConfig().setPipelineId("pipeline-0");

        env.addSource(new CountingSource("a", 6), "src-a")
                .union(env.addSource(new CountingSource("b", 6), "src-b"))
                .sink(sink::add);

        env.execute("wi7-eos-run-1");

        // exactly-once observable form: each source's 1..6 appears exactly once
        Collections.sort(sink);
        assertEquals(12, sink.size(), "no loss, no duplication over the union path");
        for (int v = 1; v <= 6; v++) {
            assertEquals(2, countOf(sink, v), "value " + v + " must appear exactly once per source");
        }

        // durable manifest exists (§八 6 evidence layer 1) — the storage key is the
        // execute() job name (TestCheckpointGateServiceLoaderE2E precedent)
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(storagePath);
        var manifest = storage.loadLatestEpochManifest(
                StorageJobIds.sanitizeJobId("wi7-eos-run-1"), "pipeline-0");
        assertNotNull(manifest, "a durable epoch manifest must exist after the run");
    }

    @Test
    public void secondRunRestoresFromDurableOffsetsWithoutDuplication() throws Exception {
        String storagePath = tempDir.resolve("wi7-storage-restore").toString();

        // run 1: produce durable state. 负载稳定化（plan 2510）：源以 EOS 门等待
        // 「latest manifest 绑定 6/6」的证据（心跳 collect 排水控制邮箱，哨兵经
        // filter 在 sink 前丢弃），bounded run 不与 checkpoint 周期赛跑——发射
        // 完成后的周期 checkpoint 必然落盘，终端 manifest 继承的就是该偏移。
        runJob(storagePath, "wi7-restore-job");

        // §八 6 evidence layer 1: the manifest's task snapshots record EXACTLY the
        // offsets the sources' snapshotState wrote (a=6, b=6)
        assertTrue(manifestBindsOffsets(storagePath, "wi7-restore-job", 6),
                "manifest task snapshots must bind both sources' durable offsets to 6");

        // run 2: same jobId/pipelineId — the restored sources must continue from the
        // durable offsets; the bounded sources were complete at offset 6, so run 2
        // re-emits NOTHING (a re-emit would duplicate the full 1..6 range).
        List<Integer> sink = new CopyOnWriteArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(50);
        env.getCheckpointConfig().setStorageProperty("path", storagePath);
        env.getCheckpointConfig().setJobId("wi7-restore-job");
        env.getCheckpointConfig().setPipelineId("pipeline-0");

        CountingSource.setRestoreProbe(new java.util.concurrent.ConcurrentHashMap<>());
        // 与 run 1 完全相同的管线声明（filter 在内）——restore 走 StreamModel
        // 指纹校验，拓扑必须逐字一致
        env.addSource(new CountingSource("a", 6), "src-a")
                .union(env.addSource(new CountingSource("b", 6), "src-b"))
                .filter(v -> v >= 0)
                .sink(sink::add);
        env.execute("wi7-restore-job");

        // §八 6 evidence layer 2: each source's initializeState received the durable
        // offset (6) from the restored state — proving the restore path consumed the
        // manifest lineage rather than starting cold
        assertEquals(6, CountingSource.restoreProbe("a"),
                "source a must be restored to its durable offset 6");
        assertEquals(6, CountingSource.restoreProbe("b"),
                "source b must be restored to its durable offset 6");
        assertTrue(sink.isEmpty(),
                "restored run must not re-emit the consumed range (offsets were complete)");
    }

    /** 负载稳定化（plan 2510）：latest durable manifest 的任务快照是否绑定两源偏移 = expected。 */
    private static boolean manifestBindsOffsets(String storagePath, String jobId, int expected) {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(storagePath);
        var manifest = storage.loadLatestEpochManifest(
                StorageJobIds.sanitizeJobId(jobId), "pipeline-0");
        if (manifest == null) {
            return false;
        }
        boolean foundA = false;
        boolean foundB = false;
        for (var entry : manifest.getTaskSnapshots().entrySet()) {
            Map<String, Object> states = entry.getValue().getOperatorStates();
            if (states == null) continue;
            // keys are operator-prefixed ("operator-0-wi7-offset:a") — match by suffix
            for (Map.Entry<String, Object> st : states.entrySet()) {
                if (st.getKey().endsWith("wi7-offset:a") && Integer.valueOf(expected).equals(st.getValue())) {
                    foundA = true;
                }
                if (st.getKey().endsWith("wi7-offset:b") && Integer.valueOf(expected).equals(st.getValue())) {
                    foundB = true;
                }
            }
        }
        return foundA && foundB;
    }

    private void runJob(String storagePath, String jobId) throws Exception {
        List<Integer> sink = new CopyOnWriteArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(50);
        env.getCheckpointConfig().setStorageProperty("path", storagePath);
        env.getCheckpointConfig().setJobId(jobId);
        env.getCheckpointConfig().setPipelineId("pipeline-0");

        EosGate gate = () -> manifestBindsOffsets(storagePath, jobId, 6);
        env.addSource(new CountingSource("a", 6, gate), "src-a")
                .union(env.addSource(new CountingSource("b", 6, gate), "src-b"))
                .filter(v -> v >= 0)
                .sink(sink::add);
        env.execute(jobId);
        assertEquals(12, sink.size());
    }

    private static int countOf(List<Integer> list, int value) {
        return (int) list.stream().filter(v -> v == value).count();
    }
}
