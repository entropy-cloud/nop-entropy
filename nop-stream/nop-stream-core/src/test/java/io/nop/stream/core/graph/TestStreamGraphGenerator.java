/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.graph;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.task.Subtask;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobGraphGenerator;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamOperatorFactory;
import io.nop.stream.core.transformation.OneInputTransformation;
import io.nop.stream.core.transformation.PartitionTransformation;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.core.transformation.TimestampsAndWatermarksTransformation;
import io.nop.stream.core.transformation.Transformation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive unit tests for StreamGraphGenerator class.
 * Tests the conversion from Transformation DAG to StreamGraph representation.
 */
public class TestStreamGraphGenerator {

    private StreamGraphGenerator generator;

    @BeforeEach
    public void setUp() {
        generator = new StreamGraphGenerator();
    }

    @Test
    public void testGenerateNullTransformations() {
        assertThrows(StreamException.class, () -> generator.generate(null));
    }

    @Test
    public void testGenerateEmptyList() {
        StreamGraph streamGraph = generator.generate(Collections.emptyList());
        
        assertNotNull(streamGraph);
        assertTrue(streamGraph.getStreamNodes().isEmpty());
        assertTrue(streamGraph.getSourceIDs().isEmpty());
        assertTrue(streamGraph.getSinkIDs().isEmpty());
    }

    @Test
    public void testSimpleSourceTransformation() {
        SourceTransformation<String> sourceTransformation = createSourceTransformation("TestSource", 2);
        
        StreamGraph streamGraph = generator.generate(Collections.singletonList(sourceTransformation));
        
        assertNotNull(streamGraph);
        assertEquals(1, streamGraph.getStreamNodes().size());
        assertEquals(1, streamGraph.getSourceIDs().size());
        
        // Verify source node
        StreamNode sourceNode = streamGraph.getStreamNode(sourceTransformation.getId());
        assertNotNull(sourceNode);
        assertEquals("TestSource", sourceNode.getName());
        assertEquals(2, sourceNode.getParallelism());
        assertEquals(sourceTransformation.getId(), sourceNode.getId());
        
        // Verify source ID is registered
        assertTrue(streamGraph.getSourceIDs().contains(sourceTransformation.getId()));
    }

    @Test
    public void testSourceToSinkChain() {
        // Create: Source -> Sink
        SourceTransformation<String> source = createSourceTransformation("Source", 2);
        SinkTransformation<String> sink = createSinkTransformation(source, "Sink", 2);
        
        StreamGraph streamGraph = generator.generate(Collections.singletonList(sink));
        
        assertNotNull(streamGraph);
        assertEquals(2, streamGraph.getStreamNodes().size());
        assertEquals(1, streamGraph.getSourceIDs().size());
        assertEquals(1, streamGraph.getSinkIDs().size());
        
        // Verify source node
        StreamNode sourceNode = streamGraph.getStreamNode(source.getId());
        assertNotNull(sourceNode);
        assertEquals("Source", sourceNode.getName());
        
        // Verify sink node
        StreamNode sinkNode = streamGraph.getStreamNode(sink.getId());
        assertNotNull(sinkNode);
        assertEquals("Sink", sinkNode.getName());
        
        // Verify edge from source to sink
        List<StreamEdge> edges = streamGraph.getStreamEdges(source.getId());
        assertEquals(1, edges.size());
        assertEquals(source.getId(), edges.get(0).getSourceId());
        assertEquals(sink.getId(), edges.get(0).getTargetId());
        
        // Verify sink ID is registered
        assertTrue(streamGraph.getSinkIDs().contains(sink.getId()));
    }

