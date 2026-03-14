/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.integration;

import io.nop.stream.core.common.functions.*;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.execution.Task;
import io.nop.stream.core.execution.TaskExecutor;
import io.nop.stream.core.graph.StreamEdge;
import io.nop.stream.core.graph.StreamGraph;
import io.nop.stream.core.graph.StreamGraphGenerator;
import io.nop.stream.core.jobgraph.*;
import io.nop.stream.core.operator.StreamOperator;
import io.nop.stream.core.operator.StreamOperatorFactory;
import io.nop.stream.core.transformation.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating the complete flow from DataStream API to execution.
 * 
 * <p>This test verifies the end-to-end pipeline transformation layers:
 * <ol>
 *   <li>API: Create transformations using the transformation API</li>
 *   <li>Transformation: Build a DAG of transformations (Source → Map → Filter → Sink)</li>
 *   <li>StreamGraph: Convert transformations to StreamGraph</li>
 *   <li>JobGraph: Convert StreamGraph to JobGraph with operator chains</li>
 *   <li>TaskExecutor: Submit JobGraph for execution</li>
 * </ol>
 * 
 * <p>The test demonstrates the complete pipeline transformation process by creating
 * a simple pipeline that includes Source → Map → Filter → Sink transformations.
 */
public class TestEndToEndPipeline {

    private TaskExecutor taskExecutor;

    @BeforeEach
    public void setUp() {
        taskExecutor = new TaskExecutor(2);
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        if (taskExecutor != null && !taskExecutor.isShutdown()) {
            taskExecutor.shutdown();
            taskExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Test the complete end-to-end pipeline transformation flow.
     * This demonstrates: API → Transformation → StreamGraph → JobGraph → TaskExecutor
     */
    @Test
    public void testCompletePipelineTransformation() throws Exception {
        // ========== Step 1: Create Transformations (API Layer) ==========
        
        SourceTransformation<Integer> source = createSourceTransformation(
            "IntegerSource", 
            1
        );
        
        OneInputTransformation<Integer, Integer> mapTransform = createMapTransformation(
            source,
            "DoubleMap",
            1
        );
        
        OneInputTransformation<Integer, Integer> filterTransform = createFilterTransformation(
            mapTransform,
            "EvenFilter",
            1
        );
        
        SinkTransformation<Integer> sinkTransform = createSinkTransformation(
            filterTransform,
            "CollectSink",
            1
        );

        // ========== Step 2: Generate StreamGraph (Transformation → StreamGraph) ==========
        
        StreamGraph streamGraph = new StreamGraphGenerator()
            .generate(Collections.singletonList(sinkTransform));
        
        // Verify StreamGraph structure
        assertNotNull(streamGraph, "StreamGraph should not be null");
        assertEquals(4, streamGraph.getStreamNodes().size(), "Should have 4 nodes");
        assertEquals(1, streamGraph.getSourceIDs().size(), "Should have 1 source");
        assertEquals(1, streamGraph.getSinkIDs().size(), "Should have 1 sink");
        
        // Verify nodes exist
        assertNotNull(streamGraph.getStreamNode(source.getId()), "Source node should exist");
        assertNotNull(streamGraph.getStreamNode(mapTransform.getId()), "Map node should exist");
        assertNotNull(streamGraph.getStreamNode(filterTransform.getId()), "Filter node should exist");
        assertNotNull(streamGraph.getStreamNode(sinkTransform.getId()), "Sink node should exist");
        
        // Verify edges: Source → Map → Filter → Sink
        verifyEdge(streamGraph, source.getId(), mapTransform.getId());
        verifyEdge(streamGraph, mapTransform.getId(), filterTransform.getId());
        verifyEdge(streamGraph, filterTransform.getId(), sinkTransform.getId());

        // ========== Step 3: Generate JobGraph (StreamGraph → JobGraph) ==========
        
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);
        
        // Verify JobGraph structure
        assertNotNull(jobGraph, "JobGraph should not be null");
        assertNotNull(jobGraph.getVertices(), "Vertices should not be null");
        assertTrue(jobGraph.getVertices().size() > 0, "Should have at least one vertex");
        
        // Verify vertices have proper configuration
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            assertNotNull(vertex.getId(), "Vertex ID should not be null");
            assertNotNull(vertex.getName(), "Vertex name should not be null");
            assertTrue(vertex.getParallelism() > 0, "Parallelism should be positive");
            assertNotNull(vertex.getOperatorChains(), "Operator chains should not be null");
            assertNotNull(vertex.getInvokable(), "Invokable should not be null");
        }

        // ========== Step 4: Submit to TaskExecutor (JobGraph → Execution) ==========
        
        // Submit all job vertices to task executor
        int totalTasks = 0;
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            List<Task> tasks = taskExecutor.submitJobVertex(vertex);
            assertNotNull(tasks, "Tasks should not be null");
            assertEquals(vertex.getParallelism(), tasks.size(), 
                "Should have correct number of tasks for parallelism");
            totalTasks += tasks.size();
        }
        
