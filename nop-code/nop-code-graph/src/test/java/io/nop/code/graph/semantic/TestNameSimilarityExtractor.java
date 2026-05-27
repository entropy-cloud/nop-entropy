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

class TestNameSimilarityExtractor {

    private final NameSimilarityExtractor extractor = new NameSimilarityExtractor();

    @Test
    void testExtractorId() {
        assertEquals("name-sim", extractor.getExtractorId());
    }

    @Test
    void testDoesNotRequireLlm() {
        assertFalse(extractor.requiresLlm());
    }

    @Test
    void testExtractsSimilarNames() {
        SymbolTable table = new SymbolTable();

        CodeSymbol sym1 = createSymbol("com.example.UserService", "UserService", CodeSymbolKind.CLASS);
        CodeSymbol sym2 = createSymbol("com.other.UserService", "UserService", CodeSymbolKind.CLASS);
        table.add(sym1);
        table.add(sym2);

        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());

        assertFalse(edges.isEmpty());
        CodeSemanticEdge edge = edges.get(0);
        assertEquals(SemanticRelationType.SEMANTICALLY_SIMILAR_TO, edge.getRelationType());
        assertEquals(EdgeConfidence.EXTRACTED, edge.getConfidence());
        assertEquals("name-sim", edge.getExtractorId());
        assertFalse(edge.isDirected());
        assertNotNull(edge.getId());
    }

    @Test
    void testNoEdgesForDissimilarNames() {
        SymbolTable table = new SymbolTable();

        CodeSymbol sym1 = createSymbol("com.example.UserService", "UserService", CodeSymbolKind.CLASS);
        CodeSymbol sym2 = createSymbol("com.example.DatabaseConnectionPool", "DatabaseConnectionPool", CodeSymbolKind.CLASS);
        table.add(sym1);
        table.add(sym2);

        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());
        assertTrue(edges.isEmpty());
    }

    @Test
    void testEmptySymbolTable() {
        List<CodeSemanticEdge> edges = extractor.extract(new SymbolTable(), new CallGraph());
        assertTrue(edges.isEmpty());
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
