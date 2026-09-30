/*
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
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.execution.task.Subtask;
import io.nop.stream.core.execution.task.SubtaskTask;
import io.nop.stream.core.execution.task.TaskExecutor;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.CheckpointPlanBuilder;
import io.nop.stream.runtime.checkpoint.PendingCheckpoint;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R5-REG-01 regression (plan 368 Phase 5): a region restart combining
 * <ul>
 *   <li>an internal NON-materialized edge (no materialization point → no
 *       store-backed replay),</li>
 *   <li>a producer task that already COMPLETED (the restart loop skips it —
 *       no re-emission source), and</li>
 *   <li>a consumer that must be rebuilt from a completed checkpoint (its
 *       operator state rolls back to the checkpoint epoch)</li>
 * </ul>
 * is an unrecoverable data-loss window: the rebuilt consumer would read only
 * the residual queue content + EOS, silently truncating the record window
 * between the checkpoint epoch and the failure. The restart must fail loud
 * with {@code ERR_STREAM_RESTART_UNREPLAYABLE_INTERNAL_EDGE} naming the edge,
 * the producer/consumer subtasks and the checkpoint epoch.
 *
 * <p>Without a checkpoint the same topology keeps its documented behavior
 * (residual + EOS delivery, inherent in-flight loss) — pinned by
 * {@code TestRegionRestartInternalEdgeE2E.internalEdge_restartAfterProducerCompleted_skipsCompletedAndDrainsResidual},
 * which runs with coordinator=null and must stay green.
 */
class TestRegionRestartUnreplayableInternalEdge {

    @TempDir
    Path tempDir;

    /**
     * Graph: Source → [internal edge, no mat] → FailingMap → [mat edge] → Sink.
     * Regions: {source, map} + {sink}. The map fails on its first record AFTER
     * the source fully completed (latch), so the restart would skip the
     * COMPLETED source (F1) while rebuilding the map with state rolled back to
     * the completed checkpoint — exactly the REG-01 combination. Pre-fix, the
     * restart "succeeded" while silently losing the post-checkpoint window.
     */
    @Test
    @Timeout(60)
    void internalEdge_completedProducer_consumerRebuild_withCheckpoint_failsLoud() throws Exception {
        CountDownLatch sourceDone = new CountDownLatch(1);
        AtomicBoolean mapFailedOnce = new AtomicBoolean(false);

        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("r1");
                ctx.collect("r2");
                ctx.collect("r3");
                sourceDone.countDown();
            }

