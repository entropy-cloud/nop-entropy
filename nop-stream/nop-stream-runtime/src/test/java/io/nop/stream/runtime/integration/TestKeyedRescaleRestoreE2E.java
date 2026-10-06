/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 KR-06/KR-09 E2E (plan 2324): a REAL parallelism-change restore — the
 * scenario the fraud-example family never covers (S2 keeps job parallelism at
 * 1) and that was structurally unreachable before the snapshot-existence-driven
 * rescale detection (the plan-time keyed marker is dead at restore: keyed
 * backends only materialize in open(), after every restore entry).
 *
 * <p>Drives the live {@code restoreTaskStatesFromSource} path via
 * {@code executeWithSavepoint} (TestChannelStateRescaleE2E harness pattern):
 * <ul>
 *   <li><b>Scale-down</b> 2→1: every key restored exactly once (no loss, no
 *       duplication) — keyed rows re-routed by KeyGroupRange intersection.</li>
 *   <li><b>Scale-up</b> 1→2, pure keyed: rows re-routed to the new subtask
 *       ranges; no operator-state guard trip.</li>
 *   <li><b>Scale-up with per-key auxiliary operator state</b> (production-shape
 *       {@code operator-0-internal-timers} key): the KR-09 guard — dead on
 *       production keys before the prefix-aware fix — fires a typed rejection.</li>
 * </ul>
 *
 * <p>Anti-Hollow: the rescale detection consumes the checkpoint PAYLOAD (staged
 * {@code TaskEpochSnapshot} keyed states), not any plan-time marker, and runs
 * through the real restore entry — not a unit-tested helper.
 */
public class TestKeyedRescaleRestoreE2E {

    private static final int MAX_P = 16;
    private static final String VERTEX_ID = "kr-rescale-vertex";
    /**
     * KR-07 tracker production format: with the plan-time keyed marker dead,
     * CheckpointBarrierTracker ACK writes the keyed snapshot under
     * {@code operator-{i}-keyed-<rawKey>}. Staging in this shape keeps the
     * fixture end-to-end faithful (the restore side reads this prefix).
     */
    private static final String KEYED_STORAGE_KEY = "operator-1-keyed-keyed-state";

    /** Records the keys each restored subtask actually received. */
    public static final class KeyCapturingOperator extends AbstractStreamOperator<String> {
        private static final long serialVersionUID = 1L;

        public static final List<Set<String>> CAPTURED = new CopyOnWriteArrayList<>();

        public KeyCapturingOperator() {
            // R6 closure audit Major-1: NO constructor backend injection — the
            // production shape (ProcessOperator/WindowOperator/CepOperator)
            // creates the keyed backend in open(), so the plan-time keyed marker
            // is dead at restore and KR-06's snapshotHasKeyedState is the only
            // live rescale signal.
        }

        @Override
        public void open() throws Exception {
            super.open();
            if (getStateBackend() != null && getKeyedStateBackend() == null) {
                setKeyedStateBackend(((MemoryStateBackend) getStateBackend())
                        .createKeyedStateBackend(String.class));
            }
        }

        @Override
        public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
            if (snapshotResult != null && snapshotResult.getKeyedStates() != null) {
                Set<String> keys = new LinkedHashSet<>();
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
        private static void extractKeys(Object keyedValue, Set<String> out) {
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
                if (!(entries instanceof List)) continue;
                for (Object entry : (List<?>) entries) {
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

    private Map<String, Long> buildKeys(int count) {
        Map<String, Long> keys = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) keys.put("kr-key-" + i, (long) i);
        return keys;
    }

    private static StateSnapshot buildKeyedSnapshot(Map<String, Long> entries) {
        List<Map<String, Object>> entryList = new ArrayList<>();
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

    /**
     * Stages a durable checkpoint at parallelism=pOld with keyed state laid out
     * by the real KeyGroupAssignment, optionally carrying per-key auxiliary
     * operator state under the PRODUCTION-SHAPE namespaced key.
     */
    private String stageCheckpoint(int pOld, Map<String, Long> allKeys, String jobId,
                                    boolean withAuxiliaryOperatorState) {
        List<List<String>> keysPerSubtask = new ArrayList<>();
        for (int s = 0; s < pOld; s++) keysPerSubtask.add(new ArrayList<>());
        for (String key : allKeys.keySet()) {
            int gid = KeyGroupAssignment.assignToKeyGroup(key, MAX_P);
            int sub = KeyGroupAssignment.assignKeyGroupToSubtask(gid, MAX_P, pOld);
            keysPerSubtask.get(sub).add(key);
        }

        Map<TaskLocation, io.nop.stream.core.checkpoint.TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        for (int s = 0; s < pOld; s++) {
            TaskLocation loc = new TaskLocation(jobId, "pipeline-0", VERTEX_ID, s);
            Map<String, Long> subEntries = new LinkedHashMap<>();
            for (String k : keysPerSubtask.get(s)) subEntries.put(k, allKeys.get(k));

            TaskEpochSnapshot ts = new TaskEpochSnapshot(loc, 1L);
            ts.putKeyedState(KEYED_STORAGE_KEY, buildKeyedSnapshot(subEntries));
            ts.setParallelism(pOld);
            ts.setMaxParallelism(MAX_P);
            KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, pOld, s);
            ts.setKeyGroupRangeStart(range.getStartKeyGroup());
            ts.setKeyGroupRangeEnd(range.getEndKeyGroup());
            if (withAuxiliaryOperatorState) {
                // Production shape: CheckpointBarrierTracker namespaces every
                // operator-state key as operator-{i}-<name>.
                ts.putOperatorState("operator-" + s + "-internal-timers", "timer-entries-" + s);
            }
            taskStates.put(loc, ts);
        }

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId(jobId).pipelineId("pipeline-0").checkpointId(1L)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.SAVEPOINT)
                .taskStates(taskStates)
                .build();
        return new LocalFileCheckpointStorage(tempDir.toString()).storeCheckPoint(checkpoint);
    }

    private void runRestoreAtParallelism(int pNew, String jobId, String savepointPath) throws Exception {
        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
            }

            @Override
            public void cancel() {
            }
        };
        OperatorChain chain = new OperatorChain(Arrays.asList(
                new StreamSourceOperator<>(source),
                new KeyCapturingOperator(),
                new StreamSinkOperator<>(v -> {
                })));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex(VERTEX_ID, "kr-rescale", pNew,
                Collections.singletonList(chain), invokable);
        JobGraph jobGraph = new JobGraph("kr-rescale-test");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId("pipeline-0");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(600000L);
        config.setStorageProperty("path", tempDir.toString());
        // R6 KR-06 closure fix: the job's key-group bound rides the checkpoint
        // config so the restore-side maxParallelism resolution and the wiring
        // provisioning both see the staged 16 (the keyed backend is only created
        // in open() now — nothing else derives M from the operator).
        config.setStateBackend(new MemoryStateBackend(MAX_P));

