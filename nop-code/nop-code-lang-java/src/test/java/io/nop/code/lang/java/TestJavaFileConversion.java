package io.nop.code.lang.java;

import io.nop.code.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TestJavaFileConversion {

    private JavaCodeFileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaCodeFileAnalyzer();
    }

    @Test
    void testAnnotationTypeParsing() {
        String source = "package io.nop.test;\n" +
                "public @interface MyAnnotation { String value(); }\n";

        CodeFileAnalysisResult result = analyzer.analyze("MyAnnotation.java", source);
        assertNotNull(result);
        assertEquals("io.nop.test", result.getPackageName());

        CodeSymbol ann = findSymbol(result.getSymbols(), "MyAnnotation");
        assertNotNull(ann, "Should find MyAnnotation");
        assertEquals(CodeSymbolKind.ANNOTATION_TYPE, ann.getKind());
    }

    @Test
    void testEnumWithConstants() {
        String source = "package io.nop.test;\n" +
                "public enum Color { RED, GREEN, BLUE }\n";

        CodeFileAnalysisResult result = analyzer.analyze("Color.java", source);
        assertNotNull(result);

        CodeSymbol color = findSymbol(result.getSymbols(), "Color");
        assertNotNull(color, "Should find Color enum");
        assertEquals(CodeSymbolKind.ENUM, color.getKind());

        List<CodeSymbol> constants = result.getSymbols().stream()
                .filter(s -> s.getKind() == CodeSymbolKind.CONSTANT)
                .collect(Collectors.toList());
        assertEquals(3, constants.size(), "Color should have 3 constants");

        assertNotNull(findSymbol(constants, "RED"));
        assertNotNull(findSymbol(constants, "GREEN"));
        assertNotNull(findSymbol(constants, "BLUE"));
    }

    @Test
    void testInterfaceWithDefaultMethod() {
        String source = "package io.nop.test;\n" +
                "public interface Service { default void doWork() {} }\n";

        CodeFileAnalysisResult result = analyzer.analyze("Service.java", source);
        assertNotNull(result);

        CodeSymbol svc = findSymbol(result.getSymbols(), "Service");
        assertNotNull(svc, "Should find Service interface");
        assertEquals(CodeSymbolKind.INTERFACE, svc.getKind());

        CodeSymbol doWork = findSymbol(result.getSymbols(), "doWork");
        assertNotNull(doWork, "Should find doWork default method");
        assertEquals(CodeSymbolKind.METHOD, doWork.getKind());
    }

    @Test
    void testComplexFileWithMultipleTypes() {
        String source = "package io.nop.test;\n" +
                "public @interface MyAnnotation { String value(); }\n" +
                "public enum Color { RED, GREEN, BLUE }\n" +
                "public interface Service { default void doWork() {} }\n";

        CodeFileAnalysisResult result = analyzer.analyze("Complex.java", source);
        assertNotNull(result);
        assertEquals(CodeLanguage.JAVA, result.getLanguage());

        assertTrue(result.getSymbols().stream()
                .anyMatch(s -> "MyAnnotation".equals(s.getName())
                        && s.getKind() == CodeSymbolKind.ANNOTATION_TYPE));

        assertTrue(result.getSymbols().stream()
                .anyMatch(s -> "Color".equals(s.getName())
                        && s.getKind() == CodeSymbolKind.ENUM));

        assertTrue(result.getSymbols().stream()
                .anyMatch(s -> "Service".equals(s.getName())
                        && s.getKind() == CodeSymbolKind.INTERFACE));
    }

    private CodeSymbol findSymbol(List<CodeSymbol> symbols, String name) {
        return symbols.stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElse(null);
    }
}
