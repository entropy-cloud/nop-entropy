/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.flow.FlowControlPolicy;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.StreamOperator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * WI21 (§八 14): every edge of a materialized execution plan must end up with a
 * resolved {@link EdgeConfig} — declared configs win, undeclared ones fall back
 * to the documented defaults (WI6 adjudication: undeclared→default is legal, so
 * the invariant's substance here is "no edge is left unconfigured or
 * inconsistently configured", enforced by the gate build's consistency fail-fast
 * — see {@code TestMultiEdgeGateConfigConsistency} for the conflict matrix and
 * {@code RemoteGraphExecutionPlanBuilder} (runtime) for the remote-side mirror).
 * This test pins the local-side resolution chain: JobEdge carrier →
 * DeploymentPlan map → gate build.
 */
public class TestDistributedEdgeEdgeConfigCoverage {

    private static OperatorChain testChain() {
        return new OperatorChain(Collections.singletonList(new StubOperator()));
    }

    private static JobVertex vertex(String id) {
        return new JobVertex(id, id, 1,
                Collections.singletonList(testChain()),
                (Invokable<Void>) () -> {
                });
    }

    @Test
    public void declaredEdgeConfigSurvivesResolution() {
        // priority 1: the JobEdge carrier — a declared config must come back
        // resolved, not silently replaced
        EdgeConfig declared = new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 777, 333, 4096);
        JobEdge edge = new JobEdge("A", "B", io.nop.stream.core.jobgraph.ResultPartitionType.PIPELINED);
        edge.setEdgeConfig(declared);

        EdgeConfig resolved = EdgeAssembly.resolveEdgeConfig(edge, null);
        assertNotNull(resolved, "declared config must resolve");
        assertEquals(777, resolved.getQueueCapacity());
        assertEquals(333, resolved.getReceiveWindow());
    }

    @Test
    public void deploymentPlanConfigResolvedByEdgeKey() {
        // priority 2: the DeploymentPlan edgeConfigs map, keyed "source->target"
        EdgeConfig declared = new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 888, 222, 2048);
        JobEdge edge = new JobEdge("A", "B", io.nop.stream.core.jobgraph.ResultPartitionType.PIPELINED);
        Map<String, EdgeConfig> configs = Map.of("A->B", declared);
        DeploymentPlan deploymentPlan =
                new io.nop.stream.core.execution.plan.DeploymentPlan("job", "pipeline-0", null,
                        "local", "memory", "local", configs, null);

        EdgeConfig resolved = EdgeAssembly.resolveEdgeConfig(edge, deploymentPlan);
        assertNotNull(resolved, "deployment-plan config must resolve by edge key");
        assertEquals(888, resolved.getQueueCapacity());
    }

    @Test
    public void everyTaskOfMaterializedPlanGetsAGate() {
        // the plan-level guarantee: a linear pipeline materializes with gates on
        // every consuming task even when no edge declares anything (defaults) —
        // no edge is left without flow-control configuration
        JobGraph graph = new JobGraph("wi21-edge-coverage");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));
        graph.addEdge(new JobEdge("A", "B", io.nop.stream.core.jobgraph.ResultPartitionType.PIPELINED));
        graph.addEdge(new JobEdge("B", "C", io.nop.stream.core.jobgraph.ResultPartitionType.PIPELINED));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph, null, true);
        assertNotNull(plan.getInvokables().get("B").getInputGate(),
                "middle task must have a resolved (default) input gate");
        assertNotNull(plan.getInvokables().get("C").getInputGate(),
                "terminal task must have a resolved (default) input gate");
    }

    /** minimal operator stub (same shape as TestMultiEdgeGateConfigConsistency). */
    @io.nop.stream.core.operators.Shareable
    public static class StubOperator implements StreamOperator<Object> {
        @Override public void open() throws Exception { }
        @Override public void finish() throws Exception { }
        @Override public void close() throws Exception { }
        @Override public void prepareSnapshotPreBarrier(long checkpointId) throws Exception { }
        @Override public void setKeyContextElement1(io.nop.stream.core.streamrecord.StreamRecord<?> record) throws Exception { }
        @Override public void setKeyContextElement2(io.nop.stream.core.streamrecord.StreamRecord<?> record) throws Exception { }
        @Override public void notifyCheckpointComplete(long checkpointId) throws Exception { }
        @Override public void setCurrentKey(Object key) { }
        @Override public Object getCurrentKey() { return null; }
    }
}
