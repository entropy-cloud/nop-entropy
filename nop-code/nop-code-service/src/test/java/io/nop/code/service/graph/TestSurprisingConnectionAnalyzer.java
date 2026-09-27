package io.nop.code.service.graph;

import io.nop.code.api.dto.SurprisingConnectionDTO;
import io.nop.code.core.graph.CodeEdgeData;
import io.nop.code.core.graph.CodeRelationGraph;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TestSurprisingConnectionAnalyzer {

    private final ISurprisingConnectionAnalyzer analyzer = new SurprisingConnectionAnalyzer();
    private final Function<String, String> names = id -> "name:" + id;

    private CodeEdgeData edge(String type, String src, String tgt) {
        return CodeEdgeData.of(type, src, tgt).build();
    }

    @Test
    void testConfidenceBonusAllFourCases() {
        // EXTRACTED=1, INFERRED=2, AMBIGUOUS=3, missing=0 (never a fabricated default)
        CodeRelationGraph graph = new CodeRelationGraph(Arrays.asList(
                CodeEdgeData.of("CALLS", "a1", "b1").confidence("EXTRACTED").build(),
                CodeEdgeData.of("CALLS", "a2", "b2").confidence("INFERRED").build(),
                CodeEdgeData.of("CALLS", "a3", "b3").confidence("AMBIGUOUS").build(),
                edge("CALLS", "a4", "b4")));

        // minScore 0 to observe all four (the missing-confidence edge scores 0 and would be
        // filtered by the default minScore 1)
        List<SurprisingConnectionDTO> result = analyzer.analyze(graph,
                Collections.emptyMap(), names, 20, 0);
        assertEquals(4, result.size());
        for (SurprisingConnectionDTO dto : result) {
            int expected = dto.getConfidence() == null ? 0
                    : ("AMBIGUOUS".equals(dto.getConfidence()) ? 3
                    : "INFERRED".equals(dto.getConfidence()) ? 2 : 1);
            assertEquals(expected, dto.getScore());
            if (dto.getConfidence() == null) {
                assertTrue(dto.getReasons().stream().noneMatch(r -> r.startsWith("confidence")),
                        "missing confidence must not produce a confidence reason");
            }
        }
    }

    @Test
    void testCrossDirBonusUsesTopLevelDir() {
        CodeRelationGraph graph = new CodeRelationGraph(Collections.singletonList(
                CodeEdgeData.of("CALLS", "a", "b")
                        .confidence("EXTRACTED")
                        .sourceFilePath("app/src/A.java")
                        .targetFilePath("web/src/B.java")
                        .build()));
        List<SurprisingConnectionDTO> result = analyzer.analyze(graph,
                Collections.emptyMap(), names, 20, 1);
        // confBonus(1) + crossDir(2) = 3; topLevelDir = first segment (app vs web)
        assertEquals(3, result.get(0).getScore());
        assertTrue(result.get(0).getReasons().stream().anyMatch(r -> r.startsWith("cross-dir:app->web")));

        // same top dir -> no bonus
        CodeRelationGraph sameDir = new CodeRelationGraph(Collections.singletonList(
                CodeEdgeData.of("CALLS", "a", "b")
                        .confidence("EXTRACTED")
                        .sourceFilePath("app/src/A.java")
                        .targetFilePath("app/B.java")
                        .build()));
        assertEquals(1, analyzer.analyze(sameDir, Collections.emptyMap(), names, 20, 1).get(0).getScore());
    }

    @Test
    void testCrossCommunityRequiresBothEndpoints() {
        Map<String, Integer> communities = new HashMap<>();
        communities.put("a", 0);
        communities.put("b", 1);
        // c has no community entry: c->a must NOT count as cross-community (miss on one side)
        CodeRelationGraph graph = new CodeRelationGraph(Arrays.asList(
                CodeEdgeData.of("CALLS", "a", "b").confidence("EXTRACTED").build(),
                CodeEdgeData.of("CALLS", "c", "a").confidence("EXTRACTED").build()));
        List<SurprisingConnectionDTO> result = analyzer.analyze(graph, communities, names, 20, 1);

        SurprisingConnectionDTO ab = result.stream()
                .filter(d -> "a".equals(d.getSourceSymbolId())).findFirst().orElseThrow();
        assertEquals(2, ab.getScore()); // 1 conf + 1 cross-community
        assertTrue(ab.getReasons().stream().anyMatch(r -> r.startsWith("cross-community:0->1")));

        SurprisingConnectionDTO ca = result.stream()
                .filter(d -> "c".equals(d.getSourceSymbolId())).findFirst().orElseThrow();
        assertEquals(1, ca.getScore()); // conf only; endpoint 'c' missing -> dimension skipped
        assertTrue(ca.getReasons().stream().noneMatch(r -> r.startsWith("cross-community")));
    }

    @Test
    void testSimilarMultiplierTruncationAndOrder() {
        // additive score 3 (conf 1 + cross-dir 2), then (int)(3 * 1.5) = 4, then edge-hub
        CodeRelationGraph graph = new CodeRelationGraph(Collections.singletonList(
                CodeEdgeData.of("SEMANTIC", "s1", "s2")
                        .relationType("SEMANTICALLY_SIMILAR_TO")
                        .confidence("EXTRACTED")
                        .sourceFilePath("x/A.java")
                        .targetFilePath("y/B.java")
                        .build()));
        List<SurprisingConnectionDTO> result = analyzer.analyze(graph,
                Collections.emptyMap(), names, 20, 1);
        // (1 + 2) * 1.5 truncated = 4; edge-to-hub does not fire (both degrees are 1)
        assertEquals(4, result.get(0).getScore());
        assertTrue(result.get(0).getReasons().stream().anyMatch(r -> r.startsWith("similar-relation")));
        // relation reads attrs.relationType (uppercase), not the edge family
        assertEquals("SEMANTICALLY_SIMILAR_TO", result.get(0).getRelation());
    }

    @Test
    void testEdgeToHubThresholdBoundaries() {
        // hub node h with degree 5 (in), spoke s with degree 1: min=1<=2, max=5>=5 -> +1
        List<CodeEdgeData> edges = new java.util.ArrayList<>();
        edges.add(CodeEdgeData.of("CALLS", "s1", "h").confidence("EXTRACTED").build());
        edges.add(CodeEdgeData.of("CALLS", "s2", "h").confidence("EXTRACTED").build());
        edges.add(CodeEdgeData.of("CALLS", "s3", "h").confidence("EXTRACTED").build());
        edges.add(CodeEdgeData.of("CALLS", "s4", "h").confidence("EXTRACTED").build());
        CodeRelationGraph graph = new CodeRelationGraph(edges);
        SurprisingConnectionDTO dto = analyzer.analyze(graph, Collections.emptyMap(), names, 20, 1)
                .stream().filter(d -> "s1".equals(d.getSourceSymbolId())).findFirst().orElseThrow();
        // conf 1 + edge-hub 1 (h degree 4 in + s1 1 out: min=1, max=4) -> not >=5? h total in-degree=4
        // s1: in 0 out 1 (total 1); h: in 4 out 0 (total 4) -> max=4 < 5 -> no hub bonus
        assertEquals(1, dto.getScore());

        // add a 5th in-edge so h total = 5 -> max>=5 fires
        List<CodeEdgeData> edges5 = new java.util.ArrayList<>(edges);
        edges5.add(CodeEdgeData.of("CALLS", "s5", "h").confidence("EXTRACTED").build());
        CodeRelationGraph graph5 = new CodeRelationGraph(edges5);
        SurprisingConnectionDTO dto5 = analyzer.analyze(graph5, Collections.emptyMap(), names, 20, 1)
                .stream().filter(d -> "s1".equals(d.getSourceSymbolId())).findFirst().orElseThrow();
        assertEquals(2, dto5.getScore()); // conf 1 + edge-hub 1 (min=1<=2, max=5>=5)
    }

    @Test
    void testSortStabilityAndTieBreak() {
        CodeRelationGraph graph = new CodeRelationGraph(Arrays.asList(
                CodeEdgeData.of("CALLS", "zz", "aa").confidence("AMBIGUOUS").build(),
                CodeEdgeData.of("CALLS", "aa", "bb").confidence("AMBIGUOUS").build()));
        List<SurprisingConnectionDTO> result = analyzer.analyze(graph,
                Collections.emptyMap(), names, 20, 1);
        // same score 3 -> tie-break by source+target lexicographic: aa->bb before zz->aa
        assertEquals("aa", result.get(0).getSourceSymbolId());
        assertEquals("zz", result.get(1).getSourceSymbolId());
    }

    @Test
    void testMinScoreFilterAndTopN() {
        List<CodeEdgeData> edges = Arrays.asList(
                CodeEdgeData.of("CALLS", "a1", "b1").confidence("EXTRACTED").build(), // score 1
                CodeEdgeData.of("CALLS", "a2", "b2").confidence("INFERRED").build(), // score 2
                CodeEdgeData.of("CALLS", "a3", "b3").build()); // score 0
        CodeRelationGraph graph = new CodeRelationGraph(edges);

        // default minScore 1: zero-signal edge dropped
        List<SurprisingConnectionDTO> result = analyzer.analyze(graph, Collections.emptyMap(), names, 20, null);
        assertEquals(2, result.size());

        // minScore 2
        assertEquals(1, analyzer.analyze(graph, Collections.emptyMap(), names, 20, 2).size());

        // topN truncation
        assertEquals(1, analyzer.analyze(graph, Collections.emptyMap(), names, 1, null).size());
    }

    @Test
    void testEmptyGraph() {
        assertTrue(analyzer.analyze(new CodeRelationGraph(Collections.emptyList()),
                Collections.emptyMap(), names, 20, null).isEmpty());
    }

    @Test
    void testDegradedCommunitiesSkipsDimension() {
        CodeRelationGraph graph = new CodeRelationGraph(Collections.singletonList(
                CodeEdgeData.of("CALLS", "a", "b").confidence("EXTRACTED").build()));
        // empty communities map -> cross-community dimension skipped, no exception
        SurprisingConnectionDTO dto = analyzer.analyze(graph, Collections.emptyMap(), names, 20, 1).get(0);
        assertEquals(1, dto.getScore());
        assertTrue(dto.getReasons().stream().noneMatch(r -> r.startsWith("cross-community")));
    }
}
