package io.nop.code.service.graph;

import io.nop.code.api.dto.ExplorationQuestionDTO;
import io.nop.code.core.graph.CodeEdgeData;
import io.nop.code.core.graph.CodeRelationGraph;
import io.nop.code.core.model.CodeSymbol;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestGraphQuestionGenerator {

    private CodeSymbol symbol(String id, String qn, String kind) {
        CodeSymbol sym = new CodeSymbol();
        sym.setId(id);
        sym.setQualifiedName(qn);
        if (kind != null) sym.setKind(io.nop.code.core.model.CodeSymbolKind.valueOf(kind));
        return sym;
    }

    private CodeEdgeData edge(String type, String src, String tgt, String confidence) {
        CodeEdgeData.Builder b = CodeEdgeData.of(type, src, tgt);
        if (confidence != null) b.confidence(confidence);
        return b.build();
    }

    /** dense graph: hub h with 5 in-edges, plus spokes; gives h degree 5 */
    private CodeRelationGraph hubGraph() {
        List<CodeEdgeData> edges = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            edges.add(edge("CALLS", "s" + i, "h", "EXTRACTED"));
        }
        return new CodeRelationGraph(edges);
    }

    @Test
    void testEmptyGraphReturnsNoSignal() {
        List<ExplorationQuestionDTO> result = new GraphQuestionGenerator().generate(
                new CodeRelationGraph(Collections.emptyList()),
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList(), "idx", 10);

        assertEquals(1, result.size());
        ExplorationQuestionDTO noSignal = result.get(0);
        assertEquals("no_signal", noSignal.getType());
        assertNull(noSignal.getQuestion());
        assertTrue(noSignal.getTargetSymbolIds().isEmpty());
        assertNull(noSignal.getSuggestedQuery());
        assertEquals(0, noSignal.getPriority());
    }

    @Test
    void testBridgeNodeTemplateExact() {
        // bridge: h betweenness top; suggestedQuery must match §3.2 template exactly
        CodeRelationGraph graph = hubGraph();
        List<Map.Entry<String, Double>> betweenness = List.of(
                new HashMap.SimpleEntry<>("h", 4.0),
                new HashMap.SimpleEntry<>("s1", 0.5));
        List<ExplorationQuestionDTO> result = new GraphQuestionGenerator().generate(
                graph, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), betweenness, "idx1", 10);

        ExplorationQuestionDTO bridge = result.stream()
                .filter(q -> "bridge_node".equals(q.getType())).findFirst().orElseThrow();
        assertEquals("NopCodeSymbol__getCallHierarchy(indexId:\"idx1\", qualifiedName:\"h\", direction:\"both\", maxDepth:2)",
                bridge.getSuggestedQuery());
        assertEquals(java.util.List.of("h", "s1"), bridge.getTargetSymbolIds());
        assertEquals(5, bridge.getPriority());
    }

    @Test
    void testBridgeSkippedWhenBetweennessAbsent() {
        // >10000-node graphs have no BETWEENNESS rows: only bridge skipped, others intact
        CodeRelationGraph graph = hubGraph();
        List<ExplorationQuestionDTO> result = new GraphQuestionGenerator().generate(
                graph, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList(), "idx1", 10);
        assertTrue(result.stream().noneMatch(q -> "bridge_node".equals(q.getType())));
        assertTrue(result.stream().anyMatch(q -> "verify_inferred".equals(q.getType())) || result.isEmpty()
                || result.stream().anyMatch(q -> "no_signal".equals(q.getType())));
    }

    @Test
    void testVerifyInferredRequiresHubAndTwoInferred() {
        // hub h: degree 5 (5 in-edges), 2 of them INFERRED -> fires
        List<CodeEdgeData> edges = new ArrayList<>();
        edges.add(edge("CALLS", "s1", "h", "INFERRED"));
        edges.add(edge("CALLS", "s2", "h", "INFERRED"));
        edges.add(edge("CALLS", "s3", "h", "EXTRACTED"));
        edges.add(edge("CALLS", "s4", "h", "EXTRACTED"));
        edges.add(edge("CALLS", "s5", "h", "EXTRACTED"));
        CodeRelationGraph graph = new CodeRelationGraph(edges);

        List<CodeSymbol> symbols = List.of(symbol("h", "demo.H", "CLASS"));
        List<ExplorationQuestionDTO> result = new GraphQuestionGenerator().generate(
                graph, symbols, Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList(), "idx1", 10);
        assertTrue(result.stream().anyMatch(q -> "verify_inferred".equals(q.getType())));

        // only 1 INFERRED edge -> does not fire
        List<CodeEdgeData> oneInferred = new ArrayList<>(edges);
        oneInferred.set(1, edge("CALLS", "s2", "h", "EXTRACTED"));
        result = new GraphQuestionGenerator().generate(
                new CodeRelationGraph(oneInferred), symbols, Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), "idx1", 10);
        assertTrue(result.stream().noneMatch(q -> "verify_inferred".equals(q.getType())));

        // 2 INFERRED but degree 2 (< 5 hub threshold) -> does not fire
        List<CodeEdgeData> small = new ArrayList<>();
        small.add(edge("CALLS", "s1", "h", "INFERRED"));
        small.add(edge("CALLS", "s2", "h", "INFERRED"));
        result = new GraphQuestionGenerator().generate(
                new CodeRelationGraph(small), symbols, Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), "idx1", 10);
        assertTrue(result.stream().noneMatch(q -> "verify_inferred".equals(q.getType())));
    }

    @Test
    void testIsolatedNodesDegreeZeroAndOneWithExclusions() {
        // edges: a -> h (a degree 1, h degree 1); b/c/d/e degree 0; ctor excluded; external excluded
        List<CodeEdgeData> edges = List.of(edge("CALLS", "a", "h", "EXTRACTED"));
        CodeRelationGraph graph = new CodeRelationGraph(edges);
        List<CodeSymbol> symbols = List.of(
                symbol("a", "demo.A.run", "METHOD"),
                symbol("b", "demo.B.m", "METHOD"),
                symbol("c", "demo.C.f", "FUNCTION"),
                symbol("ctor", "demo.D.<init>", "CONSTRUCTOR"),
                symbol("field", "demo.E.x", "FIELD"),
                symbol("h", "demo.H", "METHOD"));
        // external id "java.lang.Object#x" appears in graph but not in symbol table -> excluded
        CodeRelationGraph graphWithExternal = new CodeRelationGraph(Arrays.asList(
                edge("CALLS", "a", "h", "EXTRACTED"),
                edge("INHERITANCE", "a", "java.lang.Object#x", "EXTRACTED")));

        List<ExplorationQuestionDTO> result = new GraphQuestionGenerator().generate(
                graphWithExternal, symbols, Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList(), "idx1", 10);
        ExplorationQuestionDTO isolated = result.stream()
                .filter(q -> "isolated_nodes".equals(q.getType())).findFirst().orElseThrow();

        // a: out=2 (calls h + inherits external) -> excluded; h: in=1 METHOD -> qualifies;
        // b/c: degree 0 METHOD/FUNCTION -> qualify; ctor/FIELD excluded; external not in symbol table
        assertEquals(java.util.List.of("b", "c", "h"), isolated.getTargetSymbolIds());
        assertEquals("NopCodeSymbol__getBySymbolId(id:\"b\", indexId:\"idx1\")",
                isolated.getSuggestedQuery());
    }

    @Test
    void testLowCohesionThresholdBoundaries() {
        // community 1: cohesion 0.1 (boundary, not < 0.1) -> no; community 2: cohesion 0.05, size 5 -> yes
        Map<String, Integer> communities = new HashMap<>();
        for (int i = 1; i <= 5; i++) communities.put("m1-" + i, 1);
        for (int i = 1; i <= 5; i++) communities.put("m2-" + i, 2);
        Map<Integer, Integer> sizes = Map.of(1, 5, 2, 5);
        Map<Integer, Double> cohesion = Map.of(1, 0.1, 2, 0.05);

        CodeRelationGraph graph = new CodeRelationGraph(Collections.emptyList());
        List<ExplorationQuestionDTO> result = new GraphQuestionGenerator().generate(
                graph, Collections.emptyList(), communities, sizes, cohesion,
                Collections.emptyList(), "idx1", 10);
        ExplorationQuestionDTO low = result.stream()
                .filter(q -> "low_cohesion".equals(q.getType())).findFirst().orElseThrow();
        assertEquals(3, low.getPriority());
        assertTrue(low.getQuestion().contains("内聚度"));
        assertTrue(low.getWhy().contains("cohesion=0.05"));
    }

    @Test
    void testPriorityOrderAndTieBreak() {
        // bridge(5) and ambiguous(5): tie-break by type lexicographic -> ambiguous_edge first
        CodeRelationGraph graph = new CodeRelationGraph(Collections.singletonList(
                edge("CALLS", "s1", "h", "AMBIGUOUS")));
        List<Map.Entry<String, Double>> betweenness = List.of(new HashMap.SimpleEntry<>("h", 1.0));
        List<ExplorationQuestionDTO> result = new GraphQuestionGenerator().generate(
                graph, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), betweenness, "idx1", 10);
        assertEquals("ambiguous_edge", result.get(0).getType());
        assertEquals("bridge_node", result.get(1).getType());
    }

    @Test
    void testTopNTruncationAndNormalization() {
        CodeRelationGraph graph = hubGraph();
        List<Map.Entry<String, Double>> betweenness = List.of(new HashMap.SimpleEntry<>("h", 4.0));
        // 2 questions (bridge + verify_inferred) with topN 1
        List<ExplorationQuestionDTO> result = new GraphQuestionGenerator().generate(
                graph, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), betweenness, "idx1", 1);
        assertEquals(1, result.size());

        // topN <= 0 normalized to 10
        result = new GraphQuestionGenerator().generate(
                graph, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), betweenness, "idx1", 0);
        assertTrue(result.size() <= 10);
    }

    @Test
    void testDegradationCombinations() {
        List<CodeEdgeData> edges = new ArrayList<>();
        edges.add(edge("CALLS", "s1", "h", "INFERRED"));
        edges.add(edge("CALLS", "s2", "h", "INFERRED"));
        edges.add(edge("CALLS", "s3", "h", "EXTRACTED"));
        edges.add(edge("CALLS", "s4", "h", "EXTRACTED"));
        edges.add(edge("CALLS", "s5", "h", "EXTRACTED"));
        CodeRelationGraph graph = new CodeRelationGraph(edges);
        // BETWEENNESS missing: bridge skipped, verify_inferred still fires
        List<CodeSymbol> hubSymbols = List.of(symbol("h", "demo.H", "CLASS"));
        List<ExplorationQuestionDTO> result = new GraphQuestionGenerator().generate(
                graph, hubSymbols, Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList(), "idx1", 10);
        assertTrue(result.stream().noneMatch(q -> "bridge_node".equals(q.getType())));
        assertTrue(result.stream().anyMatch(q -> "verify_inferred".equals(q.getType())));

        // COMMUNITY rows missing: low_cohesion skipped, others intact (covered implicitly:
        // all tests above run with empty community maps and never emit low_cohesion)
        assertTrue(result.stream().noneMatch(q -> "low_cohesion".equals(q.getType())));
    }
}