        assertTrue(totalTasks > 0, "Should have submitted at least one task");
        
        // Wait for completion
        boolean completed = taskExecutor.awaitCompletion(10, TimeUnit.SECONDS);
        assertTrue(completed, "Tasks should complete within timeout");
        
        // Verify execution statistics
        assertEquals(0, taskExecutor.getFailedTaskCount(), "No tasks should fail");
        assertEquals(totalTasks, taskExecutor.getCompletedTaskCount(), "All tasks should complete");
    }

    /**
     * Test a simpler pipeline with just Source → Map → Sink.
     */
    @Test
    public void testSimplePipelineTransformation() throws Exception {
        // Create simple pipeline: Source → Map → Sink
        SourceTransformation<String> source = createSourceTransformation(
            "StringSource",
            1
        );
        
        OneInputTransformation<String, String> mapTransform = createMapTransformation(
            source,
            "UpperCase",
            1
        );
        
        SinkTransformation<String> sinkTransform = createSinkTransformation(
            mapTransform,
            "CollectSink",
            1
        );

        // Generate and verify StreamGraph
        StreamGraph streamGraph = new StreamGraphGenerator()
            .generate(Collections.singletonList(sinkTransform));
        
        assertNotNull(streamGraph);
        assertEquals(3, streamGraph.getStreamNodes().size());
        assertEquals(1, streamGraph.getSourceIDs().size());
        assertEquals(1, streamGraph.getSinkIDs().size());

        // Generate and verify JobGraph
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);
        assertNotNull(jobGraph);
        assertTrue(jobGraph.getVertices().size() > 0);

        // Execute
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            taskExecutor.submitJobVertex(vertex);
        }
        
        boolean completed = taskExecutor.awaitCompletion(5, TimeUnit.SECONDS);
        assertTrue(completed);
        assertEquals(0, taskExecutor.getFailedTaskCount());
    }

    /**
     * Test pipeline with multiple transformations in sequence.
     */
    @Test
    public void testChainedTransformations() throws Exception {
        // Create chain: Source → Map1 → Map2 → Filter → Sink
        SourceTransformation<Integer> source = createSourceTransformation(
            "NumberSource",
            1
        );
        
        OneInputTransformation<Integer, Integer> map1 = createMapTransformation(
            source, "Add10", 1
        );
        
        OneInputTransformation<Integer, Integer> map2 = createMapTransformation(
            map1, "Multiply2", 1
        );
        
        OneInputTransformation<Integer, Integer> filter = createFilterTransformation(
            map2, "GreaterThan25", 1
        );
        
        SinkTransformation<Integer> sink = createSinkTransformation(
            filter, "CollectSink", 1
        );

        // Execute pipeline transformation
        StreamGraph streamGraph = new StreamGraphGenerator()
            .generate(Collections.singletonList(sink));
        
        assertEquals(5, streamGraph.getStreamNodes().size());
        
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);
        assertNotNull(jobGraph);
        
        for (JobVertex vertex : jobGraph.getVertices().values()) {
            taskExecutor.submitJobVertex(vertex);
        }
        
        boolean completed = taskExecutor.awaitCompletion(5, TimeUnit.SECONDS);
        assertTrue(completed);
        assertEquals(0, taskExecutor.getFailedTaskCount());
    }

    // ========== Helper Methods ==========

    private <T> SourceTransformation<T> createSourceTransformation(
            String name, int parallelism) {
        return new SourceTransformation<>(
            name,
            new TestSourceFunction<>(),
            createTypeInfo(),
            parallelism
        );
    }

    private <IN, OUT> OneInputTransformation<IN, OUT> createMapTransformation(
            Transformation<IN> input, String name, int parallelism) {
        TestMapOperator<IN, OUT> operator = new TestMapOperator<>();
        TestOperatorFactory<OUT> factory = new TestOperatorFactory<>(operator, name, parallelism);
        return new OneInputTransformation<>(
            input,
            name,
            factory,
            createTypeInfo(),
            parallelism
        );
    }

    private <T> OneInputTransformation<T, T> createFilterTransformation(
            Transformation<T> input, String name, int parallelism) {
        TestFilterOperator<T> operator = new TestFilterOperator<>();
        TestOperatorFactory<T> factory = new TestOperatorFactory<>(operator, name, parallelism);
        return new OneInputTransformation<>(
            input,
            name,
            factory,
            createTypeInfo(),
            parallelism
        );
    }

    private <T> SinkTransformation<T> createSinkTransformation(
            Transformation<T> input, String name, int parallelism) {
        return new SinkTransformation<>(
            input,
            name,
            new TestSinkFunction<>(),
            createTypeInfo(),
            parallelism
        );
    }

    private void verifyEdge(StreamGraph graph, int sourceId, int targetId) {
        List<StreamEdge> edges = graph.getStreamEdges(sourceId);
        boolean found = false;
        for (StreamEdge edge : edges) {
            if (edge.getTargetId() == targetId) {
                found = true;
                assertEquals(sourceId, edge.getSourceId());
                break;
            }
        }
        assertTrue(found, "Expected edge from " + sourceId + " to " + targetId);
    }

    @SuppressWarnings("unchecked")
    private <T> TypeInformation<T> createTypeInfo() {
        return new TypeInformation<T>() {
            @Override
            public Class<T> getTypeClass() {
                return (Class<T>) Object.class;
            }
        };
    }

    // ========== Test Helper Classes ==========

    private static class TestSourceFunction<T> implements SourceFunction<T>, Serializable {
        private static final long serialVersionUID = 1L;
        private volatile boolean isRunning = true;

        @Override
        public void run(SourceContext<T> ctx) throws Exception {
            // Test implementation - doesn't emit data
        }

        @Override
        public void cancel() {
            isRunning = false;
        }
    }

    private static class TestSinkFunction<T> implements SinkFunction<T>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(T value) throws Exception {
            // Test implementation - accepts data
        }
    }

    private static class TestMapOperator<IN, OUT> implements StreamOperator<OUT>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public TypeInformation<OUT> getOutputType() {
            return null;
        }

        @Override
        public String getName() {
            return "Map";
        }

        @Override
        public void initialize() {
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public ChainingStrategy getChainingStrategy() {
            return ChainingStrategy.ALWAYS;
        }
    }

    private static class TestFilterOperator<T> implements StreamOperator<T>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public TypeInformation<T> getOutputType() {
            return null;
        }

        @Override
        public String getName() {
            return "Filter";
        }

        @Override
        public void initialize() {
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public ChainingStrategy getChainingStrategy() {
            return ChainingStrategy.ALWAYS;
        }
    }

    private static class TestOperatorFactory<OUT> implements StreamOperatorFactory<OUT>, Serializable {
        private static final long serialVersionUID = 1L;
        private final StreamOperator<OUT> operator;
        private final String name;
        private final int parallelism;

        TestOperatorFactory(StreamOperator<OUT> operator, String name, int parallelism) {
            this.operator = operator;
            this.name = name;
            this.parallelism = parallelism;
        }

        @Override
        public StreamOperator<OUT> createStreamOperator(TypeInformation<OUT> outputType) {
            return operator;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getParallelism() {
            return parallelism;
        }
    }
}
