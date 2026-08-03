/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroup;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 47, Phase 3: end-to-end proof of the unaligned-checkpoint + rescale
 * interaction (plan guide #22 Anti-Hollow, #23 Wiring). Drives the FULL restore
 * path through {@link GraphModelCheckpointExecutor}: a savepoint staged with
 * channel state (unaligned checkpoint) is restored at a different parallelism,
 * and the fail-fast must fire through the real {@code restoreTaskStatesFromSource}
 * → {@code assertNoChannelStateOnRescale} path — not just the unit-tested helper.
 *
 * <p>Three scenarios:
 * <ul>
 *   <li>{@code unalignedCheckpointThenRescale_failsFast} — the live-defect fix:
 *       channel state present + parallelism change → ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED.</li>
 *   <li>{@code alignedCheckpointThenRescale_succeeds} — regression baseline:
 *       no channel state + parallelism change → keyed rescale works (Stage 35).</li>
 *   <li>{@code unalignedCheckpointSameParallelism_succeeds} — no-regression:
 *       channel state present + SAME parallelism → channel state restored (no rescale branch).</li>
 * </ul>
 */
public class TestChannelStateRescaleE2E {

    private static final int MAX_P = 16;
    private static final String VERTEX_ID = "cs-rescale-vertex";
    private static final String KEYED_STORAGE_KEY = "operator-1-keyed";

    /**
     * A keyed operator so {@code CheckpointPlanBuilder} assigns a keyedStateStorageKey,
     * making the vertex "keyed" ({@code isVertexKeyed=true}) so the rescale branch
     * activates on a parallelism change. Records restored keys for verification.
     */
    public static final class KeyCapturingOperator extends AbstractStreamOperator<String> {
        private static final long serialVersionUID = 1L;

        public static final java.util.List<java.util.Set<String>> CAPTURED =
                new CopyOnWriteArrayList<>();

        public KeyCapturingOperator() {
            setKeyedStateBackend(new io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend<>(String.class, MAX_P));
        }

        @Override
        public void restoreState(io.nop.stream.core.checkpoint.OperatorSnapshotResult snapshotResult) throws Exception {
            if (snapshotResult != null && snapshotResult.getKeyedStates() != null) {
                java.util.Set<String> keys = new java.util.LinkedHashSet<>();
                for (Map.Entry<String, Object> e : snapshotResult.getKeyedStates().entrySet()) {
                    extractKeys(e.getValue(), keys);
                }
                if (!keys.isEmpty()) {
                    CAPTURED.add(keys);
                }
            }
            super.restoreState(snapshotResult);
        }

        @SuppressWarnings("unchecked")
        private static void extractKeys(Object keyedValue, java.util.Set<String> out) {
            Map<String, Object> data;
            if (keyedValue instanceof StateSnapshot) {
                data = ((StateSnapshot) keyedValue).getStateData();
            } else if (keyedValue instanceof Map) {
                data = (Map<String, Object>) keyedValue;
            } else {
                return;
            }
            Object statesObj = data.get("states");
            if (!(statesObj instanceof Map)) return;
            for (Object infoObj : ((Map<String, Object>) statesObj).values()) {
                if (!(infoObj instanceof Map)) continue;
                Object entries = ((Map<String, Object>) infoObj).get("entries");
                if (!(entries instanceof java.util.List)) continue;
                for (Object entry : (java.util.List<?>) entries) {
                    if (entry instanceof Map) {
                        Object k = ((Map<String, Object>) entry).get("key");
                        if (k != null) out.add(k.toString());
                    }
                }
            }
        }
    }

    @TempDir
    Path tempDir;

    /** Builds a keyed ValueState snapshot data map for the given (key -> value) entries. */
    private static StateSnapshot buildKeyedSnapshot(Map<String, Long> entries) {
        java.util.List<Map<String, Object>> entryList = new ArrayList<>();
        for (Map.Entry<String, Long> e : entries.entrySet()) {
            Map<String, Object> en = new LinkedHashMap<>();
            en.put("namespace", "_default_");
            en.put("key", e.getKey());
            en.put("value", e.getValue());
            entryList.add(en);
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("stateType", "ValueState");
        info.put("valueType", "java.lang.Long");
        info.put("entries", entryList);

        Map<String, Object> states = new LinkedHashMap<>();
        states.put("count", info);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyType", "java.lang.String");
        data.put("states", states);
        return new StateSnapshot(data);
    }

    private Map<String, Long> buildKeys(int count) {
        Map<String, Long> keys = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) keys.put("cs-rescale-key-" + i, (long) i);
        return keys;
    }

    /**
     * Stages a savepoint at parallelism=pOld with keyed state, optionally
     * attaching non-empty channel state to each subtask snapshot (simulating an
     * unaligned checkpoint).
     */
    private String stageSavepoint(int pOld, Map<String, Long> allKeys, String jobId, String pipelineId,
                                   boolean withChannelState) {
        java.util.List<java.util.List<String>> keysPerSubtask = new ArrayList<>();
        for (int s = 0; s < pOld; s++) keysPerSubtask.add(new ArrayList<>());
        for (String key : allKeys.keySet()) {
            int gid = KeyGroupAssignment.assignToKeyGroup(key, MAX_P);
            int sub = KeyGroupAssignment.assignKeyGroupToSubtask(gid, MAX_P, pOld);
            keysPerSubtask.get(sub).add(key);
        }

        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        for (int s = 0; s < pOld; s++) {
            TaskLocation loc = new TaskLocation(jobId, pipelineId, VERTEX_ID, s);
            Map<String, Long> subEntries = new LinkedHashMap<>();
            for (String k : keysPerSubtask.get(s)) subEntries.put(k, allKeys.get(k));

            TaskEpochSnapshot ts = new TaskEpochSnapshot(loc, 1L);
            ts.putKeyedState(KEYED_STORAGE_KEY, buildKeyedSnapshot(subEntries));
            ts.setParallelism(pOld);
            ts.setMaxParallelism(MAX_P);
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, pOld, s);
            ts.setKeyGroupRangeStart(range.getStartKeyGroup());
            ts.setKeyGroupRangeEnd(range.getEndKeyGroup());
            if (withChannelState) {
                ChannelState cs = new ChannelState();
                cs.putRecords(0, Collections.singletonList(new StreamRecord<>("in-flight-" + s)));
                ts.setChannelState(cs);
            }
            taskStates.put(loc, ts);
        }

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId(jobId).pipelineId(pipelineId).checkpointId(1L)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.SAVEPOINT)
                .taskStates(taskStates)
                .build();

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        return storage.storeCheckPoint(checkpoint);
    }

    private void runRestoreAtParallelism(int pNew, String jobId, String pipelineId,
                                          CheckpointConfig config) throws Exception {
        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;
            @Override public void run(SourceContext<String> ctx) { }
            @Override public void cancel() { }
        };

        StreamSourceOperator<String> srcOp = new StreamSourceOperator<>(source);
        KeyCapturingOperator keyedOp = new KeyCapturingOperator();
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(v -> {});

        OperatorChain chain = new OperatorChain(Arrays.asList(srcOp, keyedOp, sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex(VERTEX_ID, "cs-rescale", pNew, Collections.singletonList(chain), invokable);
        JobGraph jobGraph = new JobGraph("cs-rescale-test");
        jobGraph.addVertex(vertex);

        config.setJobId(jobId);
        config.setPipelineId(pipelineId);
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(600000L);
        config.setStorageProperty("path", tempDir.toString());

        GraphModelCheckpointExecutor.executeWithSavepoint(jobGraph, "cs-rescale", config, tempDir.toString());
    }

    /**
     * E2E Anti-Hollow proof of the live-defect fix: an unaligned checkpoint
     * (channel state present) restored at a different parallelism must fail-fast
     * with ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED through the real restore
     * path — not silently drop channel state.
     */
    @Test
    void unalignedCheckpointThenRescale_failsFast() throws Exception {
        KeyCapturingOperator.CAPTURED.clear();
        Map<String, Long> allKeys = buildKeys(20);
        String jobId = "ucs-rf-job";
        String pipelineId = "ucs-rf-pipe";

        // Stage at p=2 WITH channel state (unaligned checkpoint), restore at p=4 (rescale).
        stageSavepoint(2, allKeys, jobId, pipelineId, true);
        CheckpointConfig config = new CheckpointConfig();

        io.nop.stream.core.exceptions.StreamException thrown = assertThrows(
                io.nop.stream.core.exceptions.StreamException.class,
                () -> runRestoreAtParallelism(4, jobId, pipelineId, config));
        assertEquals(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED.getErrorCode(),
                thrown.getErrorCode(),
                "unaligned checkpoint + rescale must fail-fast (not silently drop channel state)");
    }

    /**
     * Regression baseline: aligned checkpoint (no channel state) + rescale works
     * — Stage 35 keyed rescale is unaffected by the Stage 47 guard.
     */
    @Test
    void alignedCheckpointThenRescale_succeeds() throws Exception {
        KeyCapturingOperator.CAPTURED.clear();
        Map<String, Long> allKeys = buildKeys(20);
        String jobId = "acs-rf-job";
        String pipelineId = "acs-rf-pipe";

        // Stage at p=2 WITHOUT channel state (aligned), restore at p=4 (rescale).
        stageSavepoint(2, allKeys, jobId, pipelineId, false);
        CheckpointConfig config = new CheckpointConfig();
        runRestoreAtParallelism(4, jobId, pipelineId, config);

        // The keyed rescale must have routed keys to the new subtasks.
        java.util.Set<String> union = new java.util.LinkedHashSet<>();
        for (java.util.Set<String> captured : KeyCapturingOperator.CAPTURED) {
            union.addAll(captured);
        }
        assertEquals(allKeys.keySet(), union,
                "aligned checkpoint + rescale: union of restored keys must equal original (Stage 35 unaffected)");
    }

    /**
     * No-regression: unaligned checkpoint (channel state present) restored at the
     * SAME parallelism takes the non-rescale branch — channel state is restored
     * normally, no fail-fast.
     */
    @Test
    void unalignedCheckpointSameParallelism_succeeds() throws Exception {
        KeyCapturingOperator.CAPTURED.clear();
        Map<String, Long> allKeys = buildKeys(20);
        String jobId = "ucs-sp-job";
        String pipelineId = "ucs-sp-pipe";

        // Stage at p=2 WITH channel state, restore at p=2 (same parallelism, no rescale).
        stageSavepoint(2, allKeys, jobId, pipelineId, true);
        CheckpointConfig config = new CheckpointConfig();
        runRestoreAtParallelism(2, jobId, pipelineId, config);

        // Same-parallelism restore: keys preserved (1:1 path).
        java.util.Set<String> union = new java.util.LinkedHashSet<>();
        for (java.util.Set<String> captured : KeyCapturingOperator.CAPTURED) {
            union.addAll(captured);
        }
        // At same parallelism each subtask gets its own slice; union must equal all keys.
        assertTrue(union.containsAll(allKeys.keySet()),
                "same-parallelism + channel state: keys must be restored (no-regression)");
    }
}