    @Test
    public void testSourceMapFilterSinkChain() {
        // Create: Source -> Map -> Filter -> Sink
        SourceTransformation<String> source = createSourceTransformation("Source", 2);
        OneInputTransformation<String, String> map = createOneInputTransformation(source, "Map", 2);
        OneInputTransformation<String, String> filter = createOneInputTransformation(map, "Filter", 2);
        SinkTransformation<String> sink = createSinkTransformation(filter, "Sink", 2);
        
        StreamGraph streamGraph = generator.generate(Collections.singletonList(sink));
        
        assertNotNull(streamGraph);
        assertEquals(4, streamGraph.getStreamNodes().size());
        assertEquals(1, streamGraph.getSourceIDs().size());
        assertEquals(1, streamGraph.getSinkIDs().size());
        
        // Verify all nodes exist
        assertNotNull(streamGraph.getStreamNode(source.getId()));
        assertNotNull(streamGraph.getStreamNode(map.getId()));
        assertNotNull(streamGraph.getStreamNode(filter.getId()));
        assertNotNull(streamGraph.getStreamNode(sink.getId()));
        
        // Verify chain of edges: Source -> Map -> Filter -> Sink
        verifyEdge(streamGraph, source.getId(), map.getId());
        verifyEdge(streamGraph, map.getId(), filter.getId());
        verifyEdge(streamGraph, filter.getId(), sink.getId());
    }

    @Test
    public void testMultipleSources() {
        // Create two sources both feeding into one sink
        SourceTransformation<String> source1 = createSourceTransformation("Source1", 2);
        SourceTransformation<String> source2 = createSourceTransformation("Source2", 2);
        
        // Note: In a real scenario, sources would connect to a union or join operator
        // For this test, we'll add both transformations to the list
        List<Transformation<?>> transformations = new ArrayList<>();
        transformations.add(source1);
        transformations.add(source2);
        
        StreamGraph streamGraph = generator.generate(transformations);
        
        assertNotNull(streamGraph);
        assertEquals(2, streamGraph.getStreamNodes().size());
        assertEquals(2, streamGraph.getSourceIDs().size());
        
        // Verify both source nodes exist
        assertNotNull(streamGraph.getStreamNode(source1.getId()));
        assertNotNull(streamGraph.getStreamNode(source2.getId()));
        
        // Verify both are registered as sources
        assertTrue(streamGraph.getSourceIDs().contains(source1.getId()));
        assertTrue(streamGraph.getSourceIDs().contains(source2.getId()));
    }

    @Test
    public void testMultipleSinks() {
        // Create one source feeding into two sinks
        SourceTransformation<String> source = createSourceTransformation("Source", 2);
        SinkTransformation<String> sink1 = createSinkTransformation(source, "Sink1", 2);
        SinkTransformation<String> sink2 = createSinkTransformation(source, "Sink2", 2);
        
        List<Transformation<?>> transformations = new ArrayList<>();
        transformations.add(sink1);
        transformations.add(sink2);
        
        StreamGraph streamGraph = generator.generate(transformations);
        
        assertNotNull(streamGraph);
        assertEquals(3, streamGraph.getStreamNodes().size());
        assertEquals(1, streamGraph.getSourceIDs().size());
        assertEquals(2, streamGraph.getSinkIDs().size());
        
        // Verify source node
        assertNotNull(streamGraph.getStreamNode(source.getId()));
        
        // Verify both sink nodes
        assertNotNull(streamGraph.getStreamNode(sink1.getId()));
        assertNotNull(streamGraph.getStreamNode(sink2.getId()));
        
        // Verify both edges exist from source to each sink
        List<StreamEdge> edges = streamGraph.getStreamEdges(source.getId());
        assertEquals(2, edges.size());
        
        // Verify both sinks are registered
        assertTrue(streamGraph.getSinkIDs().contains(sink1.getId()));
        assertTrue(streamGraph.getSinkIDs().contains(sink2.getId()));
    }

    @Test
    public void testPartitionTransformation() {
        // Create: Source -> Partition -> Map
        SourceTransformation<String> source = createSourceTransformation("Source", 2);
        PartitionTransformation<String> partition = createPartitionTransformation(source, "Partition", 4);
        OneInputTransformation<String, String> map = createOneInputTransformation(partition, "Map", 4);
        
        StreamGraph streamGraph = generator.generate(Collections.singletonList(map));
        
        assertNotNull(streamGraph);
        assertEquals(3, streamGraph.getStreamNodes().size());
        
        // Verify all nodes exist
        assertNotNull(streamGraph.getStreamNode(source.getId()));
        assertNotNull(streamGraph.getStreamNode(partition.getId()));
        assertNotNull(streamGraph.getStreamNode(map.getId()));
        
        // Verify edges
        verifyEdge(streamGraph, source.getId(), partition.getId());
        verifyEdge(streamGraph, partition.getId(), map.getId());
        
        // Verify partitioner is set on the edge from source to partition
        List<StreamEdge> sourceEdges = streamGraph.getStreamEdges(source.getId());
        assertEquals(1, sourceEdges.size());
        assertNotNull(sourceEdges.get(0).getPartitioner());
        assertEquals(partition.getId(), sourceEdges.get(0).getTargetId());
    }

