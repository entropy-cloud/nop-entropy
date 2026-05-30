package io.nop.stream.core.execution;

import io.nop.commons.partition.IPartitioner;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestGraphExecutionPlan {

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

    @Test
    public void testTopologicalSortLinearChain() {
        JobGraph graph = new JobGraph("linear-chain");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));
        graph.addEdge(edge("A", "B"));
        graph.addEdge(edge("B", "C"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);
        List<String> sorted = plan.getSortedVertexIds();

        assertEquals(3, sorted.size());
        assertEquals("A", sorted.get(0));
        assertEquals("B", sorted.get(1));
        assertEquals("C", sorted.get(2));
    }

    @Test
    public void testTopologicalSortDiamondDAG() {
        JobGraph graph = new JobGraph("diamond");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));
        graph.addVertex(vertex("D"));
        graph.addEdge(edge("A", "B"));
        graph.addEdge(edge("A", "C"));
        graph.addEdge(edge("B", "D"));
        graph.addEdge(edge("C", "D"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);
        List<String> sorted = plan.getSortedVertexIds();

        assertEquals(4, sorted.size(), "All 4 vertices should appear in sorted order");
        assertEquals("A", sorted.get(0), "A (source) should be first");
        assertEquals("D", sorted.get(3), "D (sink) should be last");
        assertTrue(sorted.indexOf("B") < sorted.indexOf("D"));
        assertTrue(sorted.indexOf("C") < sorted.indexOf("D"));
    }

    @Test
    public void testSingleVertexNoEdges() {
        JobGraph graph = new JobGraph("single");
        graph.addVertex(vertex("only"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);
        List<String> sorted = plan.getSortedVertexIds();

        assertEquals(1, sorted.size());
        assertEquals("only", sorted.get(0));
    }

    @Test
    public void testCyclicGraphThrowsException() {
        JobGraph graph = new JobGraph("cycle");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addEdge(edge("A", "B"));
        graph.addEdge(edge("B", "A"));

        StreamException ex = assertThrows(StreamException.class,
                () -> GraphExecutionPlan.build(graph),
                "Cyclic graph should throw StreamException");
        assertTrue(ex.getMessage().contains("A") || ex.getMessage().contains("B"),
                "Exception message should contain cyclic vertex IDs");
    }

    @Test
    public void testExecutionVerticesPreserved() {
        JobGraph graph = new JobGraph("vertices-test");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addEdge(edge("A", "B"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);
        Map<String, ?> vertices = plan.getExecutionVertices();

        assertEquals(2, vertices.size());
        assertTrue(vertices.containsKey("A"));
        assertTrue(vertices.containsKey("B"));
    }

    @Test
    public void testInvokablesCreated() {
        JobGraph graph = new JobGraph("invokables-test");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addEdge(edge("A", "B"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);
        Map<String, StreamTaskInvokable> invokables = plan.getInvokables();

        assertEquals(2, invokables.size());
        assertNotNull(invokables.get("A"));
        assertNotNull(invokables.get("B"));
    }

    @Test
    public void testPartitionerWiredThroughJobEdge() throws Exception {
        IPartitioner<String> partitioner = (key, numPartitions) ->
                Math.abs(key.hashCode()) % numPartitions;

        JobGraph graph = new JobGraph("partitioner-test");
        graph.addVertex(vertex("source"));
        graph.addVertex(vertex("sink"));

        JobEdge edgeWithPartitioner = new JobEdge("source", "sink",
                ResultPartitionType.PIPELINED_BOUNDED, partitioner);
        graph.addEdge(edgeWithPartitioner);

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);

        StreamTaskInvokable sourceInvokable = plan.getInvokables().get("source");
        assertNotNull(sourceInvokable, "source invokable should exist");

        RecordWriter<Object> writer = sourceInvokable.getOutputWriter();
        assertNotNull(writer, "source should have a RecordWriter");

        writer.emit(new StreamRecord<>("test-key-1"));
        writer.emit(new StreamRecord<>("test-key-2"));
        writer.close();

        StreamTaskInvokable sinkInvokable = plan.getInvokables().get("sink");
        assertNotNull(sinkInvokable);
        InputGate inputGate = sinkInvokable.getInputGate();
        assertNotNull(inputGate, "sink should have an InputGate");

        int received = 0;
        while (inputGate.read().isPresent()) {
            received++;
        }
        assertEquals(2, received, "sink should receive both records");
    }

    @Test
    public void testTopologicalSortDetectsCycle() {
        JobGraph graph = new JobGraph("cycle-abc");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addVertex(vertex("C"));
        graph.addEdge(edge("A", "B"));
        graph.addEdge(edge("B", "C"));
        graph.addEdge(edge("C", "A"));

        StreamException ex = assertThrows(StreamException.class,
                () -> GraphExecutionPlan.build(graph),
                "Cyclic graph A->B->C->A should throw StreamException");
        String msg = ex.getMessage();
        assertTrue(msg.contains("A") || msg.contains("B") || msg.contains("C"),
                "Exception should mention cyclic vertex IDs");
    }

    @Test
    public void testTopologicalSortAcceptsDAG() {
        JobGraph graph = new JobGraph("dag-ok");
        graph.addVertex(vertex("S"));
        graph.addVertex(vertex("M1"));
        graph.addVertex(vertex("M2"));
        graph.addVertex(vertex("E"));
        graph.addEdge(edge("S", "M1"));
        graph.addEdge(edge("S", "M2"));
        graph.addEdge(edge("M1", "E"));
        graph.addEdge(edge("M2", "E"));

        GraphExecutionPlan plan = GraphExecutionPlan.build(graph);
        List<String> sorted = plan.getSortedVertexIds();

        assertEquals(4, sorted.size());
        assertEquals("S", sorted.get(0));
        assertEquals("E", sorted.get(3));
    }

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