        GraphModelCheckpointExecutor.executeWithSavepoint(jobGraph, "kr-rescale", config, savepointPath);
    }

    @Test
    public void scaleDownRestoresEveryKeyExactlyOnce() throws Exception {
        String jobId = "kr-scaledown";
        int keys = 24;
        stageCheckpoint(2, buildKeys(keys), jobId, false);

        KeyCapturingOperator.CAPTURED.clear();
        runRestoreAtParallelism(1, jobId, tempDir.toString());

        assertEquals(1, KeyCapturingOperator.CAPTURED.size(),
                "P=1 restore materializes exactly one subtask");
        Set<String> restored = KeyCapturingOperator.CAPTURED.get(0);
        assertEquals(keys, restored.size(),
                "scale-down 2->1 must restore every key exactly once (no loss, no duplication)");
        for (int i = 0; i < keys; i++) {
            assertTrue(restored.contains("kr-key-" + i), "missing key kr-key-" + i);
        }
    }

    @Test
    public void scaleUpRedistributesPureKeyedStateAcrossNewSubtasks() throws Exception {
        String jobId = "kr-scaleup";
        int keys = 24;
        stageCheckpoint(1, buildKeys(keys), jobId, false);

        KeyCapturingOperator.CAPTURED.clear();
        runRestoreAtParallelism(2, jobId, tempDir.toString());

        assertEquals(2, KeyCapturingOperator.CAPTURED.size(),
                "P=2 restore materializes two subtasks");
        Set<String> union = new LinkedHashSet<>();
        for (Set<String> part : KeyCapturingOperator.CAPTURED) {
            union.addAll(part);
        }
        assertEquals(keys, union.size(), "scale-up 1->2 must redistribute every key exactly once");
        // Key-group ownership: every key must land on the subtask that owns its
        // key group under the NEW parallelism (closure audit Major-2).
        for (int subtask = 0; subtask < 2; subtask++) {
            for (String key : KeyCapturingOperator.CAPTURED.get(subtask)) {
                int gid = KeyGroupAssignment.assignToKeyGroup(key, MAX_P);
                int owner = KeyGroupAssignment.assignKeyGroupToSubtask(gid, MAX_P, 2);
                assertEquals(subtask, owner,
                        "key " + key + " restored to subtask " + subtask
                                + " but its key group is owned by subtask " + owner);
            }
        }
        // no key appears in both subtasks
        Set<String> first = KeyCapturingOperator.CAPTURED.get(0);
        Set<String> second = KeyCapturingOperator.CAPTURED.get(1);
        for (String key : first) {
            assertTrue(!second.contains(key), "key " + key + " restored into both subtasks");
        }
    }

    @Test
    public void scaleUpWithProductionShapedAuxiliaryOperatorStateFailsFast() throws Exception {
        String jobId = "kr-scaleup-aux";
        stageCheckpoint(1, buildKeys(8), jobId, true);

        // Scale-up guard, alive on production-shape keys: rejection here comes
        // from the GENERAL scale-up branch (any non-empty operator state has no
        // redistribution wiring); the KR-09 per-key auxiliary prefix matching is
        // pinned by TestScaleDownPerKeyStateGuard on the scale-down branch.
        io.nop.stream.core.exceptions.StreamException e =
                org.junit.jupiter.api.Assertions.assertThrows(
                        io.nop.stream.core.exceptions.StreamException.class,
                        () -> runRestoreAtParallelism(2, jobId, tempDir.toString()));
        assertTrue(String.valueOf(e.getParam("detail")).contains("internal-timers"),
                () -> "scale-up guard must name the auxiliary state: " + e.getParam("detail"));
        assertEquals("nop.err.stream.operator-state-scale-up-unsupported", e.getErrorCode(),
                "the R5 WIN-2/4 scale-up guard (KR-09 prefix-aware) must be the rejection source");
    }
}
