package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestE2EMultiVertexCheckpoint {

    @TempDir
    Path tempDir;

    private JobGraph buildSourceMapSinkGraph() {
        SourceFunction<Integer> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) {
                for (int i = 1; i <= 5; i++) {
                    ctx.collect(i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamMap<Integer, Integer> mapOp = new StreamMap<>(x -> x * 10);
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
            }
        });

        OperatorChain sourceChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain mapChain = new OperatorChain(Collections.singletonList(mapOp));
        OperatorChain sinkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable sourceInvokable = new StreamTaskInvokable(sourceChain);
        StreamTaskInvokable mapInvokable = new StreamTaskInvokable(mapChain);
        StreamTaskInvokable sinkInvokable = new StreamTaskInvokable(sinkChain);

        JobVertex sourceVertex = new JobVertex("source-1", "Source", 1, Collections.singletonList(sourceChain), sourceInvokable);
        JobVertex mapVertex = new JobVertex("map-2", "Map", 1, Collections.singletonList(mapChain), mapInvokable);
        JobVertex sinkVertex = new JobVertex("sink-3", "Sink", 1, Collections.singletonList(sinkChain), sinkInvokable);

        JobGraph jobGraph = new JobGraph("test-multi-vertex");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(mapVertex);
        jobGraph.addVertex(sinkVertex);
        jobGraph.addEdge(new JobEdge("source-1", "map-2", ResultPartitionType.PIPELINED));
        jobGraph.addEdge(new JobEdge("map-2", "sink-3", ResultPartitionType.PIPELINED));

        return jobGraph;
    }

    @Test
    void testCheckpointPlanFromThreeVertexGraph() {
        JobGraph jobGraph = buildSourceMapSinkGraph();
        GraphExecutionPlan executionPlan = GraphExecutionPlan.build(jobGraph);

        CheckpointPlan plan = CheckpointPlanBuilder.build(executionPlan, "job-1", "pipeline-1");

        assertEquals(3, plan.getAllTasks().size());
        assertEquals(1, plan.getSourceTasks().size());
        assertEquals("source-1", plan.getSourceTasks().get(0).getVertexId());
    }

    @Test
    void testExecuteWithCheckpointStoresState() throws Exception {
        String jobId = "test-e2e-single";
        String pipelineId = "1";

        List<Integer> sinkResults = Collections.synchronizedList(new ArrayList<>());

        SourceFunction<Integer> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<Integer> ctx) {
                for (int i = 1; i <= 5; i++) {
                    ctx.collect(i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamMap<Integer, Integer> mapOp = new StreamMap<>(x -> x * 10);
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(Integer value) {
                sinkResults.add(value);
            }
        });

        OperatorChain chain = new OperatorChain(Arrays.asList(sourceOp, mapOp, sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex("v1", "Chain", 1, Collections.singletonList(chain), invokable);

        JobGraph jobGraph = new JobGraph("test-single-chain");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId(pipelineId);
        config.setStorageProperty("path", tempDir.toString());

        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "test-single-chain", config);

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CompletedCheckpoint checkpoint = storage.getLatestCheckpoint(jobId, pipelineId);

        assertNotNull(checkpoint, "A completed checkpoint should exist after execution");
        assertEquals(jobId, checkpoint.getJobId());

        storage.deleteAllCheckpoints(jobId);
    }

    @Test
    void testManualCheckpointWithCoordinator() throws Exception {
        String jobId = "manual-e2e";
        String pipelineId = "1";

        JobGraph jobGraph = buildSourceMapSinkGraph();
        GraphExecutionPlan executionPlan = GraphExecutionPlan.build(jobGraph);

        CheckpointPlan plan = CheckpointPlanBuilder.build(executionPlan, jobId, pipelineId);

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        CheckpointIDCounter idCounter = new CheckpointIDCounter();
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId(pipelineId);

        CheckpointCoordinator coordinator = new CheckpointCoordinator(jobId, pipelineId, idCounter, storage, config);

        for (TaskLocation loc : plan.getAllTasks()) {
            coordinator.registerTask(loc);
        }

        PendingCheckpoint pending = coordinator.tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT);
        assertNotNull(pending);

        for (TaskLocation loc : plan.getAllTasks()) {
            TaskStateSnapshot taskState = TaskStateSnapshot.builder(loc)
                        .checkpointId(pending.getCheckpointId())
                        .putOperatorState("operator-0", "state-for-" + loc.getVertexId())
                        .build();
            coordinator.acknowledgeTask(loc, pending.getCheckpointId(), taskState);
        }

        CompletedCheckpoint completed = pending.getCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(completed);

        for (TaskLocation loc : plan.getAllTasks()) {
            TaskStateSnapshot taskState = completed.getTaskState(loc);
            assertNotNull(taskState, "Should have state for vertex " + loc.getVertexId());
            Object state = taskState.getOperatorState("operator-0");
            assertNotNull(state, "Should have operator state for vertex " + loc.getVertexId());
            assertTrue(String.valueOf(state).contains(loc.getVertexId()),
                    "State for " + loc.getVertexId() + " should contain its vertexId");
        }

        coordinator.shutdown();
        storage.deleteAllCheckpoints(jobId);
    }
}
