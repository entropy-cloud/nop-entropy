package io.nop.code.graph.critical;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCriticalNodeAnalyzer {

    @Test
    void testConstructionAndAnalyzeWithEmptyGraph() {
        CriticalNodeAnalyzer analyzer = new CriticalNodeAnalyzer();
        assertNotNull(analyzer);

        CallGraph callGraph = new CallGraph();
        SymbolTable symbolTable = new SymbolTable();
        CriticalNodeResult result = analyzer.analyze(callGraph, symbolTable, 5);

        assertNotNull(result);
        assertEquals(0, result.getTotalNodes());
        assertNotNull(result.getHubNodes());
        assertNotNull(result.getBridgeNodes());
    }

    @Test
    void testAnalyzeWithSingleNode() {
        CriticalNodeAnalyzer analyzer = new CriticalNodeAnalyzer();
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "A");
        SymbolTable symbolTable = new SymbolTable();

        CriticalNodeResult result = analyzer.analyze(callGraph, symbolTable, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalNodes());
    }

    @Test
    void testHubCentrality_starGraph() {
        // Star topology: hub -> A, hub -> B, hub -> C
        // Hub should have highest total degree (3 out-degree, 0 in-degree = 3)
        CriticalNodeAnalyzer analyzer = new CriticalNodeAnalyzer();
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("hub", "A");
        callGraph.addEdge("hub", "B");
        callGraph.addEdge("hub", "C");

        SymbolTable symbolTable = new SymbolTable();
        addSymbol(symbolTable, "hub", "com.example.Hub", CodeSymbolKind.CLASS);
        addSymbol(symbolTable, "A", "com.example.A", CodeSymbolKind.CLASS);
        addSymbol(symbolTable, "B", "com.example.B", CodeSymbolKind.CLASS);
        addSymbol(symbolTable, "C", "com.example.C", CodeSymbolKind.CLASS);

        CriticalNodeResult result = analyzer.analyze(callGraph, symbolTable, 10);
        assertEquals(4, result.getTotalNodes());
        assertFalse(result.getHubNodes().isEmpty());

        // Hub should be the top hub node
        CriticalNodeResult.NodeScore topHub = result.getHubNodes().get(0);
        assertEquals("hub", topHub.getSymbolId());
        assertEquals(0, topHub.getInDegree());
        assertEquals(3, topHub.getOutDegree());
        assertEquals(3, topHub.getTotalDegree());
        assertEquals(3.0, topHub.getScore());
        assertEquals("com.example.Hub", topHub.getQualifiedName());
    }

    @Test
    void testHubCentrality_topNLimit() {
        // Create 5 nodes, ask for top 2
        CriticalNodeAnalyzer analyzer = new CriticalNodeAnalyzer();
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("A", "C");
        callGraph.addEdge("A", "D");
        callGraph.addEdge("B", "C");

        SymbolTable symbolTable = new SymbolTable();

        CriticalNodeResult result = analyzer.analyze(callGraph, symbolTable, 2);
        assertEquals(4, result.getTotalNodes());
        assertEquals(2, result.getTopN());
        assertTrue(result.getHubNodes().size() <= 2);
        assertTrue(result.getBridgeNodes().size() <= 2);
    }

    @Test
    void testBridgeDetection_bridgeNode() {
        // Linear chain: A -> B -> C -> D
        // B and C are bridge nodes (highest betweenness centrality)
        CriticalNodeAnalyzer analyzer = new CriticalNodeAnalyzer();
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "C");
        callGraph.addEdge("C", "D");

        SymbolTable symbolTable = new SymbolTable();
        addSymbol(symbolTable, "A", "com.example.A", CodeSymbolKind.CLASS);
        addSymbol(symbolTable, "B", "com.example.B", CodeSymbolKind.CLASS);
        addSymbol(symbolTable, "C", "com.example.C", CodeSymbolKind.CLASS);
        addSymbol(symbolTable, "D", "com.example.D", CodeSymbolKind.CLASS);

        CriticalNodeResult result = analyzer.analyze(callGraph, symbolTable, 10);
        assertFalse(result.getBridgeNodes().isEmpty());

        // B and C should have higher betweenness than A and D
        CriticalNodeResult.NodeScore topBridge = result.getBridgeNodes().get(0);
        assertTrue(topBridge.getScore() > 0, "Bridge node should have positive betweenness centrality");

        // Verify all nodes have correct degree info in bridge results
        for (CriticalNodeResult.NodeScore ns : result.getBridgeNodes()) {
            assertNotNull(ns.getSymbolId());
            // Fallback to ID when symbol not found (we added all symbols)
            assertNotNull(ns.getQualifiedName());
        }
    }

    @Test
    void testBridgeDetection_noBridgesInCompleteGraph() {
        // Fully connected: every node calls every other node
        // Betweenness should be 0 for all (or very low since directed graph)
        CriticalNodeAnalyzer analyzer = new CriticalNodeAnalyzer();
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("A", "C");
        callGraph.addEdge("B", "A");
        callGraph.addEdge("B", "C");
        callGraph.addEdge("C", "A");
        callGraph.addEdge("C", "B");

        SymbolTable symbolTable = new SymbolTable();

        CriticalNodeResult result = analyzer.analyze(callGraph, symbolTable, 10);
        assertEquals(3, result.getTotalNodes());
        assertFalse(result.getBridgeNodes().isEmpty());

        // In a fully connected directed graph, all nodes should have similar betweenness
        double maxScore = result.getBridgeNodes().stream()
                .mapToDouble(CriticalNodeResult.NodeScore::getScore)
                .max().orElse(0);
        double minScore = result.getBridgeNodes().stream()
                .mapToDouble(CriticalNodeResult.NodeScore::getScore)
                .min().orElse(0);
        // Scores should be relatively close in a complete graph
        assertTrue(maxScore - minScore <= maxScore * 0.5,
                "In a fully connected graph, betweenness scores should be similar");
    }

    private void addSymbol(SymbolTable table, String id, String qualifiedName, CodeSymbolKind kind) {
        CodeSymbol sym = new CodeSymbol();
        sym.setId(id);
        sym.setQualifiedName(qualifiedName);
        sym.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        sym.setKind(kind);
        table.add(sym);
    }
}
