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
    void testSameNameDifferentParent_producesSimilarityEdge() {
        SymbolTable table = new SymbolTable();
        CodeSymbol sym1 = createSymbol("com.example.UserService", "UserService", CodeSymbolKind.CLASS);
        CodeSymbol sym2 = createSymbol("com.other.UserService", "UserService", CodeSymbolKind.CLASS);
        table.add(sym1);
        table.add(sym2);

        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());

        assertEquals(1, edges.size());
        CodeSemanticEdge edge = edges.get(0);
        assertEquals(SemanticRelationType.SEMANTICALLY_SIMILAR_TO, edge.getRelationType());
        assertEquals(EdgeConfidence.EXTRACTED, edge.getConfidence());
        assertEquals("name-sim", edge.getExtractorId());
        assertFalse(edge.isDirected());
        assertNotNull(edge.getId());
    }

    @Test
    void testDissimilarNames_producesNoEdge() {
        SymbolTable table = new SymbolTable();
        table.add(createSymbol("com.example.UserService", "UserService", CodeSymbolKind.CLASS));
        table.add(createSymbol("com.example.DatabaseConnectionPool", "DatabaseConnectionPool", CodeSymbolKind.CLASS));

        assertTrue(extractor.extract(table, new CallGraph()).isEmpty());
    }

    @Test
    void testSameParentSymbols_notConnected() {
        SymbolTable table = new SymbolTable();
        CodeSymbol sym1 = createSymbol("com.example.Foo.methodA", "methodA", CodeSymbolKind.METHOD);
        sym1.setParentId("com.example.Foo");
        CodeSymbol sym2 = createSymbol("com.example.Foo.methodB", "methodB", CodeSymbolKind.METHOD);
        sym2.setParentId("com.example.Foo");
        table.add(sym1);
        table.add(sym2);

        assertTrue(extractor.extract(table, new CallGraph()).isEmpty());
    }

    @Test
    void testEmptySymbolTable_returnsEmpty() {
        assertTrue(extractor.extract(new SymbolTable(), new CallGraph()).isEmpty());
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