    @Test
    public void testTimestampsAndWatermarksTransformation() {
        SourceTransformation<String> source = createSourceTransformation("Source", 2);
        WatermarkStrategy<String> strategy = WatermarkStrategy.<String>forMonotonousTimestamps();
        TimestampsAndWatermarksTransformation<String> tsTransform =
            new TimestampsAndWatermarksTransformation<>("Timestamps/Watermarks", createStringTypeInformation(), 2, source, strategy);
        OneInputTransformation<String, String> map = createOneInputTransformation(tsTransform, "Map", 2);

        StreamGraph streamGraph = generator.generate(Collections.singletonList(map));

        assertNotNull(streamGraph);
        assertEquals(3, streamGraph.getStreamNodes().size());

        assertNotNull(streamGraph.getStreamNode(source.getId()));
        assertNotNull(streamGraph.getStreamNode(tsTransform.getId()));
        assertNotNull(streamGraph.getStreamNode(map.getId()));

        verifyEdge(streamGraph, source.getId(), tsTransform.getId());
        verifyEdge(streamGraph, tsTransform.getId(), map.getId());

        StreamNode tsNode = streamGraph.getStreamNode(tsTransform.getId());
        assertEquals("Timestamps/Watermarks", tsNode.getName());
        assertEquals(2, tsNode.getParallelism());
    }

    @Test
    public void testTimestampsAndWatermarksChainWithSink() {
        SourceTransformation<String> source = createSourceTransformation("Source", 2);
        WatermarkStrategy<String> strategy = WatermarkStrategy.<String>forMonotonousTimestamps();
        TimestampsAndWatermarksTransformation<String> tsTransform =
            new TimestampsAndWatermarksTransformation<>("Timestamps/Watermarks", createStringTypeInformation(), 2, source, strategy);
        SinkTransformation<String> sink = createSinkTransformation(tsTransform, "Sink", 2);

        StreamGraph streamGraph = generator.generate(Collections.singletonList(sink));

        assertNotNull(streamGraph);
        assertEquals(3, streamGraph.getStreamNodes().size());
        assertEquals(1, streamGraph.getSourceIDs().size());
        assertEquals(1, streamGraph.getSinkIDs().size());

        verifyEdge(streamGraph, source.getId(), tsTransform.getId());
        verifyEdge(streamGraph, tsTransform.getId(), sink.getId());
    }

    @Test
    public void testTransformationIdempotency() {
        // Test that processing the same transformation twice doesn't duplicate nodes
        SourceTransformation<String> source = createSourceTransformation("Source", 2);
        OneInputTransformation<String, String> map = createOneInputTransformation(source, "Map", 2);
        
        // Add the same sink twice to the list
        SinkTransformation<String> sink = createSinkTransformation(map, "Sink", 2);
        
        List<Transformation<?>> transformations = new ArrayList<>();
        transformations.add(sink);
        transformations.add(sink); // Add same transformation again
        
        StreamGraph streamGraph = generator.generate(transformations);
        
        assertNotNull(streamGraph);
        // Should still have only 3 nodes, not 4
        assertEquals(3, streamGraph.getStreamNodes().size());
        
        // Verify all nodes exist exactly once
        assertNotNull(streamGraph.getStreamNode(source.getId()));
        assertNotNull(streamGraph.getStreamNode(map.getId()));
        assertNotNull(streamGraph.getStreamNode(sink.getId()));
    }

