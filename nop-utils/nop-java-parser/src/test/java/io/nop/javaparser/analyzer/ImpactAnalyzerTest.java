package io.nop.javaparser.analyzer;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ImpactAnalyzerTest {

    @Test
    public void testAnalyzeImpact_UpstreamAnalysis() {
        // 构建调用图：
        // A -> B -> C -> target
        // D -> B
        
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("A", Arrays.asList("B"));
        callGraph.put("B", Arrays.asList("C"));
        callGraph.put("C", Arrays.asList("target"));
        callGraph.put("D", Arrays.asList("B"));
        callGraph.put("target", Collections.emptyList());
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("B", Arrays.asList("A", "D"));
        reverseCallGraph.put("C", Arrays.asList("B"));
        reverseCallGraph.put("target", Arrays.asList("C"));
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "target", "com.test.Service.target");
        addMethodSymbol(symbolTable, "C", "com.test.Service.C");
        addMethodSymbol(symbolTable, "B", "com.test.Service.B");
        addMethodSymbol(symbolTable, "A", "com.test.Service.A");
        addMethodSymbol(symbolTable, "D", "com.test.Service.D");
        
        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Service.target", callGraph, reverseCallGraph, symbolTable, 3);
        
        assertNotNull(result);
        assertEquals("target", result.getTargetSymbolId());
        assertEquals("com.test.Service.target", result.getTargetQualifiedName());
        
        // 验证上游（调用者）分析
        List<ImpactAnalyzer.ImpactedSymbol> upstream = result.getUpstream();
        assertTrue(upstream.size() >= 2); // 至少包含 C 和 B
        
        // 验证深度
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
        // 构建调用图：
        // target -> A -> B
        // target -> C
        
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("target", Arrays.asList("A", "C"));
        callGraph.put("A", Arrays.asList("B"));
        callGraph.put("B", Collections.emptyList());
        callGraph.put("C", Collections.emptyList());
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("A", Arrays.asList("target"));
        reverseCallGraph.put("B", Arrays.asList("A"));
        reverseCallGraph.put("C", Arrays.asList("target"));
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "target", "com.test.Main.run");
        addMethodSymbol(symbolTable, "A", "com.test.Helper.A");
        addMethodSymbol(symbolTable, "B", "com.test.Helper.B");
        addMethodSymbol(symbolTable, "C", "com.test.Helper.C");
        
        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Main.run", callGraph, reverseCallGraph, symbolTable, 3);
        
        assertNotNull(result);
        
        // 验证下游（被调用者）分析
        List<ImpactAnalyzer.ImpactedSymbol> downstream = result.getDownstream();
        assertTrue(downstream.size() >= 2); // 至少包含 A 和 C
        
        // 验证深度
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
        Map<String, List<String>> callGraph = new HashMap<>();
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        
        // 创建大量调用者
        List<String> callers = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String callerId = "caller" + i;
            callers.add(callerId);
            callGraph.put(callerId, Arrays.asList("target"));
        }
        
        callGraph.put("target", Collections.emptyList());
        reverseCallGraph.put("target", callers);
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "target", "com.test.Core.critical");
        for (int i = 0; i < 60; i++) {
            addMethodSymbol(symbolTable, "caller" + i, "com.test.Caller" + i);
        }
        
        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Core.critical", callGraph, reverseCallGraph, symbolTable, 3);
        
        assertEquals("critical", result.getRiskLevel());
    }
    
    @Test
    public void testAnalyzeImpact_LowRisk() {
        // 低风险场景：少量影响
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("target", Arrays.asList("a"));
        callGraph.put("a", Collections.emptyList());
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("a", Arrays.asList("target"));
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "target", "com.test.Simple.run");
        addMethodSymbol(symbolTable, "a", "com.test.Helper.do");
        
        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Simple.run", callGraph, reverseCallGraph, symbolTable, 3);
        
        assertEquals("low", result.getRiskLevel());
    }
    
    @Test
    public void testAnalyzeImpact_TargetNotFound() {
        Map<String, List<String>> callGraph = new HashMap<>();
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        
        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.NonExistent.method", callGraph, reverseCallGraph, symbolTable, 3);
        
        assertEquals("not-found", result.getRiskLevel());
        assertNull(result.getTargetSymbolId());
    }
    
    @Test
    public void testAnalyzeImpact_MaxDepthLimit() {
        // 构建深度调用链：A -> B -> C -> D -> E -> target
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("A", Arrays.asList("B"));
        callGraph.put("B", Arrays.asList("C"));
        callGraph.put("C", Arrays.asList("D"));
        callGraph.put("D", Arrays.asList("E"));
        callGraph.put("E", Arrays.asList("target"));
        callGraph.put("target", Collections.emptyList());
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("B", Arrays.asList("A"));
        reverseCallGraph.put("C", Arrays.asList("B"));
        reverseCallGraph.put("D", Arrays.asList("C"));
        reverseCallGraph.put("E", Arrays.asList("D"));
        reverseCallGraph.put("target", Arrays.asList("E"));
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "target", "com.test.Deep.target");
        addMethodSymbol(symbolTable, "E", "com.test.Deep.E");
        addMethodSymbol(symbolTable, "D", "com.test.Deep.D");
        addMethodSymbol(symbolTable, "C", "com.test.Deep.C");
        addMethodSymbol(symbolTable, "B", "com.test.Deep.B");
        addMethodSymbol(symbolTable, "A", "com.test.Deep.A");
        
        // 限制深度为 2
        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Deep.target", callGraph, reverseCallGraph, symbolTable, 2);
        
        // 验证最大深度不超过限制
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
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("target", Arrays.asList("a"));
        callGraph.put("a", Collections.emptyList());
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("a", Arrays.asList("target"));
        reverseCallGraph.put("target", Arrays.asList("caller"));
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "target", "com.test.Main.run");
        addMethodSymbol(symbolTable, "a", "com.test.Helper.do");
        addMethodSymbol(symbolTable, "caller", "com.test.Caller.call");
        
        ImpactAnalyzer.ImpactResult result = ImpactAnalyzer.analyzeImpact(
                "com.test.Main.run", callGraph, reverseCallGraph, symbolTable, 3);
        
        String output = ImpactAnalyzer.printResult(result);
        
        assertNotNull(output);
        assertTrue(output.contains("Target:"));
        assertTrue(output.contains("Risk Level:"));
        assertTrue(output.contains("Upstream"));
        assertTrue(output.contains("Downstream"));
    }
    
    private void addMethodSymbol(Map<String, SymbolInfo> symbolTable, String id, String qualifiedName) {
        SymbolInfo symbol = new SymbolInfo();
        symbol.setId(id);
        symbol.setQualifiedName(qualifiedName);
        symbol.setName(qualifiedName.contains(".") ? 
                qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1) : qualifiedName);
        symbol.setKind(SymbolKind.METHOD);
        symbolTable.put(id, symbol);
    }
}
