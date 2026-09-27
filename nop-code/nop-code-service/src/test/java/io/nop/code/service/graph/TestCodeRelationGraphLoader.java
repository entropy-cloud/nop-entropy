package io.nop.code.service.graph;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.nop.code.core.graph.CodeEdgeData;
import io.nop.code.core.graph.CodeRelationGraph;
import io.nop.code.dao.entity.NopCodeAnnotationUsage;
import io.nop.code.dao.entity.NopCodeCall;
import io.nop.code.dao.entity.NopCodeInheritance;
import io.nop.code.dao.entity.NopCodeSemanticEdge;
import io.nop.graph.api.Edge;
import io.nop.graph.algorithm.LeidenDetector;
import io.nop.graph.api.LeidenConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCodeRelationGraphLoader {

    private NopCodeCall call(String caller, String callee, String provenance) {
        NopCodeCall call = new NopCodeCall();
        call.setCallerId(caller);
        call.setCalleeId(callee);
        if (provenance != null)
            call.setProvenance(provenance);
        return call;
    }

    private NopCodeInheritance inheritance(String sub, String sup, String relationType, String provenance) {
        NopCodeInheritance inh = new NopCodeInheritance();
        inh.setSubTypeId(sub);
        inh.setSuperTypeId(sup);
        inh.setRelationType(relationType);
        if (provenance != null)
            inh.setProvenance(provenance);
        return inh;
    }

    private NopCodeAnnotationUsage annotation(String annotated, String annotationType, String provenance) {
        NopCodeAnnotationUsage usage = new NopCodeAnnotationUsage();
        usage.setAnnotatedSymbolId(annotated);
        usage.setAnnotationTypeId(annotationType);
        if (provenance != null)
            usage.setProvenance(provenance);
        return usage;
    }

    private NopCodeSemanticEdge semantic(String source, String target, String relationType,
                                         Integer confidence, Double confidenceScore, Boolean directed) {
        NopCodeSemanticEdge edge = new NopCodeSemanticEdge();
        edge.setSourceSymbolId(source);
        edge.setTargetSymbolId(target);
        edge.setRelationType(relationType);
        edge.setConfidence(confidence);
        edge.setConfidenceScore(confidenceScore);
        edge.setDirected(directed);
        return edge;
    }

    @Test
    void testConfidenceFromProvenanceMatrix() {
        assertEquals("EXTRACTED", CodeRelationGraphLoader.confidenceFromProvenance("AST_EXTRACTION"));
        assertEquals("EXTRACTED", CodeRelationGraphLoader.confidenceFromProvenance("SYMBOL_SOLVER"));
        assertEquals("INFERRED", CodeRelationGraphLoader.confidenceFromProvenance("HEURISTIC"));
        assertEquals("INFERRED", CodeRelationGraphLoader.confidenceFromProvenance("FRAMEWORK_INFERENCE"));
        // MANUAL / unknown degrade to EXTRACTED with raw provenance preserved in attrs
        assertEquals("EXTRACTED", CodeRelationGraphLoader.confidenceFromProvenance("MANUAL"));
        assertEquals("EXTRACTED", CodeRelationGraphLoader.confidenceFromProvenance("SOMETHING_NEW"));
        assertNull(CodeRelationGraphLoader.confidenceFromProvenance(null));
    }

    @Test
    void testConfidenceFromValueMatrix() {
        assertEquals("EXTRACTED", CodeRelationGraphLoader.confidenceFromValue(10));
        assertEquals("INFERRED", CodeRelationGraphLoader.confidenceFromValue(20));
        assertEquals("AMBIGUOUS", CodeRelationGraphLoader.confidenceFromValue(30));
        // 0 (persisted absence) and unknown values omit the attribute — never silently EXTRACTED
        assertNull(CodeRelationGraphLoader.confidenceFromValue(0));
        assertNull(CodeRelationGraphLoader.confidenceFromValue(99));
        assertNull(CodeRelationGraphLoader.confidenceFromValue(null));
    }

    @Test
    void testCallEdgeMapping() {
        CodeEdgeData edge = CodeRelationGraphLoader.toCallEdge(
                call("m1", "m2", "HEURISTIC"), id -> "/src/" + id + ".java");

        assertEquals("CALLS", edge.getEdgeType());
        assertEquals("m1", edge.getSourceId());
        assertEquals("m2", edge.getTargetId());
        assertEquals("INFERRED", edge.getConfidence());
        assertEquals("HEURISTIC", edge.getProvenance());
        assertEquals("/src/m1.java", edge.getSourceFilePath());
        assertEquals("/src/m2.java", edge.getTargetFilePath());
    }

    @Test
    void testCallEdgeNullProvenanceOmitsConfidence() {
        CodeEdgeData edge = CodeRelationGraphLoader.toCallEdge(call("m1", "m2", null), null);

        assertNull(edge.getConfidence());
        assertNull(edge.getProvenance());
        assertNull(edge.getSourceFilePath());
        assertNull(edge.getTargetFilePath());

        CodeRelationGraph graph = new CodeRelationGraph(java.util.Collections.singletonList(edge));
        Map<String, Object> attrs = graph.getOutEdges("m1").get(0).getAttrs();
        // relationType (family fallback) + directed are the only attrs present
        assertEquals(2, attrs.size());
        assertFalse(attrs.containsKey("confidence"));
        assertFalse(attrs.containsKey("sourceFilePath"));
    }

    @Test
    void testInheritanceEdgeMapping() {
        CodeEdgeData edge = CodeRelationGraphLoader.toInheritanceEdge(
                inheritance("sub1", "sup1", "EXTENDS", "AST_EXTRACTION"), null);

        assertEquals("INHERITANCE", edge.getEdgeType());
        assertEquals("EXTENDS", edge.getRelationType());
        assertEquals("EXTRACTED", edge.getConfidence());

        CodeRelationGraph graph = new CodeRelationGraph(java.util.Collections.singletonList(edge));
        Edge projected = graph.getOutEdges("sub1").get(0);
        assertEquals("INHERITANCE", projected.getType());
        assertEquals("EXTENDS", projected.getAttrs().get("relationType"));
    }

    @Test
    void testAnnotationEdgeMapping() {
        CodeEdgeData edge = CodeRelationGraphLoader.toAnnotationEdge(
                annotation("bean1", "ann1", "SYMBOL_SOLVER"), null);

        assertEquals("ANNOTATION", edge.getEdgeType());
        // no refinement column: CodeEdgeData.relationType stays null; the projection falls back to family
        assertNull(edge.getRelationType());
        assertEquals("EXTRACTED", edge.getConfidence());

        CodeRelationGraph graph = new CodeRelationGraph(java.util.Collections.singletonList(edge));
        Edge projected = graph.getOutEdges("bean1").get(0);
        assertEquals("ANNOTATION", projected.getAttrs().get("relationType"));
    }

    @Test
    void testSemanticEdgeMapping() {
        CodeEdgeData edge = CodeRelationGraphLoader.toSemanticEdge(
                semantic("s1", "s2", "SEMANTICALLY_SIMILAR_TO", 20, 3.0, false), null);

        assertEquals("SEMANTIC", edge.getEdgeType());
        assertEquals("SEMANTICALLY_SIMILAR_TO", edge.getRelationType());
        assertEquals("INFERRED", edge.getConfidence());
        assertFalse(edge.isDirected());
        assertEquals(3.0, edge.getWeight());
    }

    @Test
    void testSemanticEdgeDefaultsAndUnknownConfidence() {
        // confidence 0 (persisted absence) omits confidence; weight falls back to 1.0; directed defaults true
        CodeEdgeData edge = CodeRelationGraphLoader.toSemanticEdge(
                semantic("s1", "s2", "SOLVES_SAME_PROBLEM", 0, null, null), null);

        assertNull(edge.getConfidence());
        assertEquals(1.0, edge.getWeight());
        assertTrue(edge.isDirected());
    }

    @Test
    void testResolverMissOmitsFilePathWithoutFailure() {
        Function<String, String> partialResolver = id -> id.equals("known") ? "/src/Known.java" : null;
        CodeEdgeData edge = CodeRelationGraphLoader.toCallEdge(call("known", "unknown-id", "AST_EXTRACTION"),
                partialResolver);

        assertEquals("/src/Known.java", edge.getSourceFilePath());
        assertNull(edge.getTargetFilePath());
    }

    /**
     * Wiring verification: the loader's projected graph is consumable as IGraph by the existing
     * nop-graph algorithms (LeidenDetector only reads getOutEdges over an explicit node set).
     */
    @Test
    void testProjectedGraphConsumedByLeidenDetector() {
        Map<String, String> files = new HashMap<>();
        files.put("a", "/p/A.java");
        files.put("b", "/p/B.java");
        files.put("c", "/q/C.java");
        files.put("d", "/q/D.java");

        java.util.List<CodeEdgeData> edges = java.util.Arrays.asList(
                CodeRelationGraphLoader.toCallEdge(call("a", "b", "AST_EXTRACTION"), files::get),
                CodeRelationGraphLoader.toCallEdge(call("b", "a", "AST_EXTRACTION"), files::get),
                CodeRelationGraphLoader.toCallEdge(call("c", "d", "AST_EXTRACTION"), files::get),
                CodeRelationGraphLoader.toCallEdge(call("d", "c", "AST_EXTRACTION"), files::get),
                CodeRelationGraphLoader.toInheritanceEdge(inheritance("c", "a", "EXTENDS", "AST_EXTRACTION"), files::get));

        CodeRelationGraph graph = new CodeRelationGraph(edges);
        Set<String> nodes = new HashSet<>(graph.nodeIds());
        assertEquals(4, nodes.size());

        LeidenConfig config = LeidenConfig.create()
                .setResolution(0.5)
                .setMaxIterations(10)
                .setTimeoutMs(60000);
        io.nop.graph.api.CommunityResult result = LeidenDetector.detect(graph, nodes, config);

        assertTrue(result.getTotalSymbols() > 0);
        // the cross-community INHERITANCE edge carries typed attrs for downstream surprise scoring
        Edge inheritanceEdge = graph.getOutEdges("c").stream()
                .filter(e -> "INHERITANCE".equals(e.getType()))
                .findFirst().orElseThrow(() -> new AssertionError("INHERITANCE edge missing"));
        assertEquals("EXTENDS", inheritanceEdge.getAttrs().get("relationType"));
        assertEquals("/q/C.java", inheritanceEdge.getAttrs().get("sourceFilePath"));
    }
}
