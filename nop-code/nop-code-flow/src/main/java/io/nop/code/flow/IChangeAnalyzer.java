package io.nop.code.flow;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;

public interface IChangeAnalyzer {

    ChangeAnalysisResult analyzeChanges(String indexId, String baselineCommitish, String targetCommitish,
                                        SymbolTable symbolTable, CallGraph callGraph);
}
