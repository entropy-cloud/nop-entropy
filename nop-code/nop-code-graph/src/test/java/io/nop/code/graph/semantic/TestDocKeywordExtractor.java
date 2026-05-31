package io.nop.code.graph.semantic;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.semantic.CodeSemanticEdge;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.core.semantic.SemanticRelationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestDocKeywordExtractor {

    private final DocKeywordExtractor extractor = new DocKeywordExtractor();

    @Test
    void testOverlappingDocKeywords_producesConceptualEdge() {
        SymbolTable table = new SymbolTable();

        CodeSymbol sym1 = createSymbol("com.example.UserService", "UserService", CodeSymbolKind.CLASS);
        sym1.setDocumentation("Provides user authentication and authorization services");
        CodeSymbol sym2 = createSymbol("com.example.AuthService", "AuthService", CodeSymbolKind.CLASS);
        sym2.setDocumentation("Provides user authentication and authorization handlers");
        table.add(sym1);
        table.add(sym2);

        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());

        assertEquals(1, edges.size());
        CodeSemanticEdge edge = edges.get(0);
        assertEquals(SemanticRelationType.CONCEPTUALLY_RELATED_TO, edge.getRelationType());
        assertEquals(EdgeConfidence.EXTRACTED, edge.getConfidence());
        assertEquals("doc-keyword", edge.getExtractorId());
        assertFalse(edge.isDirected());
    }

    @Test
    void testUnrelatedDocs_noEdge() {
        SymbolTable table = new SymbolTable();

        CodeSymbol sym1 = createSymbol("com.example.UserService", "UserService", CodeSymbolKind.CLASS);
        sym1.setDocumentation("Manages user accounts");
        CodeSymbol sym2 = createSymbol("com.example.MathUtils", "MathUtils", CodeSymbolKind.CLASS);
        sym2.setDocumentation("Provides mathematical calculation utilities");
        table.add(sym1);
        table.add(sym2);

        assertTrue(extractor.extract(table, new CallGraph()).isEmpty());
    }

    @Test
    void testNoDocumentation_noEdge() {
        SymbolTable table = new SymbolTable();
        table.add(createSymbol("com.example.UserService", "UserService", CodeSymbolKind.CLASS));
        table.add(createSymbol("com.example.AuthService", "AuthService", CodeSymbolKind.CLASS));

        assertTrue(extractor.extract(table, new CallGraph()).isEmpty());
    }

    @Test
    void testEmptySymbolTable_returnsEmpty() {
        assertTrue(extractor.extract(new SymbolTable(), new CallGraph()).isEmpty());
    }

    @Test
    void testHighKeywordOverlap_producesAllPairsEdges() {
        int count = 100;
        SymbolTable table = new SymbolTable();
        for (int i = 0; i < count; i++) {
            CodeSymbol sym = createSymbol("com.example.Sym" + i, "Sym" + i, CodeSymbolKind.CLASS);
            sym.setDocumentation("alpha beta gamma delta epsilon zeta eta theta iota kappa lambda");
            table.add(sym);
        }

        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());

        assertNotNull(edges);
        assertEquals(count * (count - 1) / 2, edges.size(), "All symbols share same docs, should produce all-pairs edges");
    }

    @Test
    void testSingleKeywordDocs_noEdges() {
        int count = 5500;
        SymbolTable table = new SymbolTable();
        for (int i = 0; i < count; i++) {
            CodeSymbol sym = createSymbol("com.example.Sym" + i, "Sym" + i, CodeSymbolKind.CLASS);
            sym.setDocumentation("x" + i);
            table.add(sym);
        }

        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());

        assertNotNull(edges);
        assertTrue(edges.isEmpty(), "Symbols with single-keyword docs (< MIN_KEYWORDS=2) should produce no edges");
        assertEquals(count, table.size());
    }

    private CodeSymbol createSymbol(String qualifiedName, String name, CodeSymbolKind kind) {
        CodeSymbol sym = new CodeSymbol();
        sym.setId(qualifiedName);
        sym.setQualifiedName(qualifiedName);
        sym.setName(name);
        sym.setKind(kind);
        return sym;
    }
}