    @Test
    public void testDifferentParallelism() {
        // Test chain with different parallelism at each step
        SourceTransformation<String> source = createSourceTransformation("Source", 1);
        OneInputTransformation<String, String> map = createOneInputTransformation(source, "Map", 2);
        OneInputTransformation<String, String> filter = createOneInputTransformation(map, "Filter", 4);
        SinkTransformation<String> sink = createSinkTransformation(filter, "Sink", 4);
        
        StreamGraph streamGraph = generator.generate(Collections.singletonList(sink));
        
        assertNotNull(streamGraph);
        assertEquals(4, streamGraph.getStreamNodes().size());
        
        // Verify parallelism for each node
        assertEquals(1, streamGraph.getStreamNode(source.getId()).getParallelism());
        assertEquals(2, streamGraph.getStreamNode(map.getId()).getParallelism());
        assertEquals(4, streamGraph.getStreamNode(filter.getId()).getParallelism());
        assertEquals(4, streamGraph.getStreamNode(sink.getId()).getParallelism());
    }

    // ===== 2PC Sink Parallel Build Tests (CONN-01 successor: gate removed) =====

    /**
     * Test A: a TwoPhaseCommitSinkFunction sink at parallelism=1 builds a StreamGraph
     * without error (regression guard for the proven path).
     */
    @Test
    public void testTwoPhaseCommitSinkAtParallelism1BuildsGraph() {
        SourceTransformation<String> source = createSourceTransformation("Source", 1);
        SinkTransformation<String> sink = createTwoPhaseCommitSinkTransformation(source, "My2PCSink", 1);

        StreamGraph streamGraph = generator.generate(Collections.singletonList(sink));

        assertNotNull(streamGraph);
        assertEquals(2, streamGraph.getStreamNodes().size());
        assertEquals(1, streamGraph.getSinkIDs().size());
        assertTrue(streamGraph.getSinkIDs().contains(sink.getId()));

        // Verify the sink node has parallelism=1
        StreamNode sinkNode = streamGraph.getStreamNode(sink.getId());
        assertNotNull(sinkNode);
        assertEquals(1, sinkNode.getParallelism());
    }

