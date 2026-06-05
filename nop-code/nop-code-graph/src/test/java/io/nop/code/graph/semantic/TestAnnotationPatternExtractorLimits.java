package io.nop.code.graph.semantic;

import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.core.semantic.CodeSemanticEdge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestAnnotationPatternExtractorLimits {

    private final AnnotationPatternExtractor extractor = new AnnotationPatternExtractor();

    @Test
    void testMaxSymbolsPerAnnotation_truncatesLargeSet() {
        List<CodeFileAnalysisResult> files = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            CodeFileAnalysisResult file = new CodeFileAnalysisResult();
            file.setFilePath("F" + i + ".java");
            CodeAnnotationUsage usage = new CodeAnnotationUsage();
            usage.setAnnotationTypeQualifiedName("MyCustomAnnotation");
            usage.setAnnotatedSymbolId("sym-" + i);
            file.setAnnotationUsages(Collections.singletonList(usage));
            files.add(file);
        }

        List<CodeSemanticEdge> edges = extractor.extractFromFileResults(files, new SymbolTable());
        int maxPairs = 100 * 99 / 2;
        assertTrue(edges.size() <= maxPairs,
                "Edges should be capped at MAX_SYMBOLS_PER_ANNOTATION pairs, got " + edges.size());
    }

    @Test
    void testSpringAnnotationsAreSkipped() {
        String[] springAnnotations = {
                "org.springframework.stereotype.Service",
                "org.springframework.stereotype.Component",
                "org.springframework.stereotype.Repository",
                "org.springframework.context.annotation.Configuration",
                "org.springframework.context.annotation.Bean",
                "org.springframework.beans.factory.annotation.Autowired"
        };

        for (String ann : springAnnotations) {
            CodeFileAnalysisResult file1 = new CodeFileAnalysisResult();
            file1.setFilePath("A.java");
            CodeAnnotationUsage u1 = new CodeAnnotationUsage();
            u1.setAnnotationTypeQualifiedName(ann);
            u1.setAnnotatedSymbolId("sym-A");
            file1.setAnnotationUsages(Collections.singletonList(u1));

            CodeFileAnalysisResult file2 = new CodeFileAnalysisResult();
            file2.setFilePath("B.java");
            CodeAnnotationUsage u2 = new CodeAnnotationUsage();
            u2.setAnnotationTypeQualifiedName(ann);
            u2.setAnnotatedSymbolId("sym-B");
            file2.setAnnotationUsages(Collections.singletonList(u2));

            List<CodeSemanticEdge> edges = extractor.extractFromFileResults(List.of(file1, file2), new SymbolTable());
            assertTrue(edges.isEmpty(), ann + " should be skipped, but got " + edges.size() + " edges");
        }
    }
}
