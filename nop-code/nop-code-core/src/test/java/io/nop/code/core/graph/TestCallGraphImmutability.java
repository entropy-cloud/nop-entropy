package io.nop.code.core.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCallGraphImmutability {

    @Test
    void testGetCalleesReturnsUnmodifiableList() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");

        List<String> callees = graph.getCallees("A");
        assertEquals(2, callees.size());
        assertThrows(UnsupportedOperationException.class, () -> callees.add("D"));
    }

    @Test
    void testGetCallersReturnsUnmodifiableList() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "C");
        graph.addEdge("B", "C");

        List<String> callers = graph.getCallers("C");
        assertEquals(2, callers.size());
        assertThrows(UnsupportedOperationException.class, () -> callers.add("D"));
    }

    @Test
    void testGetForwardMapReturnsUnmodifiableMap() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");

        Map<String, List<String>> forwardMap = graph.getForwardMap();
        assertThrows(UnsupportedOperationException.class, () -> forwardMap.put("X", List.of("Y")));
    }

    @Test
    void testAddEdgeDeduplication() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");
        graph.addEdge("A", "B");
        graph.addEdge("A", "B");

        assertEquals(1, graph.getCallees("A").size());
        assertEquals(1, graph.getCallers("B").size());
    }

    @Test
    void testDifferentEdgesNotDeduplicated() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "C");

        assertEquals(2, graph.getCallees("A").size());
        assertEquals(2, graph.getCallers("C").size());
    }

    @Test
    void testEmptyNodeReturnsUnmodifiableEmptyList() {
        CallGraph graph = new CallGraph();
        List<String> callees = graph.getCallees("nonexistent");
        assertThrows(UnsupportedOperationException.class, () -> callees.add("X"));
    }
}
