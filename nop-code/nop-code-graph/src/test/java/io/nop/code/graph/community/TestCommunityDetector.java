package io.nop.code.graph.community;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.graph.community.CommunityDetector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TestCommunityDetector {

    private CallGraph buildTestGraph() {
        CallGraph g = new CallGraph();
        g.addEdge("pkg.A", "pkg.B");
        g.addEdge("pkg.A", "pkg.C");
        g.addEdge("pkg.B", "pkg.C");
        g.addEdge("pkg.B", "pkg.D");
        g.addEdge("pkg.C", "pkg.D");
        g.addEdge("pkg.E", "pkg.F");
        g.addEdge("pkg.F", "pkg.E");
        return g;
    }

    private SymbolTable buildTestSymbolTable() {
        SymbolTable st = new SymbolTable();
        String[] names = {"pkg.A", "pkg.B", "pkg.C", "pkg.D", "pkg.E", "pkg.F"};
        for (String qn : names) {
            CodeSymbol sym = new CodeSymbol();
            sym.setId(qn);
            sym.setQualifiedName(qn);
            sym.setName(qn.substring(qn.lastIndexOf('.') + 1));
            sym.setKind(CodeSymbolKind.METHOD);
            st.add(sym);
        }
        return st;
    }

    @Test
    void testLeidenDetectsAtLeastOneCommunity() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        CommunityDetector.CommunityConfig config = CommunityDetector.CommunityConfig.leidenConfig();
        config.setMinCommunitySize(2);
        config.setResolution(1.0);

        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(graph, symbols, config);

        assertTrue(result.getTotalCommunities() >= 1,
                "Should detect at least 1 community, got " + result.getTotalCommunities());
    }

    @Test
    void testLeidenEFCommunityCohesion() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        CommunityDetector.CommunityConfig config = CommunityDetector.CommunityConfig.leidenConfig();
        config.setMinCommunitySize(2);
        config.setResolution(1.0);

        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(graph, symbols, config);

        List<CommunityDetector.Community> communities = result.getCommunities();
        CommunityDetector.Community efCommunity = null;
        for (CommunityDetector.Community c : communities) {
            if (c.getSymbolIds().contains("pkg.E") && c.getSymbolIds().contains("pkg.F")) {
                efCommunity = c;
                break;
            }
        }

        assertNotNull(efCommunity,
                "E and F should be grouped in the same community. Communities: " + communities);
        assertTrue(efCommunity.getCohesion() >= 0 && efCommunity.getCohesion() <= 1,
                "Cohesion should be between 0 and 1, got " + efCommunity.getCohesion());
    }

    @Test
    void testLeidenCohesionInRange() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        CommunityDetector.CommunityConfig config = CommunityDetector.CommunityConfig.leidenConfig();
        config.setMinCommunitySize(2);
        config.setResolution(1.0);

        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(graph, symbols, config);

        for (CommunityDetector.Community c : result.getCommunities()) {
            assertTrue(c.getCohesion() >= 0 && c.getCohesion() <= 1,
                    "Cohesion should be between 0 and 1, got " + c.getCohesion() + " for " + c);
        }
    }

    @Test
    void testLabelPropagationDetectsCommunities() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        CommunityDetector.CommunityConfig config = new CommunityDetector.CommunityConfig();
        config.setAlgorithm(CommunityDetector.AlgorithmType.LABEL_PROPAGATION);
        config.setMinCommunitySize(2);

        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(graph, symbols, config);

        assertEquals(CommunityDetector.AlgorithmType.LABEL_PROPAGATION, result.getAlgorithmUsed());
        assertTrue(result.getTotalCommunities() >= 1,
                "LabelPropagation should detect at least 1 community, got " + result.getTotalCommunities());
    }

    @Test
    void testEmptyGraphReturnsEmptyResult() {
        CallGraph graph = new CallGraph();
        SymbolTable symbols = new SymbolTable();

        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(graph, symbols);

        assertEquals(0, result.getTotalCommunities());
        assertTrue(result.getCommunities().isEmpty());
        assertEquals(0, result.getTotalSymbols());
    }

    @Test
    void testSingleEdgeGraph() {
        CallGraph graph = new CallGraph();
        graph.addEdge("pkg.A", "pkg.B");

        SymbolTable symbols = new SymbolTable();
        CodeSymbol a = new CodeSymbol();
        a.setId("pkg.A");
        a.setQualifiedName("pkg.A");
        a.setName("A");
        a.setKind(CodeSymbolKind.METHOD);
        symbols.add(a);

        CodeSymbol b = new CodeSymbol();
        b.setId("pkg.B");
        b.setQualifiedName("pkg.B");
        b.setName("B");
        b.setKind(CodeSymbolKind.METHOD);
        symbols.add(b);

        CommunityDetector.CommunityConfig config = CommunityDetector.CommunityConfig.leidenConfig();
        config.setMinCommunitySize(2);

        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(graph, symbols, config);

        assertTrue(result.getTotalCommunities() <= 1);
        assertTrue(result.getTotalSymbols() >= 2);
    }

    @Test
    void testGetCommunityForSymbol() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        CommunityDetector.CommunityConfig config = CommunityDetector.CommunityConfig.leidenConfig();
        config.setMinCommunitySize(2);
        config.setResolution(1.0);

        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(graph, symbols, config);

        Set<String> allClustered = result.getCommunities().stream()
                .flatMap(c -> c.getSymbolIds().stream())
                .collect(Collectors.toSet());

        for (String symbolId : allClustered) {
            CommunityDetector.Community c =
                    CommunityDetector.getCommunityForSymbol(symbolId, result.getCommunities());
            assertNotNull(c, "Should find community for clustered symbol " + symbolId);
            assertTrue(c.getSymbolIds().contains(symbolId));
        }

        assertNull(CommunityDetector.getCommunityForSymbol("nonexistent", result.getCommunities()));
    }
}
