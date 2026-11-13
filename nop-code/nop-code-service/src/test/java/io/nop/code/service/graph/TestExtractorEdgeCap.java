package io.nop.code.service.graph;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.semantic.CodeSemanticEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-13 (plan 2565, adjudication H / facility F2, TP-006): NameSimilarityExtractor and
 * DocKeywordExtractor must cap their O(N^2) output (aligned with AnnotationPatternExtractor
 * MAX_EDGES convention), WARN on truncation, expose an observable truncation flag, and keep
 * the injection seam (instance field + package setter, default = compile-time constant).
 * The save-loop batching legs (BatchQueue + chunked flush) live in the service persist path;
 * their observability seam registration is in the ledger (R1-m4).
 */
class TestExtractorEdgeCap {

    private CodeSymbol symbol(String id, String name) {
        CodeSymbol s = new CodeSymbol();
        s.setId(id);
        s.setName(name);
        s.setQualifiedName("com.example." + name);
        s.setKind(CodeSymbolKind.METHOD);
        return s;
    }

    @Test
    void testNameSimilarityExtractorCapsEdges() {
        NameSimilarityExtractor extractor = new NameSimilarityExtractor();
        extractor.setMaxEdgesForTest(3);

        SymbolTable table = new SymbolTable();
        // identical names -> similarity 1.0 for every pair -> O(N^2) candidate edges
        for (int i = 0; i < 50; i++) {
            table.add(symbol("id-" + i, "process data"));
        }
        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());

        assertTrue(edges.size() <= 3, "edges must be capped, got " + edges.size());
        assertTrue(extractor.isLastRunTruncated(), "truncation flag must be set");
    }

    @Test
    void testNameSimilarityExtractorNotTruncatedBelowCap() {
        NameSimilarityExtractor extractor = new NameSimilarityExtractor();
        SymbolTable table = new SymbolTable();
        table.add(symbol("id-1", "process data"));
        table.add(symbol("id-2", "process data"));
        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());
        assertFalse(edges.isEmpty());
        assertFalse(extractor.isLastRunTruncated(), "below the cap no truncation flag");
    }

    @Test
    void testDocKeywordExtractorCapsEdges() {
        DocKeywordExtractor extractor = new DocKeywordExtractor();
        extractor.setMaxEdgesForTest(2);

        SymbolTable table = new SymbolTable();
        for (int i = 0; i < 40; i++) {
            CodeSymbol s = symbol("dk-" + i, "doc method " + i);
            s.setDocumentation("payment invoice processing workflow engine module");
            table.add(s);
        }
        List<CodeSemanticEdge> edges = extractor.extract(table, new CallGraph());

        assertTrue(edges.size() <= 2, "edges must be capped, got " + edges.size());
        assertTrue(extractor.isLastRunTruncated(), "truncation flag must be set");
    }

    @Test
    void testDefaultCapConstantAlignsWithConvention() throws Exception {
        // structural pin: default = the compile-time constant aligned with the
        // AnnotationPatternExtractor convention (single-unit oracle)
        FieldAccessor a = new FieldAccessor(NameSimilarityExtractor.class, "maxEdges");
        FieldAccessor b = new FieldAccessor(DocKeywordExtractor.class, "maxEdges");
        assertEquals(50000, a.getInt(new NameSimilarityExtractor()));
        assertEquals(50000, b.getInt(new DocKeywordExtractor()));
    }

    private static final class FieldAccessor {
        private final java.lang.reflect.Field field;

        FieldAccessor(Class<?> clazz, String name) throws Exception {
            field = clazz.getDeclaredField(name);
            field.setAccessible(true);
        }

        int getInt(Object target) throws Exception {
            return field.getInt(target);
        }
    }
}
