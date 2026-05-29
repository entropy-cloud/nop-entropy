package io.nop.code.graph.community;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.graph.knowledge.KnowledgeGapAnalyzer;
import io.nop.code.graph.knowledge.KnowledgeGapResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestCohesionConsistency {

    private CallGraph buildTestGraph() {
        CallGraph g = new CallGraph();
        g.addEdge("pkg.A", "pkg.B");
        g.addEdge("pkg.A", "pkg.C");
        g.addEdge("pkg.B", "pkg.C");
        g.addEdge("pkg.B", "pkg.D");
        g.addEdge("pkg.E", "pkg.F");
        g.addEdge("pkg.F", "pkg.E");
        return g;
    }

    private SymbolTable buildTestSymbolTable() {
        SymbolTable st = new SymbolTable();
        String[] names = {"pkg.A", "pkg.B", "pkg.C", "pkg.D", "pkg.E", "pkg.F"};
        for (String qn : names) {
            CodeSymbol sym = new CodeSymbol();
            sym.setId(qn);
            sym.setQualifiedName(qn);
            sym.setName(qn.substring(qn.lastIndexOf('.') + 1));
            sym.setKind(CodeSymbolKind.METHOD);
            st.add(sym);
        }
        return st;
    }

    @Test
    void testCommunityDetectorCohesionMatchesKnowledgeGapFormula() throws Exception {
        CallGraph graph = buildTestGraph();
        SymbolTable symbols = buildTestSymbolTable();

        CommunityDetector.CommunityConfig config = CommunityDetector.CommunityConfig.leidenConfig();
        config.setMinCommunitySize(2);
        config.setResolution(1.0);

        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(graph, symbols, config);

        KnowledgeGapAnalyzer gapAnalyzer = new KnowledgeGapAnalyzer();
        Method computeCohesion = KnowledgeGapAnalyzer.class.getDeclaredMethod(
                "computeCohesion", List.class, CallGraph.class);
        computeCohesion.setAccessible(true);

        for (CommunityDetector.Community community : result.getCommunities()) {
            double communityCohesion = community.getCohesion();
            double gapCohesion = (double) computeCohesion.invoke(gapAnalyzer, community.getSymbolIds(), graph);

            assertEquals(gapCohesion, communityCohesion, 0.001,
                    "CommunityDetector cohesion should match KnowledgeGapAnalyzer formula for community " +
                            community.getId() + " with symbols " + community.getSymbolIds());
        }
    }

    @Test
    void testCohesionValueForFullyConnectedCluster() throws Exception {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "A");
        graph.addEdge("A", "C");
        graph.addEdge("C", "A");
        graph.addEdge("B", "C");
        graph.addEdge("C", "B");

        Set<String> cluster = new HashSet<>(Arrays.asList("A", "B", "C"));

        KnowledgeGapAnalyzer gapAnalyzer = new KnowledgeGapAnalyzer();
        Method computeCohesion = KnowledgeGapAnalyzer.class.getDeclaredMethod(
                "computeCohesion", List.class, CallGraph.class);
        computeCohesion.setAccessible(true);

        double cohesion = (double) computeCohesion.invoke(gapAnalyzer,
                Arrays.asList("A", "B", "C"), graph);

        assertEquals(1.0, cohesion, 0.001,
                "Fully internal cluster with no external edges should have cohesion = 1.0");
    }

    @Test
    void testCohesionValueForMixedCluster() throws Exception {
        CallGraph graph = new CallGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "X");

        KnowledgeGapAnalyzer gapAnalyzer = new KnowledgeGapAnalyzer();
        Method computeCohesion = KnowledgeGapAnalyzer.class.getDeclaredMethod(
                "computeCohesion", List.class, CallGraph.class);
        computeCohesion.setAccessible(true);

        double cohesion = (double) computeCohesion.invoke(gapAnalyzer,
                Arrays.asList("A", "B"), graph);

        assertEquals(0.5, cohesion, 0.001,
                "1 internal + 1 external edge should yield cohesion = 0.5");
    }
}
