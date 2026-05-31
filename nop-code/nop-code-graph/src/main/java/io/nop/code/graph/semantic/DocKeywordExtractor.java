package io.nop.code.graph.semantic;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.semantic.CodeSemanticEdge;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.core.semantic.ISemanticEdgeExtractor;
import io.nop.code.core.semantic.SemanticRelationType;
/**
 * Extracts semantic edges based on documentation keyword overlap.
 * Symbols with significant keyword overlap in their documentation get
 * a conceptually_related_to edge.
 */
public class DocKeywordExtractor implements ISemanticEdgeExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(DocKeywordExtractor.class);

    private static final double OVERLAP_THRESHOLD = 0.5;
    private static final int MIN_KEYWORDS = 2;
    private static final int MAX_SYMBOLS = 5000;

    @Override
    public String getExtractorId() {
        return "doc-keyword";
    }

    @Override
    public List<CodeSemanticEdge> extract(SymbolTable symbolTable, CallGraph callGraph) {
        List<CodeSymbol> symbols = new ArrayList<>(symbolTable.getAll());
        List<CodeSemanticEdge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Only consider symbols with documentation
        List<CodeSymbol> withDocs = new ArrayList<>();
        for (CodeSymbol sym : symbols) {
            if (sym.getDocumentation() != null && !sym.getDocumentation().isEmpty()) {
                withDocs.add(sym);
            }
        }

        if (withDocs.size() > MAX_SYMBOLS) {
            LOG.warn("DocKeywordExtractor: truncating from {} to {} symbols with docs to prevent O(N^2) explosion",
                    withDocs.size(), MAX_SYMBOLS);
            withDocs = withDocs.subList(0, MAX_SYMBOLS);
        }

        for (int i = 0; i < withDocs.size(); i++) {
            Set<String> keywords1 = extractKeywords(withDocs.get(i).getDocumentation());
            if (keywords1.size() < MIN_KEYWORDS) continue;

            for (int j = i + 1; j < withDocs.size(); j++) {
                Set<String> keywords2 = extractKeywords(withDocs.get(j).getDocumentation());
                if (keywords2.size() < MIN_KEYWORDS) continue;

                Set<String> intersection = new HashSet<>(keywords1);
                intersection.retainAll(keywords2);

                if (intersection.size() < MIN_KEYWORDS) continue;

                Set<String> union = new HashSet<>(keywords1);
                union.addAll(keywords2);

                double overlap = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
                if (overlap >= OVERLAP_THRESHOLD) {
                    String id1 = withDocs.get(i).getId();
                    String id2 = withDocs.get(j).getId();
                    String edgeKey = id1 + "|" + id2;
                    if (seen.add(edgeKey)) {
                        CodeSemanticEdge edge = new CodeSemanticEdge();
                        edge.setId(UUID.randomUUID().toString());
                        edge.setSourceSymbolId(id1);
                        edge.setTargetSymbolId(id2);
                        edge.setDirected(false);
                        edge.setRelationType(SemanticRelationType.CONCEPTUALLY_RELATED_TO);
                        edge.setConfidence(EdgeConfidence.EXTRACTED);
                        edge.setConfidenceScore(overlap);
                        edge.setRationale("Shared doc keywords: " + String.join(", ", intersection));
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

    private Set<String> extractKeywords(String documentation) {
        if (documentation == null) return Collections.emptySet();
        Set<String> keywords = new HashSet<>();
        String[] words = documentation.toLowerCase().split("[\\s,.;:!?()\\[\\]{}\"']+");
        for (String word : words) {
            if (word.length() >= 3 && !isStopWord(word)) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "this", "that", "with", "from", "are", "was",
            "were", "been", "have", "has", "had", "will", "would", "could",
            "should", "may", "might", "shall", "can", "not", "but", "also",
            "into", "over", "only", "than", "then", "when", "what", "how",
            "all", "each", "does", "get", "got", "use", "used", "using",
            "such", "here", "more", "some", "very", "just", "because",
            "about", "which", "their", "other", "after", "before"
    );

    private boolean isStopWord(String word) {
        return STOP_WORDS.contains(word);
    }
}
