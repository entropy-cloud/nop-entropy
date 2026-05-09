package io.nop.code.core.analyzer;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;

public interface ICommunityDetector {
    CommunityDetector.CommunityDetectionResult detectCommunities(CallGraph callGraph, SymbolTable symbolTable);

    CommunityDetector.CommunityDetectionResult detectCommunities(CallGraph callGraph, SymbolTable symbolTable, CommunityDetector.CommunityConfig config);
}
