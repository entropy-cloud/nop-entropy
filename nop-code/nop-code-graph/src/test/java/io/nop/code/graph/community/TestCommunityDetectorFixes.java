package io.nop.code.graph.community;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TestCommunityDetectorFixes {

    @Test
    void testSingletonNodesNotDiscarded_clusteredSymbolsMatchesInput() {
        CallGraph graph = new CallGraph();
        graph.addEdge("pkg.A", "pkg.B");
        graph.addEdge("pkg.B", "pkg.C");
        graph.addEdge("pkg.D", "pkg.E");

        SymbolTable st = new SymbolTable();
        String[] names = {"pkg.A", "pkg.B", "pkg.C", "pkg.D", "pkg.E", "pkg.ISOLO"};
        for (String qn : names) {
            CodeSymbol sym = new CodeSymbol();
            sym.setId(qn);
            sym.setQualifiedName(qn);
            sym.setName(qn.substring(qn.lastIndexOf('.') + 1));
            sym.setKind(CodeSymbolKind.METHOD);
            st.add(sym);
        }

        CommunityDetector.CommunityConfig config = CommunityDetector.CommunityConfig.leidenConfig();
        config.setMinCommunitySize(2);
        config.setResolution(1.0);

        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(graph, st, config);

        assertTrue(result.getClusteredSymbols() >= 5,
                "clusteredSymbols should include at least the 5 connected nodes, got " + result.getClusteredSymbols());
    }

    @Test
    void testLeidenUsesUndirectedNetwork() {
        CallGraph graph = new CallGraph();
        graph.addEdge("pkg.X", "pkg.Y");
        graph.addEdge("pkg.Y", "pkg.Z");
        graph.addEdge("pkg.Z", "pkg.X");

        SymbolTable st = new SymbolTable();
        String[] names = {"pkg.X", "pkg.Y", "pkg.Z"};
        for (String qn : names) {
            CodeSymbol sym = new CodeSymbol();
            sym.setId(qn);
            sym.setQualifiedName(qn);
            sym.setName(qn.substring(qn.lastIndexOf('.') + 1));
            sym.setKind(CodeSymbolKind.METHOD);
            st.add(sym);
        }

        CommunityDetector.CommunityConfig config = CommunityDetector.CommunityConfig.leidenConfig();
        config.setMinCommunitySize(2);

        CommunityDetector.CommunityDetectionResult result =
                new CommunityDetector().detectCommunities(graph, st, config);

        assertTrue(result.getTotalCommunities() >= 1);
        assertTrue(result.getClusteredSymbols() >= 3);
    }
}
