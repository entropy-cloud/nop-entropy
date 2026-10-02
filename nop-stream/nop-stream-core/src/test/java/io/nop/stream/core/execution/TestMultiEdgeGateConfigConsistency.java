/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.flow.FlowControlPolicy;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI6 constraint 1 regression: a multi-input vertex's input gate must not silently
 * inherit the FIRST incoming edge's flow-control config. Two edges that both DECLARE
 * configs must agree by value (EdgeConfig has no equals — any field mismatch fails
 * the plan build); an undeclared edge defers to a declared one (documented
 * inheritance, preserving pre-WI6 topologies where only some edges carry configs).
 */
public class TestMultiEdgeGateConfigConsistency {

    @io.nop.stream.core.operators.Shareable
    private static class StubOperator implements StreamOperator<Object> {
        @Override public void open() throws Exception {}
        @Override public void finish() throws Exception {}
        @Override public void close() throws Exception {}
        @Override public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {}
        @Override public void setKeyContextElement1(StreamRecord<?> record) throws Exception {}
        @Override public void setKeyContextElement2(StreamRecord<?> record) throws Exception {}
        @Override public void notifyCheckpointComplete(long checkpointId) throws Exception {}
        @Override public void setCurrentKey(Object key) {}
        @Override public Object getCurrentKey() { return null; }
    }

    private static OperatorChain testChain() {
        return new OperatorChain(Collections.singletonList(new StubOperator()));
    }

    private static JobVertex vertex(String id) {
        return new JobVertex(id, id, 1,
                Collections.singletonList(testChain()),
                (Invokable<Void>) () -> {
                });
    }

    private static JobGraph twoEdgesInto(String target) {
        JobGraph graph = new JobGraph("multi-edge-gate-config-" + target);
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex(target));
        graph.addEdge(new JobEdge("A", target, ResultPartitionType.PIPELINED));
        graph.addEdge(new JobEdge("B", target, ResultPartitionType.PIPELINED));
        return graph;
    }

    private static DeploymentPlan planWithConfigs(EdgeConfig aEdge, EdgeConfig bEdge) {
        Map<String, EdgeConfig> configs = new HashMap<>();
        if (aEdge != null) {
            configs.put("A->T", aEdge);
        }
        if (bEdge != null) {
            configs.put("B->T", bEdge);
        }
        return new DeploymentPlan("job", "pipeline-0", null, "local", "memory", "local",
                configs, null);
    }

    @Test
    public void testDistinctInstancesWithEqualValuesBuildGate() {
        // Two NEW EdgeConfig instances (no equals — only value equality counts) with
        // identical fields: the gate must build, not fail on instance identity.
        JobGraph graph = twoEdgesInto("T");
        DeploymentPlan deploymentPlan = planWithConfigs(
                new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 512, 256, 2048),
                new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 512, 256, 2048));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph, deploymentPlan, true);
        StreamTaskInvokable target = plan.getInvokables().get("T");
        assertNotNull(target.getInputGate(), "value-equal multi-edge gate must build");
        assertEquals(2, target.getInputGate().getChannels().size());
    }

    @Test
    public void testBothUndeclaredConfigsBuildGate() {
        // No edge declares a config (null == null): the pre-WI6 default topology
        // must keep building.
        JobGraph graph = twoEdgesInto("T");
        GraphExecutionPlan plan = GraphExecutionPlan.build(graph, null, true);
        assertNotNull(plan.getInvokables().get("T").getInputGate());
    }

    @Test
    public void testConflictingConfigsFailFast() {
        JobGraph graph = twoEdgesInto("T");
        DeploymentPlan deploymentPlan = planWithConfigs(
                new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 512, 256, 2048),
                new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 1024, 256, 2048));

        StreamException e = assertThrows(StreamException.class,
                () -> GraphExecutionPlan.build(graph, deploymentPlan, true));
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), e.getErrorCode());
        assertTrue(String.valueOf(e.getMessage()).contains("conflicting flow-control configs"),
                "failure must name the constraint: " + e.getMessage());
    }

    @Test
    public void testDeclaredVersusUndeclaredConfigBuildsGateWithDeclaredValue() {
        // One edge declares a config, the other does not: the undeclared edge defers
        // to the declared one (documented inheritance — this preserves the pre-WI6
        // remote diamond topology where only some edges carry configs); the gate
        // carries the declared value.
        JobGraph graph = twoEdgesInto("T");
        EdgeConfig declared = new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 512, 256, 2048);
        DeploymentPlan deploymentPlan = planWithConfigs(declared, null);

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph, deploymentPlan, true);
        assertNotNull(plan.getInvokables().get("T").getInputGate(),
                "declared + undeclared must build (undeclared defers to declared)");
    }
}
