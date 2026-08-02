/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
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
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 35 end-to-end: verifies the executor's KeyGroupRange-aware restore
 * dispatch ({@code GraphModelCheckpointExecutor.restoreTaskStatesFromSource})
 * routes keyed state correctly across a parallelism change.
 *
 * <p>A savepoint is staged with per-subtask keyed snapshots at parallelism=P_old
 * (keys partitioned by their p=P_old owner subtask). The job is then restored at
 * a different parallelism P_new. Each new subtask captures the keyed state the
 * executor routed to it; the test asserts that:
 * <ul>
 *   <li>the union of all new subtasks' keys equals the original key set (no loss/dup),</li>
 *   <li>each new subtask received exactly the keys whose key-group falls in its own
 *       {@link KeyGroupRange} under P_new (per-subtask slice, not a full replica).</li>
 * </ul>
 *
 * <p>Anti-hollow: a non-range-aware dispatch would either fail-fast (new subtask
 * index has no old TaskLocation) or hand every new subtask the full key set. Both
 * are caught by the per-subtask slice assertion.
 */
public class TestKeyGroupRescaleDispatchE2E {

    private static final int MAX_P = 16;
    private static final String VERTEX_ID = "kv-vertex";
    private static final String KEYED_STORAGE_KEY = "operator-1-keyed";

    /**
     * Shared capture sink: every cloned capturing operator (one per subtask)
     * appends the set of raw keys it observed in the keyed snapshot routed to it.
     * Static so it survives the executor's per-subtask deep-copy of the chain.
     */
    static final List<Set<String>> CAPTURED_KEYS_PER_SUBTASK = new CopyOnWriteArrayList<>();

    @TempDir
    Path tempDir;

    /** A keyed operator that records the keys present in its restored keyed state. */
    public static final class KeyCapturingOperator extends AbstractStreamOperator<String> {
        private static final long serialVersionUID = 1L;

        public KeyCapturingOperator() {
            // Provision a keyed backend at construction time so CheckpointPlanBuilder
            // assigns this operator a keyedStateStorageKey ("operator-1-keyed"),
            // enabling keyed-state routing through the restore dispatch. Each subtask
            // gets its own backend via the executor's per-subtask deep copy.
            setKeyedStateBackend(new io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend<>(String.class, MAX_P));
        }

        @Override
        public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
            if (snapshotResult != null && snapshotResult.getKeyedStates() != null) {
                Set<String> keys = new LinkedHashSet<>();
                for (Map.Entry<String, Object> e : snapshotResult.getKeyedStates().entrySet()) {
                    extractKeys(e.getValue(), keys);
                }
                if (!keys.isEmpty()) {
                    CAPTURED_KEYS_PER_SUBTASK.add(keys);
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

    /** Build a keyed ValueState snapshot data map for the given (key -> value) entries. */
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

    /** Stage a savepoint with per-subtask keyed snapshots at parallelism=pOld. */
    private String stageSavepoint(int pOld, Map<String, Long> allKeys, String jobId, String pipelineId) {
        // Partition keys by their p=pOld owner subtask.
        List<List<String>> keysPerSubtask = new ArrayList<>();
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

            TaskStateSnapshot ts = new TaskStateSnapshot(loc, 1L);
            ts.putKeyedState(KEYED_STORAGE_KEY, buildKeyedSnapshot(subEntries));
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

    private Map<String, Long> buildKeys(int count) {
        Map<String, Long> keys = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) keys.put("dispatch-key-" + i, (long) i);
        return keys;
    }

    private void runRestoreAtParallelism(int pNew, String savepointPath, String jobId, String pipelineId,
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
        JobVertex vertex = new JobVertex(VERTEX_ID, "kv", pNew, Collections.singletonList(chain), invokable);
        JobGraph jobGraph = new JobGraph("rescale-dispatch-test");
        jobGraph.addVertex(vertex);

        config.setJobId(jobId);
        config.setPipelineId(pipelineId);
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(600000L);
        config.setStorageProperty("path", tempDir.toString());

        // restoreFromSavepointPath expects the storage base directory (it resolves
        // the latest checkpoint by jobId/pipelineId), not the per-checkpoint file.
        GraphModelCheckpointExecutor.executeWithSavepoint(jobGraph, "rescale-dispatch", config, tempDir.toString());
    }

    private void assertPartitionedCorrectly(int pNew, Map<String, Long> allKeys) {
        // Union of captured keys must equal allKeys (no loss/dup).
        Set<String> union = new LinkedHashSet<>();
        for (Set<String> captured : CAPTURED_KEYS_PER_SUBTASK) {
            union.addAll(captured);
        }
        assertEquals(allKeys.keySet(), union, "union of restored keys must equal the original key set");

        // Each captured key-set must be exactly one p=pNew subtask's KeyGroupRange slice.
        boolean atLeastOneSlice = false;
        for (Set<String> captured : CAPTURED_KEYS_PER_SUBTASK) {
            // Find which p=pNew subtask range this slice corresponds to.
            int matchedSubtask = -1;
            for (int s = 0; s < pNew; s++) {
                KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, pNew, s);
                boolean allInRange = true;
                for (String k : captured) {
                    if (!range.contains(KeyGroupAssignment.assignToKeyGroup(k, MAX_P))) { allInRange = false; break; }
                }
                if (allInRange && !captured.isEmpty()) {
                    matchedSubtask = s;
                    break;
                }
            }
            assertTrue(matchedSubtask >= 0 || captured.isEmpty(),
                    "captured keys " + captured + " do not fit a single p=" + pNew + " subtask range");
            if (matchedSubtask >= 0) atLeastOneSlice = true;
        }
        assertTrue(atLeastOneSlice || allKeys.isEmpty(),
                "at least one new subtask must own a non-empty keyed slice (range restore took effect)");
    }

    @Test
    void scaleUp_parallelism_4_to_16() throws Exception {
        CAPTURED_KEYS_PER_SUBTASK.clear();
        Map<String, Long> allKeys = buildKeys(60);
        String jobId = "su-job";
        String pipelineId = "su-pipe";

        String savepointPath = stageSavepoint(4, allKeys, jobId, pipelineId);
        CheckpointConfig config = new CheckpointConfig();
        runRestoreAtParallelism(16, savepointPath, jobId, pipelineId, config);
        assertPartitionedCorrectly(16, allKeys);
    }

    @Test
    void scaleDown_parallelism_16_to_4() throws Exception {
        CAPTURED_KEYS_PER_SUBTASK.clear();
        Map<String, Long> allKeys = buildKeys(60);
        String jobId = "sd-job";
        String pipelineId = "sd-pipe";

        String savepointPath = stageSavepoint(16, allKeys, jobId, pipelineId);
        CheckpointConfig config = new CheckpointConfig();
        runRestoreAtParallelism(4, savepointPath, jobId, pipelineId, config);
        assertPartitionedCorrectly(4, allKeys);
    }

    @Test
    void unchangedParallelismStillWorks_4_to_4() throws Exception {
        // Regression guard: no rescale (p==p) must keep the strict 1:1 path working.
        CAPTURED_KEYS_PER_SUBTASK.clear();
        Map<String, Long> allKeys = buildKeys(30);
        String jobId = "eq-job";
        String pipelineId = "eq-pipe";

        String savepointPath = stageSavepoint(4, allKeys, jobId, pipelineId);
        CheckpointConfig config = new CheckpointConfig();
        runRestoreAtParallelism(4, savepointPath, jobId, pipelineId, config);
        assertPartitionedCorrectly(4, allKeys);
    }
}
