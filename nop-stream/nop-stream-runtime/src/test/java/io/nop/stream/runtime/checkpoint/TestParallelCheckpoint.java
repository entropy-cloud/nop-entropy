package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.jobgraph.*;
import io.nop.stream.core.operators.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestParallelCheckpoint {

    private static class SimpleSource implements SourceFunction<String>, Serializable {
        private static final long serialVersionUID = 1L;
        private final String[] items;

        SimpleSource(String... items) {
            this.items = items;
        }

        @Override
        public void run(SourceContext<String> ctx) {
            for (String item : items) {
                ctx.collect(item);
            }
        }

        @Override
        public void cancel() {}
    }

    @TempDir
    Path tempDir;

    @Test
    void testParallelCheckpointPlan_includesAllSubtasks() {
        String jobId = "p2-job";
        String pipelineId = "p2-pipe";

        SimpleSource source = new SimpleSource("a");
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(v -> {});
        OperatorChain chain = new OperatorChain(Arrays.asList(sourceOp, sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex("v1", "vertex-1", 2,
                Collections.singletonList(chain), invokable);

        JobGraph jobGraph = new JobGraph(jobId);
        jobGraph.addVertex(vertex);

        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph);

        assertEquals(2, execPlan.getSubtasks("v1").size());

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId(pipelineId);

        CheckpointPlan plan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, config);

        assertEquals(2, plan.getAllTasks().size());
        assertEquals(2, plan.getSourceTasks().size());

        for (TaskLocation loc : plan.getAllTasks()) {
            assertEquals("v1", loc.getVertexId());
            assertTrue(loc.getTaskIndex() == 0 || loc.getTaskIndex() == 1);
            assertEquals(jobId, loc.getJobId());
            assertEquals(pipelineId, loc.getPipelineId());
        }
    }

    @Test
    void testParallelCheckpointEndToEnd() throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        SimpleSource source = new SimpleSource("item-1", "item-2", "item-3");

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(results::add);

        OperatorChain chain = new OperatorChain(Arrays.asList(sourceOp, sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex("v1", "vertex-1", 1,
                Collections.singletonList(chain), invokable);

        JobGraph jobGraph = new JobGraph("e2e-test");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId("e2e-test");
        config.setPipelineId("p-0");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000L);
        config.setStorageProperty("path", tempDir.toString());

        var result = GraphModelCheckpointExecutor.executeWithCheckpoint(
                jobGraph, "E2E Checkpoint Test", config);
        assertNotNull(result);

        assertEquals(3, results.size());
        assertTrue(results.contains("item-1"));
        assertTrue(results.contains("item-2"));
        assertTrue(results.contains("item-3"));
    }

    @Test
    void testCheckpointRestoresAllSubtaskStates() throws Exception {
        String jobId = "restore-job";
        String pipelineId = "p-0";

        SimpleSource source = new SimpleSource("data");
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(v -> {});
        OperatorChain chain = new OperatorChain(Arrays.asList(sourceOp, sinkOp));
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex("v1", "vertex-1", 1,
                Collections.singletonList(chain), invokable);

        JobGraph jobGraph = new JobGraph(jobId);
        jobGraph.addVertex(vertex);

        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId(pipelineId);
        config.setStorageProperty("path", tempDir.toString());

        CheckpointPlan plan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, config);

        TaskLocation loc0 = new TaskLocation(jobId, pipelineId, "v1", 0);
        TaskStateSnapshot state0 = TaskStateSnapshot.builder(loc0)
                .checkpointId(1L)
                .putOperatorState("operator-0", "state-subtask-0")
                .build();

        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        taskStates.put(loc0, state0);

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(1L)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT)
                .taskStates(taskStates)
                .build();

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        String path = storage.storeCheckPoint(checkpoint);
        assertNotNull(path);

        CompletedCheckpoint restored = storage.getLatestCheckpoint(jobId, pipelineId);
        assertNotNull(restored);
        assertEquals(1, restored.getTaskStates().size());

        TaskStateSnapshot restoredState = restored.getTaskState(loc0);
        assertNotNull(restoredState);
        assertEquals("state-subtask-0", restoredState.getOperatorState("operator-0"));
    }

    @Test
    void testParallelCheckpointPlan_twoVertices_parallelism2() {
        String jobId = "2v-p2";
        String pipelineId = "p-0";

        SimpleSource source = new SimpleSource("a");
        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(v -> {});

        OperatorChain srcChain = new OperatorChain(Collections.singletonList(sourceOp));
        OperatorChain snkChain = new OperatorChain(Collections.singletonList(sinkOp));

        StreamTaskInvokable srcInvokable = new StreamTaskInvokable(srcChain);
        StreamTaskInvokable snkInvokable = new StreamTaskInvokable(snkChain);

        JobVertex srcVertex = new JobVertex("src", "source", 2,
                Collections.singletonList(srcChain), srcInvokable);
        JobVertex snkVertex = new JobVertex("snk", "sink", 2,
                Collections.singletonList(snkChain), snkInvokable);

        JobGraph jobGraph = new JobGraph(jobId);
        jobGraph.addVertex(srcVertex);
        jobGraph.addVertex(snkVertex);
        jobGraph.addEdge(new JobEdge("src", "snk", ResultPartitionType.PIPELINED));

        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph);

        assertEquals(2, execPlan.getSubtasks("src").size());
        assertEquals(2, execPlan.getSubtasks("snk").size());

        CheckpointPlan plan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId);

        assertEquals(4, plan.getAllTasks().size());
        assertEquals(2, plan.getSourceTasks().size());

        long srcLocations = plan.getAllTasks().stream()
                .filter(loc -> loc.getVertexId().equals("src")).count();
        long snkLocations = plan.getAllTasks().stream()
                .filter(loc -> loc.getVertexId().equals("snk")).count();
        assertEquals(2, srcLocations);
        assertEquals(2, snkLocations);

        for (TaskLocation loc : plan.getAllTasks()) {
            assertNotNull(plan.getStateMappings(loc));
        }
    }
}
