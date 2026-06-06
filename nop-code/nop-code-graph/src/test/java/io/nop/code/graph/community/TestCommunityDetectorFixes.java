package io.nop.code.graph.community;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
    void testCommunityGetSymbolIdsReturnsMutableList() {
        CommunityDetector.Community community = new CommunityDetector.Community();
        community.setId("test-comm");
        List<String> ids = community.getSymbolIds();
        assertNotNull(ids);
        assertDoesNotThrow(() -> ids.add("symbol-1"),
                "getSymbolIds() must return a mutable list");
        assertEquals(1, ids.size());
        assertEquals("symbol-1", ids.get(0));
    }

    @Test
    void testCommunityGetSymbolIds_mutabilityAfterSet() {
        CommunityDetector.Community community = new CommunityDetector.Community();
        community.setSymbolIds(new ArrayList<>(List.of("a", "b")));
        List<String> ids = community.getSymbolIds();
        assertDoesNotThrow(() -> ids.add("c"),
                "getSymbolIds() must return the backing mutable list");
        assertEquals(3, ids.size());
    }
}
