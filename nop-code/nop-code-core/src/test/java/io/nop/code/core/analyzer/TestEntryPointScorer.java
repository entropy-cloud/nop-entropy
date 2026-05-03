package io.nop.code.core.analyzer;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EntryPointScorer scoring and classification logic.
 */
class TestEntryPointScorer {

    private SymbolTable buildTestSymbolTable() {
        SymbolTable st = new SymbolTable();

        // entry: 3 callees, 0 callers => score=3.0, ENTRY_POINT
        CodeSymbol entry = new CodeSymbol();
        entry.setId("entry");
        entry.setQualifiedName("com.test.entry");
        entry.setName("entry");
        entry.setKind(CodeSymbolKind.METHOD);
        st.add(entry);

        // util1: 0 callees, 1 caller => score=0.0, LEAF
        CodeSymbol util1 = new CodeSymbol();
        util1.setId("util1");
        util1.setQualifiedName("com.test.util1");
        util1.setName("util1");
        util1.setKind(CodeSymbolKind.METHOD);
        st.add(util1);

        // util2: 1 callee, 1 caller => score=0.5, MIDDLEWARE
        CodeSymbol util2 = new CodeSymbol();
        util2.setId("util2");
        util2.setQualifiedName("com.test.util2");
        util2.setName("util2");
        util2.setKind(CodeSymbolKind.METHOD);
        st.add(util2);

        // util3: 0 callees, 2 callers => score=0.0, LEAF
        CodeSymbol util3 = new CodeSymbol();
        util3.setId("util3");
        util3.setQualifiedName("com.test.util3");
        util3.setName("util3");
        util3.setKind(CodeSymbolKind.METHOD);
        st.add(util3);

        return st;
    }

    private CallGraph buildTestGraph() {
        CallGraph g = new CallGraph();
        // entry calls util1, util2, util3
        g.addEdge("entry", "util1");
        g.addEdge("entry", "util2");
        g.addEdge("entry", "util3");
        // util2 calls util3
        g.addEdge("util2", "util3");
        return g;
    }

    @Test
    void testEntryHasHighestScore() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        List<EntryPointScorer.EntryPointScore> scores =
                EntryPointScorer.scoreEntryPoints(graph, symbols);

        assertFalse(scores.isEmpty(), "Should have scored methods");

        // First element should be "entry" (highest score)
        EntryPointScorer.EntryPointScore top = scores.get(0);
        assertEquals("entry", top.getSymbolId());
        assertEquals(3.0, top.getScore(), 0.001);
        assertEquals(EntryPointScorer.EntryPointType.ENTRY_POINT, top.getEntryPointType());
    }

    @Test
    void testUtil1IsLeaf() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        List<EntryPointScorer.EntryPointScore> scores =
                EntryPointScorer.scoreEntryPoints(graph, symbols);

        EntryPointScorer.EntryPointScore util1Score = scores.stream()
                .filter(s -> "util1".equals(s.getSymbolId()))
                .findFirst()
                .orElseThrow();

        assertEquals(EntryPointScorer.EntryPointType.LEAF, util1Score.getEntryPointType());
        assertEquals(0, util1Score.getCalleeCount());
        assertEquals(1, util1Score.getCallerCount());
    }

    @Test
    void testUtil2IsMiddleware() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        List<EntryPointScorer.EntryPointScore> scores =
                EntryPointScorer.scoreEntryPoints(graph, symbols);

        EntryPointScorer.EntryPointScore util2Score = scores.stream()
                .filter(s -> "util2".equals(s.getSymbolId()))
                .findFirst()
                .orElseThrow();

        // score = 1 / (1 + 1) = 0.5, which is < threshold 2.0 => not ENTRY_POINT
        // calleeCount=1, callerCount=1 => MIDDLEWARE
        assertEquals(EntryPointScorer.EntryPointType.MIDDLEWARE, util2Score.getEntryPointType());
        assertEquals(1, util2Score.getCalleeCount());
        assertEquals(1, util2Score.getCallerCount());
        assertEquals(0.5, util2Score.getScore(), 0.001);
    }

    @Test
    void testScoresAreSortedDescending() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        List<EntryPointScorer.EntryPointScore> scores =
                EntryPointScorer.scoreEntryPoints(graph, symbols);

        for (int i = 1; i < scores.size(); i++) {
            assertTrue(scores.get(i - 1).getScore() >= scores.get(i).getScore(),
                    "Scores should be sorted descending: " + scores);
        }
    }

    @Test
    void testEmptySymbolTableReturnsEmpty() {
        CallGraph graph = new CallGraph();
        SymbolTable symbols = new SymbolTable();

        List<EntryPointScorer.EntryPointScore> scores =
                EntryPointScorer.scoreEntryPoints(graph, symbols);

        assertTrue(scores.isEmpty());
    }

    @Test
    void testGetEntryPointsFiltersCorrectly() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        List<EntryPointScorer.EntryPointScore> scores =
                EntryPointScorer.scoreEntryPoints(graph, symbols);

        List<EntryPointScorer.EntryPointScore> entryPoints =
                EntryPointScorer.getEntryPoints(scores);

        for (EntryPointScorer.EntryPointScore ep : entryPoints) {
            assertEquals(EntryPointScorer.EntryPointType.ENTRY_POINT, ep.getEntryPointType());
        }
        assertTrue(entryPoints.stream().anyMatch(s -> "entry".equals(s.getSymbolId())));
    }

    @Test
    void testCountByType() {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        List<EntryPointScorer.EntryPointScore> scores =
                EntryPointScorer.scoreEntryPoints(graph, symbols);

        java.util.Map<EntryPointScorer.EntryPointType, Long> counts =
                EntryPointScorer.countByType(scores);

        assertTrue(counts.getOrDefault(EntryPointScorer.EntryPointType.ENTRY_POINT, 0L) >= 1);
        assertTrue(counts.getOrDefault(EntryPointScorer.EntryPointType.LEAF, 0L) >= 1);
    }
}
