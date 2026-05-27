package io.nop.code.graph.semantic;

import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.core.semantic.CodeSemanticEdge;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.core.semantic.SemanticRelationType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestAnnotationPatternExtractor {

    private final AnnotationPatternExtractor extractor = new AnnotationPatternExtractor();

    @Test
    void testSharedAnnotation_producesConceptualEdge() {
        SymbolTable table = new SymbolTable();

        CodeFileAnalysisResult file1 = new CodeFileAnalysisResult();
        file1.setFilePath("ServiceA.java");
        CodeAnnotationUsage usage1 = new CodeAnnotationUsage();
        usage1.setAnnotationTypeQualifiedName("javax.inject.Inject");
        usage1.setAnnotatedSymbolId("sym-A");
        file1.setAnnotationUsages(Collections.singletonList(usage1));

        CodeFileAnalysisResult file2 = new CodeFileAnalysisResult();
        file2.setFilePath("ServiceB.java");
        CodeAnnotationUsage usage2 = new CodeAnnotationUsage();
        usage2.setAnnotationTypeQualifiedName("javax.inject.Inject");
        usage2.setAnnotatedSymbolId("sym-B");
        file2.setAnnotationUsages(Collections.singletonList(usage2));

        List<CodeSemanticEdge> edges = extractor.extractFromFileResults(
                List.of(file1, file2), table);

        assertEquals(1, edges.size());
        CodeSemanticEdge edge = edges.get(0);
        assertEquals(SemanticRelationType.CONCEPTUALLY_RELATED_TO, edge.getRelationType());
        assertEquals(EdgeConfidence.EXTRACTED, edge.getConfidence());
        assertEquals("annotation-pattern", edge.getExtractorId());
        assertFalse(edge.isDirected());
        assertTrue(edge.getRationale().contains("javax.inject.Inject"));
    }

    @Test
    void testCommonAnnotationsLikeOverride_ignored() {
        CodeFileAnalysisResult file1 = new CodeFileAnalysisResult();
        file1.setFilePath("A.java");
        CodeAnnotationUsage usage1 = new CodeAnnotationUsage();
        usage1.setAnnotationTypeQualifiedName("java.lang.Override");
        usage1.setAnnotatedSymbolId("sym-A");
        file1.setAnnotationUsages(Collections.singletonList(usage1));

        CodeFileAnalysisResult file2 = new CodeFileAnalysisResult();
        file2.setFilePath("B.java");
        CodeAnnotationUsage usage2 = new CodeAnnotationUsage();
        usage2.setAnnotationTypeQualifiedName("java.lang.Override");
        usage2.setAnnotatedSymbolId("sym-B");
        file2.setAnnotationUsages(Collections.singletonList(usage2));

        assertTrue(extractor.extractFromFileResults(
                List.of(file1, file2), new SymbolTable()).isEmpty());
    }

    @Test
    void testSingleFile_noEdge() {
        CodeFileAnalysisResult file1 = new CodeFileAnalysisResult();
        file1.setFilePath("A.java");
        CodeAnnotationUsage usage1 = new CodeAnnotationUsage();
        usage1.setAnnotationTypeQualifiedName("javax.inject.Inject");
        usage1.setAnnotatedSymbolId("sym-A");
        file1.setAnnotationUsages(Collections.singletonList(usage1));

        assertTrue(extractor.extractFromFileResults(
                List.of(file1), new SymbolTable()).isEmpty());
    }

    @Test
    void testStandardExtract_alwaysReturnsEmpty() {
        assertTrue(extractor.extract(new SymbolTable(), null).isEmpty());
    }
}
