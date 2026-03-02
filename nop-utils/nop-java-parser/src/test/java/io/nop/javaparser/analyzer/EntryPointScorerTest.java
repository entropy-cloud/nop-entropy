package io.nop.javaparser.analyzer;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class EntryPointScorerTest {

    @Test
    public void testScoreEntryPoints_BasicScoring() {
        // 构建简单的调用图
        // A -> B, A -> C
        // B -> D
        // C -> D
        // D 被多人调用，不调用其他人
        
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("A", Arrays.asList("B", "C"));
        callGraph.put("B", Arrays.asList("D"));
        callGraph.put("C", Arrays.asList("D"));
        callGraph.put("D", Collections.emptyList());
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("B", Arrays.asList("A"));
        reverseCallGraph.put("C", Arrays.asList("A"));
        reverseCallGraph.put("D", Arrays.asList("B", "C"));
        reverseCallGraph.put("A", Collections.emptyList());
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "A", "com.test.ClassA.methodA");
        addMethodSymbol(symbolTable, "B", "com.test.ClassB.methodB");
        addMethodSymbol(symbolTable, "C", "com.test.ClassC.methodC");
        addMethodSymbol(symbolTable, "D", "com.test.ClassD.methodD");
        
        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, reverseCallGraph, symbolTable);
        
        assertEquals(4, scores.size());
        
        // 验证排序：分数最高的在前
        assertTrue(scores.get(0).getScore() >= scores.get(1).getScore());
        
        // A 是入口点（调用2个，被调用0个）-> score = 2/(0+1) = 2
        EntryPointScorer.EntryPointScore scoreA = scores.stream()
                .filter(s -> "A".equals(s.getSymbolId()))
                .findFirst().orElse(null);
        assertNotNull(scoreA);
        assertEquals(2.0, scoreA.getScore(), 0.01);
        assertEquals(0, scoreA.getCallerCount());
        assertEquals(2, scoreA.getCalleeCount());
        
        // D 是叶子函数（调用0个，被调用2个）-> score = 0/(2+1) = 0
        EntryPointScorer.EntryPointScore scoreD = scores.stream()
                .filter(s -> "D".equals(s.getSymbolId()))
                .findFirst().orElse(null);
        assertNotNull(scoreD);
        assertEquals(0.0, scoreD.getScore(), 0.01);
        assertEquals(2, scoreD.getCallerCount());
        assertEquals(0, scoreD.getCalleeCount());
    }
    
    @Test
    public void testScoreEntryPoints_EntryPointClassification() {
        // 入口点 - 高分，低被调用
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("entry", Arrays.asList("a", "b", "c", "d", "e"));
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("entry", Collections.emptyList());
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "entry", "com.test.Main.run");
        
        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, reverseCallGraph, symbolTable);
        
        EntryPointScorer.EntryPointScore entryScore = scores.stream()
                .filter(s -> "entry".equals(s.getSymbolId()))
                .findFirst().orElse(null);
        
        assertNotNull(entryScore);
        // 高分 + 低被调用 = 入口点
        assertEquals(EntryPointScorer.EntryPointType.ENTRY_POINT, entryScore.getEntryPointType());
    }
    
    @Test
    public void testScoreEntryPoints_LeafClassification() {
        // 叶子节点 - 不调用其他人
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("leaf", Collections.emptyList());
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("leaf", Arrays.asList("someone"));
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "leaf", "com.test.Leaf.process");
        
        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, reverseCallGraph, symbolTable);
        
        EntryPointScorer.EntryPointScore leafScore = scores.stream()
                .filter(s -> "leaf".equals(s.getSymbolId()))
                .findFirst().orElse(null);
        
        assertNotNull(leafScore);
        assertEquals(EntryPointScorer.EntryPointType.LEAF, leafScore.getEntryPointType());
    }
    
    @Test
    public void testScoreEntryPoints_IsolatedClassification() {
        // 孤立节点 - 既不调用也不被调用
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("isolated", Collections.emptyList());
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("isolated", Collections.emptyList());
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "isolated", "com.test.Isolated.dead");
        
        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, reverseCallGraph, symbolTable);
        
        EntryPointScorer.EntryPointScore isolatedScore = scores.stream()
                .filter(s -> "isolated".equals(s.getSymbolId()))
                .findFirst().orElse(null);
        
        assertNotNull(isolatedScore);
        assertEquals(EntryPointScorer.EntryPointType.ISOLATED, isolatedScore.getEntryPointType());
    }
    
    @Test
    public void testGetEntryPoints() {
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("entry", Arrays.asList("a", "b", "c"));
        callGraph.put("leaf", Collections.emptyList());
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("entry", Collections.emptyList());
        reverseCallGraph.put("leaf", Arrays.asList("x"));
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "entry", "Main.run");
        addMethodSymbol(symbolTable, "leaf", "Helper.do");
        
        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, reverseCallGraph, symbolTable);
        
        List<EntryPointScorer.EntryPointScore> entryPoints = EntryPointScorer.getEntryPoints(scores);
        
        assertEquals(1, entryPoints.size());
        assertEquals("entry", entryPoints.get(0).getSymbolId());
    }
    
    @Test
    public void testPrintSummary() {
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("entry", Arrays.asList("a"));
        callGraph.put("a", Collections.emptyList());
        
        Map<String, List<String>> reverseCallGraph = new HashMap<>();
        reverseCallGraph.put("a", Arrays.asList("entry"));
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        addMethodSymbol(symbolTable, "entry", "Main.run");
        addMethodSymbol(symbolTable, "a", "Helper.do");
        
        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, reverseCallGraph, symbolTable);
        
        String summary = EntryPointScorer.printSummary(scores);
        
        assertNotNull(summary);
        assertTrue(summary.contains("Total methods"));
        assertTrue(summary.contains("Entry Points"));
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
