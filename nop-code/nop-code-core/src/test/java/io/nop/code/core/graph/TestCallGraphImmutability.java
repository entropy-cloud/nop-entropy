package io.nop.code.core.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCallGraphImmutability {

    @Test
    void testGetCalleesReturnsDefensiveCopy() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");

        List<String> callees = graph.getCallees("A");
        assertEquals(2, callees.size());
        callees.add("D");
        assertEquals(2, graph.getCallees("A").size());
    }

    @Test
    void testGetCallersReturnsDefensiveCopy() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "C");
        graph.addEdge("B", "C");

        List<String> callers = graph.getCallers("C");
        assertEquals(2, callers.size());
        callers.add("D");
        assertEquals(2, graph.getCallers("C").size());
    }

    @Test
    void testGetForwardMapReturnsUnmodifiableMap() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");

        Map<String, List<String>> forwardMap = graph.getForwardMap();
        assertThrows(UnsupportedOperationException.class, () -> forwardMap.put("X", List.of("Y")));
    }

    // WP-5 AR-148: the value lists must be unmodifiable AND isolated from later addEdge calls.
    @Test
    void testGetForwardMapValuesAreImmutableSnapshot() {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");

        Map<String, List<String>> forwardMap = graph.getForwardMap();
        List<String> callees = forwardMap.get("A");
        assertEquals(1, callees.size());
        assertThrows(UnsupportedOperationException.class, () -> callees.add("X"));

        graph.addEdge("A", "C");
        assertEquals(1, callees.size(), "snapshot value must not reflect later addEdge");
        assertEquals(2, graph.getForwardMap().get("A").size());
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
    void testEmptyNodeReturnsEmptyList() {
        CallGraph graph = new CallGraph();
        List<String> callees = graph.getCallees("nonexistent");
        assertTrue(callees.isEmpty());
    }
}
