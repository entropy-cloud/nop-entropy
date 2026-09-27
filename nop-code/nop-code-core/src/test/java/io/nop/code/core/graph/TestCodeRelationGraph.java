package io.nop.code.core.graph;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.nop.graph.api.Edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCodeRelationGraph {

    private CodeEdgeData edge(String edgeType, String source, String target) {
        return CodeEdgeData.of(edgeType, source, target).build();
    }

    @Test
    void testOutAndInEdgesBothDirections() {
        CodeRelationGraph graph = new CodeRelationGraph(Collections.singletonList(
                CodeEdgeData.of("CALLS", "a", "b").build()));

        List<Edge> out = graph.getOutEdges("a");
        assertEquals(1, out.size());
        assertEquals("a", out.get(0).getSourceId());
        assertEquals("b", out.get(0).getTargetId());
        assertEquals("CALLS", out.get(0).getType());

        List<Edge> in = graph.getInEdges("b");
        assertEquals(1, in.size());
        assertEquals("a", in.get(0).getSourceId());

        assertTrue(graph.getOutEdges("b").isEmpty());
        assertTrue(graph.getInEdges("a").isEmpty());
    }

    @Test
    void testAttrsFullAndPartial() {
        CodeEdgeData full = CodeEdgeData.of("SEMANTIC", "s1", "s2")
                .relationType("SEMANTICALLY_SIMILAR_TO")
                .confidence("EXTRACTED")
                .provenance("AST_EXTRACTION")
                .directed(false)
                .sourceFilePath("/a/X.java")
                .targetFilePath("/b/Y.java")
                .build();
        CodeRelationGraph graph = new CodeRelationGraph(Collections.singletonList(full));

        assertEquals(6, graph.getOutEdges("s1").get(0).getAttrs().size());
        assertEquals("SEMANTICALLY_SIMILAR_TO", graph.getOutEdges("s1").get(0).getAttrs().get("relationType"));
        assertEquals("EXTRACTED", graph.getOutEdges("s1").get(0).getAttrs().get("confidence"));
        assertEquals("AST_EXTRACTION", graph.getOutEdges("s1").get(0).getAttrs().get("provenance"));
        assertEquals(Boolean.FALSE, graph.getOutEdges("s1").get(0).getAttrs().get("directed"));
        assertEquals("/a/X.java", graph.getOutEdges("s1").get(0).getAttrs().get("sourceFilePath"));
        assertEquals("/b/Y.java", graph.getOutEdges("s1").get(0).getAttrs().get("targetFilePath"));

        // partial: null fields omitted, relationType falls back to family, directed always present
        CodeEdgeData partial = CodeEdgeData.of("CALLS", "p1", "p2").build();
        CodeRelationGraph graph2 = new CodeRelationGraph(Collections.singletonList(partial));
        assertEquals(2, graph2.getOutEdges("p1").get(0).getAttrs().size());
        assertEquals("CALLS", graph2.getOutEdges("p1").get(0).getAttrs().get("relationType"));
    }

    @Test
    void testRefinementInAttrsWhileTypeKeepsFamily() {
        CodeEdgeData inh = CodeEdgeData.of("INHERITANCE", "sub", "sup")
                .relationType("IMPLEMENTS")
                .build();
        CodeRelationGraph graph = new CodeRelationGraph(Collections.singletonList(inh));

        Edge edge = graph.getOutEdges("sub").get(0);
        assertEquals("INHERITANCE", edge.getType());
        assertEquals("IMPLEMENTS", edge.getAttrs().get("relationType"));
    }

    @Test
    void testMixedFamilies() {
        List<CodeEdgeData> edges = Arrays.asList(
                CodeEdgeData.of("CALLS", "m1", "m2").build(),
                CodeEdgeData.of("INHERITANCE", "c1", "c2").relationType("EXTENDS").build(),
                CodeEdgeData.of("ANNOTATION", "a1", "an1").build(),
                CodeEdgeData.of("SEMANTIC", "s1", "s2").relationType("CONCEPTUALLY_RELATED_TO")
                        .confidence("INFERRED").build());
        CodeRelationGraph graph = new CodeRelationGraph(edges);

        assertEquals("CALLS", graph.getOutEdges("m1").get(0).getType());
        assertEquals("INHERITANCE", graph.getOutEdges("c1").get(0).getType());
        assertEquals("ANNOTATION", graph.getOutEdges("a1").get(0).getType());
        assertEquals("SEMANTIC", graph.getOutEdges("s1").get(0).getType());
        assertEquals(8, graph.nodeIds().size());
    }

    @Test
    void testDuplicateEdgesKeepFirst() {
        CodeEdgeData first = CodeEdgeData.of("CALLS", "a", "b").confidence("EXTRACTED").build();
        CodeEdgeData second = CodeEdgeData.of("CALLS", "a", "b").confidence("INFERRED").build();
        CodeRelationGraph graph = new CodeRelationGraph(Arrays.asList(first, second));

        assertEquals(1, graph.getOutEdges("a").size());
        assertEquals("EXTRACTED", graph.getOutEdges("a").get(0).getAttrs().get("confidence"));
    }

    @Test
    void testSamePairDifferentRefinementNotDeduplicated() {
        List<CodeEdgeData> edges = Arrays.asList(
                CodeEdgeData.of("INHERITANCE", "sub", "sup").relationType("EXTENDS").build(),
                CodeEdgeData.of("INHERITANCE", "sub", "sup").relationType("IMPLEMENTS").build());
        CodeRelationGraph graph = new CodeRelationGraph(edges);

        assertEquals(2, graph.getOutEdges("sub").size());
    }

    @Test
    void testEmptyGraph() {
        CodeRelationGraph graph = new CodeRelationGraph(Collections.emptyList());
        assertTrue(graph.getOutEdges("any").isEmpty());
        assertTrue(graph.getInEdges("any").isEmpty());
        assertTrue(graph.nodeIds().isEmpty());

        CodeRelationGraph nullGraph = new CodeRelationGraph(null);
        assertTrue(nullGraph.nodeIds().isEmpty());
    }

    @Test
    void testNodeIdsReturnsAllEndpoints() {
        CodeRelationGraph graph = new CodeRelationGraph(Arrays.asList(
                edge("CALLS", "a", "b"),
                edge("CALLS", "b", "c")));
        Set<String> ids = graph.nodeIds();
        assertEquals(3, ids.size());
        assertTrue(ids.contains("a"));
        assertTrue(ids.contains("b"));
        assertTrue(ids.contains("c"));
    }

    @Test
    void testEdgeEqualityIgnoresAttrs() {
        Edge e1 = new Edge("a", "b", 1.0, "CALLS");
        Edge e2 = new Edge("a", "b", 1.0, "CALLS");
        e1.setAttrs(Collections.singletonMap("confidence", "EXTRACTED"));

        assertEquals(e1, e2);
    }

    @Test
    void testFailFastOnMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> CodeEdgeData.of(null, "a", "b").build());
        assertThrows(IllegalArgumentException.class, () -> CodeEdgeData.of("", "a", "b").build());
        assertThrows(IllegalArgumentException.class, () -> CodeEdgeData.of("CALLS", null, "b").build());
        assertThrows(IllegalArgumentException.class, () -> CodeEdgeData.of("CALLS", "a", null).build());
    }
}
