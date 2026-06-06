package io.nop.code.flow;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
public interface IChangeAnalyzer {

    void setFlowDetector(IFlowDetector flowDetector);

    ChangeAnalysisResult analyzeChanges(String indexId, String baselineCommitish, String targetCommitish,
                                        SymbolTable symbolTable, CallGraph callGraph, String workingDirectory);
}
