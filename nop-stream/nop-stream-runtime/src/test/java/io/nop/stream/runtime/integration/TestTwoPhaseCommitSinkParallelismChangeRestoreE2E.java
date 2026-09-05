/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CONN-01 successor D1 (checkpoint-design.md §8.5.2): end-to-end proof that a
 * 2PC sink vertex cannot restore across a parallelism change. Drives the FULL
 * restore path through {@link GraphModelCheckpointExecutor} — savepoint staged
 * at pOld, restore attempted at pNew ≠ pOld — and asserts the typed rejection
 * {@code ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED} fires through the
 * real {@code restoreTaskStatesFromSource} detection point, for BOTH vertex
 * shapes (non-keyed sink chain and keyed chain holding a 2PC sink).
 *
 * <p>Scenarios:
 * <ul>
 *   <li>{@code nonKeyedScaleUpRestore_failsFast} — p=2 → p=3: closes the generic
 *       {@code ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED} gap (typed mismatch
 *       semantics instead).</li>
 *   <li>{@code nonKeyedScaleDownRestore_failsFast} — p=3 → p=2: closes the
 *       silent-drop gap (retired subtask 2's durable-uncommitted pendingCommits
 *       would never be re-committed — §6.4 invariant violation).</li>
 *   <li>{@code keyedVertexHolding2PcSinkScaleRestore_failsFast} — keyed chain with
 *       2PC sink, p=2 → p=4: the rejection covers the keyed vertex shape too (the
 *       live {@code rescale} boolean is keyed-only; the D1 check deliberately does
 *       NOT gate on it).</li>
 *   <li>{@code sameParallelismRestore_succeeds} — p=2 → p=2: kill/recover
 *       regression guard — the 1:1 restore path stays intact and every subtask's
 *       independent UDF copy is restored.</li>
 * </ul>
 */
public class TestTwoPhaseCommitSinkParallelismChangeRestoreE2E {

    private static final int MAX_P = 16;
    private static final String VERTEX_ID = "d1-2pc-sink-vertex";
    private static final String KEYED_STORAGE_KEY = "operator-1-keyed";

    /**
     * Recording 2PC sink: per-subtask copies record their assigned subtask index
     * ({@code copyForSubtask}) and their {@code restoreFromEpoch} invocation —
     * the observable evidence that the same-parallelism restore drives each
     * subtask's independent copy.
     */
    public static final class RecordingTwoPhaseCommitSink extends TwoPhaseCommitSinkFunction<String> {
        private static final long serialVersionUID = 1L;

        public static final List<Integer> RESTORED_SUBTASKS = new CopyOnWriteArrayList<>();

        private final int assignedSubtaskIndex;

        public RecordingTwoPhaseCommitSink() {
            this(-1);
        }

        private RecordingTwoPhaseCommitSink(int assignedSubtaskIndex) {
            this.assignedSubtaskIndex = assignedSubtaskIndex;
        }

        @Override
        public RecordingTwoPhaseCommitSink copyForSubtask(int subtaskIndex) {
            return new RecordingTwoPhaseCommitSink(subtaskIndex);
        }

        @Override
        public void beginTransaction() {
        }

        @Override
        public void invoke(String value) {
        }

        @Override
        public void preCommit(long checkpointId) {
        }

        @Override
        public void commit(long checkpointId) {
        }

        @Override
        public void rollback() {
        }

        @Override
        public void restoreFromEpoch(long epochId, TaskStateSnapshot state) {
            RESTORED_SUBTASKS.add(assignedSubtaskIndex);
        }
    }

