package io.nop.javaparser.analyzer;

import io.nop.code.core.analyzer.ImpactAnalyzer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ImpactAnalyzerTest {

    @Test
    public void testAnalyzeImpact_UpstreamAnalysis() {
        // A -> B -> C -> target
        // D -> B
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "C");
        callGraph.addEdge("C", "target");
        callGraph.addEdge("D", "B");

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "target", "com.test.Service.target");
        addMethodSymbol(symbolTable, "C", "com.test.Service.C");
        addMethodSymbol(symbolTable, "B", "com.test.Service.B");
        addMethodSymbol(symbolTable, "A", "com.test.Service.A");
        addMethodSymbol(symbolTable, "D", "com.test.Service.D");

        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Service.target", callGraph, symbolTable, 3);

        assertNotNull(result);
        assertEquals("target", result.getTargetSymbolId());
        assertEquals("com.test.Service.target", result.getTargetQualifiedName());

        List<ImpactAnalyzer.ImpactedSymbol> upstream = result.getUpstream();
        assertTrue(upstream.size() >= 2);

        Optional<ImpactAnalyzer.ImpactedSymbol> cSymbol = upstream.stream()
                .filter(s -> "C".equals(s.getSymbolId()))
                .findFirst();
        assertTrue(cSymbol.isPresent());
        assertEquals(1, cSymbol.get().getDepth());

        Optional<ImpactAnalyzer.ImpactedSymbol> bSymbol = upstream.stream()
                .filter(s -> "B".equals(s.getSymbolId()))
                .findFirst();
        assertTrue(bSymbol.isPresent());
        assertEquals(2, bSymbol.get().getDepth());
    }
    
    @Test
    public void testAnalyzeImpact_DownstreamAnalysis() {
        // target -> A -> B
        // target -> C
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("target", "A");
        callGraph.addEdge("target", "C");
        callGraph.addEdge("A", "B");

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "target", "com.test.Main.run");
        addMethodSymbol(symbolTable, "A", "com.test.Helper.A");
        addMethodSymbol(symbolTable, "B", "com.test.Helper.B");
        addMethodSymbol(symbolTable, "C", "com.test.Helper.C");

        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Main.run", callGraph, symbolTable, 3);

        assertNotNull(result);

        List<ImpactAnalyzer.ImpactedSymbol> downstream = result.getDownstream();
        assertTrue(downstream.size() >= 2);

        Optional<ImpactAnalyzer.ImpactedSymbol> aSymbol = downstream.stream()
                .filter(s -> "A".equals(s.getSymbolId()))
                .findFirst();
        assertTrue(aSymbol.isPresent());
        assertEquals(1, aSymbol.get().getDepth());

        Optional<ImpactAnalyzer.ImpactedSymbol> bSymbol = downstream.stream()
                .filter(s -> "B".equals(s.getSymbolId()))
                .findFirst();
        assertTrue(bSymbol.isPresent());
        assertEquals(2, bSymbol.get().getDepth());
    }
    
    @Test
    public void testAnalyzeImpact_RiskEvaluation() {
        // 高风险场景：大量调用者
        CallGraph callGraph = new CallGraph();
        for (int i = 0; i < 60; i++) {
            String callerId = "caller" + i;
            callGraph.addEdge(callerId, "target");
        }

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "target", "com.test.Core.critical");
        for (int i = 0; i < 60; i++) {
            addMethodSymbol(symbolTable, "caller" + i, "com.test.Caller" + i);
        }

        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Core.critical", callGraph, symbolTable, 3);

        assertEquals("critical", result.getRiskLevel());
    }
    
    @Test
    public void testAnalyzeImpact_LowRisk() {
        // 低风险场景：少量影响
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("target", "a");

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "target", "com.test.Simple.run");
        addMethodSymbol(symbolTable, "a", "com.test.Helper.do");

        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Simple.run", callGraph, symbolTable, 3);

        assertEquals("low", result.getRiskLevel());
    }
    
    @Test
    public void testAnalyzeImpact_TargetNotFound() {
        CallGraph callGraph = new CallGraph();
        SymbolTable symbolTable = new SymbolTable();

        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.NonExistent.method", callGraph, symbolTable, 3);

        assertEquals("not-found", result.getRiskLevel());
        assertNull(result.getTargetSymbolId());
    }
    
    @Test
    public void testAnalyzeImpact_MaxDepthLimit() {
        // A -> B -> C -> D -> E -> target
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "C");
        callGraph.addEdge("C", "D");
        callGraph.addEdge("D", "E");
        callGraph.addEdge("E", "target");

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "target", "com.test.Deep.target");
        addMethodSymbol(symbolTable, "E", "com.test.Deep.E");
        addMethodSymbol(symbolTable, "D", "com.test.Deep.D");
        addMethodSymbol(symbolTable, "C", "com.test.Deep.C");
        addMethodSymbol(symbolTable, "B", "com.test.Deep.B");
        addMethodSymbol(symbolTable, "A", "com.test.Deep.A");

        // 限制深度为 2
        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Deep.target", callGraph, symbolTable, 2);

        assertTrue(result.getMaxDepth() <= 2);
    }
    
    @Test
    public void testGroupByDepth() {
        List<ImpactAnalyzer.ImpactedSymbol> symbols = new ArrayList<>();

        ImpactAnalyzer.ImpactedSymbol s1 = new ImpactAnalyzer.ImpactedSymbol();
        s1.setSymbolId("a");
        s1.setDepth(1);
        symbols.add(s1);

        ImpactAnalyzer.ImpactedSymbol s2 = new ImpactAnalyzer.ImpactedSymbol();
        s2.setSymbolId("b");
        s2.setDepth(1);
        symbols.add(s2);

        ImpactAnalyzer.ImpactedSymbol s3 = new ImpactAnalyzer.ImpactedSymbol();
        s3.setSymbolId("c");
        s3.setDepth(2);
        symbols.add(s3);

        Map<Integer, List<ImpactAnalyzer.ImpactedSymbol>> grouped = ImpactAnalyzer.groupByDepth(symbols);

        assertEquals(2, grouped.size());
        assertEquals(2, grouped.get(1).size());
        assertEquals(1, grouped.get(2).size());
    }
    
    @Test
    public void testPrintResult() {
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("caller", "target");
        callGraph.addEdge("target", "a");

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "target", "com.test.Main.run");
        addMethodSymbol(symbolTable, "a", "com.test.Helper.do");
        addMethodSymbol(symbolTable, "caller", "com.test.Caller.call");

        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Main.run", callGraph, symbolTable, 3);

        String output = ImpactAnalyzer.printResult(result);

        assertNotNull(output);
        assertTrue(output.contains("Target:"));
        assertTrue(output.contains("Risk Level:"));
        assertTrue(output.contains("Upstream"));
        assertTrue(output.contains("Downstream"));
    }
    
    private void addMethodSymbol(SymbolTable symbolTable, String id, String qualifiedName) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId(id);
        symbol.setQualifiedName(qualifiedName);
        symbol.setName(qualifiedName.contains(".") ? 
                qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1) : qualifiedName);
        symbol.setKind(CodeSymbolKind.METHOD);
        symbolTable.add(symbol);
    }
}
