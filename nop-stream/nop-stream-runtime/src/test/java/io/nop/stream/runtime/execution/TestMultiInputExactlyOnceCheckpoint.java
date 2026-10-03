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

    /** Offset-checkpointed source: replay-safe via the durable offset. */
    static class CountingSource implements CheckpointedSourceFunction<Integer> {
        private static final long serialVersionUID = 1L;
        private final String tag;
        private final int count;
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
            this.tag = tag;
            this.count = count;
        }

        @Override
        public void run(SourceFunction.SourceContext<Integer> ctx) throws Exception {
            for (int i = emitted + 1; i <= count && running; i++) {
                ctx.collect(i);
                emitted = i;
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

        // run 1: produce durable state
        runJob(storagePath, "wi7-restore-job");

        // §八 6 evidence layer 1: the manifest's task snapshots record EXACTLY the
        // offsets the sources' snapshotState wrote (a=6, b=6)
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(storagePath);
        var manifest = storage.loadLatestEpochManifest(
                StorageJobIds.sanitizeJobId("wi7-restore-job"), "pipeline-0");
        assertNotNull(manifest);
        boolean foundA = false;
        boolean foundB = false;
        for (var entry : manifest.getTaskSnapshots().entrySet()) {
            Map<String, Object> states = entry.getValue().getOperatorStates();
            if (states == null) continue;
            // keys are operator-prefixed ("operator-0-wi7-offset:a") — match by suffix
            for (Map.Entry<String, Object> st : states.entrySet()) {
                if (st.getKey().endsWith("wi7-offset:a") && Integer.valueOf(6).equals(st.getValue())) {
                    foundA = true;
                }
                if (st.getKey().endsWith("wi7-offset:b") && Integer.valueOf(6).equals(st.getValue())) {
                    foundB = true;
                }
            }
        }
        assertTrue(foundA && foundB,
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
        env.addSource(new CountingSource("a", 6), "src-a")
                .union(env.addSource(new CountingSource("b", 6), "src-b"))
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

    private void runJob(String storagePath, String jobId) throws Exception {
        List<Integer> sink = new CopyOnWriteArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.enableCheckpointing(50);
        env.getCheckpointConfig().setStorageProperty("path", storagePath);
        env.getCheckpointConfig().setJobId(jobId);
        env.getCheckpointConfig().setPipelineId("pipeline-0");

        env.addSource(new CountingSource("a", 6), "src-a")
                .union(env.addSource(new CountingSource("b", 6), "src-b"))
                .sink(sink::add);
        env.execute(jobId);
        assertEquals(12, sink.size());
    }

    private static int countOf(List<Integer> list, int value) {
        return (int) list.stream().filter(v -> v == value).count();
    }
}
