package io.nop.code.graph.semantic;

import java.util.*;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeAnnotationUsage;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.semantic.CodeSemanticEdge;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.core.semantic.ISemanticEdgeExtractor;
import io.nop.code.core.semantic.SemanticRelationType;
/**
 * Extracts semantic edges based on shared annotation patterns.
 * Symbols annotated with the same annotations are conceptually related.
 */
public class AnnotationPatternExtractor implements ISemanticEdgeExtractor {

    private static final int MIN_SHARED_ANNOTATIONS = 1;
    private static final int MAX_SYMBOLS_PER_ANNOTATION = 100;
    private static final int MAX_EDGES = 50000;

    private static final Set<String> SKIP_ANNOTATIONS = Set.of(
            "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface",
            "java.lang.Override", "java.lang.Deprecated", "java.lang.SuppressWarnings",
            "Autowired", "org.springframework.beans.factory.annotation.Autowired",
            "Service", "org.springframework.stereotype.Service",
            "Component", "org.springframework.stereotype.Component",
            "Repository", "org.springframework.stereotype.Repository",
            "Configuration", "org.springframework.context.annotation.Configuration",
            "Bean", "org.springframework.context.annotation.Bean",
            "Test", "org.junit.jupiter.api.Test",
            "org.testng.annotations.Test"
    );

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

    @Override
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

        Set<String> skipAnnotations = SKIP_ANNOTATIONS;

        List<CodeSemanticEdge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Map.Entry<String, Set<String>> entry : annotationToSymbols.entrySet()) {
            String annotation = entry.getKey();
            if (skipAnnotations.contains(annotation)) continue;

            List<String> symbolIds = new ArrayList<>(entry.getValue());
            if (symbolIds.size() < 2) continue;

            if (symbolIds.size() > MAX_SYMBOLS_PER_ANNOTATION) {
                symbolIds = symbolIds.subList(0, MAX_SYMBOLS_PER_ANNOTATION);
            }

            for (int i = 0; i < symbolIds.size(); i++) {
                for (int j = i + 1; j < symbolIds.size(); j++) {
                    if (edges.size() >= MAX_EDGES) return edges;
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
