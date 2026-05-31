package io.nop.code.graph.knowledge;

import java.util.ArrayList;
import java.util.List;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
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

    @Test
    void testIsolatedSymbols_nodesWithNoEdges() {
        // Create 3 symbols with no call graph edges → all should be isolated
        KnowledgeGapAnalyzer analyzer = new KnowledgeGapAnalyzer();
        CallGraph callGraph = new CallGraph();
        SymbolTable symbolTable = new SymbolTable();
        addSymbol(symbolTable, "A", "com.example.ServiceA", CodeSymbolKind.CLASS);
        addSymbol(symbolTable, "B", "com.example.ServiceB", CodeSymbolKind.CLASS);
        addSymbol(symbolTable, "C", "com.example.ServiceC", CodeSymbolKind.CLASS);

        CommunityDetector.CommunityDetectionResult communities =
                new CommunityDetector.CommunityDetectionResult();

        KnowledgeGapResult result = analyzer.analyze(callGraph, symbolTable, communities);
        assertEquals(3, result.getIsolatedSymbols().size());

        // Verify isolated symbol metadata
        KnowledgeGapResult.IsolatedSymbol iso = result.getIsolatedSymbols().get(0);
        assertNotNull(iso.getSymbolId());
        assertNotNull(iso.getQualifiedName());
        assertNotNull(iso.getName());
        assertEquals("CLASS", iso.getKind());
    }

    @Test
    void testIsolatedSymbols_connectedNodesNotIsolated() {
        // A -> B: only C is isolated
        KnowledgeGapAnalyzer analyzer = new KnowledgeGapAnalyzer();
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");

        SymbolTable symbolTable = new SymbolTable();
        addSymbol(symbolTable, "A", "com.example.A", CodeSymbolKind.CLASS);
        addSymbol(symbolTable, "B", "com.example.B", CodeSymbolKind.CLASS);
        addSymbol(symbolTable, "C", "com.example.C", CodeSymbolKind.METHOD);

        CommunityDetector.CommunityDetectionResult communities =
                new CommunityDetector.CommunityDetectionResult();

        KnowledgeGapResult result = analyzer.analyze(callGraph, symbolTable, communities);
        assertEquals(1, result.getIsolatedSymbols().size());
        assertEquals("C", result.getIsolatedSymbols().get(0).getSymbolId());
        assertEquals("METHOD", result.getIsolatedSymbols().get(0).getKind());
    }

    @Test
    void testWeakCommunity_belowThreshold() {
        // Community with only external edges → cohesion = 0 → weak
        KnowledgeGapAnalyzer analyzer = new KnowledgeGapAnalyzer();
        analyzer.setWeakCommunityCohesionThreshold(0.5);

        CallGraph callGraph = new CallGraph();
        // Nodes A,B in community, C outside. A->C, B->C: no internal edges
        callGraph.addEdge("A", "C");
        callGraph.addEdge("B", "C");

        SymbolTable symbolTable = new SymbolTable();

        CommunityDetector.CommunityDetectionResult communities =
                new CommunityDetector.CommunityDetectionResult();
        CommunityDetector.Community weakComm = new CommunityDetector.Community();
        weakComm.setId("comm_0");
        weakComm.setLabel("weak-community");
        List<String> members = new ArrayList<>();
        members.add("A");
        members.add("B");
        weakComm.setSymbolIds(members);

        List<CommunityDetector.Community> commList = new ArrayList<>();
        commList.add(weakComm);
        communities.setCommunities(commList);

        KnowledgeGapResult result = analyzer.analyze(callGraph, symbolTable, communities);
        assertEquals(1, result.getWeakCommunities().size());

        KnowledgeGapResult.WeakCommunity wc = result.getWeakCommunities().get(0);
        assertEquals("comm_0", wc.getCommunityId());
        assertEquals("weak-community", wc.getLabel());
        assertEquals(2, wc.getSymbolCount());
        assertEquals(0.0, wc.getCohesion(), 0.001);
        assertEquals(0.5, wc.getThreshold(), 0.001);
    }

    @Test
    void testStrongCommunity_aboveThreshold_notReported() {
        // Community with all internal edges → cohesion = 1.0 → not weak
        KnowledgeGapAnalyzer analyzer = new KnowledgeGapAnalyzer();
        analyzer.setWeakCommunityCohesionThreshold(0.5);

        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "A");

        SymbolTable symbolTable = new SymbolTable();

        CommunityDetector.CommunityDetectionResult communities =
                new CommunityDetector.CommunityDetectionResult();
        CommunityDetector.Community strongComm = new CommunityDetector.Community();
        strongComm.setId("comm_strong");
        List<String> members = new ArrayList<>();
        members.add("A");
        members.add("B");
        strongComm.setSymbolIds(members);

        List<CommunityDetector.Community> commList = new ArrayList<>();
        commList.add(strongComm);
        communities.setCommunities(commList);

        KnowledgeGapResult result = analyzer.analyze(callGraph, symbolTable, communities);
        assertTrue(result.getWeakCommunities().isEmpty(),
                "Strong community (cohesion=1.0) should not be reported as weak");
    }

    @Test
    void testNullCommunities_noWeakCommunities() {
        KnowledgeGapAnalyzer analyzer = new KnowledgeGapAnalyzer();
        CallGraph callGraph = new CallGraph();
        SymbolTable symbolTable = new SymbolTable();

        CommunityDetector.CommunityDetectionResult communities =
                new CommunityDetector.CommunityDetectionResult();
        // communities.getCommunities() returns empty list

        KnowledgeGapResult result = analyzer.analyze(callGraph, symbolTable, communities);
        assertTrue(result.getWeakCommunities().isEmpty());
    }

    @Test
    void testThresholdGetterSetter() {
        KnowledgeGapAnalyzer analyzer = new KnowledgeGapAnalyzer();
        assertEquals(0.1, analyzer.getWeakCommunityCohesionThreshold(), 0.001);

        analyzer.setWeakCommunityCohesionThreshold(0.3);
        assertEquals(0.3, analyzer.getWeakCommunityCohesionThreshold(), 0.001);
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
