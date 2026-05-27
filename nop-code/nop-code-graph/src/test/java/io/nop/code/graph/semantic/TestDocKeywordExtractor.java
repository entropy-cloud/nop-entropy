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
    void testExtractorId() {
        assertEquals("doc-keyword", extractor.getExtractorId());
    }

    @Test
    void testDoesNotRequireLlm() {
        assertFalse(extractor.requiresLlm());
    }

    @Test
    void testExtractsFromDocumentation() {
        SymbolTable table = new SymbolTable();

        CodeSymbol sym1 = createSymbol("com.example.UserService", "UserService", CodeSymbolKind.CLASS);
        sym1.setDocumentation("Provides user authentication and authorization services");
        CodeSymbol sym2 = createSymbol("com.example.AuthService", "AuthService", CodeSymbolKind.CLASS);
        sym2.setDocumentation("Provides user authentication and authorization handlers");
        table.add(sym1);
        table.add(sym2);

        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());

        assertFalse(edges.isEmpty());
        CodeSemanticEdge edge = edges.get(0);
        assertEquals(SemanticRelationType.CONCEPTUALLY_RELATED_TO, edge.getRelationType());
        assertEquals(EdgeConfidence.EXTRACTED, edge.getConfidence());
        assertEquals("doc-keyword", edge.getExtractorId());
    }

    @Test
    void testNoEdgesForUnrelatedDocumentation() {
        SymbolTable table = new SymbolTable();

        CodeSymbol sym1 = createSymbol("com.example.UserService", "UserService", CodeSymbolKind.CLASS);
        sym1.setDocumentation("Manages user accounts");
        CodeSymbol sym2 = createSymbol("com.example.MathUtils", "MathUtils", CodeSymbolKind.CLASS);
        sym2.setDocumentation("Provides mathematical calculation utilities");
        table.add(sym1);
        table.add(sym2);

        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());
        assertTrue(edges.isEmpty());
    }

    @Test
    void testNoEdgesWhenNoDocumentation() {
        SymbolTable table = new SymbolTable();

        CodeSymbol sym1 = createSymbol("com.example.UserService", "UserService", CodeSymbolKind.CLASS);
        CodeSymbol sym2 = createSymbol("com.example.AuthService", "AuthService", CodeSymbolKind.CLASS);
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
