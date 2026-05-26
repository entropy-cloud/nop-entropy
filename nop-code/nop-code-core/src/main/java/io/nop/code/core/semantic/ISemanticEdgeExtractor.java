package io.nop.code.core.semantic;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;

import java.util.List;

/**
 * Interface for semantic edge extractors.
 * Each extractor discovers semantic relationships between code symbols.
 */
public interface ISemanticEdgeExtractor {

    /**
     * Unique identifier for this extractor (e.g., "name-sim", "doc-keyword").
     */
    String getExtractorId();

    /**
     * Extract semantic edges from the given symbol table and call graph.
     */
    List<CodeSemanticEdge> extract(SymbolTable symbolTable, CallGraph callGraph);

    /**
     * Whether this extractor requires LLM access.
     */
    boolean requiresLlm();

    /**
     * Estimated token consumption for the given symbol table.
     * Returns 0 for deterministic extractors.
     */
    default int estimatedTokens(SymbolTable symbolTable) {
        return 0;
    }
}
