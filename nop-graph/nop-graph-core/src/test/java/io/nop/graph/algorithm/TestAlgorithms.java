package io.nop.graph.algorithm;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.nop.graph.api.ImpactConfig;
import io.nop.graph.api.ImpactResult;
import io.nop.graph.api.ImpactedNode;
import io.nop.graph.api.GraphDiff;
import io.nop.graph.api.BfsResult;
import io.nop.graph.impl.InMemoryGraph;

import static org.junit.jupiter.api.Assertions.*;

class TestAlgorithms {

    // ==================== BFS Tests ====================

    @Test
    void testBfsDepth1() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("a", "c");
        g.addEdge("b", "d");
        g.addEdge("c", "e");

        Set<String> result = Bfs.traverse(g, "a", 1);
        assertEquals(2, result.size());
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    @Test
    void testBfsDepth2() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("a", "c");
        g.addEdge("b", "d");
        g.addEdge("c", "e");

        Set<String> result = Bfs.traverse(g, "a", 2);
        assertEquals(4, result.size());
    }

    @Test
    void testBfsWithDepth() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("b", "c");

        BfsResult result = Bfs.traverseWithDepth(g, "a", 5);
        assertEquals(0, result.getDepth("a"));
        assertEquals(1, result.getDepth("b"));
        assertEquals(2, result.getDepth("c"));
    }

    @Test
    void testBfsFiltered() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("a", "c");
        g.addEdge("b", "d");

        Set<String> result = Bfs.traverseFiltered(g, "a", 5, node -> !node.equals("b"));
        assertTrue(result.contains("c"));
        assertFalse(result.contains("d"));
    }

    // ==================== TarjanSCC Tests ====================

    @Test
    void testTarjanSCCWithCycle() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("b", "c");
        g.addEdge("c", "a");
        g.addEdge("d", "e");

        Set<String> nodes = new HashSet<>(Set.of("a", "b", "c", "d", "e"));
        List<Set<String>> sccs = TarjanSCC.compute(g, nodes);

        assertEquals(3, sccs.size());
        Set<String> cycleScc = sccs.stream().filter(s -> s.size() == 3).findFirst().orElse(null);
        assertNotNull(cycleScc);
        assertTrue(cycleScc.contains("a"));
        assertTrue(cycleScc.contains("b"));
        assertTrue(cycleScc.contains("c"));
    }

    @Test
    void testTarjanSCCNoCycle() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("b", "c");

        Set<String> nodes = new HashSet<>(Set.of("a", "b", "c"));
        List<Set<String>> sccs = TarjanSCC.compute(g, nodes);

        assertEquals(3, sccs.size());
    }

    // ==================== TopologicalSort Tests ====================

    @Test
    void testTopologicalSortDAG() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("a", "c");
        g.addEdge("b", "c");

        Set<String> nodes = new HashSet<>(Set.of("a", "b", "c"));
        List<String> sorted = TopologicalSort.sort(g, nodes);

        assertEquals(3, sorted.size());
        int idxA = sorted.indexOf("a");
        int idxB = sorted.indexOf("b");
        int idxC = sorted.indexOf("c");
        assertTrue(idxA < idxB);
        assertTrue(idxA < idxC);
        assertTrue(idxB < idxC);
    }

    @Test
    void testTopologicalSortCycleThrows() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("b", "a");

        Set<String> nodes = new HashSet<>(Set.of("a", "b"));
        assertThrows(IllegalArgumentException.class, () -> TopologicalSort.sort(g, nodes));
    }

    // ==================== PageRank Tests ====================

    @Test
    void testPageRankStarGraph() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("leaf1", "center");
        g.addEdge("leaf2", "center");
        g.addEdge("leaf3", "center");
        g.addEdge("leaf4", "center");

        Set<String> nodes = new HashSet<>(Set.of("center", "leaf1", "leaf2", "leaf3", "leaf4"));
        Map<String, Double> ranks = PageRank.compute(g, nodes, 20);

        assertTrue(ranks.get("center") > ranks.get("leaf1"));
        assertTrue(ranks.get("center") > ranks.get("leaf2"));
        assertTrue(ranks.get("center") > ranks.get("leaf3"));
        assertTrue(ranks.get("center") > ranks.get("leaf4"));
    }

    // ==================== ImpactPropagator Tests ====================

    @Test
    void testImpactPropagatorBidirectional() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("caller", "target");
        g.addEdge("target", "callee1");
        g.addEdge("target", "callee2");
        g.addEdge("callee1", "callee3");

        ImpactConfig config = ImpactConfig.create().setMaxDepth(3).setMaxNodes(100);
        ImpactResult result = ImpactPropagator.propagate(g, "target", config);

        assertEquals("target", result.getTargetNodeId());
        assertTrue(result.getUpstream().size() >= 1);
        assertTrue(result.getDownstream().size() >= 2);

        boolean hasCaller = result.getUpstream().stream().anyMatch(n -> n.getNodeId().equals("caller"));
        assertTrue(hasCaller);

        boolean hasCallee1 = result.getDownstream().stream().anyMatch(n -> n.getNodeId().equals("callee1"));
        assertTrue(hasCallee1);
    }

    @Test
    void testImpactPropagatorMaxNodes() {
        InMemoryGraph g = new InMemoryGraph();
        for (int i = 0; i < 10; i++) {
            g.addEdge("start", "node" + i);
        }

        ImpactConfig config = ImpactConfig.create().setMaxDepth(5).setMaxNodes(3);
        ImpactResult result = ImpactPropagator.propagate(g, "start", config);

        assertTrue(result.getTotalImpacted() <= 3);
    }

    @Test
    void testImpactPropagatorRiskLevel() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("start", "a");
        g.addEdge("a", "b");
        g.addEdge("b", "c");
        g.addEdge("c", "d");
        g.addEdge("d", "e");
        g.addEdge("e", "f");

        ImpactConfig config = ImpactConfig.create().setMaxDepth(10).setMaxNodes(100);
        ImpactResult result = ImpactPropagator.propagate(g, "start", config);

        assertTrue(result.getDownstream().size() >= 5);
        assertNotEquals("low", result.getRiskLevel());
    }

    // ==================== GraphDiffer Tests ====================

    @Test
    void testGraphDiffer() {
        InMemoryGraph baseline = new InMemoryGraph();
        baseline.addEdge("a", "b");
        baseline.addEdge("b", "c");

        InMemoryGraph target = new InMemoryGraph();
        target.addEdge("a", "b");
        target.addEdge("b", "c");
        target.addEdge("c", "d");
        target.addEdge("x", "y");

        Set<String> baselineNodes = new HashSet<>(Set.of("a", "b", "c"));
        Set<String> targetNodes = new HashSet<>(Set.of("a", "b", "c", "d", "x", "y"));

        GraphDiff diff = GraphDiffer.diff(baseline, baselineNodes, target, targetNodes);

        assertEquals(3, diff.getAddedNodes().size());
        assertTrue(diff.getAddedNodes().contains("d"));
        assertTrue(diff.getAddedNodes().contains("x"));
        assertTrue(diff.getAddedNodes().contains("y"));
        assertTrue(diff.getRemovedNodes().isEmpty());
    }

    // ==================== InMemoryGraph Tests ====================

    @Test
    void testInMemoryGraph() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b", 1.0, "CALLS");
        g.addEdge("a", "c", 0.5, "EXTENDS");
        g.addEdge("b", "c");
        g.addNode("d");

        assertEquals(4, g.getNodeCount());
        assertEquals(3, g.getEdgeCount());

        assertEquals(2, g.getOutEdges("a").size());
        assertEquals(2, g.getInEdges("c").size());
        assertEquals(0, g.getOutEdges("d").size());

        assertTrue(g.nodeSet().contains("d"));
    }

    @Test
    void testInMemoryGraphEmptyResults() {
        InMemoryGraph g = new InMemoryGraph();
        g.addNode("a");

        assertTrue(g.getOutEdges("nonexistent").isEmpty());
        assertTrue(g.getInEdges("nonexistent").isEmpty());
        assertTrue(g.getOutEdges("a").isEmpty());
    }

    // ==================== End-to-End Test ====================

    @Test
    void testEndToEnd() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("A", "B", 1.0, "CALLS");
        g.addEdge("B", "C", 1.0, "CALLS");
        g.addEdge("C", "D", 1.0, "CALLS");
        g.addEdge("X", "B", 1.0, "CALLS");

        ImpactResult impact = ImpactPropagator.propagate(g, "B",
                ImpactConfig.create().setMaxDepth(3).setMaxNodes(100));

        assertTrue(impact.getUpstream().stream().anyMatch(n -> n.getNodeId().equals("X")));
        assertTrue(impact.getDownstream().stream().anyMatch(n -> n.getNodeId().equals("C")));
        assertTrue(impact.getDownstream().stream().anyMatch(n -> n.getNodeId().equals("D")));

        BfsResult bfs = Bfs.traverseWithDepth(g, "B", 5);
        assertEquals(1, bfs.getDepth("C"));
        assertEquals(2, bfs.getDepth("D"));

        Map<String, Double> ranks = PageRank.compute(g, g.nodeSet(), 20);
        assertNotNull(ranks);
        assertEquals(5, ranks.size());
    }

    // ==================== Empty Input Tests ====================

    @Test
    void testEmptyGraphThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> TarjanSCC.compute(new InMemoryGraph(), new HashSet<>()));

        assertThrows(IllegalArgumentException.class,
                () -> TopologicalSort.sort(new InMemoryGraph(), new HashSet<>()));

        assertThrows(IllegalArgumentException.class,
                () -> PageRank.compute(new InMemoryGraph(), new HashSet<>(), 10));

        assertThrows(IllegalArgumentException.class,
                () -> ImpactPropagator.propagate(new InMemoryGraph(), "x", null));
    }
}
