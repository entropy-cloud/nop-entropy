/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.integration;

import io.nop.stream.core.common.functions.FlatMapFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.IDeploymentPlanProvider;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.graph.PartitionedPlanGenerator;
import io.nop.stream.core.graph.StreamGraph;
import io.nop.stream.core.graph.StreamGraphGenerator;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobGraphGenerator;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.model.StreamModelFingerprint;
import io.nop.stream.core.transformation.*;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test verifying that PartitionedPlan and DeploymentPlan generators
 * are invoked during the StreamExecutionEnvironment.execute() pipeline.
 *
 * <p>Phase 1 of Plan 44: validates the execution pipeline extension.
 */
public class TestPartitionedDeploymentPlanIntegration {

    /**
     * Verify the complete execute() path produces correct results with the plan pipeline.
     */
    @Test
    public void testExecuteWithPlanPipeline() throws Exception {
        List<String> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.fromElements("a", "b", "c")
                .map(String::toUpperCase)
                .filter(s -> !s.equals("B"))
                .sink(results::add);

        var execResult = env.execute("Plan Pipeline Test");

        assertNotNull(execResult);
        assertEquals("Plan Pipeline Test", execResult.getJobName());
        assertEquals(Arrays.asList("A", "C"), results);
    }

    /**
     * Verify PartitionedPlanGenerator produces a valid plan from a manually built pipeline.
     */
    @Test
    public void testPartitionedPlanGeneration() throws Exception {
        // Build pipeline manually via Transformation API
        SourceTransformation<String> source = new SourceTransformation<>(
                "Source", new TestSourceFunction<>(), createTypeInfo(), 1);
        OneInputTransformation<String, String> map = new OneInputTransformation<>(
                source, "Map", new TestOperatorFactory<>("Map", 1), createTypeInfo(), 1);
        SinkTransformation<String> sink = new SinkTransformation<>(
                map, "Sink", new TestSinkFunction<>(), createTypeInfo(), 1);

        StreamGraph streamGraph = new StreamGraphGenerator()
                .generate(Collections.singletonList(sink));
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);

        // Build a minimal StreamModel
        Map<String, Transformation<?>> transformMap = new LinkedHashMap<>();
        transformMap.put(String.valueOf(source.getId()), source);
        transformMap.put(String.valueOf(map.getId()), map);
        transformMap.put(String.valueOf(sink.getId()), sink);
        StreamModel streamModel = new StreamModel(
                new io.nop.stream.core.model.StreamComponents(), transformMap);

        StreamModelFingerprint fingerprint = streamModel.computeFingerprint();

        // Generate PartitionedPlan
        PartitionedPlanGenerator planGenerator = new PartitionedPlanGenerator();
        PartitionedPlan partitionedPlan = planGenerator.generate(jobGraph, fingerprint);

