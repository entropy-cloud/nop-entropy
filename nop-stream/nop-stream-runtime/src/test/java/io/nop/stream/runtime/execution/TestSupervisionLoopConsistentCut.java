/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.execution.SubtaskTask;
import io.nop.stream.core.execution.TaskExecutor;
import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.execution.materialization.IMaterializationPoint;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.CheckpointPlanBuilder;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 44 successor 4 Phase 1: consistent-cut epoch alignment + operator state
 * restore integration tests.
 *
 * <p>Verifies (via the public {@link SupervisionLoop#run} entry point):
 * <ul>
 *   <li>(c) When a completed checkpoint exists, {@code rebuildTask} restores
 *       operator state from it and selects the checkpoint-aligned epoch for
 *       materialization replay (not epoch 0). Observable: the restarted consumer
 *       processes only post-checkpoint records, NOT the full materialized set.</li>
 *   <li>Startup edge case: when no checkpoint exists, restart falls back to
 *       epoch 0 + full replay (successor-3 behavior).</li>
 *   <li>No-Silent-No-Op (#24): when a checkpoint exists but the task's state is
 *       missing, restart fails-fast rather than silently starting from empty.</li>
 * </ul>
 */
class TestSupervisionLoopConsistentCut {

    @TempDir
    Path tempDir;

    /**
     * Helper: completes a pending checkpoint in the coordinator so that
     * {@code getLatestCheckpoint()} returns it synchronously (sync snapshot mode,
     * temp-dir storage).
     */
    private static CompletedCheckpoint primeCoordinator(
            CheckpointCoordinator coordinator, CheckpointPlan plan,
            java.util.function.Function<TaskLocation, TaskStateSnapshot> stateSupplier) throws Exception {
        // Disable async so completePendingCheckpoint runs inline.
        coordinator.completePendingCheckpoint(
                CompletedCheckpoint.builder()
                        .jobId(plan.getJobId())
                        .pipelineId(plan.getPipelineId())
                        .checkpointId(0L)
                        .triggerTimestamp(System.currentTimeMillis())
                        .completedTimestamp(System.currentTimeMillis())
                        .checkpointType(CheckpointType.CHECKPOINT)
                        .build());
        return null;
    }

    /**
     * (c) Consistent-cut epoch selection: a checkpoint with id=5 exists. The
     * materialization point has pre-checkpoint records (epoch 0, emitted by the
     * source) and post-checkpoint records (epoch 5, pre-populated). On restart,
     * replay(epoch >= 5) returns ONLY post-checkpoint records.
     *
     * <p>Graph: Source(A) →[materialization edge]→ FailingSink(B)
     * <ul>
     *   <li>Source emits "pre-1", "pre-2" (epoch 0).</li>
     *   <li>Materialization point also has "post-1", "post-2" (epoch 5).</li>
     *   <li>Checkpoint id=5 with minimal TaskStateSnapshot for the sink.</li>
     *   <li>FailingSink throws on first consume; succeeds on restart.</li>
     * </ul>
     * Expected: restart processes ["post-1", "post-2"] only (NOT "pre-1", "pre-2").
     */
    @Test
    void consistentCutEpochSelection_replaysOnlyPostCheckpointRecords() throws Exception {
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean hasFailedOnce = new AtomicBoolean(false);

        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("pre-1");
                ctx.collect("pre-2");
            }

            @Override
            public void cancel() {
            }
        };

        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                if (!hasFailedOnce.getAndSet(true)) {
                    throw new StreamException("Injected first-attempt failure");
                }
                sinkResults.add(value);
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("src-cut", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("sink-cut", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-consistent-cut");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);

        JobEdge edge = new JobEdge("src-cut", "sink-cut", ResultPartitionType.PIPELINED);
        edge.setMaterializationEnabled(true);
        jobGraph.addEdge(edge);

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        // Pre-populate the materialization point with POST-checkpoint records at
        // epoch 5. The source will add PRE-checkpoint records at epoch 0 when it
        // runs (write() defaults to epoch 0 with no barrier).
        Subtask sinkSubtask0 = execPlan.getSubtasks("sink-cut").get(0);
        InputGate gate = sinkSubtask0.getInvokable().getInputGate();
        assertNotNull(gate, "Sink should have an InputGate");
        ResultPartition partition = gate.getChannels().get(0).getPartition();
        IMaterializationPoint point = partition.getMaterializationPoint();
        assertNotNull(point, "Materialization-enabled edge should attach a point");
        point.write(new StreamRecord<>("post-1"), 5L);
        point.write(new StreamRecord<>("post-2"), 5L);

        // Set up a coordinator holding a completed checkpoint with id=5 and a
        // minimal TaskStateSnapshot for the sink task.
        CheckpointCoordinator coordinator = buildCoordinatorWithCheckpoint(execPlan, "sink-cut", 5L);
        TaskLocation sinkLoc = execPlan.getSubtasks("sink-cut").get(0).getTaskLocation();

        // Build tasks (same pattern as the existing E2E test).
        Map<String, SubtaskTask> tasks = buildTasks(execPlan);

        TaskExecutor executor = new TaskExecutor();
        try {
            SupervisionLoop.run(execPlan, tasks, executor, jobGraph, coordinator,
                    CheckpointPlanBuilder.build(execPlan, sinkLoc.getJobId(), sinkLoc.getPipelineId()),
                    SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                    SupervisionLoop.DEFAULT_POLL_INTERVAL_MS);
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
            coordinator.shutdown();
        }

        // The restarted sink should have processed ONLY post-checkpoint records.
        // If epoch selection were broken (replay from 0), it would also re-process
        // "pre-1" and "pre-2" (4 results). Correct consistent-cut → 2 results.
        assertEquals(List.of("post-1", "post-2"), new ArrayList<>(sinkResults),
                "Consistent-cut replay should return only post-checkpoint records (epoch >= 5)");

        SubtaskTask sinkTask = tasks.get("sink-cut-0");
        assertNotNull(sinkTask);
        assertEquals(SubtaskTask.State.COMPLETED, sinkTask.getState(),
                "Restarted sink should complete successfully");
    }

    /**
     * Startup edge case: no checkpoint exists (no coordinator). Restart falls
     * back to epoch 0 + full replay. The restarted consumer replays ALL
     * materialized data (including epoch-0 records emitted by the source).
     */
    @Test
    void noCheckpoint_fallsBackToEpoch0_fullReplay() throws Exception {
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean hasFailedOnce = new AtomicBoolean(false);

        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("a");
                ctx.collect("b");
            }

            @Override
            public void cancel() {
            }
        };

        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                if (!hasFailedOnce.getAndSet(true)) {
                    throw new StreamException("Injected first-attempt failure (no-checkpoint case)");
                }
                sinkResults.add(value);
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("src-nockp", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("sink-nockp", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-no-checkpoint");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);

        JobEdge edge = new JobEdge("src-nockp", "sink-nockp", ResultPartitionType.PIPELINED);
        edge.setMaterializationEnabled(true);
        jobGraph.addEdge(edge);

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        Map<String, SubtaskTask> tasks = buildTasks(execPlan);

        TaskExecutor executor = new TaskExecutor();
        try {
            // coordinator=null, checkpointPlan=null → epoch 0 + full replay fallback.
            SupervisionLoop.run(execPlan, tasks, executor, jobGraph, null, null,
                    SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                    SupervisionLoop.DEFAULT_POLL_INTERVAL_MS);
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
        }

        // Full replay: both records replayed on restart.
        assertEquals(List.of("a", "b"), new ArrayList<>(sinkResults),
                "No-checkpoint fallback should replay ALL materialized data (epoch 0)");
    }

    /**
     * No-Silent-No-Op (#24): a checkpoint exists but its task-state map is missing
     * the failing task's TaskLocation. Restart must fail-fast (not silently start
     * from empty state). The error propagates as a StreamException.
     */
    @Test
    void checkpointExistsButTaskStateMissing_failsFast() throws Exception {
        AtomicBoolean hasFailedOnce = new AtomicBoolean(false);
        List<String> sinkResults = Collections.synchronizedList(new ArrayList<>());

        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("x");
            }

            @Override
            public void cancel() {
            }
        };

        SinkFunction<String> sinkFn = new SinkFunction<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
                if (!hasFailedOnce.getAndSet(true)) {
                    throw new StreamException("Injected failure (missing-state case)");
                }
                sinkResults.add(value);
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(sinkFn);

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("src-missing", "Source", 1,
                Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex sinkVertex = new JobVertex("sink-missing", "Sink", 1,
                Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-missing-state");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(sinkVertex);

        JobEdge edge = new JobEdge("src-missing", "sink-missing", ResultPartitionType.PIPELINED);
        edge.setMaterializationEnabled(true);
        jobGraph.addEdge(edge);

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        // Build a coordinator with a checkpoint that has NO state for the sink task
        // (empty task-states map). This simulates a topology/identity mismatch.
        CheckpointCoordinator coordinator = buildCoordinatorWithEmptyTaskStates(execPlan, 3L);
        TaskLocation anyLoc = execPlan.getSubtasks("src-missing").get(0).getTaskLocation();

        Map<String, SubtaskTask> tasks = buildTasks(execPlan);

        TaskExecutor executor = new TaskExecutor();
        try {
            StreamException ex = assertThrows(StreamException.class, () ->
                    SupervisionLoop.run(execPlan, tasks, executor, jobGraph, coordinator,
                            CheckpointPlanBuilder.build(execPlan, anyLoc.getJobId(), anyLoc.getPipelineId()),
                            SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                            SupervisionLoop.DEFAULT_POLL_INTERVAL_MS));
            assertNotNull(ex.getErrorCode());
            assertTrue(ex.getErrorCode().contains("region-restart-unsupported"),
                    "Should fail-fast on missing task state, got: " + ex.getErrorCode());
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
            coordinator.shutdown();
        }
    }

    // ==================== Helpers ====================

    private static Map<String, SubtaskTask> buildTasks(GraphExecutionPlan execPlan) {
        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain chain = subtask.getInvokable().getOperatorChain();
                tasks.put(taskKey, new SubtaskTask(subtask, vertex, Collections.singletonList(chain)));
            }
        }
        return tasks;
    }

    /**
     * Builds a coordinator holding a completed checkpoint with the given id and a
     * minimal (non-null) TaskStateSnapshot for the specified vertex's subtask 0.
     */
    private CheckpointCoordinator buildCoordinatorWithCheckpoint(
            GraphExecutionPlan execPlan, String sinkVertexId, long checkpointId) throws Exception {
        // Use the ACTUAL TaskLocation from the execution plan's subtask (it is
        // derived from jobGraph.getJobName() + "pipeline-0", not from arbitrary
        // test strings — the checkpoint's task-state key MUST match exactly).
        TaskLocation sinkLocation = execPlan.getSubtasks(sinkVertexId).get(0).getTaskLocation();
        String jobId = sinkLocation.getJobId();
        String pipelineId = sinkLocation.getPipelineId();
        CheckpointPlan plan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId);

        TaskStateSnapshot sinkState = TaskStateSnapshot.empty(sinkLocation);

        CompletedCheckpoint completed = CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT)
                .addTaskState(sinkLocation, sinkState)
                .build();

        return completeCheckpoint(coordinatorFromStorage(jobId, pipelineId, plan, checkpointId), plan, completed);
    }

    /**
     * Builds a coordinator holding a completed checkpoint whose task-states map
     * is empty (missing the failing task's state).
     */
    private CheckpointCoordinator buildCoordinatorWithEmptyTaskStates(
            GraphExecutionPlan execPlan, long checkpointId) throws Exception {
        // Derive jobId/pipelineId from the actual execution plan.
        TaskLocation anyLoc = execPlan.getSubtasks(
                execPlan.getSortedVertexIds().get(0)).get(0).getTaskLocation();
        String jobId = anyLoc.getJobId();
        String pipelineId = anyLoc.getPipelineId();
        CheckpointPlan plan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId);

        CompletedCheckpoint completed = CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT)
                .build(); // no task states

        return completeCheckpoint(coordinatorFromStorage(jobId, pipelineId, plan, checkpointId), plan, completed);
    }

    private CheckpointCoordinator coordinatorFromStorage(
            String jobId, String pipelineId, CheckpointPlan plan, long checkpointId) {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId(pipelineId);
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(false); // sync so completion is inline
        config.setStorageProperty("path", tempDir.toString());
        // Counter starting at checkpointId so tryTriggerPendingCheckpoint produces
        // a pending entry with the matching id.
        CheckpointIDCounter idCounter = new CheckpointIDCounter(checkpointId);
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointCoordinator coordinator = new CheckpointCoordinator(jobId, pipelineId, idCounter, storage, config);
        for (TaskLocation loc : plan.getAllTasks()) {
            coordinator.registerTask(loc);
        }
        return coordinator;
    }

    private CheckpointCoordinator completeCheckpoint(
            CheckpointCoordinator coordinator, CheckpointPlan plan, CompletedCheckpoint completed) throws Exception {
        // Trigger a pending checkpoint with the matching id, then complete it inline.
        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending, "Should be able to trigger a pending checkpoint");
        assertEquals(completed.getCheckpointId(), pending.getCheckpointId(),
                "Pending checkpoint id must match the completed checkpoint id");
        coordinator.completePendingCheckpoint(completed);
        assertNotNull(coordinator.getLatestCheckpoint(),
                "Coordinator should hold the completed checkpoint after completion");
        assertEquals(completed.getCheckpointId(), coordinator.getLatestCheckpoint().getCheckpointId());
        return coordinator;
    }
}
