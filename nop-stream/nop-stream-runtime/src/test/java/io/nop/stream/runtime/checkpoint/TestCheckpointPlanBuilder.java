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
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

class TestCheckpointPlanBuilder {

    private JobGraph buildThreeVertexJobGraph() {
        SourceFunction<String> sourceFn = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(sourceFn);
        StreamMap<String, String> mapOp = new StreamMap<>(x -> x);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(new SinkFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void consume(String value) {
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

        JobGraph jobGraph = new JobGraph("test-3-vertex");
        jobGraph.addVertex(sourceVertex);
        jobGraph.addVertex(mapVertex);
        jobGraph.addVertex(sinkVertex);
        jobGraph.addEdge(new JobEdge("source-1", "map-2", ResultPartitionType.PIPELINED));
        jobGraph.addEdge(new JobEdge("map-2", "sink-3", ResultPartitionType.PIPELINED));

        return jobGraph;
    }

    @Test
    void testBuildThreeVertexPlan() {
        JobGraph jobGraph = buildThreeVertexJobGraph();
        GraphExecutionPlan executionPlan = GraphExecutionPlan.build(jobGraph);

        CheckpointPlan plan = CheckpointPlanBuilder.build(executionPlan, "job-1", "pipeline-1");

        assertEquals("job-1", plan.getJobId());
        assertEquals("pipeline-1", plan.getPipelineId());
        assertEquals(3, plan.getAllTasks().size());
        assertEquals(1, plan.getSourceTasks().size());

        TaskLocation sourceLoc = plan.getSourceTasks().get(0);
        assertEquals("source-1", sourceLoc.getVertexId());
        assertEquals("job-1", sourceLoc.getJobId());

        Set<String> vertexIds = new HashSet<>();
        for (TaskLocation loc : plan.getAllTasks()) {
            vertexIds.add(loc.getVertexId());
        }
        assertTrue(vertexIds.contains("source-1"));
        assertTrue(vertexIds.contains("map-2"));
        assertTrue(vertexIds.contains("sink-3"));

        assertEquals(3, plan.getStateMappings().size());
        for (TaskLocation loc : plan.getAllTasks()) {
            List<OperatorStateMapping> mappings = plan.getStateMappings(loc);
            assertNotNull(mappings);
            assertEquals(1, mappings.size());
            assertEquals(0, mappings.get(0).getOperatorIndex());
        }
    }

    @Test
    void testNullExecutionPlanThrows() {
        assertThrows(StreamException.class, () ->
                CheckpointPlanBuilder.build(null, "job-1", "pipeline-1"));
    }

    @Test
    void testNullJobIdThrows() {
        JobGraph jobGraph = buildThreeVertexJobGraph();
        GraphExecutionPlan executionPlan = GraphExecutionPlan.build(jobGraph);
        assertThrows(StreamException.class, () ->
                CheckpointPlanBuilder.build(executionPlan, null, "pipeline-1"));
    }

    @Test
    void testEmptyJobIdThrows() {
        JobGraph jobGraph = buildThreeVertexJobGraph();
        GraphExecutionPlan executionPlan = GraphExecutionPlan.build(jobGraph);
        assertThrows(StreamException.class, () ->
                CheckpointPlanBuilder.build(executionPlan, "", "pipeline-1"));
    }

    @Test
    void testNullPipelineIdThrows() {
        JobGraph jobGraph = buildThreeVertexJobGraph();
        GraphExecutionPlan executionPlan = GraphExecutionPlan.build(jobGraph);
        assertThrows(StreamException.class, () ->
                CheckpointPlanBuilder.build(executionPlan, "job-1", null));
    }

    @Test
    void testEmptyVerticesReturnsEmptyPlan() {
        JobGraph emptyGraph = new JobGraph("empty");
        GraphExecutionPlan executionPlan = GraphExecutionPlan.build(emptyGraph);

        CheckpointPlan plan = CheckpointPlanBuilder.build(executionPlan, "job-1", "pipeline-1");

        assertEquals("job-1", plan.getJobId());
        assertTrue(plan.getAllTasks().isEmpty());
        assertTrue(plan.getSourceTasks().isEmpty());
        assertTrue(plan.getStateMappings().isEmpty());
    }
}