        assertNotNull(partitionedPlan, "PartitionedPlan should not be null");
        assertNotNull(partitionedPlan.getJobId(), "Job ID should not be null");
        assertNotNull(partitionedPlan.getVertexPlans(), "Vertex plans should not be null");
        assertFalse(partitionedPlan.getVertexPlans().isEmpty(), "Should have vertex plans");
        assertNotNull(partitionedPlan.getFingerprint(), "Fingerprint should not be null");
    }

    /**
     * Verify DeploymentPlan is generated correctly from PartitionedPlan using the SPI provider.
     */
    @Test
    public void testDeploymentPlanGeneration() throws Exception {
        SourceTransformation<String> source = new SourceTransformation<>(
                "Source", new TestSourceFunction<>(), createTypeInfo(), 1);
        OneInputTransformation<String, String> map = new OneInputTransformation<>(
                source, "Map", new TestOperatorFactory<>("Map", 1), createTypeInfo(), 1);
        SinkTransformation<String> sink = new SinkTransformation<>(
                map, "Sink", new TestSinkFunction<>(), createTypeInfo(), 1);

        StreamGraph streamGraph = new StreamGraphGenerator()
                .generate(Collections.singletonList(sink));
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);

        StreamModelFingerprint fingerprint = StreamModelFingerprint.builder()
                .dagTopologyHash("test-hash")
                .build();

        PartitionedPlan partitionedPlan = new PartitionedPlanGenerator()
                .generate(jobGraph, fingerprint);

        // Generate DeploymentPlan using the default SPI provider
        IDeploymentPlanProvider provider = IDeploymentPlanProvider.getProvider();
        DeploymentPlan deploymentPlan = provider.generateLocal(partitionedPlan);

        assertNotNull(deploymentPlan, "DeploymentPlan should not be null");
        assertEquals("local", deploymentPlan.getTransportBackend());
        assertEquals("memory", deploymentPlan.getStateBackendBinding());
        assertNotNull(deploymentPlan.getEdgeConfigs(), "Edge configs should not be null");
        assertNotNull(deploymentPlan.getPartitionedPlan(), "PartitionedPlan reference should not be null");
    }

    /**
     * Verify GraphExecutionPlan.build() works with a DeploymentPlan parameter.
     */
    @Test
    public void testGraphExecutionPlanWithDeploymentPlan() throws Exception {
        SourceTransformation<String> source = new SourceTransformation<>(
                "Source", new TestSourceFunction<>(), createTypeInfo(), 1);
        OneInputTransformation<String, String> map = new OneInputTransformation<>(
                source, "Map", new TestOperatorFactory<>("Map", 1), createTypeInfo(), 1);
        SinkTransformation<String> sink = new SinkTransformation<>(
                map, "Sink", new TestSinkFunction<>(), createTypeInfo(), 1);

        StreamGraph streamGraph = new StreamGraphGenerator()
                .generate(Collections.singletonList(sink));
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);

        StreamModelFingerprint fingerprint = StreamModelFingerprint.builder()
                .dagTopologyHash("test-hash")
                .build();

        PartitionedPlan partitionedPlan = new PartitionedPlanGenerator()
                .generate(jobGraph, fingerprint);

        IDeploymentPlanProvider provider = IDeploymentPlanProvider.getProvider();
        DeploymentPlan deploymentPlan = provider.generateLocal(partitionedPlan);

        // Build GraphExecutionPlan with DeploymentPlan
        GraphExecutionPlan execPlan = GraphExecutionPlan.build(jobGraph, deploymentPlan);
        assertNotNull(execPlan, "GraphExecutionPlan should not be null");
        assertFalse(execPlan.getSortedVertexIds().isEmpty(), "Should have sorted vertices");
    }

    /**
     * Verify GraphExecutionPlan.build() still works with null DeploymentPlan (backward compat).
     */
    @Test
    public void testGraphExecutionPlanWithNullDeploymentPlan() throws Exception {
        SourceTransformation<String> source = new SourceTransformation<>(
                "Source", new TestSourceFunction<>(), createTypeInfo(), 1);
        SinkTransformation<String> sink = new SinkTransformation<>(
                source, "Sink", new TestSinkFunction<>(), createTypeInfo(), 1);

        StreamGraph streamGraph = new StreamGraphGenerator()
                .generate(Collections.singletonList(sink));
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);

        // Build with null DeploymentPlan (default behavior)
        GraphExecutionPlan plan = GraphExecutionPlan.build(jobGraph, null);
        assertNotNull(plan);
        assertFalse(plan.getSortedVertexIds().isEmpty());

        // Should produce same result as build(jobGraph)
        GraphExecutionPlan planDefault = GraphExecutionPlan.build(jobGraph);
        assertEquals(planDefault.getSortedVertexIds(), plan.getSortedVertexIds());
    }

    /**
     * Verify execute() works for a simple source-to-sink pipeline.
     */
    @Test
    public void testSimpleSourceSinkExecution() throws Exception {
        List<String> results = new ArrayList<>();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();
        env.fromElements("x", "y", "z").sink(results::add);
        env.execute("Simple Test");

        assertEquals(Arrays.asList("x", "y", "z"), results);
    }

    // ========== Helper Classes ==========

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

    private static class TestSinkFunction<T> implements io.nop.stream.core.common.functions.SinkFunction<T>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void consume(T value) throws Exception {
        }
    }

    private static class TestOperatorFactory<OUT> implements io.nop.stream.core.operators.StreamOperatorFactory<OUT>, Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final int parallelism;

        TestOperatorFactory(String name, int parallelism) {
            this.name = name;
            this.parallelism = parallelism;
        }

        @Override
        public io.nop.stream.core.operators.StreamOperator<OUT> createStreamOperator(TypeInformation<OUT> outputType) {
            return new TestOperator<>();
        }

        @Override
        public String getName() { return name; }

        @Override
        public int getParallelism() { return parallelism; }
    }

    private static class TestOperator<OUT> implements io.nop.stream.core.operators.StreamOperator<OUT>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void open() throws Exception {}

        @Override
        public void finish() throws Exception {}

        @Override
        public void close() throws Exception {}

        @Override
        public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {}

        @Override
        public void setKeyContextElement1(io.nop.stream.core.streamrecord.StreamRecord<?> record) throws Exception {}

        @Override
        public void setKeyContextElement2(io.nop.stream.core.streamrecord.StreamRecord<?> record) throws Exception {}

        @Override
        public void setCurrentKey(Object key) {}

        @Override
        public Object getCurrentKey() { return null; }

        @Override
        public void notifyCheckpointComplete(long checkpointId) throws Exception {}
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
}
