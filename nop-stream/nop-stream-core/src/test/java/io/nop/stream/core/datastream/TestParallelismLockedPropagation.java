package io.nop.stream.core.datastream;

import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan.EdgePlan;
import io.nop.stream.core.execution.plan.PartitionedPlan.VertexPlan;
import io.nop.stream.core.graph.StreamGraph;
import io.nop.stream.core.graph.StreamGraphGenerator;
import io.nop.stream.core.graph.StreamNode;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobGraphGenerator;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.operators.SimpleStreamOperatorFactory;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.SourceTransformation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@code parallelismLocked} propagation chain
 * {@code Transformation → StreamNode → JobVertex → GraphExecutionPlan}
 * that backs {@link SingleOutputStreamOperator#forceNonParallel()}.
 *
 * <p>Plan {@code 2026-07-26-0804-2-parallel-execution-cep-correctness.md} Phase 1
 * exit criterion: "{@code parallelismLocked} 经 {@code Transformation → StreamNode
 * → JobVertex} 传播（三处新增字段），{@code GraphExecutionPlan.build()} 读取
 * {@code JobVertex.parallelismLocked} 强制并行度=1".
 */
public class TestParallelismLockedPropagation {

    @Test
    void transformationLockPropagatesThroughStreamNodeAndJobVertex() {
        // Source transformation locked to parallel-1 (simulates forceNonParallel()).
        SourceTransformation<String> source = new SourceTransformation<>(
                "Source", new NoopSourceFunction<>(), BasicTypeInfo.of(String.class), 4);
        source.lockParallelismToOne();

        StreamMap<String, String> mapOp = new StreamMap<>(new IdentityMapFn());
        OneInputTransformation<String, String> map = new OneInputTransformation<>(
                source, "Map",
                new SimpleStreamOperatorFactory<>(mapOp, "Map", 4),
                BasicTypeInfo.of(String.class), 4);

        SinkTransformation<String> sink = new SinkTransformation<>(
                map, "Sink", new NoopSinkFn(), null, 4);

        // Phase 1: Transformation → StreamNode
        StreamGraph streamGraph = new StreamGraphGenerator()
                .generate(Collections.singletonList(sink));
        StreamNode sourceNode = streamGraph.getStreamNode(source.getId());
        assertNotNull(sourceNode, "Source StreamNode must exist");
        assertTrue(sourceNode.isParallelismLocked(),
                "StreamNode.parallelismLocked must be true when source Transformation is locked");
        assertEquals(1, sourceNode.getParallelism(),
                "Locked StreamNode must be forced to parallelism = 1");

        // Phase 2: StreamNode → JobVertex
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);
        JobVertex sourceVertex = jobGraph.getVertices().values().stream()
                .filter(v -> v.getName().contains("Source"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Source JobVertex not found"));
        assertTrue(sourceVertex.isParallelismLocked(),
                "JobVertex.parallelismLocked must be true when chain contains a locked StreamNode");
        assertEquals(1, sourceVertex.getParallelism(),
                "Locked JobVertex must be forced to parallelism = 1");
    }

    @Test
    void graphExecutionPlanForcesLockedVertexToOneEvenWithHigherDeploymentPlan() {
        SourceTransformation<String> source = new SourceTransformation<>(
                "Source", new NoopSourceFunction<>(), BasicTypeInfo.of(String.class), 4);
        source.lockParallelismToOne();

        StreamMap<String, String> mapOp = new StreamMap<>(new IdentityMapFn());
        OneInputTransformation<String, String> map = new OneInputTransformation<>(
                source, "Map",
                new SimpleStreamOperatorFactory<>(mapOp, "Map", 4),
                BasicTypeInfo.of(String.class), 4);

        SinkTransformation<String> sink = new SinkTransformation<>(
                map, "Sink", new NoopSinkFn(), null, 4);

        StreamGraph streamGraph = new StreamGraphGenerator()
                .generate(Collections.singletonList(sink));
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);

        // DeploymentPlan attempts to override every vertex parallelism to 4.
        Map<String, VertexPlan> vertexPlans = new LinkedHashMap<>();
        for (String vertexId : jobGraph.getVertices().keySet()) {
            vertexPlans.put(vertexId, new VertexPlan(vertexId, 4, "op-" + vertexId));
        }
        List<EdgePlan> edgePlans = Collections.emptyList();
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                "lock-test", "p-0", vertexPlans, edgePlans, Collections.emptySet(), null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                "lock-test", "p-0", partitionedPlan, "local", "memory", "local", null, null);

        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph, deploymentPlan);

        // The locked source vertex must be forced to parallel-1 even though the
        // DeploymentPlan attempts to override it to 4.
        boolean foundLocked = false;
        for (Map.Entry<String, List<Subtask>> entry : plan.getSubtasks().entrySet()) {
            JobVertex vertex = jobGraph.getVertex(entry.getKey());
            if (vertex != null && vertex.isParallelismLocked()) {
                foundLocked = true;
                assertEquals(1, entry.getValue().size(),
                        "Locked vertex " + entry.getKey() + " must be forced to parallel-1 "
                                + "regardless of DeploymentPlan override");
            }
        }
        assertTrue(foundLocked, "Test setup must include at least one locked vertex");
    }

    private static class NoopSourceFunction<T> implements SourceFunction<T> {
        private static final long serialVersionUID = 1L;
        @Override public void run(SourceContext<T> ctx) {}
        @Override public void cancel() {}
    }

    private static class IdentityMapFn implements io.nop.stream.core.common.functions.MapFunction<String, String> {
        private static final long serialVersionUID = 1L;
        @Override public String map(String value) { return value; }
    }

    private static class NoopSinkFn implements io.nop.stream.core.common.functions.SinkFunction<String> {
        private static final long serialVersionUID = 1L;
        @Override public void consume(String value) {}
    }
}
