package io.nop.code.core.semantic;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeFileAnalysisResult;

import java.util.List;

public interface ISemanticEdgeExtractor {

    String getExtractorId();

    List<CodeSemanticEdge> extract(SymbolTable symbolTable, CallGraph callGraph);

    boolean requiresLlm();

    default int estimatedTokens(SymbolTable symbolTable) {
        return 0;
    }

    default List<CodeSemanticEdge> extractFromFileResults(List<CodeFileAnalysisResult> fileResults,
                                                           SymbolTable symbolTable) {
        return List.of();
    }
}
