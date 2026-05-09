package io.nop.code.core.analyzer;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;

import java.util.List;

public interface IEntryPointScorer {
    List<EntryPointScorer.EntryPointScore> scoreEntryPoints(CallGraph callGraph, SymbolTable symbolTable);

    List<EntryPointScorer.EntryPointScore> scoreEntryPoints(CallGraph callGraph, SymbolTable symbolTable, EntryPointScorer.ScoringConfig config);
}
