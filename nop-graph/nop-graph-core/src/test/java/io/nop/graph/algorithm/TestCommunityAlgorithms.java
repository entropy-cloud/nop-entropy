package io.nop.graph.algorithm;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.nop.graph.api.CommunityResult;
import io.nop.graph.api.LeidenConfig;
import io.nop.graph.impl.InMemoryGraph;

import static org.junit.jupiter.api.Assertions.*;

class TestCommunityAlgorithms {

    private InMemoryGraph buildTwoCommunityGraph() {
        InMemoryGraph g = new InMemoryGraph();
        // Community 1: a-b-c-d (dense)
        g.addEdge("a", "b");
        g.addEdge("b", "a");
        g.addEdge("b", "c");
        g.addEdge("c", "b");
        g.addEdge("c", "d");
        g.addEdge("d", "c");
        g.addEdge("a", "c");
        g.addEdge("c", "a");

        // Community 2: e-f-g-h (dense)
        g.addEdge("e", "f");
        g.addEdge("f", "e");
        g.addEdge("f", "g");
        g.addEdge("g", "f");
        g.addEdge("g", "h");
        g.addEdge("h", "g");
        g.addEdge("e", "g");
        g.addEdge("g", "e");

        // Sparse bridge between communities
        g.addEdge("a", "e");

        return g;
    }

    @Test
    void testLeidenDetectsTwoCommunities() {
        InMemoryGraph g = buildTwoCommunityGraph();
        Set<String> nodes = g.nodeSet();

        LeidenConfig config = LeidenConfig.create().setMaxIterations(5);
        CommunityResult result = LeidenDetector.detect(g, nodes, config);

        assertTrue(result.getTotalCommunities() >= 1);
        assertTrue(result.getModularity() > 0);
        assertEquals("LEIDEN", result.getAlgorithmUsed());
    }

    @Test
    void testLabelPropagationDetectsTwoCommunities() {
        InMemoryGraph g = buildTwoCommunityGraph();
        Set<String> nodes = g.nodeSet();

        CommunityResult result = LabelPropagation.detect(g, nodes, 10);

        assertTrue(result.getTotalCommunities() >= 1);
        assertEquals("LABEL_PROPAGATION", result.getAlgorithmUsed());
    }

    @Test
    void testLeidenRequiresMinTwoNodes() {
        InMemoryGraph g = new InMemoryGraph();
        g.addNode("a");

        assertThrows(IllegalArgumentException.class,
                () -> LeidenDetector.detect(g, new HashSet<>(Set.of("a")),
                        LeidenConfig.create()));
    }

    @Test
    void testLabelPropagationRequiresMinTwoNodes() {
        InMemoryGraph g = new InMemoryGraph();
        g.addNode("a");

        assertThrows(IllegalArgumentException.class,
                () -> LabelPropagation.detect(g, new HashSet<>(Set.of("a")), 5));
    }

    @Test
    void testBetweennessCentralityBridgeNode() {
        InMemoryGraph g = new InMemoryGraph();
        // a -> bridge -> b
        // bridge is on all shortest paths
        g.addEdge("a", "bridge");
        g.addEdge("bridge", "b");
        g.addEdge("a1", "a");
        g.addEdge("a2", "a");
        g.addEdge("b", "b1");
        g.addEdge("b", "b2");

        Set<String> nodes = g.nodeSet();
        Map<String, Double> scores = BetweennessCentrality.compute(g, nodes);

        assertNotNull(scores);
        assertTrue(scores.containsKey("bridge"));
        // bridge should have higher betweenness than leaf nodes
        assertTrue(scores.get("bridge") > 0);
    }

    @Test
    void testGraphExporterGraphML() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("b", "c");

        String result = GraphExporter.export(g, g.nodeSet(), "GRAPHML");
        assertNotNull(result);
        assertTrue(result.contains("<graph"));
        assertTrue(result.contains("xml"));
    }

    @Test
    void testGraphExporterMermaid() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("b", "c");

        String result = GraphExporter.export(g, g.nodeSet(), "MERMAID");
        assertNotNull(result);
        assertTrue(result.startsWith("graph LR"));
        assertTrue(result.contains("-->"));
    }

    @Test
    void testGraphExporterJson() {
        InMemoryGraph g = new InMemoryGraph();
        g.addEdge("a", "b");
        g.addEdge("b", "c");

        String result = GraphExporter.export(g, g.nodeSet(), "JSON");
        assertNotNull(result);
        assertTrue(result.contains("\"nodes\""));
        assertTrue(result.contains("\"edges\""));
        assertTrue(result.contains("\"source\""));
        assertTrue(result.contains("\"target\""));
    }

    @Test
    void testGraphExporterWithCommunityView() {
        InMemoryGraph g = buildTwoCommunityGraph();
        Set<String> nodes = g.nodeSet();

        CommunityResult communities = LabelPropagation.detect(g, nodes, 10);

        String result = GraphExporter.export(g, nodes, "JSON", communities);
        assertNotNull(result);
        assertTrue(result.contains("comm_"));
    }

    @Test
    void testGraphExporterUnsupportedFormat() {
        InMemoryGraph g = new InMemoryGraph();
        g.addNode("a");

        assertThrows(IllegalArgumentException.class,
                () -> GraphExporter.export(g, g.nodeSet(), "UNKNOWN"));
    }

    @Test
    void testCommunityResultFields() {
        InMemoryGraph g = buildTwoCommunityGraph();
        CommunityResult result = LabelPropagation.detect(g, g.nodeSet(), 10);

        assertTrue(result.getTotalSymbols() >= 8);
        assertTrue(result.getAverageCohesion() >= 0 && result.getAverageCohesion() <= 1);
        assertTrue(result.getProcessingTimeMs() >= 0);
    }
}
