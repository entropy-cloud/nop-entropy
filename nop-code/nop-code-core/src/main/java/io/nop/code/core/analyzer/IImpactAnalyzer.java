package io.nop.code.core.analyzer;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;

public interface IImpactAnalyzer {
    ImpactAnalyzer.ImpactResult analyzeImpact(String symbolId, CallGraph callGraph, SymbolTable symbolTable, int maxDepth);

    ImpactAnalyzer.ImpactResult analyzeImpact(String symbolId, CallGraph callGraph, SymbolTable symbolTable, ImpactAnalyzer.ImpactConfig config);
}
