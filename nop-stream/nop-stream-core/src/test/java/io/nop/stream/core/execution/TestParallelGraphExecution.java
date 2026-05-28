package io.nop.stream.core.execution;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan.EdgePlan;
import io.nop.stream.core.execution.plan.PartitionedPlan.VertexPlan;
import io.nop.stream.core.execution.plan.PartitionPolicy;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestParallelGraphExecution {

    private static OperatorChain testChain() {
        return new OperatorChain(Collections.singletonList(new StubOperator()));
    }

    private static JobVertex vertex(String id, int parallelism) {
        return new JobVertex(id, id, parallelism,
                Collections.singletonList(testChain()),
                (Invokable<Void>) () -> {});
    }

    private static JobEdge edge(String from, String to) {
        return new JobEdge(from, to, ResultPartitionType.PIPELINED);
    }

    private static JobEdge edge(String from, String to, IPartitioner<?> partitioner) {
        return new JobEdge(from, to, ResultPartitionType.PIPELINED_BOUNDED, partitioner);
    }

    // ---- Parallelism 1 backward compat ----

    @Test
    public void testParallelism1_backwardCompat() {
        JobGraph graph = new JobGraph("compat");
        graph.addVertex(vertex("source", 1));
        graph.addVertex(vertex("sink", 1));
        graph.addEdge(edge("source", "sink"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        // Legacy API still works
        assertEquals(1, plan.getSubtasks("source").size());
        assertEquals(1, plan.getSubtasks("sink").size());
        assertNotNull(plan.getInvokables().get("source"));
        assertNotNull(plan.getInvokables().get("sink"));
    }

    // ---- Parallelism 2: source (p=2) -> sink (p=2) FORWARD ----

    @Test
    public void testParallelism2_forward() throws Exception {
        JobGraph graph = new JobGraph("p2-forward");
        graph.addVertex(vertex("source", 2));
        graph.addVertex(vertex("sink", 2));
        graph.addEdge(edge("source", "sink"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        // 2 subtasks per vertex
        assertEquals(2, plan.getSubtasks("source").size());
        assertEquals(2, plan.getSubtasks("sink").size());

        // Each source subtask should have a RecordWriter with 2 partitions (targetParallelism)
        Subtask src0 = plan.getSubtasks("source").get(0);
        Subtask src1 = plan.getSubtasks("source").get(1);
        assertNotNull(src0.getInvokable().getOutputWriter());
        assertNotNull(src1.getInvokable().getOutputWriter());
        assertEquals(2, src0.getInvokable().getOutputWriter().getNumberOfPartitions());
        assertEquals(2, src1.getInvokable().getOutputWriter().getNumberOfPartitions());

        // Each sink subtask should have an InputGate with 2 channels (sourceParallelism)
        Subtask snk0 = plan.getSubtasks("sink").get(0);
        Subtask snk1 = plan.getSubtasks("sink").get(1);
        assertNotNull(snk0.getInvokable().getInputGate());
        assertNotNull(snk1.getInvokable().getInputGate());
        assertEquals(2, snk0.getInvokable().getInputGate().getNumberOfChannels());
        assertEquals(2, snk1.getInvokable().getInputGate().getNumberOfChannels());

        // Emit from source subtask 0, verify sink subtask 0 receives (FORWARD)
        RecordWriter<Object> writer0 = src0.getInvokable().getOutputWriter();
        writer0.emit(new StreamRecord<>("data-0"));

        // Close all writers to signal end-of-stream
        src0.getInvokable().getOutputWriter().close();
        src1.getInvokable().getOutputWriter().close();

        // Sink subtask 0 should receive the record via FORWARD routing
        int received = 0;
        while (snk0.getInvokable().getInputGate().read().isPresent()) {
            received++;
        }
        assertEquals(1, received, "sink[0] should receive 1 record from FORWARD routing");
    }

    // ---- Parallelism 2: source (p=2) -> sink (p=2) HASH ----

    @Test
    public void testParallelism2_hash() throws Exception {
        IPartitioner<String> keyPartitioner = (key, numPartitions) ->
                Math.abs(key.hashCode()) % numPartitions;

        JobGraph graph = new JobGraph("p2-hash");
        graph.addVertex(vertex("source", 2));
        graph.addVertex(vertex("sink", 2));
        graph.addEdge(edge("source", "sink", keyPartitioner));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        assertEquals(2, plan.getSubtasks("source").size());
        assertEquals(2, plan.getSubtasks("sink").size());

        // Each source subtask has 2 partitions (targetParallelism=2)
        Subtask src0 = plan.getSubtasks("source").get(0);
        assertEquals(2, src0.getInvokable().getOutputWriter().getNumberOfPartitions());

        // Emit records with known keys to check hash routing
        RecordWriter<Object> writer0 = src0.getInvokable().getOutputWriter();
        // Emit enough records to ensure at least some go to each partition
        for (int i = 0; i < 20; i++) {
            writer0.emit(new StreamRecord<>("key-" + i));
        }

        // Close all writers
        for (Subtask s : plan.getSubtasks("source")) {
            s.getInvokable().getOutputWriter().close();
        }

        // Both sink subtasks should receive records via HASH routing
        int totalReceived = 0;
        for (Subtask snk : plan.getSubtasks("sink")) {
            int count = 0;
            while (snk.getInvokable().getInputGate().read().isPresent()) {
                count++;
            }
            totalReceived += count;
        }
        assertEquals(20, totalReceived, "All 20 records should be received across sink subtasks");
    }

    // ---- Parallelism 2: source (p=2) -> sink (p=3) REBALANCE via DeploymentPlan ----

    @Test
    public void testParallelism2_rebalance() throws Exception {
        JobGraph graph = new JobGraph("p2-rebalance");
        graph.addVertex(vertex("source", 2));
        graph.addVertex(vertex("sink", 3));
        graph.addEdge(edge("source", "sink"));

        // Set REBALANCE partition policy via DeploymentPlan
        List<EdgePlan> edgePlans = Collections.singletonList(
                new EdgePlan("source", "sink", PartitionPolicy.REBALANCE));
        Map<String, VertexPlan> vertexPlans = new LinkedHashMap<>();
        vertexPlans.put("source", new VertexPlan("source", 2, "op-source"));
        vertexPlans.put("sink", new VertexPlan("sink", 3, "op-sink"));
        PartitionedPlan partitionedPlan = new PartitionedPlan(
                "p2-rebalance", "p-0", vertexPlans, edgePlans, Collections.emptySet(), null);
        DeploymentPlan deploymentPlan = new DeploymentPlan(
                "p2-rebalance", "p-0", partitionedPlan, "local", "memory", "local", null, null);

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph, deploymentPlan);

        assertEquals(2, plan.getSubtasks("source").size());
        assertEquals(3, plan.getSubtasks("sink").size());

        // Each source subtask has 3 partitions (targetParallelism=3)
        Subtask src0 = plan.getSubtasks("source").get(0);
        Subtask src1 = plan.getSubtasks("source").get(1);
        assertEquals(3, src0.getInvokable().getOutputWriter().getNumberOfPartitions());
        assertEquals(3, src1.getInvokable().getOutputWriter().getNumberOfPartitions());

        // Each sink subtask has 2 channels (sourceParallelism=2)
        for (Subtask snk : plan.getSubtasks("sink")) {
            assertEquals(2, snk.getInvokable().getInputGate().getNumberOfChannels());
        }

        // Emit 9 records from source[0] via REBALANCE (round-robin across 3 partitions)
        RecordWriter<Object> writer0 = src0.getInvokable().getOutputWriter();
        for (int i = 0; i < 9; i++) {
            writer0.emit(new StreamRecord<>("record-" + i));
        }

        // Close all writers
        for (Subtask s : plan.getSubtasks("source")) {
            s.getInvokable().getOutputWriter().close();
        }

        // Records from source[0] should be evenly distributed across 3 sink subtasks
        int[] counts = new int[3];
        for (int i = 0; i < 3; i++) {
            Subtask snk = plan.getSubtasks("sink").get(i);
            while (snk.getInvokable().getInputGate().read().isPresent()) {
                counts[i]++;
            }
        }
        // With round-robin starting at index 0: each partition should get 3 records
        assertEquals(3, counts[0], "sink[0] should receive 3 records from REBALANCE");
        assertEquals(3, counts[1], "sink[1] should receive 3 records from REBALANCE");
        assertEquals(3, counts[2], "sink[2] should receive 3 records from REBALANCE");
    }

    // ---- 3-vertex chain with mixed parallelism ----

    @Test
    public void testThreeVertexChain_mixedParallelism() throws Exception {
        JobGraph graph = new JobGraph("3-chain");
        graph.addVertex(vertex("source", 2));
        graph.addVertex(vertex("middle", 2));
        graph.addVertex(vertex("sink", 1));
        graph.addEdge(edge("source", "middle"));
        graph.addEdge(edge("middle", "sink"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        assertEquals(2, plan.getSubtasks("source").size());
        assertEquals(2, plan.getSubtasks("middle").size());
        assertEquals(1, plan.getSubtasks("sink").size());

        // Source -> Middle: source[0] has 2 partitions (middle parallelism=2)
        assertEquals(2, plan.getSubtasks("source").get(0).getInvokable()
                .getOutputWriter().getNumberOfPartitions());

        // Middle -> Sink: middle[0] has 1 partition (sink parallelism=1)
        assertEquals(1, plan.getSubtasks("middle").get(0).getInvokable()
                .getOutputWriter().getNumberOfPartitions());

        // Sink has 2 channels (from 2 middle subtasks)
        assertEquals(2, plan.getSubtasks("sink").get(0).getInvokable()
                .getInputGate().getNumberOfChannels());
    }

    // ---- TaskLocation has correct indices ----

    @Test
    public void testTaskLocations_correctIndices() {
        JobGraph graph = new JobGraph("locations");
        graph.addVertex(vertex("source", 3));
        graph.addVertex(vertex("sink", 2));
        graph.addEdge(edge("source", "sink"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        List<Subtask> srcSubtasks = plan.getSubtasks("source");
        assertEquals(3, srcSubtasks.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(i, srcSubtasks.get(i).getTaskIndex());
            assertEquals("source", srcSubtasks.get(i).getVertexId());
        }

        List<Subtask> snkSubtasks = plan.getSubtasks("sink");
        assertEquals(2, snkSubtasks.size());
        for (int i = 0; i < 2; i++) {
            assertEquals(i, snkSubtasks.get(i).getTaskIndex());
            assertEquals("sink", snkSubtasks.get(i).getVertexId());
        }
    }

    // ---- Diamond DAG with parallelism ----

    @Test
    public void testDiamondDAG_withParallelism() {
        JobGraph graph = new JobGraph("diamond-p");
        graph.addVertex(vertex("A", 2));
        graph.addVertex(vertex("B", 2));
        graph.addVertex(vertex("C", 2));
        graph.addVertex(vertex("D", 2));
        graph.addEdge(edge("A", "B"));
        graph.addEdge(edge("A", "C"));
        graph.addEdge(edge("B", "D"));
        graph.addEdge(edge("C", "D"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        List<String> sorted = plan.getSortedVertexIds();
        assertEquals(4, sorted.size());
        assertEquals("A", sorted.get(0));
        assertEquals("D", sorted.get(3));

        // A has 2 outgoing edges -> fan-out via multiple writers
        // The primary outputWriter holds partitions for one edge (2 partitions)
        // BroadcastingOutput wraps both writers for complete fan-out
        assertNotNull(plan.getSubtasks("A").get(0).getInvokable().getOutputWriter());
        assertTrue(plan.getSubtasks("A").get(0).getInvokable().getOutputWriter().getNumberOfPartitions() >= 2);

        // D has 2 incoming edges, each with 2 source subtasks -> 4 channels
        assertEquals(4, plan.getSubtasks("D").get(0).getInvokable()
                .getInputGate().getNumberOfChannels());
    }

    // ---- Self-contained vertex with parallelism ----

    @Test
    public void testSelfContained_withParallelism() {
        JobGraph graph = new JobGraph("self-contained-p");
        graph.addVertex(vertex("only", 3));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        assertEquals(3, plan.getSubtasks("only").size());
        for (Subtask subtask : plan.getSubtasks("only")) {
            assertNull(subtask.getInvokable().getOutputWriter());
            assertNull(subtask.getInvokable().getInputGate());
        }
    }

    // ---- Stub operator ----

    private static class StubOperator implements StreamOperator<Object> {

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
        public void setKeyContextElement1(StreamRecord<?> record) throws Exception {
        }

        @Override
        public void setKeyContextElement2(StreamRecord<?> record) throws Exception {
        }

        @Override
        public void notifyCheckpointComplete(long checkpointId) throws Exception {
        }

        @Override
        public void setCurrentKey(Object key) {
        }

        @Override
        public Object getCurrentKey() {
            return null;
        }
    }
}
