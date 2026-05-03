package io.nop.code.core.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCallGraph {
    @Test
    void testAddEdgeCreatesForwardAndReverse() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "C");

        assertEquals(2, graph.getCallees("A").size());
        assertEquals(1, graph.getCallees("B").size());
        assertEquals(2, graph.getCallers("C").size());
        assertEquals(1, graph.getCallers("B").size());
    }

    @Test
    void testGetCalleesReturnsCorrectList() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        assertTrue(graph.getCallees("A").contains("B"));
        assertTrue(graph.getCallees("A").contains("C"));
    }

    @Test
    void testGetCallersReturnsReverse() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "C");
        graph.addEdge("B", "C");
        assertTrue(graph.getCallers("C").contains("A"));
        assertTrue(graph.getCallers("C").contains("B"));
    }

    @Test
    void testGetAllNodeIds() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        assertEquals(3, graph.getAllNodeIds().size());
        assertTrue(graph.getAllNodeIds().contains("A"));
        assertTrue(graph.getAllNodeIds().contains("B"));
        assertTrue(graph.getAllNodeIds().contains("C"));
    }

    @Test
    void testEmptyGraphReturnsEmptyCollections() {
        CallGraph graph = new CallGraph();
        assertTrue(graph.getCallees("nonexistent").isEmpty());
        assertTrue(graph.getCallers("nonexistent").isEmpty());
        assertTrue(graph.getAllNodeIds().isEmpty());
    }
}