    /**
     * Test B (CONN-01 successor): a TwoPhaseCommitSinkFunction sink at parallelism=2
     * builds through the FULL planning pipeline — StreamGraph -&gt; JobGraph -&gt;
     * {@link GraphExecutionPlan} — and the sink vertex materializes exactly 2 subtasks,
     * each holding an INDEPENDENT 2PC UDF copy whose subtask identity equals the
     * subtask index. This is the plan-side pin of the landed parallel-2PC capability
     * (per-subtask isolation via {@code copyForSubtask(int)}); the former planning-time
     * gate used to reject this exact shape with
     * {@code ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED}.
     */
    @Test
    public void testTwoPhaseCommitSinkAtParallelism2BuildsAndSplitsIntoIndependentSubtaskCopies() {
        SourceTransformation<String> source = createSourceTransformation("Source", 2);
        SinkTransformation<String> sink = createTwoPhaseCommitSinkTransformation(source, "My2PCSink", 2);

        StreamGraph streamGraph = generator.generate(Collections.singletonList(sink));

        assertNotNull(streamGraph);
        assertEquals(2, streamGraph.getStreamNodes().size());
        assertEquals(1, streamGraph.getSinkIDs().size());
        assertTrue(streamGraph.getSinkIDs().contains(sink.getId()));

        StreamNode sinkNode = streamGraph.getStreamNode(sink.getId());
        assertNotNull(sinkNode);
        assertEquals(2, sinkNode.getParallelism());

        // Per-subtask split: JobGraph + GraphExecutionPlan materialize one chain copy
        // per subtask, each carrying an independent 2PC UDF with matching identity.
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);
        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph, null, false);
        assertSinkSubtaskCopies(jobGraph, plan, "My2PCSink", 2);
    }

    /**
     * Test C: a non-2PC sink (plain SinkFunction) at parallelism>1 is NOT rejected
     * (planning is sink-type agnostic since the gate removal).
     */
    @Test
    public void testNonTwoPhaseCommitSinkAtParallelism2IsAllowed() {
        SourceTransformation<String> source = createSourceTransformation("Source", 2);
        SinkTransformation<String> sink = createSinkTransformation(source, "PlainSink", 2);

        StreamGraph streamGraph = generator.generate(Collections.singletonList(sink));

        assertNotNull(streamGraph);
        assertEquals(2, streamGraph.getStreamNodes().size());
        assertTrue(streamGraph.getSinkIDs().contains(sink.getId()));

        StreamNode sinkNode = streamGraph.getStreamNode(sink.getId());
        assertNotNull(sinkNode);
        assertEquals(2, sinkNode.getParallelism());
    }

    /**
     * Item 29 regression (CONN-01 successor): a TwoPhaseCommitSinkFunction whose
     * parallelism was raised from 1 to 3 via the per-operator {@code setParallelism}
     * entry builds through the full pipeline and materializes 3 per-subtask copies
     * (the former gate used to reject this declared-parallelism shape too).
     */
    @Test
    public void testTwoPhaseCommitSinkRaisedToParallelism3ViaSetParallelismBuildsPerSubtaskCopies() {
        SourceTransformation<String> source = createSourceTransformation("Source", 1);
        SinkTransformation<String> sink = createTwoPhaseCommitSinkTransformation(source, "My2PCSink", 1);
        assertEquals(1, sink.getParallelism());
        sink.setParallelism(3);

        StreamGraph streamGraph = generator.generate(Collections.singletonList(sink));

        assertNotNull(streamGraph);
        assertEquals(3, streamGraph.getStreamNode(sink.getId()).getParallelism());

        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);
        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph, null, false);
        assertSinkSubtaskCopies(jobGraph, plan, "My2PCSink", 3);
    }

    /**
     * Shared per-subtask assertion: the vertex named {@code sinkName} materializes
     * exactly {@code expected} subtasks; each subtask's chain holds a
     * {@link TestTwoPhaseCommitSinkFunction} UDF whose recorded subtask identity
     * equals the subtask index, and no two subtasks share a UDF instance.
     */
    private void assertSinkSubtaskCopies(JobGraph jobGraph, GraphExecutionPlan plan,
                                         String sinkName, int expected) {
        String sinkVertexId = null;
        for (java.util.Map.Entry<String, JobVertex> entry : jobGraph.getVertices().entrySet()) {
            if (entry.getValue().getName() != null && entry.getValue().getName().contains(sinkName)) {
                sinkVertexId = entry.getKey();
                break;
            }
        }
        assertNotNull(sinkVertexId, "sink vertex named " + sinkName + " must exist in the JobGraph");

        List<Subtask> subtasks = plan.getSubtasks(sinkVertexId);
        assertEquals(expected, subtasks.size(),
                "2PC sink vertex must materialize exactly " + expected + " subtasks");

        List<TestTwoPhaseCommitSinkFunction<?>> udps = new ArrayList<>();
        for (Subtask subtask : subtasks) {
            TestTwoPhaseCommitSinkFunction<?> udf = sinkUdfOf(subtask);
            assertEquals(subtask.getTaskIndex(), udf.getAssignedSubtaskIndex(),
                    "subtask " + subtask.getTaskIndex() + " must hold a UDF copy with matching identity");
            udps.add(udf);
        }
        for (int i = 0; i < udps.size(); i++) {
            for (int j = i + 1; j < udps.size(); j++) {
                assertNotSame(udps.get(i), udps.get(j),
                        "subtask UDF copies must be independent instances");
            }
        }
    }

    private TestTwoPhaseCommitSinkFunction<?> sinkUdfOf(Subtask subtask) {
        for (StreamOperator<?> op : subtask.getInvokable().getOperatorChain().getOperators()) {
            if (op instanceof AbstractUdfStreamOperator) {
                Object udf = ((AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
                if (udf instanceof TestTwoPhaseCommitSinkFunction) {
                    return (TestTwoPhaseCommitSinkFunction<?>) udf;
                }
            }
        }
        throw new AssertionError("no TestTwoPhaseCommitSinkFunction UDF found in the subtask chain");
    }

    @Test
    public void testOneInputTransformationWithKeySelector() {
        SourceTransformation<String> source = createSourceTransformation("Source", 2);
        
        TypeInformation<String> outputType = createStringTypeInformation();
        KeySelector<String, String> keySelector = new TestKeySelector();
        StreamOperatorFactory<String> operatorFactory = new TestOperatorFactory("Map");
        
        OneInputTransformation<String, String> mapWithKey = new OneInputTransformation<>(
            source, "KeyedMap", operatorFactory, outputType, 2, keySelector
        );
        
        StreamGraph streamGraph = generator.generate(Collections.singletonList(mapWithKey));
        
        assertNotNull(streamGraph);
        assertEquals(2, streamGraph.getStreamNodes().size());
        
        // Verify the node has key selector set
        StreamNode mapNode = streamGraph.getStreamNode(mapWithKey.getId());
        assertNotNull(mapNode);
        assertNotNull(mapNode.getKeySelector());
    }

    @Test
    public void testComplexTopology() {
        // Create a more complex topology:
        // Source1 -> Map1 -> Filter -> Sink1
        // Source2 -> Map2 --------^
        SourceTransformation<String> source1 = createSourceTransformation("Source1", 2);
        SourceTransformation<String> source2 = createSourceTransformation("Source2", 2);
        
        OneInputTransformation<String, String> map1 = createOneInputTransformation(source1, "Map1", 2);
        OneInputTransformation<String, String> map2 = createOneInputTransformation(source2, "Map2", 2);
        
        // In a real scenario, we would have a union or join here
        // For this test, we'll just verify both paths can be processed
        SinkTransformation<String> sink1 = createSinkTransformation(map1, "Sink1", 2);
        SinkTransformation<String> sink2 = createSinkTransformation(map2, "Sink2", 2);
        
        List<Transformation<?>> transformations = new ArrayList<>();
        transformations.add(sink1);
        transformations.add(sink2);
        
        StreamGraph streamGraph = generator.generate(transformations);
        
        assertNotNull(streamGraph);
        assertEquals(6, streamGraph.getStreamNodes().size());
        assertEquals(2, streamGraph.getSourceIDs().size());
        assertEquals(2, streamGraph.getSinkIDs().size());
        
        // Verify all nodes exist
        assertNotNull(streamGraph.getStreamNode(source1.getId()));
        assertNotNull(streamGraph.getStreamNode(source2.getId()));
        assertNotNull(streamGraph.getStreamNode(map1.getId()));
        assertNotNull(streamGraph.getStreamNode(map2.getId()));
        assertNotNull(streamGraph.getStreamNode(sink1.getId()));
        assertNotNull(streamGraph.getStreamNode(sink2.getId()));
    }

    // ===== Helper Methods =====

    private SourceTransformation<String> createSourceTransformation(String name, int parallelism) {
        TypeInformation<String> outputType = createStringTypeInformation();
        SourceFunction<String> sourceFunction = new TestSourceFunction();
        return new SourceTransformation<>(name, sourceFunction, outputType, parallelism);
    }

    private <T> OneInputTransformation<T, String> createOneInputTransformation(
            Transformation<T> input, String name, int parallelism) {
        TypeInformation<String> outputType = createStringTypeInformation();
        StreamOperatorFactory<String> operatorFactory = new TestOperatorFactory(name);
        return new OneInputTransformation<>(input, name, operatorFactory, outputType, parallelism);
    }

    private <T> SinkTransformation<T> createSinkTransformation(
            Transformation<T> input, String name, int parallelism) {
        TypeInformation<Void> outputType = createVoidTypeInformation();
        SinkFunction<T> sinkFunction = new TestSinkFunction<>();
        return new SinkTransformation<>(input, name, sinkFunction, outputType, parallelism);
    }

    private <T> SinkTransformation<T> createTwoPhaseCommitSinkTransformation(
            Transformation<T> input, String name, int parallelism) {
        TypeInformation<Void> outputType = createVoidTypeInformation();
        SinkFunction<T> sinkFunction = new TestTwoPhaseCommitSinkFunction<>();
        return new SinkTransformation<>(input, name, sinkFunction, outputType, parallelism);
    }

    private <T> PartitionTransformation<T> createPartitionTransformation(
            Transformation<T> input, String name, int parallelism) {
        TypeInformation<T> outputType = (TypeInformation<T>) createStringTypeInformation();
        IPartitioner<T> partitioner = new TestPartitioner<>();
        return new PartitionTransformation<>(input, name, partitioner, outputType, parallelism);
    }

    private void verifyEdge(StreamGraph streamGraph, int sourceId, int targetId) {
        List<StreamEdge> edges = streamGraph.getStreamEdges(sourceId);
        boolean found = false;
        for (StreamEdge edge : edges) {
            if (edge.getTargetId() == targetId) {
                found = true;
                assertEquals(sourceId, edge.getSourceId());
                break;
            }
        }
        assertTrue(found, "Expected edge from " + sourceId + " to " + targetId + " not found");
    }

    private TypeInformation<String> createStringTypeInformation() {
        return new TypeInformation<String>() {
            @Override
            public Class<String> getTypeClass() {
                return String.class;
            }
        };
    }

    private TypeInformation<Void> createVoidTypeInformation() {
        return new TypeInformation<Void>() {
            @Override
            public Class<Void> getTypeClass() {
                return Void.class;
            }
        };
    }

    // ===== Test Helper Classes =====

    private static class TestSourceFunction implements SourceFunction<String> {
        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            // No-op for testing
        }

        @Override
        public void cancel() {
            // No-op for testing
        }
    }

    private static class TestSinkFunction<T> implements SinkFunction<T> {
        @Override
        public void consume(T value) throws Exception {
            // No-op for testing
        }
    }

    /**
     * Parallel-capable test 2PC sink: mirrors the built-in sinks' contract —
     * {@code copyForSubtask(int)} returns an independent copy recording the subtask
     * identity (the base-class default would throw for index &gt; 0, which is the
     * subclass fail-fast contract tested in {@code TestOperatorSubtaskIsolation}).
     */
    private static class TestTwoPhaseCommitSinkFunction<T> extends TwoPhaseCommitSinkFunction<T> {
        private final int assignedSubtaskIndex;

        TestTwoPhaseCommitSinkFunction() {
            this(-1);
        }

        private TestTwoPhaseCommitSinkFunction(int assignedSubtaskIndex) {
            this.assignedSubtaskIndex = assignedSubtaskIndex;
        }

        int getAssignedSubtaskIndex() {
            return assignedSubtaskIndex;
        }

        @Override
        public TestTwoPhaseCommitSinkFunction<T> copyForSubtask(int subtaskIndex) {
            return new TestTwoPhaseCommitSinkFunction<>(subtaskIndex);
        }

        @Override
        public void beginTransaction() {
        }

        @Override
        public void invoke(T value) {
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
    }

    private static class TestOperatorFactory implements StreamOperatorFactory<String> {
        private final String name;

        TestOperatorFactory(String name) {
            this.name = name;
        }

        @Override
        public StreamOperator<String> createStreamOperator(TypeInformation<String> outputType) {
            return new TestOperator(name);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getParallelism() {
            return 1;
        }
    }

    @io.nop.stream.core.operators.Shareable
    private static class TestOperator implements StreamOperator<String> {
        private final String name;

        TestOperator(String name) {
            this.name = name;
        }

        @Override
        public void open() throws Exception {
        }

        @Override
        public void finish() throws Exception {
        }

        @Override
        public void close() throws Exception {
        }

        @Override
        public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {
        }

        @Override
        public void setKeyContextElement1(io.nop.stream.core.streamrecord.StreamRecord<?> record) throws Exception {
        }

        @Override
        public void setKeyContextElement2(io.nop.stream.core.streamrecord.StreamRecord<?> record) throws Exception {
        }

        @Override
        public void setCurrentKey(Object key) {
        }

        @Override
        public Object getCurrentKey() {
            return null;
        }

        @Override
        public void notifyCheckpointComplete(long checkpointId) throws Exception {
        }
    }

    private static class TestPartitioner<T> implements IPartitioner<T> {
        @Override
        public int partition(T key, int numPartitions) {
            // Simple hash-based partitioning for testing
            return key == null ? 0 : Math.abs(key.hashCode()) % numPartitions;
        }
    }

    private static class TestKeySelector implements KeySelector<String, String> {
        @Override
        public String getKey(String value) throws Exception {
            return value;
        }
    }
}