            @Override
            public void cancel() {
            }
        };

        MapFunction<String, String> mapFn = v -> {
            sourceDone.await(5, TimeUnit.SECONDS);
            if (!mapFailedOnce.getAndSet(true)) {
                throw new StreamException("Injected first-map failure (REG-01)");
            }
            return v;
        };

        SinkFunction<String> sinkFn = v -> {
        };

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(new OperatorChain(
                Collections.singletonList(new StreamSourceOperator<>(sourceFn))));
        StreamTaskInvokable mapInvokable = new StreamTaskInvokable(new OperatorChain(
                Collections.singletonList(new StreamMap<>(mapFn))));
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(new OperatorChain(
                Collections.singletonList(new StreamSinkOperator<>(sinkFn))));

        JobVertex sourceVertex = new JobVertex("reg01-src", "Source", 1,
                Collections.singletonList(sourceInvokable.getOperatorChain()), sourceInvokable);
        JobVertex mapVertex = new JobVertex("reg01-map", "Map", 1,
                Collections.singletonList(mapInvokable.getOperatorChain()), mapInvokable);
        JobVertex sinkVertex = new JobVertex("reg01-sink", "Sink", 1,
                Collections.singletonList(sinkInvokable.getOperatorChain()), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-reg01-internal-edge");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(mapVertex);
        jobGraph.addVertex(sinkVertex);
        // Internal edge: NOT materialization-enabled → same-region internal edge.
        jobGraph.addEdge(new JobEdge("reg01-src", "reg01-map", ResultPartitionType.PIPELINED));
        JobEdge matEdge = new JobEdge("reg01-map", "reg01-sink", ResultPartitionType.PIPELINED);
        matEdge.setMaterializationEnabled(true);
        jobGraph.addEdge(matEdge);

        assertEquals(2, jobGraph.decomposeRegions().getRegionCount(),
                "only the materialization edge cuts regions; source→map stays internal");

        BufferPool bufferPool = new BufferPool(64);
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, null, false, 0L, bufferPool);

        Map<String, SubtaskTask> tasks = new LinkedHashMap<>();
        for (String vertexId : execPlan.getSortedVertexIds()) {
            JobVertex vertex = execPlan.getExecutionVertices().get(vertexId);
            for (Subtask subtask : execPlan.getSubtasks(vertexId)) {
                String taskKey = vertexId + "-" + subtask.getTaskIndex();
                OperatorChain chain = subtask.getInvokable().getOperatorChain();
                tasks.put(taskKey, new SubtaskTask(subtask, vertex, Collections.singletonList(chain)));
            }
        }

        // A completed checkpoint exists (epoch 5) with state for every task — the
        // rebuilt map's operator state would roll back to it while no re-emission
        // source covers the post-checkpoint window.
        CheckpointCoordinator coordinator = buildCoordinatorWithCheckpoint(execPlan, 5L);
        TaskLocation anyLoc = execPlan.getSubtasks(execPlan.getSortedVertexIds().get(0)).get(0).getTaskLocation();
        CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(
                execPlan, anyLoc.getJobId(), anyLoc.getPipelineId());

        TaskExecutor executor = new TaskExecutor();
        try {
            StreamException ex = assertThrows(StreamException.class,
                    () -> SupervisionLoop.run(execPlan, tasks, executor, jobGraph, coordinator,
                            checkpointPlan,
                            SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION,
                            SupervisionLoop.DEFAULT_POLL_INTERVAL_MS),
                    "the no-mat x COMPLETED-producer x checkpoint restart must fail loud "
                            + "instead of silently truncating output");

            assertNotNull(ex.getErrorCode());
            assertEquals("nop.err.stream.restart-unreplayable-internal-edge", ex.getErrorCode(),
                    "must fail with the dedicated REG-01 error code, got: " + ex.getErrorCode());

            // Error must be locally attributable: consumer subtask, producer vertex,
            // checkpoint epoch, edge facts.
            Object detail = ex.getParam("detail");
            assertNotNull(detail, "error must carry the detail context");
            String detailStr = detail.toString();
            assertTrue(detailStr.contains("reg01-src"),
                    "producer vertex must be named in the error detail: " + detailStr);
            assertTrue(detailStr.contains("materialized=false") && detailStr.contains("partitionFinished=true"),
                    "edge facts must be in the error detail: " + detailStr);
            assertEquals("reg01-map", ex.getParam("vertexId"),
                    "the rebuilt consumer vertex must be named");
            assertEquals(0, ((Number) ex.getParam("taskIndex")).intValue(),
                    "the rebuilt consumer subtask index must be named");
            assertEquals(5L, ((Number) ex.getParam("checkpointId")).longValue(),
                    "the checkpoint epoch must be named");
        } finally {
            executor.shutdownNow();
            execPlan.closeBufferPool();
            coordinator.shutdown();
        }
    }

    // ==================== Helpers (mirroring TestSupervisionLoopConsistentCut) ====================

    /**
     * Builds a coordinator holding a completed checkpoint with the given id and a
     * minimal TaskStateSnapshot for every task of the plan.
     */
    private CheckpointCoordinator buildCoordinatorWithCheckpoint(
            GraphExecutionPlan execPlan, long checkpointId) throws Exception {
        TaskLocation anyLoc = execPlan.getSubtasks(
                execPlan.getSortedVertexIds().get(0)).get(0).getTaskLocation();
        String jobId = anyLoc.getJobId();
        String pipelineId = anyLoc.getPipelineId();
        CheckpointPlan plan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId);

        CompletedCheckpoint.Builder completedBuilder = CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT);
        for (TaskLocation loc : plan.getAllTasks()) {
            completedBuilder.addTaskState(loc, TaskStateSnapshot.empty(loc));
        }
        CompletedCheckpoint completed = completedBuilder.build();

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId(pipelineId);
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(false);
        config.setStorageProperty("path", tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter(checkpointId);
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointCoordinator coordinator = new CheckpointCoordinator(jobId, pipelineId, idCounter, storage, config);
        for (TaskLocation loc : plan.getAllTasks()) {
            coordinator.registerTask(loc);
        }

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending, "Should be able to trigger a pending checkpoint");
        assertEquals(checkpointId, pending.getCheckpointId(),
                "Pending checkpoint id must match the completed checkpoint id");
        coordinator.completePendingCheckpoint(completed);
        assertNotNull(coordinator.getLatestCheckpoint(),
                "Coordinator should hold the completed checkpoint after completion");
        return coordinator;
    }
}
