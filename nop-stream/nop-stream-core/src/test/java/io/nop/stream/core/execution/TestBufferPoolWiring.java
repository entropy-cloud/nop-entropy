package io.nop.stream.core.execution;

import io.nop.stream.core.execution.buffer.BufferPool;
import io.nop.stream.core.execution.buffer.IBufferPool;
import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.flow.FlowControlPolicy;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wiring & EdgeConfig tests for the {@link IBufferPool} integration into
 * {@link GraphExecutionPlan} (G53 production build() path).
 *
 * <p>Verifies (#23 wiring verification):
 * <ul>
 *   <li>old build() overloads self-create a per-build pool (backward compat)</li>
 *   <li>the new build() overload shares one pool across all partitions in the plan</li>
 *   <li>{@link EdgeConfig#getQueueCapacity()} is wired into per-partition capacity</li>
 *   <li>{@link GraphExecutionPlan#create(...)} stays pool-free (Remote path)</li>
 *   <li>E2E: building with a small pool, partitions share it and a producer beyond
 *       the global bound blocks (backpressure observable end-to-end through build)</li>
 * </ul>
 */
public class TestBufferPoolWiring {

    private static OperatorChain testChain() {
        return new OperatorChain(Collections.singletonList(new StubOperator()));
    }

    private static JobVertex vertex(String id) {
        return new JobVertex(id, id, 1,
                Collections.singletonList(testChain()),
                (Invokable<Void>) () -> {});
    }

    private static JobEdge edge(String from, String to) {
        return new JobEdge(from, to, ResultPartitionType.PIPELINED);
    }

    private static ResultPartition firstPartitionOf(GraphExecutionPlan plan, String sourceVertexId) {
        StreamTaskInvokable inv = plan.getInvokables().get(sourceVertexId);
        assertNotNull(inv, "source invokable must exist: " + sourceVertexId);
        RecordWriter<Object> writer = inv.getOutputWriter();
        assertNotNull(writer, "source must have a RecordWriter");
        ResultPartition[] partitions = writer.getPartitions();
        assertTrue(partitions.length > 0, "source writer must have >=1 partition");
        return partitions[0];
    }

    @Test
    public void testOldOverloadSelfCreatesPerBuildPool() {
        JobGraph graph = new JobGraph("old-overload");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addEdge(edge("A", "B"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        IBufferPool pool = plan.getBufferPool();
        assertNotNull(pool, "old build() overload must self-create a per-build pool");
        assertEquals(GraphExecutionPlan.DEFAULT_GLOBAL_BUFFER_CAPACITY, pool.getGlobalTotalCapacity());

        // The partition actually references the same pool (wiring is live, not a stub)
        ResultPartition partition = firstPartitionOf(plan, "A");
        assertSame(pool, partition.getBufferPool(),
                "partition must consume the self-created pool (not a private hardcoded queue)");
    }

    @Test
    public void testExplicitPoolSharedAcrossPartitions() {
        // Diamond: A -> B, A -> C ; two outgoing edges => two distinct partitions,
        // both must reference the same injected pool instance (per-job single instance).
        JobGraph graph = new JobGraph("diamond-shared-pool");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));
        graph.addEdge(edge("A", "B"));
        graph.addEdge(edge("A", "C"));

        BufferPool pool = new BufferPool(16);
        GraphExecutionPlan plan = GraphExecutionPlan.build(
                graph, null, true, 0L, pool);

        assertSame(pool, plan.getBufferPool());

        // Every partition across every edge must reference the single injected pool
        for (Map.Entry<String, List<Subtask>> entry : plan.getSubtasks().entrySet()) {
            for (Subtask subtask : entry.getValue()) {
                RecordWriter<Object> writer = subtask.getInvokable().getOutputWriter();
                if (writer == null) {
                    continue;
                }
                for (ResultPartition p : writer.getPartitions()) {
                    assertSame(pool, p.getBufferPool(),
                            "every production-built partition must share the per-job pool: vertex=" + entry.getKey());
                }
            }
        }

        plan.closeBufferPool();
    }

    @Test
    public void testEdgeConfigQueueCapacityAffectsPartitionCapacity() {
        JobGraph graph = new JobGraph("edge-config-capacity");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));

        JobEdge e = new JobEdge("A", "B", ResultPartitionType.PIPELINED);
        e.setEdgeConfig(new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 10, 1024, 4096));
        graph.addEdge(e);

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        ResultPartition partition = firstPartitionOf(plan, "A");
        assertEquals(10, partition.getTotalCapacity(),
                "EdgeConfig.queueCapacity=10 must wire into the partition capacity (not the 1024 default)");

        plan.closeBufferPool();
    }

    @Test
    public void testEdgeConfigFromDeploymentPlanAlsoWired() {
        JobGraph graph = new JobGraph("edge-config-deployment");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addEdge(edge("A", "B"));

        Map<String, EdgeConfig> edgeConfigs = new HashMap<>();
        edgeConfigs.put("A->B", new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 25, 1024, 4096));
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                null, null, null, "local", "memory", "local", edgeConfigs, null);

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph, deploymentPlan, true);

        ResultPartition partition = firstPartitionOf(plan, "A");
        assertEquals(25, partition.getTotalCapacity(),
                "EdgeConfig resolved from DeploymentPlan must wire into partition capacity");

        plan.closeBufferPool();
    }

    @Test
    public void testCreatePathHasNoPool() {
        // GraphExecutionPlan.create(...) is used by runtime builders that supply their
        // own partitions (e.g. Remote). It must not attach a pool.
        GraphExecutionPlan plan = GraphExecutionPlan.create(
                Collections.singletonList("V"),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap());
        assertNull(plan.getBufferPool(), "create() path must remain pool-free (Remote exclusion)");
    }

    @Test
    public void testExplicitNullPoolRejected() {
        JobGraph graph = new JobGraph("null-pool");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addEdge(edge("A", "B"));

        // The explicit-pool overload must reject null (fail-fast, no silent legacy fallback
        // when the caller clearly intended to pass a pool — #24 no silent no-op).
        assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                () -> GraphExecutionPlan.build(graph, null, true, 0L, null));
    }

    @Test
    public void testE2eBackpressureWhenGlobalPoolExhausted() throws Exception {
        // End-to-end through GraphExecutionPlan.build(): a small pool + fan-out of
        // partitions => producer beyond the GLOBAL bound blocks until a consumer frees.
        JobGraph graph = new JobGraph("e2e-backpressure");
        graph.addVertex(vertex("src"));
        graph.addVertex(vertex("mid"));
        graph.addEdge(edge("src", "mid"));

        BufferPool pool = new BufferPool(2);
        GraphExecutionPlan plan = GraphExecutionPlan.build(graph, null, true, 0L, pool);

        RecordWriter<Object> srcWriter = plan.getInvokables().get("src").getOutputWriter();
        assertNotNull(srcWriter);
        ResultPartition partition = srcWriter.getPartitions()[0];
        assertSame(pool, partition.getBufferPool());

        // Fill the global pool (2 permits)
        partition.write(new StreamRecord<>(1));
        partition.write(new StreamRecord<>(2));
        assertTrue(pool.isGlobalBackpressured());
        assertEquals(2, pool.getGlobalUsage());

        // A third write must block on the global pool (plenty of per-partition room left)
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            try {
                blocked.countDown();
                partition.write(new StreamRecord<>(3));
                done.countDown();
            } catch (Throwable t) {
                error.set(t);
                done.countDown();
            }
        });
        producer.start();
        assertTrue(blocked.await(2, TimeUnit.SECONDS));
        assertFalse(done.await(500, TimeUnit.MILLISECONDS),
                "producer must block once the global pool is exhausted");

        // Consume one element -> one global permit freed -> blocked producer proceeds
        StreamElement consumed = partition.read();
        assertNotNull(consumed);
        assertTrue(done.await(2, TimeUnit.SECONDS),
                "blocked producer must proceed once a global permit frees");
        assertNull(error.get());
        assertEquals(2, pool.getGlobalUsage());

        producer.join(2000);
        plan.closeBufferPool();
        assertTrue(pool.isClosed(), "closeBufferPool must close the per-job pool");
    }

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
}
