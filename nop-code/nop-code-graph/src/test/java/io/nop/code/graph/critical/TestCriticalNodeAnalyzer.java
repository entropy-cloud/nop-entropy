package io.nop.code.graph.critical;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
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
}
