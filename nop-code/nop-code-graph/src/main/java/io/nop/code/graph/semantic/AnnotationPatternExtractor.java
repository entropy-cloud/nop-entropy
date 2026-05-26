package io.nop.code.graph.semantic;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeAnnotationUsage;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.semantic.CodeSemanticEdge;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.core.semantic.ISemanticEdgeExtractor;
import io.nop.code.core.semantic.SemanticRelationType;

import java.util.*;

/**
 * Extracts semantic edges based on shared annotation patterns.
 * Symbols annotated with the same annotations are conceptually related.
 */
public class AnnotationPatternExtractor implements ISemanticEdgeExtractor {

    private static final int MIN_SHARED_ANNOTATIONS = 1;

    @Override
    public String getExtractorId() {
        return "annotation-pattern";
    }

    @Override
    public List<CodeSemanticEdge> extract(SymbolTable symbolTable, CallGraph callGraph) {
        // Annotation data is not available in the in-memory SymbolTable/CallGraph.
        // This extractor returns empty results when called with in-memory structures.
        // It should be called with file-level analysis results to access annotation data.
        return Collections.emptyList();
    }

    /**
     * Extract semantic edges from file analysis results with annotation data.
     */
    public List<CodeSemanticEdge> extractFromFileResults(List<CodeFileAnalysisResult> fileResults,
                                                          SymbolTable symbolTable) {
        // Build annotation -> symbol IDs mapping
        Map<String, Set<String>> annotationToSymbols = new HashMap<>();

        for (CodeFileAnalysisResult file : fileResults) {
            for (CodeAnnotationUsage usage : file.getAnnotationUsages()) {
                String annType = usage.getAnnotationTypeQualifiedName();
                if (annType == null) continue;
                String symbolId = usage.getAnnotatedSymbolId();
                if (symbolId == null) continue;

                annotationToSymbols.computeIfAbsent(annType, k -> new HashSet<>()).add(symbolId);
            }
        }

        // Skip common annotations that don't indicate conceptual similarity
        Set<String> skipAnnotations = Set.of(
                "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface",
                "java.lang.Override", "java.lang.Deprecated", "java.lang.SuppressWarnings"
        );

        List<CodeSemanticEdge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Map.Entry<String, Set<String>> entry : annotationToSymbols.entrySet()) {
            String annotation = entry.getKey();
            if (skipAnnotations.contains(annotation)) continue;

            List<String> symbolIds = new ArrayList<>(entry.getValue());
            if (symbolIds.size() < 2) continue;

            // Create edges between all pairs of symbols sharing this annotation
            for (int i = 0; i < symbolIds.size(); i++) {
                for (int j = i + 1; j < symbolIds.size(); j++) {
                    String id1 = symbolIds.get(i);
                    String id2 = symbolIds.get(j);
                    String edgeKey = id1 + "|" + id2;
                    if (seen.add(edgeKey)) {
                        CodeSemanticEdge edge = new CodeSemanticEdge();
                        edge.setId(UUID.randomUUID().toString());
                        edge.setSourceSymbolId(id1);
                        edge.setTargetSymbolId(id2);
                        edge.setDirected(false);
                        edge.setRelationType(SemanticRelationType.CONCEPTUALLY_RELATED_TO);
                        edge.setConfidence(EdgeConfidence.EXTRACTED);
                        edge.setConfidenceScore(0.8);
                        edge.setRationale("Shared annotation: @" + annotation);
                        edge.setExtractorId(getExtractorId());
                        edges.add(edge);
                    }
                }
            }
        }

        return edges;
    }

    @Override
    public boolean requiresLlm() {
        return false;
    }
}
