package io.nop.code.service.graph;

import io.nop.code.api.dto.GraphWikiDTO;
import io.nop.code.core.graph.CodeEdgeData;
import io.nop.code.core.graph.CodeRelationGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TestGraphWikiExporter {

    private final IGraphWikiExporter exporter = new GraphWikiExporter();
    private final Function<String, String> names = id -> {
        Map<String, String> table = Map.of(
                "a", "demo.app.A",
                "b", "demo.util.B",
                "c", "demo.util.C",
                "hub", "demo.svc.Hub",
                "ext", "java.lang.Object");
        return table.getOrDefault(id, id);
    };
    private final Function<String, String> files = id -> "src/" + id + ".java";

    /** hub graph: hub has degree 5; a has degree 2 (call + inheritance); c cross-links */
    private CodeRelationGraph wikiGraph() {
        List<CodeEdgeData> edges = new ArrayList<>();
        edges.add(CodeEdgeData.of("CALLS", "a", "hub").confidence("EXTRACTED").build());
        edges.add(CodeEdgeData.of("CALLS", "s2", "hub").confidence("INFERRED").build());
        edges.add(CodeEdgeData.of("CALLS", "s3", "hub").confidence("INFERRED").build());
        edges.add(CodeEdgeData.of("CALLS", "s4", "hub").confidence("EXTRACTED").build());
        edges.add(CodeEdgeData.of("CALLS", "s5", "hub").confidence("EXTRACTED").build());
        edges.add(CodeEdgeData.of("INHERITANCE", "a", "b").confidence("EXTRACTED").build());
        return new CodeRelationGraph(edges);
    }

    @Test
    void testSlugDeterministicAndIllegalChars() {
        assertEquals("demo-util", GraphWikiExporter.slug("demo.util"));
        assertEquals("foo-bar-baz-qux-quux", GraphWikiExporter.slug("Foo:Bar Baz$Qux#Quux"));
        assertEquals(GraphWikiExporter.slug("A/B C"), GraphWikiExporter.slug("A-B C"));
        // truncation to 64
        String longLabel = "x".repeat(100);
        assertEquals(64, GraphWikiExporter.slug(longLabel).length());
        // empty label fallback
        assertEquals("article", GraphWikiExporter.slug("///"));
    }

    @Test
    void testIndexBodyAndArticleKeys() {
        Map<String, Integer> communities = Map.of("a", 1, "s2", 1, "b", 2, "c", 2);
        Map<Integer, Double> cohesion = Map.of(1, 0.5, 2, 0.7);
        GraphWikiDTO wiki = exporter.exportWiki(wikiGraph(), communities, cohesion,
                names, files, 20, 20);

        assertNotNull(wiki.getIndex());
        assertTrue(wiki.getIndex().contains("节点数:7"));
        assertTrue(wiki.getIndex().contains("边数:6"));
        // community rows show cohesion and exported counts
        assertTrue(wiki.getIndex().contains("内聚度"));
        assertTrue(wiki.getIndex().contains("社区数:2(导出 2)"));

        // community article keys are prefixed and present; interlink targets exist
        for (String key : wiki.getArticles().keySet()) {
            assertTrue(key.endsWith(".md"));
            assertFalse(key.equals("index.md"), "articles must not contain index.md (dto.index is the body)");
        }
        assertTrue(wiki.getArticles().size() >= 2, "two communities -> two community articles");
    }

    @Test
    void testCommunityArticleContent() {
        Map<String, Integer> communities = Map.of("a", 1, "s2", 1, "b", 2, "c", 2);
        Map<Integer, Double> cohesion = Map.of(1, 0.5, 2, 0.7);
        GraphWikiDTO wiki = exporter.exportWiki(wikiGraph(), communities, cohesion,
                names, files, 20, 20);

        String communityWithA = wiki.getArticles().values().stream()
                .filter(body -> body.contains("demo.app.A"))
                .findFirst().orElseThrow();
        // members sorted by degree desc (a degree 2 leads)
        assertTrue(communityWithA.contains("关键概念"));
        assertTrue(communityWithA.indexOf("demo.app.A") < communityWithA.indexOf("s2(度"));
        // confidence audit trail: relation type + confidence on edge lines
        assertTrue(communityWithA.contains("INHERITANCE, confidence=EXTRACTED"));

        String communityWithB = wiki.getArticles().get("community-demo-util.md");
        assertNotNull(communityWithB);
        assertTrue(communityWithB.contains("src/b.java"));
    }

    @Test
    void testHubArticleNeighborsGroupedByType() {
        GraphWikiDTO wiki = exporter.exportWiki(wikiGraph(), Collections.emptyMap(),
                Collections.emptyMap(), names, files, 20, 20);

        String hubArticle = wiki.getArticles().values().stream()
                .filter(body -> body.contains("# 枢纽 demo.svc.Hub"))
                .findFirst().orElseThrow(() -> new AssertionError("hub article expected"));
        // hub degree 5
        assertTrue(hubArticle.contains("度 5(入 5 / 出 0)"));
        // neighbors grouped by relation type with confidence markers
        assertTrue(hubArticle.contains("### CALLS"));
        assertTrue(hubArticle.contains("confidence=EXTRACTED"));
        assertTrue(hubArticle.contains("confidence=INFERRED"));
    }

    @Test
    void testCapsTruncateAndIndexOnlyListsExported() {
        Map<String, Integer> communities = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            String nodeId = "n" + i;
            communities.put(nodeId, i);
        }
        communities.put("a", 100);
        Map<Integer, Double> cohesion = new HashMap<>();
        for (int i = 1; i <= 100; i++) cohesion.put(i, 0.5);

        GraphWikiDTO wiki = exporter.exportWiki(wikiGraph(), communities, cohesion,
                names, files, 2, 1);
        // index lists only exported items (no broken links), with total counts
        assertTrue(wiki.getIndex().contains("社区数:" + communities.size() + "(导出 2)"));
        long communityArticles = wiki.getArticles().keySet().stream()
                .filter(k -> k.startsWith("community-")).count();
        assertEquals(2, communityArticles);
        long hubArticles = wiki.getArticles().keySet().stream()
                .filter(k -> k.startsWith("hub-")).count();
        assertTrue(hubArticles <= 1);
    }

    @Test
    void testDeterministicExports() {
        Map<String, Integer> communities = Map.of("a", 1, "s2", 1, "b", 2, "c", 2);
        Map<Integer, Double> cohesion = Map.of(1, 0.5, 2, 0.7);
        GraphWikiDTO first = exporter.exportWiki(wikiGraph(), communities, cohesion,
                names, files, 20, 20);
        GraphWikiDTO second = exporter.exportWiki(wikiGraph(), communities, cohesion,
                names, files, 20, 20);
        assertEquals(first.getIndex(), second.getIndex());
        assertEquals(first.getArticles(), second.getArticles());
    }

    @Test
    void testEmptyGraphProducesIndexOnly() {
        GraphWikiDTO wiki = exporter.exportWiki(new CodeRelationGraph(java.util.Collections.emptyList()),
                Collections.emptyMap(), Collections.emptyMap(), names, files, 20, 20);
        assertTrue(wiki.getIndex().contains("节点数:0"));
        assertTrue(wiki.getArticles().isEmpty());
    }

    @Test
    void testInvalidCapsNormalized() {
        GraphWikiDTO wiki = exporter.exportWiki(wikiGraph(), Collections.emptyMap(),
                Collections.emptyMap(), names, files, -1, 0);
        // normalized to 20: no exception, hub article still exported despite cap input 0
        assertTrue(wiki.getArticles().values().stream()
                .anyMatch(body -> body.contains("# 枢纽")));
    }

    @Test
    void testExternalIdDoesNotQualifyAsHub() {
        // external id with degree 5 must NOT produce a hub article (not a project symbol);
        // its neighbor lines still fall back to the raw id in other articles
        List<CodeEdgeData> edges = new ArrayList<>();
        edges.add(CodeEdgeData.of("CALLS", "u1", "java.lang.Object#x").confidence("EXTRACTED").build());
        edges.add(CodeEdgeData.of("CALLS", "u2", "java.lang.Object#x").confidence("EXTRACTED").build());
        edges.add(CodeEdgeData.of("CALLS", "u3", "java.lang.Object#x").confidence("EXTRACTED").build());
        edges.add(CodeEdgeData.of("CALLS", "u4", "java.lang.Object#x").confidence("EXTRACTED").build());
        edges.add(CodeEdgeData.of("CALLS", "u5", "java.lang.Object#x").confidence("EXTRACTED").build());
        GraphWikiDTO wiki = exporter.exportWiki(new CodeRelationGraph(edges),
                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(),
                names, files, 20, 20);
        assertTrue(wiki.getArticles().values().stream()
                .noneMatch(body -> body.contains("# 枢纽 java.lang.Object")));
    }

    @Test
    void testExternalIdNeighborFallsBackToRawId() {
        List<CodeEdgeData> edges = List.of(
                CodeEdgeData.of("INHERITANCE", "a", "java.lang.Object#x").confidence("EXTRACTED").build());
        // make 'a' a hub via 4 extra edges
        List<CodeEdgeData> edges2 = new ArrayList<>(edges);
        edges2.add(CodeEdgeData.of("CALLS", "a", "hub").confidence("EXTRACTED").build());
        edges2.add(CodeEdgeData.of("CALLS", "s2", "a").confidence("EXTRACTED").build());
        edges2.add(CodeEdgeData.of("CALLS", "s3", "a").confidence("EXTRACTED").build());
        edges2.add(CodeEdgeData.of("CALLS", "s4", "a").confidence("EXTRACTED").build());
        GraphWikiDTO wiki = exporter.exportWiki(new CodeRelationGraph(edges2),
                Collections.emptyMap(), Collections.emptyMap(), names, files, 20, 20);
        String hubArticle = wiki.getArticles().values().stream()
                .filter(body -> body.contains("# 枢纽 demo.app.A")).findFirst().orElseThrow();
        // external neighbor label falls back to raw id
        assertTrue(hubArticle.contains("java.lang.Object#x"));
    }
}
