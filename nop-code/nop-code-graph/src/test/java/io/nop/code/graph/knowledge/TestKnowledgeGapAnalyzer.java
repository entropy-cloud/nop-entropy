package io.nop.code.graph.knowledge;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.graph.community.CommunityDetector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestKnowledgeGapAnalyzer {

    @Test
    void testConstructionAndAnalyzeWithEmptyGraph() {
        KnowledgeGapAnalyzer analyzer = new KnowledgeGapAnalyzer();
        assertNotNull(analyzer);

        CallGraph callGraph = new CallGraph();
        SymbolTable symbolTable = new SymbolTable();
        CommunityDetector.CommunityDetectionResult communities =
                new CommunityDetector.CommunityDetectionResult();

        KnowledgeGapResult result = analyzer.analyze(callGraph, symbolTable, communities);
        assertNotNull(result);
        assertNotNull(result.getIsolatedSymbols());
        assertNotNull(result.getWeakCommunities());
        assertTrue(result.getIsolatedSymbols().isEmpty());
    }
}