    @TempDir
    Path tempDir;

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
     * Stages a savepoint at parallelism=pOld whose per-subtask snapshots carry the
     * 2PC sink operator state ({@code participant-pending-commits}); when
     * {@code keyed}, keyed state is attached under {@code operator-1-keyed} so the
     * checkpoint plan marks the vertex keyed (mirrors the channel-state rescale
     * fixture shape).
     */
    private void stageSavepoint(int pOld, String jobId, String pipelineId, boolean keyed) {
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        for (int s = 0; s < pOld; s++) {
            TaskLocation loc = new TaskLocation(jobId, pipelineId, VERTEX_ID, s);
            TaskEpochSnapshot ts = new TaskEpochSnapshot(loc, 1L);
            Map<Long, Object> pending = new TreeMap<>();
            pending.put(1L, Collections.emptyList());
            ts.putOperatorState("participant-" + TwoPhaseCommitSinkFunction.PENDING_COMMITS_KEY, pending);
            if (keyed) {
                Map<String, Long> subEntries = new LinkedHashMap<>();
                subEntries.put("d1-key-" + s, (long) s);
                ts.putKeyedState(KEYED_STORAGE_KEY, buildKeyedSnapshot(subEntries));
                ts.setParallelism(pOld);
                ts.setMaxParallelism(MAX_P);
                KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(MAX_P, pOld, s);
                ts.setKeyGroupRangeStart(range.getStartKeyGroup());
                ts.setKeyGroupRangeEnd(range.getEndKeyGroup());
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
        storage.storeCheckPoint(checkpoint);
    }

    private void runRestoreAtParallelism(int pNew, String jobId, String pipelineId,
                                         CheckpointConfig config, boolean keyedChain) throws Exception {
        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;
            @Override public void run(SourceContext<String> ctx) { }
            @Override public void cancel() { }
        };

        StreamSourceOperator<String> srcOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new RecordingTwoPhaseCommitSink());

        OperatorChain chain = keyedChain
                ? new OperatorChain(Arrays.asList(srcOp,
                        new TestChannelStateRescaleE2E.KeyCapturingOperator(), sinkOp))
                : new OperatorChain(Arrays.asList(srcOp, sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex(VERTEX_ID, "d1-2pc-restore", pNew,
                Collections.singletonList(chain), invokable);
        JobGraph jobGraph = new JobGraph("d1-2pc-restore-test");
        jobGraph.addVertex(vertex);

        config.setJobId(jobId);
        config.setPipelineId(pipelineId);
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(600000L);
        config.setStorageProperty("path", tempDir.toString());

        GraphModelCheckpointExecutor.executeWithSavepoint(jobGraph, "d1-2pc-restore", config, tempDir.toString());
    }

    private static void assertTypedRejection(io.nop.stream.core.exceptions.StreamException thrown,
                                             int oldP, int newP) {
        assertEquals(ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED.getErrorCode(),
                thrown.getErrorCode(),
                "2PC sink vertex restore across a parallelism change must fail-fast with the typed "
                        + "parallelism-mismatch error (D1, checkpoint-design.md 8.5.2)");
        assertEquals(VERTEX_ID, thrown.getParam("vertexId"));
        assertEquals(oldP, thrown.getParam("oldParallelism"));
        assertEquals(newP, thrown.getParam("newParallelism"));
    }

    /** p=2 → p=3 non-keyed scale-up: typed rejection (not the generic restore failure). */
    @Test
    void nonKeyedScaleUpRestore_failsFast() throws Exception {
        String jobId = "d1-su-job";
        String pipelineId = "d1-su-pipe";
        stageSavepoint(2, jobId, pipelineId, false);

        io.nop.stream.core.exceptions.StreamException thrown = assertThrows(
                io.nop.stream.core.exceptions.StreamException.class,
                () -> runRestoreAtParallelism(3, jobId, pipelineId, new CheckpointConfig(), false));
        assertTypedRejection(thrown, 2, 3);
    }

    /** p=3 → p=2 non-keyed scale-down: typed rejection (no silent drop of subtask 2's pendingCommits). */
    @Test
    void nonKeyedScaleDownRestore_failsFast() throws Exception {
        String jobId = "d1-sd-job";
        String pipelineId = "d1-sd-pipe";
        stageSavepoint(3, jobId, pipelineId, false);

        io.nop.stream.core.exceptions.StreamException thrown = assertThrows(
                io.nop.stream.core.exceptions.StreamException.class,
                () -> runRestoreAtParallelism(2, jobId, pipelineId, new CheckpointConfig(), false));
        assertTypedRejection(thrown, 3, 2);
    }

    /**
     * Keyed vertex holding a 2PC sink, p=2 → p=4: the D1 rejection covers the keyed
     * shape too — it fires at the parallelism-mismatch detection point regardless of
     * {@code vertexKeyed}, closing the keyed-rescale silent operator-state drop path.
     */
    @Test
    void keyedVertexHolding2PcSinkScaleRestore_failsFast() throws Exception {
        TestChannelStateRescaleE2E.KeyCapturingOperator.CAPTURED.clear();
        String jobId = "d1-kd-job";
        String pipelineId = "d1-kd-pipe";
        stageSavepoint(2, jobId, pipelineId, true);

        io.nop.stream.core.exceptions.StreamException thrown = assertThrows(
                io.nop.stream.core.exceptions.StreamException.class,
                () -> runRestoreAtParallelism(4, jobId, pipelineId, new CheckpointConfig(), true));
        assertTypedRejection(thrown, 2, 4);
    }

    /**
     * Same-parallelism restore (kill/recover shape, p=2 → p=2): the typed rejection
     * must NOT fire — the 1:1 restore path stays intact and every subtask's
     * independent UDF copy participates in the restore.
     */
    @Test
    void sameParallelismRestore_succeeds() throws Exception {
        RecordingTwoPhaseCommitSink.RESTORED_SUBTASKS.clear();
        String jobId = "d1-sp-job";
        String pipelineId = "d1-sp-pipe";
        stageSavepoint(2, jobId, pipelineId, false);

        runRestoreAtParallelism(2, jobId, pipelineId, new CheckpointConfig(), false);

        assertTrue(RecordingTwoPhaseCommitSink.RESTORED_SUBTASKS.contains(0),
                "subtask 0's independent 2PC UDF copy must be restored");
        assertTrue(RecordingTwoPhaseCommitSink.RESTORED_SUBTASKS.contains(1),
                "subtask 1's independent 2PC UDF copy must be restored");
    }
}
