package io.nop.code.graph.impact;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.graph.impact.ImpactAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestImpactAnalyzer {

    private CallGraph buildLinearChain() {
        CallGraph g = new CallGraph();
        g.addEdge("pkg.A", "pkg.B");
        g.addEdge("pkg.B", "pkg.C");
        g.addEdge("pkg.C", "pkg.D");
        return g;
    }

    private SymbolTable buildLinearSymbolTable() {
        SymbolTable st = new SymbolTable();
        String[] names = {"pkg.A", "pkg.B", "pkg.C", "pkg.D"};
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
    void testUpstreamContainsA_atDepth1() {
        CallGraph graph = buildLinearChain();
        SymbolTable symbols = buildLinearSymbolTable();

        ImpactAnalyzer.ImpactResult result =
                new ImpactAnalyzer().analyzeImpact("pkg.B", graph, symbols, 2);

        assertNotNull(result);
        assertNotEquals("not-found", result.getRiskLevel());

        List<ImpactAnalyzer.ImpactedSymbol> upstream = result.getUpstream();
        assertTrue(upstream.stream().anyMatch(s ->
                        "pkg.A".equals(s.getQualifiedName()) && s.getDepth() == 1),
                "Upstream should contain A at depth=1. Got: " + upstream);
    }

    @Test
    void testDownstreamContainsC_atDepth1_D_atDepth2() {
        CallGraph graph = buildLinearChain();
        SymbolTable symbols = buildLinearSymbolTable();

        ImpactAnalyzer.ImpactResult result =
                new ImpactAnalyzer().analyzeImpact("pkg.B", graph, symbols, 2);

        List<ImpactAnalyzer.ImpactedSymbol> downstream = result.getDownstream();

        assertTrue(downstream.stream().anyMatch(s ->
                        "pkg.C".equals(s.getQualifiedName()) && s.getDepth() == 1),
                "Downstream should contain C at depth=1. Got: " + downstream);

        assertTrue(downstream.stream().anyMatch(s ->
                        "pkg.D".equals(s.getQualifiedName()) && s.getDepth() == 2),
                "Downstream should contain D at depth=2. Got: " + downstream);
    }

    @Test
    void testRiskLevelIsNotFound() {
        CallGraph graph = buildLinearChain();
        SymbolTable symbols = buildLinearSymbolTable();

        ImpactAnalyzer.ImpactResult result =
                new ImpactAnalyzer().analyzeImpact("nonexistent.Symbol", graph, symbols, 3);

        assertEquals("not-found", result.getRiskLevel());
        assertTrue(result.getUpstream().isEmpty());
        assertTrue(result.getDownstream().isEmpty());
    }

    @Test
    void testMaxDepthLimit() {
        CallGraph graph = buildLinearChain();
        SymbolTable symbols = buildLinearSymbolTable();

        ImpactAnalyzer.ImpactResult result =
                new ImpactAnalyzer().analyzeImpact("pkg.B", graph, symbols, 1);

        List<ImpactAnalyzer.ImpactedSymbol> downstream = result.getDownstream();
        assertTrue(downstream.stream().anyMatch(s -> "pkg.C".equals(s.getQualifiedName())),
                "Should reach C at depth=1");
        assertFalse(downstream.stream().anyMatch(s -> "pkg.D".equals(s.getQualifiedName())),
                "Should NOT reach D with maxDepth=1. Got: " + downstream);
    }

    @Test
    void testTotalImpactedCount() {
        CallGraph graph = buildLinearChain();
        SymbolTable symbols = buildLinearSymbolTable();

        ImpactAnalyzer.ImpactResult result =
                new ImpactAnalyzer().analyzeImpact("pkg.B", graph, symbols, 3);

        assertEquals(3, result.getTotalImpacted());
    }

    @Test
    void testGetMaxDepth() {
        CallGraph graph = buildLinearChain();
        SymbolTable symbols = buildLinearSymbolTable();

        ImpactAnalyzer.ImpactResult result =
                new ImpactAnalyzer().analyzeImpact("pkg.B", graph, symbols, 5);

        assertEquals(2, result.getMaxDepth());
    }

    @Test
    void testGroupByDepth() {
        CallGraph graph = buildLinearChain();
        SymbolTable symbols = buildLinearSymbolTable();

        ImpactAnalyzer.ImpactResult result =
                new ImpactAnalyzer().analyzeImpact("pkg.B", graph, symbols, 3);

        Map<Integer, List<ImpactAnalyzer.ImpactedSymbol>> grouped =
                ImpactAnalyzer.groupByDepth(result.getDownstream());

        assertTrue(grouped.containsKey(1), "Should have depth=1 (C)");
        assertTrue(grouped.containsKey(2), "Should have depth=2 (D)");
        assertEquals(1, grouped.get(1).size());
        assertEquals(1, grouped.get(2).size());
    }

    @Test
    void testImpactConfig() {
        ImpactAnalyzer.ImpactConfig config = new ImpactAnalyzer.ImpactConfig();
        assertEquals(3, config.getMaxDepth());
        assertEquals(100, config.getMaxNodes());

        config.setMaxDepth(5);
        config.setMaxNodes(50);
        assertEquals(5, config.getMaxDepth());
        assertEquals(50, config.getMaxNodes());
    }

    @Test
    void testRiskEvaluation() {
        CallGraph graph = buildLinearChain();
        SymbolTable symbols = buildLinearSymbolTable();

        ImpactAnalyzer.ImpactResult result =
                new ImpactAnalyzer().analyzeImpact("pkg.B", graph, symbols, 2);

        assertEquals("low", result.getRiskLevel());
    }
}
