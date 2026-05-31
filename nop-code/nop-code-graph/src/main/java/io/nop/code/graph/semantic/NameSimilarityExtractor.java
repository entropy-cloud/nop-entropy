package io.nop.code.graph.semantic;

import java.util.*;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.semantic.CodeSemanticEdge;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.core.semantic.ISemanticEdgeExtractor;
import io.nop.code.core.semantic.SemanticRelationType;
/**
 * Extracts semantic edges based on symbol name similarity.
 * Symbols with similar names (normalized, Levenshtein/Jaccard) that are not
 * connected via calls/inheritance get a semantically_similar_to edge.
 */
public class NameSimilarityExtractor implements ISemanticEdgeExtractor {

    private static final double SIMILARITY_THRESHOLD = 0.7;
    private static final int MAX_SYMBOLS = 5000;

    @Override
    public String getExtractorId() {
        return "name-sim";
    }

    @Override
    public List<CodeSemanticEdge> extract(SymbolTable symbolTable, CallGraph callGraph) {
        List<CodeSymbol> symbols = new ArrayList<>(symbolTable.getAll());
        if (symbols.size() > MAX_SYMBOLS) {
            symbols = symbols.subList(0, MAX_SYMBOLS);
        }

        Set<String> existingEdges = buildExistingEdgeSet(callGraph);
        List<CodeSemanticEdge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < symbols.size(); i++) {
            String name1 = normalizeName(symbols.get(i).getName());
            if (name1 == null || name1.length() < 3) continue;

            for (int j = i + 1; j < symbols.size(); j++) {
                String name2 = normalizeName(symbols.get(j).getName());
                if (name2 == null || name2.length() < 3) continue;

                // Skip symbols from the same type (parent match)
                String parent1 = symbols.get(i).getParentId();
                String parent2 = symbols.get(j).getParentId();
                if (parent1 != null && parent1.equals(parent2)) continue;

                // Skip if already connected via call graph
                String id1 = symbols.get(i).getId();
                String id2 = symbols.get(j).getId();
                String edgeKey = id1 + "|" + id2;
                if (existingEdges.contains(edgeKey)) continue;
                if (seen.contains(edgeKey)) continue;

                double similarity = jaccardSimilarity(name1, name2);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    seen.add(edgeKey);

                    CodeSemanticEdge edge = new CodeSemanticEdge();
                    edge.setId(UUID.randomUUID().toString());
                    edge.setSourceSymbolId(id1);
                    edge.setTargetSymbolId(id2);
                    edge.setDirected(false);
                    edge.setRelationType(SemanticRelationType.SEMANTICALLY_SIMILAR_TO);
                    edge.setConfidence(EdgeConfidence.EXTRACTED);
                    edge.setConfidenceScore(similarity);
                    edge.setRationale("Name similarity: " + name1 + " / " + name2);
                    edge.setExtractorId(getExtractorId());
                    edges.add(edge);
                }
            }
        }

        return edges;
    }

    @Override
    public boolean requiresLlm() {
        return false;
    }

    private Set<String> buildExistingEdgeSet(CallGraph callGraph) {
        Set<String> edges = new HashSet<>();
        for (String node : callGraph.getAllNodeIds()) {
            for (String callee : callGraph.getCallees(node)) {
                edges.add(node + "|" + callee);
            }
        }
        return edges;
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        // Convert camelCase/snake_case to lowercase words
        return name.replaceAll("([a-z])([A-Z])", "$1 $2")
                    .replaceAll("_", " ")
                    .toLowerCase()
                    .trim();
    }

    private double jaccardSimilarity(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split(" ")));
        if (setA.isEmpty() && setB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
