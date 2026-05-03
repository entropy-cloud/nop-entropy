package io.nop.javaparser.analyzer;

import io.nop.code.core.analyzer.EntryPointScorer;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EntryPointScorerTest {

    @Test
    public void testScoreEntryPoints_BasicScoring() {
        // 构建简单的调用图
        // A -> B, A -> C
        // B -> D
        // C -> D
        // D 被多人调用，不调用其他人

        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("A", "C");
        callGraph.addEdge("B", "D");
        callGraph.addEdge("C", "D");

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "A", "com.test.ClassA.methodA");
        addMethodSymbol(symbolTable, "B", "com.test.ClassB.methodB");
        addMethodSymbol(symbolTable, "C", "com.test.ClassC.methodC");
        addMethodSymbol(symbolTable, "D", "com.test.ClassD.methodD");

        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, symbolTable);

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
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("entry", "a");
        callGraph.addEdge("entry", "b");
        callGraph.addEdge("entry", "c");
        callGraph.addEdge("entry", "d");
        callGraph.addEdge("entry", "e");

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "entry", "com.test.Main.run");

        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, symbolTable);

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
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("someone", "leaf");

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "leaf", "com.test.Leaf.process");

        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, symbolTable);

        EntryPointScorer.EntryPointScore leafScore = scores.stream()
                .filter(s -> "leaf".equals(s.getSymbolId()))
                .findFirst().orElse(null);

        assertNotNull(leafScore);
        assertEquals(EntryPointScorer.EntryPointType.LEAF, leafScore.getEntryPointType());
    }

    @Test
    public void testScoreEntryPoints_IsolatedClassification() {
        // 孤立节点 - 既不调用也不被调用
        CallGraph callGraph = new CallGraph();

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "isolated", "com.test.Isolated.dead");

        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, symbolTable);

        EntryPointScorer.EntryPointScore isolatedScore = scores.stream()
                .filter(s -> "isolated".equals(s.getSymbolId()))
                .findFirst().orElse(null);

        assertNotNull(isolatedScore);
        assertEquals(EntryPointScorer.EntryPointType.ISOLATED, isolatedScore.getEntryPointType());
    }

    @Test
    public void testGetEntryPoints() {
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("entry", "a");
        callGraph.addEdge("entry", "b");
        callGraph.addEdge("entry", "c");
        callGraph.addEdge("x", "leaf");

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "entry", "Main.run");
        addMethodSymbol(symbolTable, "leaf", "Helper.do");

        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, symbolTable);

        List<EntryPointScorer.EntryPointScore> entryPoints = EntryPointScorer.getEntryPoints(scores);

        assertEquals(1, entryPoints.size());
        assertEquals("entry", entryPoints.get(0).getSymbolId());
    }

    @Test
    public void testPrintSummary() {
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("entry", "a");

        SymbolTable symbolTable = new SymbolTable();
        addMethodSymbol(symbolTable, "entry", "Main.run");
        addMethodSymbol(symbolTable, "a", "Helper.do");

        List<EntryPointScorer.EntryPointScore> scores = EntryPointScorer.scoreEntryPoints(
                callGraph, symbolTable);

        String summary = EntryPointScorer.printSummary(scores);

        assertNotNull(summary);
        assertTrue(summary.contains("Total methods"));
        assertTrue(summary.contains("Entry Points"));
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
